/**
 * PentaMind - background.js (Service Worker)
 * 职责：
 *   - 接收来自 content/scanner.js 的功能点扫描结果
 *   - 按 tabId 存储各页面的功能点数据
 *   - 响应 popup 的数据查询请求
 */

// 存储结构：{ tabId: { url, timestamp, elements[] } }
const scanResults = {};

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  const tabId = sender.tab?.id;

  switch (message.type) {
    // 接收 content script 发来的扫描结果
    case 'SCAN_RESULT': {
      if (!tabId) break;
      scanResults[tabId] = {
        url: message.url,
        timestamp: Date.now(),
        elements: message.elements,
        summary: message.summary,
      };
      console.log(`[PentaMind] Tab ${tabId} scan complete: ${message.elements.length} elements found.`);
      // 更新扩展徽章显示数量
      const count = message.elements.length;
      chrome.action.setBadgeText({ text: count > 0 ? String(count) : '', tabId });
      chrome.action.setBadgeBackgroundColor({ color: '#e74c3c', tabId });
      break;
    }

    // popup 查询当前 tab 的扫描结果
    case 'GET_RESULT': {
      chrome.tabs.query({ active: true, currentWindow: true }, (tabs) => {
        const activeTabId = tabs[0]?.id;
        sendResponse({ result: scanResults[activeTabId] || null });
      });
      return true; // 保持 sendResponse 通道开放（异步）
    }

    // 主动触发对当前 tab 的重新扫描
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

// 页面导航完成后清理旧的扫描数据
chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.status === 'loading') {
    delete scanResults[tabId];
    chrome.action.setBadgeText({ text: '', tabId });
  }
});

// tab 关闭时释放内存
chrome.tabs.onRemoved.addListener((tabId) => {
  delete scanResults[tabId];
});
