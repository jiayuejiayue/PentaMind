/**
 * PentaMind - popup/popup.js  V1.1
 * 面板逻辑：功能点列表 + API 清单双 Tab 视图
 */

const RISK_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

let allElements = [];
let allApis = [];
let currentFilter = 'ALL';
let searchQuery = '';
let activeTab = 'elements'; // 'elements' | 'apis'

const $id = (id) => document.getElementById(id);

/* ===== 初始化 ===== */
document.addEventListener('DOMContentLoaded', () => {
    loadResult();

    $id('btn-rescan').addEventListener('click', triggerRescan);
    $id('btn-trigger').addEventListener('click', triggerRescan);
    $id('btn-export').addEventListener('click', exportJSON);
    $id('search-input').addEventListener('input', (e) => {
        searchQuery = e.target.value.toLowerCase();
        renderCurrentTab();
    });
    $id('risk-filter').addEventListener('change', (e) => {
        currentFilter = e.target.value;
        renderCurrentTab();
    });

    // Tab 切换
    document.querySelectorAll('.tab-btn').forEach((btn) => {
        btn.addEventListener('click', () => {
            activeTab = btn.dataset.tab;
            document.querySelectorAll('.tab-btn').forEach((b) => b.classList.remove('active'));
            btn.classList.add('active');
            renderCurrentTab();
        });
    });

    // 风险卡片点击过滤
    document.querySelectorAll('.risk-card').forEach((card) => {
        card.addEventListener('click', () => {
            const filter = card.dataset.filter;
            currentFilter = currentFilter === filter ? 'ALL' : filter;
            $id('risk-filter').value = currentFilter;
            document.querySelectorAll('.risk-card').forEach((c) => c.classList.remove('active'));
            if (currentFilter !== 'ALL') card.classList.add('active');
            renderCurrentTab();
        });
    });
});

/* ===== 从 background 获取数据 ===== */
function loadResult() {
    chrome.runtime.sendMessage({ type: 'GET_RESULT' }, (resp) => {
        if (resp?.result) {
            renderData(resp.result);
        } else {
            showEmpty();
        }
    });
}

/* ===== 触发重扫 ===== */
function triggerRescan() {
    const btn = $id('btn-rescan');
    btn.classList.add('scanning');
    showEmpty('重新扫描中...');
    chrome.runtime.sendMessage({ type: 'TRIGGER_SCAN' }, () => {
        setTimeout(() => { btn.classList.remove('scanning'); loadResult(); }, 1500);
    });
}

/* ===== 渲染数据 ===== */
function renderData(data) {
    allElements = data.elements || [];
    allApis = data.apis || [];

    $id('current-url').textContent = formatUrl(data.url);

    // 统计卡片
    const s = data.summary?.riskCount || {};
    $id('count-critical').textContent = s.CRITICAL || 0;
    $id('count-high').textContent = s.HIGH || 0;
    $id('count-medium').textContent = s.MEDIUM || 0;
    $id('count-low').textContent = s.LOW || 0;
    $id('count-info').textContent = s.INFO || 0;

    // Tab 计数
    $id('tab-el-count').textContent = allElements.length;
    $id('tab-api-count').textContent = allApis.length;

    // 显示区域
    $id('tab-bar').style.display = 'flex';
    $id('risk-summary').style.display = 'flex';
    $id('filter-bar').style.display = 'flex';
    $id('state-empty').style.display = 'none';
    $id('footer').style.display = 'flex';

    renderCurrentTab();
}

/* ===== 渲染当前激活的 Tab ===== */
function renderCurrentTab() {
    const elList = $id('element-list');
    const apiList = $id('api-list');

    if (activeTab === 'elements') {
        elList.style.display = 'block';
        apiList.style.display = 'none';
        renderElementList();
    } else {
        elList.style.display = 'none';
        apiList.style.display = 'block';
        renderApiList();
    }
}

/* ===== 功能点列表渲染 ===== */
function renderElementList() {
    const list = $id('element-list');
    list.innerHTML = '';

    let filtered = allElements;

    if (currentFilter !== 'ALL') {
        filtered = filtered.filter((el) => el.overallRisk === currentFilter);
    }
    if (searchQuery) {
        filtered = filtered.filter((el) => {
            const h = (el.text + ' ' + el.textFeature + ' ' + el.selector + ' ' + el.tag + ' ' +
                (el.riskFeatures?.map((f) => f.tag).join(' ') || '')).toLowerCase();
            return h.includes(searchQuery);
        });
    }

    filtered.sort((a, b) => RISK_ORDER.indexOf(a.overallRisk) - RISK_ORDER.indexOf(b.overallRisk));

    if (!filtered.length) {
        list.innerHTML = '<div style="padding:20px;text-align:center;color:#8b949e;font-size:12px;">无匹配功能点</div>';
    } else {
        filtered.forEach((el) => list.appendChild(createElementItem(el)));
    }

    $id('total-count').textContent = `功能点 ${filtered.length}/${allElements.length} · API ${allApis.length}`;
}

