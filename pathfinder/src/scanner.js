/**
 * PentaMind PathFinder - 路径扫描引擎
 * 
 * 核心能力：
 *   1. 高效并发 HTTP 请求（可控并发数）
 *   2. 智能状态码判断（区分真实页面与自定义404）
 *   3. 敏感文件内容特征匹配
 *   4. 备份文件检测（Content-Type + Content-Length）
 *   5. 实时进度回调 + 结果收集
 *
 * 使用 Node.js 原生 http/https 模块，零外部依赖
 */

const http = require('http');
const https = require('https');
const { URL } = require('url');
const { EventEmitter } = require('events');

// ========== 常量 ==========

/** 自定义 404 页面的常见特征 */
const CUSTOM_404_SIGNATURES = [
    'page not found', 'not found', '404', 'does not exist',
    'page you requested', 'no longer available', 'couldn\'t find',
    'we can\'t find', '找不到', '页面不存在', '无法找到',
];

/** 敏感内容特征（用于验证文件内容） */
const SENSITIVE_CONTENT_PATTERNS = [
    { pattern: /DB_PASSWORD|DB_HOST|DB_NAME|MYSQL_PASSWORD/i, tag: 'ENV_DATABASE', severity: 'CRITICAL' },
    { pattern: /AWS_ACCESS_KEY|AWS_SECRET|AZURE_KEY|GOOGLE_APPLICATION/i, tag: 'CLOUD_CREDENTIALS', severity: 'CRITICAL' },
    { pattern: /api[_-]?key|apikey|secret[_-]?key|private[_-]?key/i, tag: 'API_KEY', severity: 'CRITICAL' },
    { pattern: /-----BEGIN (RSA |DSA |EC )?PRIVATE KEY-----/i, tag: 'PRIVATE_KEY', severity: 'CRITICAL' },
    { pattern: /password\s*[:=]\s*['"]/i, tag: 'HARDCODED_PASSWORD', severity: 'CRITICAL' },
    { pattern: /\[core\]\s*\n\s*repositoryformatversion/i, tag: 'GIT_CONFIG', severity: 'HIGH' },
    { pattern: /ref:\s*refs\/heads\//i, tag: 'GIT_HEAD', severity: 'HIGH' },
    { pattern: /phpinfo\(\)/i, tag: 'PHPINFO', severity: 'HIGH' },
    { pattern: /swagger|openapi/i, tag: 'API_DOCS', severity: 'MEDIUM' },
    { pattern: /CREATE TABLE|INSERT INTO|DROP TABLE/i, tag: 'SQL_DUMP', severity: 'CRITICAL' },
    { pattern: /Index of \//i, tag: 'DIRECTORY_LISTING', severity: 'HIGH' },
    { pattern: /<web-app/i, tag: 'JAVA_WEB_XML', severity: 'MEDIUM' },
    { pattern: /\[mysqld\]|\[client\]/i, tag: 'MYSQL_CONFIG', severity: 'HIGH' },
];

/** 备份文件的 Content-Type */
const ARCHIVE_CONTENT_TYPES = [
    'application/zip', 'application/x-zip-compressed',
    'application/gzip', 'application/x-gzip', 'application/x-tar',
    'application/x-rar-compressed', 'application/x-7z-compressed',
    'application/octet-stream',
];

// ========== 扫描引擎类 ==========

class Scanner extends EventEmitter {
    /**
     * @param {Object} options
     * @param {string} options.target - 目标 URL（如 https://example.com）
     * @param {string[]} options.paths - 要扫描的路径数组
     * @param {number} [options.concurrency=20] - 并发数
     * @param {number} [options.timeout=8000] - 单次请求超时 ms
     * @param {string} [options.method='GET'] - 请求方法
     * @param {Object} [options.headers={}] - 自定义请求头
     * @param {number[]} [options.validCodes] - 视为有效的状态码
     * @param {boolean} [options.followRedirect=false] - 是否跟随重定向
     * @param {boolean} [options.detectContent=true] - 是否检测响应内容
     * @param {string[]} [options.extensions=[]] - 额外扩展名 (如 ['.php', '.asp'])
     */
    constructor(options) {
        super();
        this.target = options.target.replace(/\/+$/, '');
        this.paths = options.paths || [];
        this.concurrency = options.concurrency || 20;
        this.timeout = options.timeout || 8000;
        this.method = (options.method || 'GET').toUpperCase();
        this.customHeaders = options.headers || {};
        this.validCodes = new Set(options.validCodes || [200, 201, 202, 204, 301, 302, 303, 307, 308, 401, 403]);
        this.followRedirect = options.followRedirect || false;
        this.detectContent = options.detectContent !== false;
        this.extensions = options.extensions || [];

        // 状态
        this.results = [];
        this.total = 0;
        this.completed = 0;
        this.running = false;
        this.aborted = false;
        this.startTime = 0;

        // 自定义 404 基准（扫描启动时自动校准）
        this._baseline404 = null;
    }

    /**
     * 开始扫描
     */
    async start() {
        this.running = true;
        this.aborted = false;
        this.startTime = Date.now();
        this.results = [];
        this.completed = 0;

        // 展开扩展名
        const expandedPaths = this._expandPaths(this.paths);
        this.total = expandedPaths.length;

        this.emit('start', { target: this.target, total: this.total });

        // 先探测基准 404 响应
        await this._calibrate404();

        // 并发扫描
        await this._runConcurrent(expandedPaths);

        this.running = false;
        const duration = Date.now() - this.startTime;
        this.emit('complete', {
            target: this.target,
            total: this.total,
            found: this.results.length,
            duration,
            results: this.results,
        });

        return this.results;
    }

    /** 中止扫描 */
    abort() {
        this.aborted = true;
        this.emit('abort');
    }

    /**
     * 校准自定义 404 ——
     * 请求一个几乎不可能存在的随机路径，记录其状态码和响应体长度
     */
    async _calibrate404() {
        const randomPath = `__pentamind_404_test_${Date.now()}_${Math.random().toString(36).slice(2)}`;
        try {
            const res = await this._request(randomPath);
            this._baseline404 = {
                statusCode: res.statusCode,
                bodyLength: res.body?.length || 0,
                bodySnippet: (res.body || '').slice(0, 500).toLowerCase(),
            };
            this.emit('calibrate', this._baseline404);
        } catch {
            this._baseline404 = null;
        }
    }

    /**
     * 展开路径 + 扩展名
     */
    _expandPaths(paths) {
        if (!this.extensions.length) return [...paths];
        const expanded = new Set(paths);
        for (const p of paths) {
            for (const ext of this.extensions) {
                expanded.add(p + ext);
            }
        }
        return Array.from(expanded);
    }

    /**
     * 并发控制器
     */
    async _runConcurrent(paths) {
        let index = 0;
        const workers = [];

        const worker = async () => {
            while (index < paths.length && !this.aborted) {
                const i = index++;
                const p = paths[i];
                try {
                    await this._probe(p);
                } catch {
                    // 超时或网络错误，跳过
                }
                this.completed++;
                if (this.completed % 10 === 0 || this.completed === this.total) {
                    this.emit('progress', {
                        completed: this.completed,
                        total: this.total,
                        percent: Math.round((this.completed / this.total) * 100),
                        found: this.results.length,
                        elapsed: Date.now() - this.startTime,
                    });
                }
            }
        };

        for (let i = 0; i < Math.min(this.concurrency, paths.length); i++) {
            workers.push(worker());
        }

        await Promise.all(workers);
    }

    /**
     * 探测单个路径
     */
    async _probe(pathStr) {
        const res = await this._request(pathStr);
        const { statusCode, headers, body } = res;

        // 1. 状态码过滤
        if (!this.validCodes.has(statusCode)) return;

        // 2. 自定义 404 检测
        if (this._isCustom404(res)) return;

        // 3. 构建结果
        const contentType = headers['content-type'] || '';
        const contentLength = parseInt(headers['content-length'] || '0', 10) || (body ? body.length : 0);
        const redirectUrl = headers['location'] || null;

        const result = {
            path: '/' + pathStr,
            url: `${this.target}/${pathStr}`,
            statusCode,
            contentType: contentType.split(';')[0].trim(),
            contentLength,
            redirectUrl,
            findings: [],
            timestamp: Date.now(),
        };

        // 4. 敏感内容检测
        if (this.detectContent && body) {
            for (const rule of SENSITIVE_CONTENT_PATTERNS) {
                if (rule.pattern.test(body)) {
                    result.findings.push({ tag: rule.tag, severity: rule.severity });
                }
            }
        }

        // 5. 备份文件检测
        if (ARCHIVE_CONTENT_TYPES.some(ct => contentType.includes(ct)) && contentLength > 1024) {
            result.findings.push({ tag: 'ARCHIVE_FILE', severity: 'HIGH' });
        }

        // 6. 目录列表检测
        if (body && /Index of \//i.test(body)) {
            result.findings.push({ tag: 'DIRECTORY_LISTING', severity: 'HIGH' });
        }

        // 7. 403 可能意味着存在但禁止访问
        if (statusCode === 403) {
            result.findings.push({ tag: 'FORBIDDEN_EXISTS', severity: 'MEDIUM' });
        }

        // 8. 401 需要认证
        if (statusCode === 401) {
            result.findings.push({ tag: 'AUTH_REQUIRED', severity: 'MEDIUM' });
        }

        this.results.push(result);
        this.emit('found', result);
    }

    /**
     * 判断是否为自定义 404 页面
     */
    _isCustom404(res) {
        if (!this._baseline404) return false;

        // 状态码与基准一致 + 响应体长度差异 <= 10%
        if (res.statusCode === this._baseline404.statusCode) {
            const lenDiff = Math.abs((res.body?.length || 0) - this._baseline404.bodyLength);
            const threshold = Math.max(this._baseline404.bodyLength * 0.1, 50);
            if (lenDiff <= threshold) return true;
        }

        // 内容含典型 404 关键词
        const lower = (res.body || '').slice(0, 1000).toLowerCase();
        const hit404Keywords = CUSTOM_404_SIGNATURES.filter(sig => lower.includes(sig));
        if (hit404Keywords.length >= 2) return true;

        return false;
    }

    /**
     * 发送 HTTP 请求（返回 Promise）
     */
    _request(pathStr) {
        return new Promise((resolve, reject) => {
            const fullUrl = `${this.target}/${pathStr}`;
            let parsedUrl;
            try {
                parsedUrl = new URL(fullUrl);
            } catch {
                return reject(new Error(`Invalid URL: ${fullUrl}`));
            }

            const isHttps = parsedUrl.protocol === 'https:';
            const lib = isHttps ? https : http;

            const options = {
                hostname: parsedUrl.hostname,
                port: parsedUrl.port || (isHttps ? 443 : 80),
                path: parsedUrl.pathname + parsedUrl.search,
                method: this.method,
                timeout: this.timeout,
                headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                    'Accept': '*/*',
                    'Accept-Language': 'en-US,en;q=0.9',
                    'Connection': 'keep-alive',
                    ...this.customHeaders,
                },
                rejectUnauthorized: false, // 允许自签证书
            };

            const req = lib.request(options, (res) => {
                const chunks = [];
                let bodySize = 0;
                const maxBody = 64 * 1024; // 最多读取 64KB

                res.on('data', (chunk) => {
                    bodySize += chunk.length;
                    if (bodySize <= maxBody) chunks.push(chunk);
                });

                res.on('end', () => {
                    resolve({
                        statusCode: res.statusCode,
                        headers: res.headers,
                        body: Buffer.concat(chunks).toString('utf-8'),
                    });
                });
            });

            req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
            req.on('error', (err) => reject(err));
            req.end();
        });
    }
}

module.exports = { Scanner, SENSITIVE_CONTENT_PATTERNS };
