/**
 * PentaMind - popup/popup.js
 * Popup 面板逻辑：从 background 获取扫描结果，渲染功能点列表
 */

const RISK_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
const RISK_LABEL_CN = { CRITICAL: '危急', HIGH: '高危', MEDIUM: '中危', LOW: '低危', INFO: '信息' };

let allElements = [];
let currentFilter = 'ALL';
let searchQuery = '';

/* ---- 元素获取 ---- */
const $id = (id) => document.getElementById(id);

/* ---- 初始化 ---- */
document.addEventListener('DOMContentLoaded', () => {
    loadResult();

    $id('btn-rescan').addEventListener('click', triggerRescan);
    $id('btn-trigger').addEventListener('click', triggerRescan);
    $id('btn-export').addEventListener('click', exportJSON);
    $id('search-input').addEventListener('input', (e) => {
        searchQuery = e.target.value.toLowerCase();
        renderList();
    });
    $id('risk-filter').addEventListener('change', (e) => {
        currentFilter = e.target.value;
        renderList();
    });

    // 风险卡片点击过滤
    document.querySelectorAll('.risk-card').forEach((card) => {
        card.addEventListener('click', () => {
            const filter = card.dataset.filter;
            currentFilter = currentFilter === filter ? 'ALL' : filter;
            $id('risk-filter').value = currentFilter;
            document.querySelectorAll('.risk-card').forEach((c) => c.classList.remove('active'));
            if (currentFilter !== 'ALL') card.classList.add('active');
            renderList();
        });
    });
});

/* ---- 从 background 获取数据 ---- */
function loadResult() {
    chrome.runtime.sendMessage({ type: 'GET_RESULT' }, (resp) => {
        if (resp?.result) {
            renderData(resp.result);
        } else {
            showEmpty();
        }
    });
}

/* ---- 主动触发扫描 ---- */
function triggerRescan() {
    const btn = $id('btn-rescan');
    btn.classList.add('scanning');
    showEmpty('重新扫描中...');

    chrome.runtime.sendMessage({ type: 'TRIGGER_SCAN' }, () => {
        // 延迟 1.5s 后重新拉取
        setTimeout(() => {
            btn.classList.remove('scanning');
            loadResult();
        }, 1500);
    });
}

/* ---- 渲染数据 ---- */
function renderData(data) {
    allElements = data.elements || [];

    // 更新 URL
    $id('current-url').textContent = formatUrl(data.url);

    // 更新统计卡片
    const s = data.summary?.riskCount || {};
    $id('count-critical').textContent = s.CRITICAL || 0;
    $id('count-high').textContent = s.HIGH || 0;
    $id('count-medium').textContent = s.MEDIUM || 0;
    $id('count-low').textContent = s.LOW || 0;
    $id('count-info').textContent = s.INFO || 0;

    // 显示区域
    $id('risk-summary').style.display = 'flex';
    $id('filter-bar').style.display = 'flex';
    $id('state-empty').style.display = 'none';
    $id('footer').style.display = 'flex';

    renderList();
}

/* ---- 渲染列表（带过滤和搜索） ---- */
function renderList() {
    const list = $id('element-list');
    list.innerHTML = '';

    let filtered = allElements;

    // 风险过滤
    if (currentFilter !== 'ALL') {
        filtered = filtered.filter((el) => el.overallRisk === currentFilter);
    }

    // 搜索过滤
    if (searchQuery) {
        filtered = filtered.filter((el) => {
            const haystack = (
                el.text + ' ' + el.textFeature + ' ' + el.selector + ' ' + el.tag + ' ' +
                (el.riskFeatures?.map((f) => f.tag).join(' ') || '')
            ).toLowerCase();
            return haystack.includes(searchQuery);
        });
    }

    // 按风险排序
    filtered.sort((a, b) => RISK_ORDER.indexOf(a.overallRisk) - RISK_ORDER.indexOf(b.overallRisk));

    if (!filtered.length) {
        list.innerHTML = `<div style="padding:20px;text-align:center;color:#8b949e;font-size:12px;">无匹配功能点</div>`;
    } else {
        filtered.forEach((el) => list.appendChild(createElementItem(el)));
    }

    // 更新底部计数
    $id('total-count').textContent = `共 ${filtered.length} / ${allElements.length} 个功能点`;
}

/* ---- 创建功能点条目 DOM ---- */
function createElementItem(el) {
    const item = document.createElement('div');
    item.className = 'element-item';

    const displayText = el.text || el.name || el.id || el.href || '(无文本)';
    const featureTags = (el.riskFeatures || [])
        .map((f) => `<span class="ftag ${f.risk}">${f.tag}</span>`)
        .join('');

    item.innerHTML = `
    <div class="risk-dot ${el.overallRisk}"></div>
    <div class="element-main">
      <div class="element-header">
        <span class="el-tag">&lt;${el.tag}${el.type ? '[' + el.type + ']' : ''}&gt;</span>
        <span class="el-text" title="${escapeHtml(displayText)}">${escapeHtml(displayText.slice(0, 50))}</span>
      </div>
      ${featureTags ? `<div class="feature-tags">${featureTags}</div>` : ''}
      <div class="el-selector" title="${escapeHtml(el.selector)}">${escapeHtml(el.selector)}</div>
    </div>
  `;

    // 悬停高亮（通过 content script 高亮目标元素）
    item.addEventListener('mouseenter', () => {
        sendToContent('HIGHLIGHT_ELEMENT', { selector: el.selector });
    });
    item.addEventListener('mouseleave', () => {
        sendToContent('CLEAR_HIGHLIGHT', {});
    });

    return item;
}

/* ---- 导出 JSON ---- */
function exportJSON() {
    const data = JSON.stringify(allElements, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `pentamind_scan_${Date.now()}.json`;
    a.click();
    URL.revokeObjectURL(url);
}

/* ---- 向 content script 发消息 ---- */
function sendToContent(type, payload) {
    chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        if (tabs[0]?.id) {
            chrome.tabs.sendMessage(tabs[0].id, { type, ...payload }).catch(() => { });
        }
    });
}

/* ---- 辅助 ---- */
function showEmpty(msg = '正在扫描页面功能点...') {
    $id('risk-summary').style.display = 'none';
    $id('filter-bar').style.display = 'none';
    $id('element-list').innerHTML = '';
    $id('footer').style.display = 'none';
    $id('state-empty').style.display = 'flex';
    $id('state-empty').querySelector('p').textContent = msg;
}

function formatUrl(url) {
    try {
        const u = new URL(url);
        return u.hostname + u.pathname;
    } catch {
        return url || '—';
    }
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}
