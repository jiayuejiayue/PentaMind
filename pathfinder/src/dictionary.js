/**
 * PentaMind PathFinder - 字典管理模块
 * 
 * 职责：
 *   1. 加载内置字典（通用路径、敏感文件、备份文件、API 端点）
 *   2. 支持自定义字典导入与合并
 *   3. 字典去重、排序、分类标签
 */

const fs = require('fs');
const path = require('path');

// ========== 内置字典 ==========

/** 通用目录路径 */
const COMMON_DIRS = [
    // 管理后台
    'admin', 'administrator', 'admin.php', 'admin.html', 'admin/login',
    'manage', 'manager', 'backend', 'dashboard', 'panel',
    'wp-admin', 'wp-login.php', 'cpanel', 'webadmin', 'siteadmin',
    'console', 'control', 'portal',

    // 常见目录
    'api', 'api/v1', 'api/v2', 'api/v3', 'graphql',
    'login', 'signin', 'signup', 'register', 'auth', 'oauth',
    'user', 'users', 'account', 'profile', 'member', 'members',
    'upload', 'uploads', 'files', 'file', 'media', 'images', 'img', 'static',
    'assets', 'css', 'js', 'fonts', 'lib', 'vendor',
    'docs', 'doc', 'documentation', 'help', 'faq', 'about',
    'blog', 'news', 'post', 'posts', 'article', 'articles',
    'search', 'query', 'find',
    'config', 'conf', 'settings', 'setup', 'install',
    'test', 'tests', 'testing', 'debug', 'dev', 'staging',
    'temp', 'tmp', 'cache', 'log', 'logs',
    'backup', 'backups', 'bak', 'old', 'archive',
    'data', 'database', 'db', 'sql', 'mysql',
    'download', 'downloads', 'export', 'import',
    'cgi-bin', 'bin', 'scripts', 'includes', 'inc',
    'private', 'secret', 'hidden', 'internal',
    'status', 'health', 'ping', 'info', 'version',
    'sitemap.xml', 'robots.txt', 'favicon.ico', 'crossdomain.xml',
    'humans.txt', 'security.txt', '.well-known/security.txt',
];

/** 敏感文件 */
const SENSITIVE_FILES = [
    // 配置文件
    '.env', '.env.local', '.env.production', '.env.development', '.env.staging',
    '.env.backup', '.env.bak', '.env.old', '.env.example', '.env.sample',
    'config.php', 'config.yml', 'config.yaml', 'config.json', 'config.xml',
    'config.inc.php', 'config.inc', 'configuration.php',
    'database.yml', 'database.php', 'db.php', 'db.conf',
    'settings.php', 'settings.py', 'settings.json', 'settings.yml',
    'application.yml', 'application.properties', 'application.xml',
    'wp-config.php', 'wp-config.php.bak', 'wp-config.php.old',
    'web.config', 'web.xml', 'appSettings.json', 'appsettings.json',

    // 版本控制泄露
    '.git/HEAD', '.git/config', '.git/index',
    '.svn/entries', '.svn/wc.db',
    '.hg/store', '.bzr/README',
    '.gitignore', '.gitmodules', '.gitattributes',

    // 调试与信息泄露
    'phpinfo.php', 'info.php', 'test.php', 'debug.php',
    'server-status', 'server-info',
    'elmah.axd', 'trace.axd',
    'actuator', 'actuator/env', 'actuator/health', 'actuator/info',
    'actuator/beans', 'actuator/configprops', 'actuator/mappings',
    'swagger-ui.html', 'swagger-ui/', 'swagger/v1/swagger.json',
    'api-docs', 'swagger.json', 'swagger.yaml', 'openapi.json',

    // 密钥和证书
    '.htpasswd', '.htaccess',
    'id_rsa', 'id_rsa.pub', 'id_dsa',
    '.ssh/authorized_keys',
    'server.key', 'server.crt', 'server.pem',
    'private.key', 'privatekey.pem', 'certificate.pem',
    '.pem', '.key', '.p12', '.pfx',

    // 数据库文件
    'dump.sql', 'database.sql', 'backup.sql', 'data.sql', 'db.sql',
    'db.sqlite', 'db.sqlite3', 'database.sqlite', 'database.db',

    // 日志文件
    'error.log', 'access.log', 'debug.log', 'app.log', 'server.log',
    'error_log', 'access_log',
    'wp-content/debug.log',
    'logs/error.log', 'logs/access.log',

    // 编辑器临时文件
    '.DS_Store', 'Thumbs.db', 'desktop.ini',
    '.vscode/settings.json', '.idea/workspace.xml',
];

/** 备份文件模式（会基于当前域名/路径动态展开） */
const BACKUP_PATTERNS = [
    // 通用备份
    'backup.zip', 'backup.tar.gz', 'backup.rar', 'backup.7z',
    'site.zip', 'site.tar.gz', 'www.zip', 'www.tar.gz',
    'web.zip', 'web.tar.gz', 'html.zip', 'html.tar.gz',
    'public.zip', 'public.tar.gz',
    'htdocs.zip', 'htdocs.tar.gz',
    'dist.zip', 'build.zip',

    // 数据库备份
    'db.zip', 'db.tar.gz', 'database.zip', 'mysql.zip',
    'dump.sql.gz', 'dump.tar.gz',

    // 源码备份
    'source.zip', 'src.zip', 'code.zip',
    'release.zip', 'deploy.zip',
    'master.zip', 'main.zip', 'dev.zip',

    // 时间戳备份 (动态生成)
    // -> 由引擎根据当前年月动态添加
];

