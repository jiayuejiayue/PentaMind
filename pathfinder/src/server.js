/**
 * PentaMind PathFinder - Web 服务器
 * 
 * 提供：
 *   1. RESTful API 供前端调用（启动扫描、查询进度、获取结果）
 *   2. SSE（Server-Sent Events）实时推送扫描进度
 *   3. 静态文件服务（Web UI）
 *
 * 零外部依赖，纯 Node.js 原生实现
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');
const { Dictionary } = require('./dictionary');
const { Scanner } = require('./scanner');

const PORT = process.env.PORT || 3000;
const UI_DIR = path.join(__dirname, '..', 'ui');

// ========== 全局扫描状态 ==========
let currentScanner = null;
let scanHistory = [];
const sseClients = new Set();

// ========== MIME 类型 ==========
const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'application/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.png': 'image/png',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
};

// ========== HTTP 服务 ==========
const server = http.createServer(async (req, res) => {
    const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
    const pathname = parsedUrl.pathname;

    // CORS
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

    // API 路由
    if (pathname.startsWith('/api/')) {
        return handleApi(req, res, pathname, parsedUrl);
    }

    // SSE 端点
    if (pathname === '/events') {
        return handleSSE(req, res);
    }

    // 静态文件
    serveStatic(req, res, pathname);
});

// ========== API 处理 ==========
async function handleApi(req, res, pathname, parsedUrl) {
    const json = (data, code = 200) => {
        res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(JSON.stringify(data));
    };

    try {
        // POST /api/scan - 启动扫描
        if (pathname === '/api/scan' && req.method === 'POST') {
            const body = await readBody(req);
            const opts = JSON.parse(body);

            if (!opts.target) return json({ error: '缺少 target 参数' }, 400);
            if (currentScanner?.running) return json({ error: '已有扫描任务在运行中' }, 409);

            // 构建字典
            const dict = new Dictionary();
            dict.loadBuiltin({
                commonDirs: opts.commonDirs !== false,
                sensitiveFiles: opts.sensitiveFiles !== false,
                backupFiles: opts.backupFiles !== false,
                apiEndpoints: opts.apiEndpoints !== false,
                domain: extractDomain(opts.target),
            });

            // 加载自定义字典
            if (opts.customPaths?.length) {
                dict.loadFromArray(opts.customPaths, 'custom');
            }

            const paths = dict.getPaths();
            const dictStats = dict.getStats();

            // 创建扫描器
            currentScanner = new Scanner({
                target: opts.target,
                paths,
                concurrency: Math.min(opts.concurrency || 20, 50),
                timeout: opts.timeout || 8000,
                method: opts.method || 'GET',
                headers: opts.headers || {},
                extensions: opts.extensions || [],
                detectContent: opts.detectContent !== false,
                followRedirect: opts.followRedirect || false,
            });

            // 绑定事件 -> SSE 推送
            currentScanner.on('start', (data) => broadcast('scan:start', data));
            currentScanner.on('progress', (data) => broadcast('scan:progress', data));
            currentScanner.on('found', (data) => broadcast('scan:found', data));
            currentScanner.on('calibrate', (data) => broadcast('scan:calibrate', data));
            currentScanner.on('complete', (data) => {
                broadcast('scan:complete', data);
                // 存入历史
                scanHistory.unshift({
                    id: Date.now().toString(36),
                    target: opts.target,
                    timestamp: Date.now(),
                    total: data.total,
                    found: data.found,
                    duration: data.duration,
                    results: data.results,
                });
                if (scanHistory.length > 20) scanHistory.pop();
            });

            // 异步启动（不等待完成）
            currentScanner.start().catch((err) => {
                broadcast('scan:error', { error: err.message });
            });

            return json({
                status: 'started',
                target: opts.target,
                totalPaths: paths.length,
                dictStats,
            });
        }

        // GET /api/status - 当前扫描状态
        if (pathname === '/api/status' && req.method === 'GET') {
            if (!currentScanner) return json({ status: 'idle' });
            return json({
                status: currentScanner.running ? 'running' : 'idle',
                target: currentScanner.target,
                completed: currentScanner.completed,
                total: currentScanner.total,
                found: currentScanner.results.length,
                elapsed: currentScanner.running ? Date.now() - currentScanner.startTime : 0,
            });
        }

        // GET /api/results - 当前扫描结果
        if (pathname === '/api/results' && req.method === 'GET') {
            return json({ results: currentScanner?.results || [] });
        }

        // POST /api/abort - 中止扫描
        if (pathname === '/api/abort' && req.method === 'POST') {
            if (currentScanner?.running) {
                currentScanner.abort();
                return json({ status: 'aborted' });
            }
            return json({ status: 'no_active_scan' });
        }

        // GET /api/history - 历史记录
        if (pathname === '/api/history' && req.method === 'GET') {
            return json({ history: scanHistory.map(h => ({ ...h, results: undefined, found: h.found })) });
        }

        // GET /api/history/:id - 历史记录详情
        const historyMatch = pathname.match(/^\/api\/history\/(.+)$/);
        if (historyMatch && req.method === 'GET') {
            const record = scanHistory.find(h => h.id === historyMatch[1]);
            if (!record) return json({ error: 'Not found' }, 404);
            return json(record);
        }

        // GET /api/dict/stats - 字典统计
        if (pathname === '/api/dict/stats' && req.method === 'GET') {
            const dict = new Dictionary().loadBuiltin({ domain: 'example.com' });
            return json(dict.getStats());
        }

        json({ error: 'Not found' }, 404);
    } catch (err) {
        json({ error: err.message }, 500);
    }
}

// ========== SSE ==========
function handleSSE(req, res) {
    res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive',
    });

    const client = { res };
    sseClients.add(client);
    req.on('close', () => sseClients.delete(client));
}

function broadcast(event, data) {
    const msg = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
    for (const client of sseClients) {
        try { client.res.write(msg); } catch { sseClients.delete(client); }
    }
}

// ========== 静态文件服务 ==========
function serveStatic(req, res, pathname) {
    if (pathname === '/') pathname = '/index.html';
    const filePath = path.join(UI_DIR, pathname);

    // 安全检查：防止路径穿越
    if (!filePath.startsWith(UI_DIR)) {
        res.writeHead(403);
        res.end('Forbidden');
        return;
    }

    fs.readFile(filePath, (err, data) => {
        if (err) {
            res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end('<h1>404 Not Found</h1>');
            return;
        }
        const ext = path.extname(filePath);
        const contentType = MIME_TYPES[ext] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': contentType });
        res.end(data);
    });
}

// ========== 工具函数 ==========
function readBody(req) {
    return new Promise((resolve, reject) => {
        const chunks = [];
        req.on('data', (c) => chunks.push(c));
        req.on('end', () => resolve(Buffer.concat(chunks).toString()));
        req.on('error', reject);
    });
}

function extractDomain(target) {
    try { return new URL(target).hostname; } catch { return null; }
}

// ========== 启动 ==========
server.listen(PORT, () => {
    console.log(`
╔════════════════════════════════════════════════╗
║   PentaMind PathFinder v1.0                    ║
║   Server running at http://localhost:${PORT}      ║
║   Press Ctrl+C to stop                         ║
╚════════════════════════════════════════════════╝
  `);
});
