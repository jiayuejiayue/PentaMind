/**
 * PentaMind - background.js (Service Worker)
 * V1.1 - 新增网络流量 API 收集与功能点关联
 *
 * 职责：
 *   - 接收 content/scanner.js 的 DOM 功能点扫描结果
 *   - 接收 content/net-relay.js 转发的网络请求数据 (来自 network-hook.js)
 *   - 按 tabId 存储各页面的功能点 + API 数据
 *   - 自动关联 API 与功能点（基于 URL 参数、时序等）
 *   - 响应 popup 查询
 */

// ========== 数据存储 ==========
// 结构：{ tabId: { url, timestamp, elements[], apis[], summary } }
const tabData = {};

// ========== 平台回连配置 ==========
// 平台后端地址，修改此处即可切换目标平台
const PLATFORM_API = 'http://localhost:8080';
const HEARTBEAT_INTERVAL = 30000; // 30秒心跳

/**
 * 向平台发送数据（静默失败，不影响独立运行）
 */
async function reportToPlatform(endpoint, data) {
    try {
        await fetch(`${PLATFORM_API}${endpoint}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data),
        });
    } catch (e) {
        // 平台未运行时静默忽略，不影响插件独立工作
    }
}

/** 定时心跳 - 让平台知道插件在线 */
function startHeartbeat() {
    const send = () => reportToPlatform('/api/plugin/heartbeat', {
        plugin: 'Extension',
        version: '1.1',
        type: 'EXTENSION',
    });
    send(); // 立即发一次
    setInterval(send, HEARTBEAT_INTERVAL);
}
startHeartbeat();

/**
 * 上报扫描结果到平台
 */
function reportScanResult(url, elements, apis) {
    reportToPlatform('/api/plugin/report', {
        plugin: 'Extension',
        type: 'SCAN_RESULT',
        url,
        elementCount: elements?.length || 0,
        apiCount: apis?.length || 0,
        elements: (elements || []).slice(0, 50).map(el => ({
            tag: el.tag,
            selector: el.selector,
            riskFeatures: el.riskFeatures,
            name: el.name,
        })),
        apis: (apis || []).slice(0, 50).map(api => ({
            method: api.method,
            url: api.url,
            statusCode: api.statusCode,
            riskTags: api.riskTags,
        })),
        timestamp: Date.now(),
    });
}


/** 获取或初始化某 tab 的数据容器 */
function getTab(tabId, url) {
    if (!tabData[tabId]) {
        tabData[tabId] = { url: url || '', timestamp: Date.now(), elements: [], apis: [], summary: null };
    }
    if (url) tabData[tabId].url = url;
    return tabData[tabId];
}

// ========== API 去重 ==========
/** 生成 API 的唯一指纹 (Method + URL path, 忽略动态参数值) */
function apiFingerprint(api) {
    try {
        const u = new URL(api.url);
        // 仅保留路径参数名（忽略值）用于去重
        const paramKeys = Array.from(u.searchParams.keys()).sort().join(',');
        return `${api.method}|${u.origin}${u.pathname}|${paramKeys}`;
    } catch {
        return `${api.method}|${api.url}`;
    }
}

// ========== API 与功能点关联 ==========
/**
 * 尝试将一条 API 记录与已有功能点进行关联
 * 策略：
 *   1. URL 路径关键词匹配（如 /login -> tag=AUTH 的元素）
 *   2. 请求参数名与表单字段 name 匹配
 *   3. 时间接近度（未来可结合点击事件用）
 */
function correlateApiWithElements(api, elements) {
    if (!api || !elements || !elements.length) return [];
    const correlated = [];

    let apiPath = '';
    let apiParamKeys = [];
    try {
        const u = new URL(api.url);
        apiPath = u.pathname.toLowerCase();
        apiParamKeys = Array.from(u.searchParams.keys()).map((k) => k.toLowerCase());
    } catch {
        apiPath = (api.url || '').toLowerCase();
    }

    // 如果请求体是 JSON/FormData，提取字段名
    if (api.requestBody) {
        try {
            const bodyObj = JSON.parse(api.requestBody);
            if (bodyObj && typeof bodyObj === 'object') {
                apiParamKeys.push(...Object.keys(bodyObj).map((k) => k.toLowerCase()));
            }
        } catch { /* 非 JSON 则跳过 */ }
    }

    for (const el of elements) {
        let score = 0;
        const reasons = [];

        // 策略1：URL 路径含元素的风险标签关键词
        if (el.riskFeatures && el.riskFeatures.length) {
            for (const feat of el.riskFeatures) {
                const tagLower = feat.tag.toLowerCase().replace(/_/g, '');
                if (apiPath.includes(tagLower) || apiPath.includes(tagLower.replace('auth', 'login'))) {
                    score += 3;
                    reasons.push(`URL_PATH_MATCH:${feat.tag}`);
                }
            }
        }

        // 策略2：表单字段 name 出现在 API 参数中
        if (el.name && apiParamKeys.includes(el.name.toLowerCase())) {
            score += 5;
            reasons.push(`PARAM_NAME_MATCH:${el.name}`);
        }

        // 策略3：元素 href 与 API URL 路径一致
        if (el.href) {
            try {
                const elPath = new URL(el.href).pathname.toLowerCase();
                if (apiPath === elPath) {
                    score += 4;
                    reasons.push('HREF_PATH_MATCH');
                }
            } catch { /* href 可能不是完整 URL */ }
        }

        // 策略4：form action 匹配
        if (el.tag === 'form' && el.textFeature) {
            const actionMatch = el.textFeature.match(/action="([^"]+)"/i);
            if (actionMatch) {
                const actionPath = actionMatch[1].toLowerCase();
                if (apiPath.includes(actionPath) || actionPath.includes(apiPath)) {
                    score += 5;
                    reasons.push('FORM_ACTION_MATCH');
                }
            }
        }

        if (score > 0) {
            correlated.push({ elementSelector: el.selector, score, reasons });
        }
    }

    // 按分数降序
    correlated.sort((a, b) => b.score - a.score);
    return correlated.slice(0, 5); // 最多关联5个元素
}

// ========== 消息处理 ==========
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    const tabId = sender.tab?.id;

    switch (message.type) {

        // ---- DOM 功能点扫描结果 ----
        case 'SCAN_RESULT': {
            if (!tabId) break;
            const tab = getTab(tabId, message.url);
            tab.elements = message.elements;
            tab.summary = message.summary;
            tab.timestamp = Date.now();

            // 对已有的 API 重新关联
            tab.apis.forEach((api) => {
                api.correlations = correlateApiWithElements(api, tab.elements);
            });

            // 更新徽章
            const total = tab.elements.length + tab.apis.length;
            chrome.action.setBadgeText({ text: total > 0 ? String(total) : '', tabId });
            chrome.action.setBadgeBackgroundColor({ color: '#e74c3c', tabId });

            // 上报平台
            reportScanResult(tab.url, tab.elements, tab.apis);
            break;
        }

        // ---- 网络请求捕获（来自 net-relay.js 转发） ----
        case 'API_CAPTURED': {
            if (!tabId) break;
            const tab = getTab(tabId, message.pageUrl);
            const api = message.payload;

            // 过滤掉静态资源和浏览器内部请求
            if (shouldFilterApi(api)) break;

            // 去重
            const fp = apiFingerprint(api);
            const existing = tab.apis.find((a) => a._fingerprint === fp);
            if (existing) {
                // 同一 API 多次调用时，更新最后一次的数据
                existing.lastSeen = Date.now();
                existing.callCount = (existing.callCount || 1) + 1;
                existing.statusCode = api.statusCode;
                existing.duration = api.duration;
                break;
            }

            // 新 API，进行功能点关联
            api._fingerprint = fp;
            api.callCount = 1;
            api.lastSeen = Date.now();
            api.correlations = correlateApiWithElements(api, tab.elements);

            // 推断 API 风险标签
            api.riskTags = inferApiRisk(api);

            tab.apis.push(api);

            // 限制单 tab 最多保存200条API，超出则移除最早的
            if (tab.apis.length > 200) tab.apis.shift();

            // 更新徽章
            const total = tab.elements.length + tab.apis.length;
            chrome.action.setBadgeText({ text: total > 0 ? String(total) : '', tabId });
            chrome.action.setBadgeBackgroundColor({ color: '#58a6ff', tabId });
            break;
        }

        // ---- Popup 查询当前 tab 全部数据 ----
        case 'GET_RESULT': {
            chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
                const activeTabId = tabs[0]?.id;
                sendResponse({ result: tabData[activeTabId] || null });
            });
            return true;
        }

        // ---- 主动触发重扫 ----
        case 'TRIGGER_SCAN': {
            chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
                const activeTabId = tabs[0]?.id;
                if (activeTabId) {
                    chrome.scripting.executeScript({
                        target: { tabId: activeTabId },
                        files: ['content/scanner.js'],
                    });
                }
                sendResponse({ ok: true });
            });
            return true;
        }

        default:
            break;
    }
});

// ========== API 过滤规则 ==========
/** 过滤静态资源、图片、字体等无关请求 */
function shouldFilterApi(api) {
    if (!api.url) return true;
    const url = api.url.toLowerCase();

    // 过滤掉资源文件
    const staticExts = [
        '.css', '.js', '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico',
        '.woff', '.woff2', '.ttf', '.eot', '.mp4', '.webm', '.mp3',
        '.map', '.br', '.gz',
    ];
    if (staticExts.some((ext) => url.includes(ext))) return true;

    // 过滤浏览器内部
    if (url.startsWith('chrome://') || url.startsWith('chrome-extension://')) return true;
    if (url.startsWith('moz-extension://')) return true;

    // 过滤 Google/Analytics 等第三方追踪
    const trackingDomains = [
        'google-analytics.com', 'googletagmanager.com', 'doubleclick.net',
        'facebook.net', 'hotjar.com', 'mixpanel.com', 'segment.com',
    ];
    if (trackingDomains.some((d) => url.includes(d))) return true;

    return false;
}

// ========== API 风险推断 ==========
/** 基于 URL/Method/参数等推断 API 可能的风险分类 */
function inferApiRisk(api) {
    const tags = [];
    const url = (api.url || '').toLowerCase();
    const method = (api.method || '').toUpperCase();
    const body = (api.requestBody || '').toLowerCase();
    const combined = url + ' ' + body;

    // 写操作方法天然敏感
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        tags.push({ tag: 'WRITE_METHOD', risk: 'MEDIUM' });
    }

    // 认证相关
    if (/\/(login|signin|auth|oauth|token|session|register|signup)/.test(url)) {
        tags.push({ tag: 'AUTH_ENDPOINT', risk: 'HIGH' });
    }

    // 文件操作
    if (/\/(upload|download|import|export|file|attachment)/.test(url)) {
        tags.push({ tag: 'FILE_ENDPOINT', risk: 'CRITICAL' });
    }

    // 管理接口
    if (/\/(admin|manage|dashboard|config|setting)/.test(url)) {
        tags.push({ tag: 'ADMIN_ENDPOINT', risk: 'CRITICAL' });
    }

    // 用户/数据 CRUD
    if (/\/(user|account|profile|delete|remove|update|edit|create|add)/.test(url)) {
        tags.push({ tag: 'DATA_CRUD', risk: 'HIGH' });
    }

    // 参数含 ID 类敏感字段（可能存在 IDOR）
    if (/[?&](id|user_id|uid|order_id|file|path|url|redirect|next)=/i.test(url)) {
        tags.push({ tag: 'SENSITIVE_PARAM', risk: 'HIGH' });
    }

    // 支付相关
    if (/\/(pay|payment|checkout|order|price|charge|refund)/.test(url)) {
        tags.push({ tag: 'PAYMENT_ENDPOINT', risk: 'CRITICAL' });
    }

    return tags;
}

// ========== Tab 生命周期 ==========
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
    if (changeInfo.status === 'loading') {
        delete tabData[tabId];
        chrome.action.setBadgeText({ text: '', tabId });
    }
});

chrome.tabs.onRemoved.addListener((tabId) => {
    delete tabData[tabId];
});
