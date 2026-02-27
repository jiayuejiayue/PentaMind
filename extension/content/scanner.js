/**
 * PentaMind - content/scanner.js
 * 核心扫描引擎 V1.0
 *
 * 功能模块：
 *   1. DOM 静态遍历  ——  扫描所有可交互元素
 *   2. 元素定位指纹  ——  生成唯一 XPath / CSS Selector
 *   3. 特征匹配强化  ——  关键词 + 正则识别高危功能点
 *   4. 动态监听      ——  MutationObserver 捕获异步加载元素
 */

(function () {
    'use strict';

    /* ============================================================
     * 模块一：常量与配置
     * ============================================================ */

    /** 目标元素 CSS 选择器（静态 DOM 维度） */
    const INTERACTIVE_SELECTOR = [
        'button',
        'input',
        'select',
        'textarea',
        'a[href]',
        '[role="button"]',
        '[role="link"]',
        '[role="menuitem"]',
        '[role="tab"]',
        '[onclick]',
        '[ng-click]',
        '[v-on\\:click]',
        '[data-action]',
        '[data-href]',
        'form',
        'label[for]',
    ].join(', ');

    /**
     * 高危特征关键词库
     * 分类：认证 / 数据操作 / 文件 / 用户管理 / 敏感参数
     */
    const KEYWORD_RULES = [
        // 认证相关
        { keywords: ['login', '登录', '登陆', 'sign in', 'signin'], tag: 'AUTH', risk: 'HIGH' },
        { keywords: ['register', '注册', 'signup', 'sign up'], tag: 'AUTH', risk: 'MEDIUM' },
        { keywords: ['logout', '退出', '注销', 'sign out'], tag: 'AUTH', risk: 'LOW' },
        { keywords: ['password', '密码', 'passwd', 'pwd'], tag: 'AUTH', risk: 'HIGH' },
        { keywords: ['token', 'session', 'captcha', '验证码'], tag: 'AUTH', risk: 'MEDIUM' },

        // 数据增删改
        { keywords: ['submit', '提交', 'save', '保存', 'confirm', '确认', '确定'], tag: 'DATA_WRITE', risk: 'HIGH' },
        { keywords: ['delete', '删除', 'remove', '移除', '清除', 'drop'], tag: 'DATA_DELETE', risk: 'CRITICAL' },
        { keywords: ['edit', '编辑', 'update', '更新', 'modify', '修改'], tag: 'DATA_WRITE', risk: 'HIGH' },
        { keywords: ['create', '新建', 'add', '添加', 'insert', '插入'], tag: 'DATA_WRITE', risk: 'MEDIUM' },
        { keywords: ['reset', '重置', 'clear', '清空'], tag: 'DATA_WRITE', risk: 'MEDIUM' },

        // 文件操作
        { keywords: ['upload', '上传', 'import', '导入'], tag: 'FILE_OPS', risk: 'CRITICAL' },
        { keywords: ['download', '下载', 'export', '导出'], tag: 'FILE_OPS', risk: 'HIGH' },

        // 权限 / 用户管理
        { keywords: ['admin', '管理', 'role', '角色', 'permission', '权限', 'grant', '授权'], tag: 'ADMIN', risk: 'CRITICAL' },
        { keywords: ['user', '用户', 'account', '账户', 'profile', '个人信息'], tag: 'USER_MGT', risk: 'MEDIUM' },

        // 支付 / 金融
        { keywords: ['pay', '支付', '付款', 'order', '订单', 'checkout', 'price', '金额', '价格'], tag: 'PAYMENT', risk: 'CRITICAL' },

        // 搜索 / 查询（SQL注入易发点）
        { keywords: ['search', '搜索', '查询', 'query', 'filter', '筛选', 'find'], tag: 'QUERY', risk: 'HIGH' },

        // 敏感参数（URL / 隐藏字段）
        { keywords: ['id=', 'user_id', 'uid', 'file=', 'path=', 'url=', 'redirect=', 'next='], tag: 'PARAM_SENSITIVE', risk: 'HIGH' },
    ];

    /** 特殊输入类型（天然高危） */
    const HIGH_RISK_INPUT_TYPES = new Set([
        'file', 'password', 'hidden', 'email', 'search', 'url', 'tel',
    ]);

    /* ============================================================
     * 模块二：工具函数
     * ============================================================ */

    /**
     * 生成元素的唯一 CSS Selector（指纹核心）
     * 策略：id > 精准属性 > nth-of-type 逐级回溯
     */
    function getCssSelector(el) {
        if (el.id && /^[a-zA-Z][\w-]*$/.test(el.id)) {
            return `#${CSS.escape(el.id)}`;
        }

        const parts = [];
        let node = el;
        while (node && node.nodeType === Node.ELEMENT_NODE && node !== document.body) {
            let segment = node.tagName.toLowerCase();

            // 优先使用 name 属性
            if (node.name) {
                segment += `[name="${CSS.escape(node.name)}"]`;
                parts.unshift(segment);
                break;
            }

            // 计算 nth-of-type
            const siblings = Array.from(node.parentNode?.children || []).filter(
                (s) => s.tagName === node.tagName
            );
            if (siblings.length > 1) {
                const idx = siblings.indexOf(node) + 1;
                segment += `:nth-of-type(${idx})`;
            }

            parts.unshift(segment);
            node = node.parentNode;
        }
        return parts.join(' > ');
    }

    /**
     * 生成元素的 XPath（备用定位，更精准）
     */
    function getXPath(el) {
        if (el.id) return `//*[@id="${el.id}"]`;
        const parts = [];
        let node = el;
        while (node && node.nodeType === Node.ELEMENT_NODE) {
            const tag = node.tagName.toLowerCase();
            const siblings = Array.from(node.parentNode?.children || []).filter(
                (s) => s.tagName === node.tagName
            );
            const idx = siblings.indexOf(node) + 1;
            parts.unshift(siblings.length > 1 ? `${tag}[${idx}]` : tag);
            node = node.parentNode;
        }
        return '/' + parts.join('/');
    }

    /**
     * 提取元素的综合文本特征
     * （innerText + placeholder + aria-label + value + title）
     */
    function getTextFeature(el) {
        return [
            el.innerText?.trim(),
            el.getAttribute('placeholder'),
            el.getAttribute('aria-label'),
            el.getAttribute('title'),
            el.getAttribute('value'),
            el.getAttribute('name'),
            el.getAttribute('data-action'),
            el.getAttribute('alt'),
        ]
            .filter(Boolean)
            .join(' ')
            .toLowerCase()
            .slice(0, 200); // 限长，避免超大DOM
    }

    /**
     * 元素是否可见（过滤隐藏元素，保留 hidden input 因其有安全价值）
     */
    function isVisible(el) {
        if (el.type === 'hidden') return true; // hidden input 保留
        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
            return false;
        }
        const rect = el.getBoundingClientRect();
        return rect.width > 0 || rect.height > 0;
    }

    /* ============================================================
     * 模块三：特征匹配 —— 识别高危功能点
     * ============================================================ */

    /**
     * 对元素进行关键词 / 正则匹配，返回匹配到的标签列表
     */
    function matchRiskFeatures(el) {
        const text = getTextFeature(el);
        const attrStr = el.outerHTML.slice(0, 300).toLowerCase();
        const combined = text + ' ' + attrStr;

        const matched = [];

        for (const rule of KEYWORD_RULES) {
            if (rule.keywords.some((kw) => combined.includes(kw.toLowerCase()))) {
                matched.push({ tag: rule.tag, risk: rule.risk });
            }
        }

        // 特殊 input 类型
        if (el.tagName === 'INPUT' && HIGH_RISK_INPUT_TYPES.has(el.type?.toLowerCase())) {
            matched.push({ tag: `INPUT_${(el.type || 'text').toUpperCase()}`, risk: el.type === 'file' || el.type === 'hidden' ? 'CRITICAL' : 'HIGH' });
        }

        return matched;
    }

    /**
     * 计算元素综合风险等级
     */
    const RISK_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    function calcOverallRisk(features) {
        if (!features.length) return 'INFO';
        return features.reduce((best, f) => {
            return RISK_ORDER.indexOf(f.risk) < RISK_ORDER.indexOf(best) ? f.risk : best;
        }, 'INFO');
    }

    /* ============================================================
     * 模块四：DOM 静态扫描
     * ============================================================ */

    /**
     * 扫描给定根节点下所有可交互元素，返回结构化报告数组
     */
    function scanElements(root = document) {
        const nodes = root.querySelectorAll(INTERACTIVE_SELECTOR);
        const results = [];
        const seen = new Set(); // 防重复（同一元素被多个选择器匹配）

        nodes.forEach((el) => {
            if (seen.has(el)) return;
            seen.add(el);

            if (!isVisible(el)) return;

            const selector = getCssSelector(el);
            const xpath = getXPath(el);
            const textFeature = getTextFeature(el);
            const riskFeatures = matchRiskFeatures(el);
            const overallRisk = calcOverallRisk(riskFeatures);

            results.push({
                tag: el.tagName.toLowerCase(),
                type: el.type || null,
                selector,
                xpath,
                text: el.innerText?.trim().slice(0, 100) || '',
                textFeature,
                href: el.href || null,
                name: el.name || null,
                id: el.id || null,
                riskFeatures,
                overallRisk,
                // 元素的坐标（用于后续截图标注）
                rect: (() => {
                    const r = el.getBoundingClientRect();
                    return { top: Math.round(r.top), left: Math.round(r.left), width: Math.round(r.width), height: Math.round(r.height) };
                })(),
            });
        });

        return results;
    }

    /* ============================================================
     * 模块五：汇总与上报
     * ============================================================ */

    function buildSummary(elements) {
        const riskCount = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
        const tagCount = {};
        const tags = new Set();

        elements.forEach((el) => {
            riskCount[el.overallRisk] = (riskCount[el.overallRisk] || 0) + 1;
            tagCount[el.tag] = (tagCount[el.tag] || 0) + 1;
            el.riskFeatures.forEach((f) => tags.add(f.tag));
        });

        return {
            total: elements.length,
            riskCount,
            tagCount,
            featureTags: Array.from(tags),
        };
    }

    function reportToBackground(elements) {
        const summary = buildSummary(elements);
        chrome.runtime.sendMessage({
            type: 'SCAN_RESULT',
            url: location.href,
            elements,
            summary,
        });
    }

    /* ============================================================
     * 模块六：动态监听 —— MutationObserver
     * ============================================================ */

    let debounceTimer = null;
    let lastElementCount = 0;

    function triggerRescan() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(() => {
            const elements = scanElements();
            // 仅在元素数量发生变化时上报，避免无意义的刷新
            if (elements.length !== lastElementCount) {
                lastElementCount = elements.length;
                reportToBackground(elements);
            }
        }, 800); // 防抖 800ms，等待异步渲染稳定
    }

    const observer = new MutationObserver((mutations) => {
        const hasNewNodes = mutations.some(
            (m) => m.addedNodes.length > 0 || m.type === 'attributes'
        );
        if (hasNewNodes) triggerRescan();
    });

    /* ============================================================
     * 启动入口
     * ============================================================ */

    function start() {
        // 立即执行一次静态扫描
        const elements = scanElements();
        lastElementCount = elements.length;
        reportToBackground(elements);

        // 开启动态监听
        observer.observe(document.body || document.documentElement, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['onclick', 'style', 'class', 'hidden', 'disabled'],
        });
    }

    // 根据文档状态决定立即执行还是等待加载
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }

    // 监听来自 popup / background 的主动重扫请求
    chrome.runtime.onMessage.addListener((message) => {
        if (message.type === 'FORCE_SCAN') {
            const elements = scanElements();
            lastElementCount = elements.length;
            reportToBackground(elements);
        }
    });
})();
