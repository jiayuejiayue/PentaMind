/**
 * PentaMind PathFinder - Web UI Logic
 * 前端交互：SSE 实时监听 + API 调用 + 结果渲染
 */

const API_BASE = '';

// ========== DOM ==========
const $ = (s) => document.querySelector(s);
const $$ = (s) => document.querySelectorAll(s);

const configPanel = $('#config-panel');
const progressPanel = $('#progress-panel');
const resultsPanel = $('#results-panel');
const btnScan = $('#btn-scan');
const btnAbort = $('#btn-abort');
const btnExport = $('#btn-export-json');
const btnNewScan = $('#btn-new-scan');
const liveFeed = $('#live-feed');
const resultTbody = $('#result-tbody');
const resultSearch = $('#result-search');

let allResults = [];
let eventSource = null;

// ========== 初始化 ==========
btnScan.addEventListener('click', startScan);
btnAbort.addEventListener('click', abortScan);
btnNewScan.addEventListener('click', resetUI);
btnExport.addEventListener('click', exportResults);
resultSearch.addEventListener('input', filterResults);

// ========== 启动扫描 ==========
async function startScan() {
    const target = $('#target').value.trim();
    if (!target) return alert('请输入目标地址');

    const body = {
        target,
        concurrency: parseInt($('#concurrency').value) || 20,
        timeout: parseInt($('#timeout').value) || 8000,
        commonDirs: $('#dict-common').checked,
        sensitiveFiles: $('#dict-sensitive').checked,
        backupFiles: $('#dict-backup').checked,
        apiEndpoints: $('#dict-api').checked,
        extensions: $('#extensions').value.split(',').map(s => s.trim()).filter(Boolean),
        customPaths: $('#custom-paths').value.split('\n').map(s => s.trim()).filter(Boolean),
    };

    btnScan.disabled = true;
    allResults = [];

    try {
        const res = await fetch(`${API_BASE}/api/scan`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        const data = await res.json();

        if (!res.ok) {
            alert(data.error || '启动失败');
            btnScan.disabled = false;
            return;
        }

        addFeedLine(`🚀 开始扫描 ${target}，共 ${data.totalPaths} 条路径`, '');
        showProgress();
        connectSSE();
    } catch (err) {
        alert('请求失败: ' + err.message);
        btnScan.disabled = false;
    }
}

// ========== SSE 连接 ==========
function connectSSE() {
    if (eventSource) eventSource.close();
    eventSource = new EventSource(`${API_BASE}/events`);

    eventSource.addEventListener('scan:progress', (e) => {
        const d = JSON.parse(e.data);
        $('#progress-bar').style.width = d.percent + '%';
        $('#stat-progress').textContent = `${d.completed} / ${d.total}`;
        $('#stat-found').textContent = `发现: ${d.found}`;
        const sec = Math.round(d.elapsed / 1000);
        $('#stat-elapsed').textContent = `耗时: ${sec}s`;
        const speed = sec > 0 ? Math.round(d.completed / sec) : 0;
        $('#stat-speed').textContent = `速度: ${speed}/s`;
    });

    eventSource.addEventListener('scan:found', (e) => {
        const r = JSON.parse(e.data);
        allResults.push(r);
        const tags = r.findings.map(f => f.tag).join(', ');
        const cls = r.findings.some(f => f.severity === 'CRITICAL') ? 'critical' :
            r.findings.some(f => f.severity === 'HIGH') ? 'high' : 'found';
        addFeedLine(`✅ [${r.statusCode}] ${r.path} ${tags ? '⚠ ' + tags : ''}`, cls);
    });

    eventSource.addEventListener('scan:calibrate', (e) => {
        const d = JSON.parse(e.data);
        addFeedLine(`🔧 404 校准: status=${d.statusCode}, bodyLen=${d.bodyLength}`, '');
    });

    eventSource.addEventListener('scan:complete', (e) => {
        const d = JSON.parse(e.data);
        addFeedLine(`✅ 扫描完成！发现 ${d.found} 个端点，耗时 ${Math.round(d.duration / 1000)}s`, 'found');
        showResults(d.results);
        if (eventSource) { eventSource.close(); eventSource = null; }
        btnScan.disabled = false;
    });

    eventSource.addEventListener('scan:error', (e) => {
        const d = JSON.parse(e.data);
        addFeedLine(`❌ 错误: ${d.error}`, 'critical');
        btnScan.disabled = false;
    });
}

// ========== 中止扫描 ==========
async function abortScan() {
    await fetch(`${API_BASE}/api/abort`, { method: 'POST' });
    addFeedLine('⛔ 扫描已中止', 'critical');
    if (eventSource) { eventSource.close(); eventSource = null; }
    btnScan.disabled = false;
    if (allResults.length) showResults(allResults);
}

// ========== UI 切换 ==========
function showProgress() {
    configPanel.style.display = 'none';
    progressPanel.style.display = 'block';
    resultsPanel.style.display = 'none';
    liveFeed.innerHTML = '';
    $('#progress-bar').style.width = '0%';
}

function showResults(results) {
    allResults = results || allResults;
    progressPanel.style.display = 'none';
    resultsPanel.style.display = 'block';
    renderStatCards(allResults);
    renderTable(allResults);
}

function resetUI() {
    configPanel.style.display = 'block';
    progressPanel.style.display = 'none';
    resultsPanel.style.display = 'none';
    btnScan.disabled = false;
    allResults = [];
}

// ========== 渲染统计卡片 ==========
function renderStatCards(results) {
    const cards = $('#stat-cards');
    let critical = 0, high = 0, medium = 0;
    results.forEach(r => {
        r.findings.forEach(f => {
            if (f.severity === 'CRITICAL') critical++;
            else if (f.severity === 'HIGH') high++;
            else if (f.severity === 'MEDIUM') medium++;
        });
    });

    cards.innerHTML = `
    <div class="stat-card total">
      <div class="stat-value">${results.length}</div>
      <div class="stat-label">总发现</div>
    </div>
    <div class="stat-card critical">
      <div class="stat-value">${critical}</div>
      <div class="stat-label">CRITICAL</div>
    </div>
    <div class="stat-card high">
      <div class="stat-value">${high}</div>
      <div class="stat-label">HIGH</div>
    </div>
    <div class="stat-card medium">
      <div class="stat-value">${medium}</div>
      <div class="stat-label">MEDIUM</div>
    </div>`;
}

// ========== 渲染结果表格 ==========
function renderTable(results) {
    resultTbody.innerHTML = '';
    if (!results.length) {
        resultTbody.innerHTML = '<tr><td colspan="5" style="text-align:center;color:var(--text-3);padding:24px;">无发现</td></tr>';
        return;
    }

    // 按严重度排序
    const severityOrder = { CRITICAL: 0, HIGH: 1, MEDIUM: 2 };
    results.sort((a, b) => {
        const aMax = Math.min(...a.findings.map(f => severityOrder[f.severity] ?? 9), 9);
        const bMax = Math.min(...b.findings.map(f => severityOrder[f.severity] ?? 9), 9);
        return aMax - bMax;
    });

    for (const r of results) {
        const tr = document.createElement('tr');
        const sc = r.statusCode;
        const scClass = sc >= 500 ? 's5xx' : sc >= 400 ? 's4xx' : sc >= 300 ? 's3xx' : 's2xx';
        const findings = r.findings.map(f =>
            `<span class="finding-badge ${f.severity}">${f.tag}</span>`
        ).join(' ');
        const size = r.contentLength > 1024 ? Math.round(r.contentLength / 1024) + 'KB' : r.contentLength + 'B';

        tr.innerHTML = `
      <td><span class="status-badge ${scClass}">${sc}</span></td>
      <td class="path-cell"><a href="${esc(r.url)}" target="_blank">${esc(r.path)}</a></td>
      <td style="color:var(--text-2);font-size:11px;">${esc(r.contentType)}</td>
      <td style="font-family:'JetBrains Mono',monospace;font-size:11px;">${size}</td>
      <td>${findings || '<span style="color:var(--text-3);">—</span>'}</td>`;
        resultTbody.appendChild(tr);
    }
}

// ========== 搜索过滤 ==========
function filterResults() {
    const q = resultSearch.value.toLowerCase();
    const filtered = q
        ? allResults.filter(r => r.path.toLowerCase().includes(q) || r.findings.some(f => f.tag.toLowerCase().includes(q)))
        : allResults;
    renderTable(filtered);
}

// ========== 导出 JSON ==========
function exportResults() {
    const blob = new Blob([JSON.stringify(allResults, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `pathfinder_results_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
}

// ========== 工具 ==========
function addFeedLine(text, cls) {
    const div = document.createElement('div');
    div.className = 'feed-line ' + (cls || '');
    div.textContent = `[${new Date().toLocaleTimeString()}] ${text}`;
    liveFeed.appendChild(div);
    liveFeed.scrollTop = liveFeed.scrollHeight;
}

function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