/** API 端点 */
const API_ENDPOINTS = [
    // REST 通用
    'api', 'api/', 'api/v1', 'api/v2', 'api/v3',
    'api/users', 'api/user', 'api/admin', 'api/auth',
    'api/login', 'api/register', 'api/token', 'api/refresh',
    'api/config', 'api/settings', 'api/upload', 'api/download',
    'api/search', 'api/export', 'api/import',
    'api/status', 'api/health', 'api/version', 'api/info',
    'api/debug', 'api/test', 'api/docs',

    // GraphQL
    'graphql', 'graphql/console', 'graphiql',

    // JSON-RPC / WebSocket
    'jsonrpc', 'ws', 'wss', 'socket.io',

    // 常见后端框架端点
    'rest', 'service', 'services', 'rpc',
    'gateway', 'proxy',

    // 微服务 / DevOps
    'eureka', 'nacos', 'consul',
    'prometheus', 'metrics', 'grafana',
    'kibana', 'elasticsearch',
    'jenkins', 'sonar',
];

// ========== 字典类 ==========

class Dictionary {
    constructor() {
        this.entries = new Map(); // path -> { path, category, source }
    }

    /**
     * 加载内置字典
     * @param {Object} options - 要加载的字典类别
     */
    loadBuiltin(options = {}) {
        const {
            commonDirs = true,
            sensitiveFiles = true,
            backupFiles = true,
            apiEndpoints = true,
            domain = null,       // 目标域名（用于生成动态备份名）
        } = options;

        if (commonDirs) this._addEntries(COMMON_DIRS, 'common_dir');
        if (sensitiveFiles) this._addEntries(SENSITIVE_FILES, 'sensitive_file');
        if (backupFiles) {
            this._addEntries(BACKUP_PATTERNS, 'backup_file');
            if (domain) this._addDomainBackups(domain);
        }
        if (apiEndpoints) this._addEntries(API_ENDPOINTS, 'api_endpoint');

        return this;
    }

    /**
     * 从文件加载自定义字典（每行一个路径）
     */
    loadFromFile(filePath) {
        try {
            const content = fs.readFileSync(filePath, 'utf-8');
            const lines = content.split(/\r?\n/)
                .map(l => l.trim())
                .filter(l => l && !l.startsWith('#'));
            this._addEntries(lines, 'custom');
        } catch (err) {
            console.error(`[PathFinder] Failed to load dictionary: ${filePath}`, err.message);
        }
        return this;
    }

    /**
     * 从数组加载
     */
    loadFromArray(paths, category = 'custom') {
        this._addEntries(paths, category);
        return this;
    }

    /**
     * 基于域名动态生成备份文件名
     */
    _addDomainBackups(domain) {
        const clean = domain.replace(/[^a-zA-Z0-9.-]/g, '');
        const parts = clean.split('.');
        const names = [clean, parts[0]]; // e.g. ['example.com', 'example']
        const exts = ['.zip', '.tar.gz', '.rar', '.7z', '.bak', '.sql', '.sql.gz'];

        // 当前年份前后
        const year = new Date().getFullYear();
        const years = [year, year - 1, year - 2];
        const months = ['01', '02', '03', '04', '05', '06', '07', '08', '09', '10', '11', '12'];

        const dynamicPaths = [];

        for (const name of names) {
            for (const ext of exts) {
                dynamicPaths.push(`${name}${ext}`);
                dynamicPaths.push(`${name}_backup${ext}`);
                dynamicPaths.push(`${name}_bak${ext}`);
                for (const y of years) {
                    dynamicPaths.push(`${name}_${y}${ext}`);
                    dynamicPaths.push(`backup_${y}${ext}`);
                    for (const m of months) {
                        dynamicPaths.push(`${name}_${y}${m}${ext}`);
                    }
                }
            }
        }

        this._addEntries(dynamicPaths, 'backup_dynamic');
    }

    /**
     * 添加条目
     */
    _addEntries(paths, category) {
        for (const p of paths) {
            const normalized = p.replace(/^\/+/, ''); // 去掉前导斜杠
            if (!this.entries.has(normalized)) {
                this.entries.set(normalized, { path: normalized, category });
            }
        }
    }

    /**
     * 获取所有条目
     */
    getAll() {
        return Array.from(this.entries.values());
    }

    /**
     * 按分类获取
     */
    getByCategory(category) {
        return this.getAll().filter(e => e.category === category);
    }

    /**
     * 获取所有唯一路径
     */
    getPaths() {
        return Array.from(this.entries.keys());
    }

    /**
     * 获取统计信息
     */
    getStats() {
        const stats = {};
        for (const entry of this.entries.values()) {
            stats[entry.category] = (stats[entry.category] || 0) + 1;
        }
        return { total: this.entries.size, categories: stats };
    }
}

module.exports = { Dictionary, COMMON_DIRS, SENSITIVE_FILES, BACKUP_PATTERNS, API_ENDPOINTS };
