/**
 * PentaMind - content/net-relay.js
 * 运行环境：ISOLATED (内容脚本默认环境)
 * 职责：
 *   1. 监听 MAIN world 的 network-hook.js 通过 postMessage 发来的请求数据
 *   2. 转发到 background Service Worker 进行存储
 *   3. 接收 background 下发的 API 清单，供 scanner.js 做功能点关联
 */

(function () {
    'use strict';

    if (window.__PENTAMIND_RELAY_INIT__) return;
    window.__PENTAMIND_RELAY_INIT__ = true;

    // 监听来自页面主线程 (MAIN world) 的请求数据
    window.addEventListener('message', (event) => {
        if (event.source !== window) return;
        if (!event.data || !event.data.__pentamind__) return;

        if (event.data.type === 'NET_REQUEST') {
            // 转发给 background service worker
            chrome.runtime.sendMessage({
                type: 'API_CAPTURED',
                payload: event.data.payload,
                pageUrl: location.href,
            }).catch(() => { });
        }
    });

    // 监听来自 background 的消息（如强制刷新等）
    chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
        if (message.type === 'GET_PAGE_INFO') {
            sendResponse({
                url: location.href,
                title: document.title,
            });
        }
    });
})();
