#!/usr/bin/env node

/**
 * PentaMind PathFinder - CLI 入口
 * 用法:
 *   node src/cli.js -t https://example.com
 *   node src/cli.js -t https://example.com -c 30 --no-backup
 */

const { Dictionary } = require('./dictionary');
const { Scanner } = require('./scanner');

// ========== 参数解析 ==========
const args = process.argv.slice(2);
const opts = {
    target: null,
    concurrency: 20,
    timeout: 8000,
    extensions: [],
    noCommon: false,
    noSensitive: false,
    noBackup: false,
    noApi: false,
    dictFile: null,
};

for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
        case '-t': case '--target': opts.target = args[++i]; break;
        case '-c': case '--concurrency': opts.concurrency = parseInt(args[++i]) || 20; break;
        case '--timeout': opts.timeout = parseInt(args[++i]) || 8000; break;
        case '-e': case '--ext': opts.extensions.push(args[++i]); break;
        case '-d': case '--dict': opts.dictFile = args[++i]; break;
        case '--no-common': opts.noCommon = true; break;
        case '--no-sensitive': opts.noSensitive = true; break;
        case '--no-backup': opts.noBackup = true; break;
        case '--no-api': opts.noApi = true; break;
        case '-h': case '--help': printHelp(); process.exit(0);
        default: break;
    }
}

if (!opts.target) {
    console.error('❌ 缺少目标参数 (-t URL)');
    printHelp();
    process.exit(1);
}

function printHelp() {
    console.log(`
PentaMind PathFinder - 目录扫描与敏感文件发现

用法:
  node src/cli.js -t <URL> [选项]

选项:
  -t, --target <URL>       目标地址 (必填)
  -c, --concurrency <N>    并发数 (默认: 20)
  --timeout <ms>           超时时间 (默认: 8000)
  -e, --ext <ext>          追加扩展名 (可多次, 如 -e .php -e .asp)
  -d, --dict <file>        自定义字典文件路径
  --no-common              不加载通用目录字典
  --no-sensitive           不加载敏感文件字典
  --no-backup              不加载备份文件字典
  --no-api                 不加载 API 端点字典
  -h, --help               显示帮助
  `);
}

// ========== 执行扫描 ==========
(async () => {
    console.log(`\n⬡ PentaMind PathFinder v1.0`);
    console.log(`  Target: ${opts.target}`);

    // 构建字典
    const dict = new Dictionary();
    dict.loadBuiltin({
        commonDirs: !opts.noCommon,
        sensitiveFiles: !opts.noSensitive,
        backupFiles: !opts.noBackup,
        apiEndpoints: !opts.noApi,
        domain: (() => { try { return new URL(opts.target).hostname; } catch { return null; } })(),
    });

    if (opts.dictFile) {
        dict.loadFromFile(opts.dictFile);
    }

    const stats = dict.getStats();
    console.log(`  Dictionary: ${stats.total} paths`);
    for (const [cat, count] of Object.entries(stats.categories)) {
        console.log(`    - ${cat}: ${count}`);
    }
    console.log(`  Concurrency: ${opts.concurrency}`);
    console.log('');

    const scanner = new Scanner({
        target: opts.target,
        paths: dict.getPaths(),
        concurrency: opts.concurrency,
        timeout: opts.timeout,
        extensions: opts.extensions,
    });

    scanner.on('calibrate', (data) => {
        console.log(`  🔧 Baseline 404: status=${data.statusCode}, bodyLen=${data.bodyLength}`);
    });

    scanner.on('progress', (data) => {
        process.stdout.write(`\r  ⏳ Progress: ${data.completed}/${data.total} (${data.percent}%) | Found: ${data.found} | ${Math.round(data.elapsed / 1000)}s`);
    });

    scanner.on('found', (result) => {
        const severity = result.findings.length ? result.findings.map(f => f.tag).join(', ') : '';
        console.log(`\n  ✅ [${result.statusCode}] ${result.path} (${result.contentLength}B) ${severity ? '⚠ ' + severity : ''}`);
    });

    const results = await scanner.start();

    console.log(`\n\n════════════════════════════════════════════`);
    console.log(`  扫描完成！共扫描 ${scanner.total} 条路径`);
    console.log(`  发现 ${results.length} 个有效端点`);
    console.log(`  耗时 ${Math.round((Date.now() - scanner.startTime) / 1000)}s`);
    console.log(`════════════════════════════════════════════\n`);

    if (results.length) {
        console.log('  发现列表:\n');
        for (const r of results) {
            const tags = r.findings.map(f => `[${f.severity}] ${f.tag}`).join(', ');
            console.log(`  ${r.statusCode} | ${r.path} | ${r.contentType} | ${r.contentLength}B${tags ? ' | ' + tags : ''}`);
        }
    }
})();