/* ===== API 列表渲染 ===== */
function renderApiList() {
    const list = $id('api-list');
    list.innerHTML = '';

    let filtered = allApis;

    // 风险过滤
    if (currentFilter !== 'ALL') {
        filtered = filtered.filter((api) => {
            if (!api.riskTags || !api.riskTags.length) return currentFilter === 'INFO';
            return api.riskTags.some((t) => t.risk === currentFilter);
        });
    }

    // 搜索过滤
    if (searchQuery) {
        filtered = filtered.filter((api) => {
            const h = (api.method + ' ' + api.url + ' ' + (api.riskTags?.map((t) => t.tag).join(' ') || '')).toLowerCase();
            return h.includes(searchQuery);
        });
    }

    if (!filtered.length) {
        list.innerHTML = '<div style="padding:20px;text-align:center;color:#8b949e;font-size:12px;">无匹配 API 记录</div>';
    } else {
        filtered.forEach((api) => list.appendChild(createApiItem(api)));
    }

    $id('total-count').textContent = `功能点 ${allElements.length} · API ${filtered.length}/${allApis.length}`;
}

/* ===== 创建功能点 DOM ===== */
function createElementItem(el) {
    const item = document.createElement('div');
    item.className = 'element-item';

    const displayText = el.text || el.name || el.id || el.href || '(无文本)';
    const featureTags = (el.riskFeatures || [])
        .map((f) => `<span class="ftag ${f.risk}">${f.tag}</span>`).join('');

    item.innerHTML = `
    <div class="risk-dot ${el.overallRisk}"></div>
    <div class="element-main">
      <div class="element-header">
        <span class="el-tag">&lt;${el.tag}${el.type ? '[' + el.type + ']' : ''}&gt;</span>
        <span class="el-text" title="${esc(displayText)}">${esc(displayText.slice(0, 50))}</span>
      </div>
      ${featureTags ? `<div class="feature-tags">${featureTags}</div>` : ''}
      <div class="el-selector" title="${esc(el.selector)}">${esc(el.selector)}</div>
    </div>`;
    return item;
}

/* ===== 创建 API DOM ===== */
function createApiItem(api) {
    const item = document.createElement('div');
    item.className = 'api-item';

    const method = (api.method || 'GET').toUpperCase();
    const apiUrl = formatApiUrl(api.url);
    const status = api.statusCode || 0;
    const statusClass = status >= 500 ? 's5xx' : status >= 400 ? 's4xx' : status >= 300 ? 's3xx' : 's2xx';
    const duration = api.duration ? `${api.duration}ms` : '—';
    const calls = api.callCount > 1 ? `×${api.callCount}` : '';

    // 风险标签
    const riskTags = (api.riskTags || [])
        .map((t) => `<span class="ftag ${t.risk}">${t.tag}</span>`).join('');

    // 关联信息
    const correlations = (api.correlations || []).slice(0, 2)
        .map((c) => `🔗 ${c.reasons.join(', ')} → <code>${esc(c.elementSelector.slice(0, 40))}</code>`).join('<br>');

    item.innerHTML = `
    <div class="api-header">
      <span class="method-badge ${method}">${method}</span>
      <span class="api-url" title="${esc(api.url)}">${esc(apiUrl)}</span>
      <span class="status-badge ${statusClass}">${status}</span>
    </div>
    ${riskTags ? `<div class="feature-tags">${riskTags}</div>` : ''}
    <div class="api-meta">
      <span class="api-meta-item">⏱ ${duration}</span>
      <span class="api-meta-item">📡 ${api.source || '—'}</span>
      ${calls ? `<span class="api-meta-item">🔄 ${calls}</span>` : ''}
    </div>
    ${correlations ? `<div class="api-correlation">${correlations}</div>` : ''}`;

    return item;
}

/* ===== 导出 ===== */
function exportJSON() {
    const data = JSON.stringify({ elements: allElements, apis: allApis }, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `pentamind_scan_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
}

/* ===== 辅助 ===== */
function showEmpty(msg = '正在扫描页面功能点...') {
    $id('tab-bar').style.display = 'none';
    $id('risk-summary').style.display = 'none';
    $id('filter-bar').style.display = 'none';
    $id('element-list').innerHTML = '';
    $id('api-list').innerHTML = '';
    $id('footer').style.display = 'none';
    $id('state-empty').style.display = 'flex';
    $id('state-empty').querySelector('p').textContent = msg;
}

function formatUrl(url) {
    try { const u = new URL(url); return u.hostname + u.pathname; } catch { return url || '—'; }
}

function formatApiUrl(url) {
    try { const u = new URL(url); return u.pathname + u.search; } catch { return url || '—'; }
}

function esc(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
