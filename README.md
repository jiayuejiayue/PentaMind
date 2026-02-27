# PentaMind - 自动化渗透测试浏览器插件

> 🔐 一款辅助安全研究人员进行渗透测试的 Chrome 浏览器扩展，通过自动发现并分析页面功能点、评估潜在安全风险，结合 LLM 完成从信息收集到漏洞验证的完整测试链路。

---

## 项目结构

```
PentaMind/
├── docs/
│   └── extension_development_plan.md  # 完整开发方案计划书
├── extension/                          # Chrome 扩展插件主体
│   ├── manifest.json                   # MV3 配置文件 (v1.1.0)
│   ├── background.js                   # Service Worker（数据存储 + API 关联引擎）
│   ├── content/
│   │   ├── scanner.js                  # DOM 扫描引擎（功能点发现）
│   │   ├── network-hook.js             # XHR/Fetch Hook（MAIN world 注入）
│   │   └── net-relay.js                # MAIN↔ISOLATED 消息桥接
│   ├── popup/
│   │   ├── popup.html                  # 插件弹出面板（双 Tab 视图）
│   │   ├── popup.css                   # 面板样式（深色安全风格）
│   │   └── popup.js                    # 面板交互逻辑
│   └── assets/                         # 图标资源
└── README.md
```

---

## 当前版本功能（V1.1）

### ✅ V1.0 - 感知层（功能点发现）

| 模块 | 功能描述 |
|------|----------|
| **DOM 静态扫描** | 自动遍历全页面 DOM 树，提取所有可交互元素（按钮/表单/输入框/链接等） |
| **元素定位指纹** | 为每个元素生成唯一 CSS Selector 和 XPath，确保后续自动化操作精准命中 |
| **特征关键词匹配** | 基于 15 大类关键词规则库（认证/数据操作/文件/支付/权限等）识别高危功能点 |
| **动态元素监听** | MutationObserver 监听异步加载的 DOM 变化，自动补扫新增功能点 |
| **风险分级展示** | CRITICAL / HIGH / MEDIUM / LOW / INFO 五级风险分级，弹出面板可视化 |

### ✅ V1.1 - 网络流量拦截与 API 收集

| 模块 | 功能描述 |
|------|----------|
| **XHR/Fetch Hook** | 在页面主线程 Hook 原生 XMLHttpRequest 和 fetch，完整捕获请求/响应 |
| **API 接口清单** | 自动收集 URL、Method、Headers、Body、响应码、耗时等全量数据 |
| **智能过滤** | 自动过滤静态资源（CSS/JS/图片/字体）和第三方追踪域名 |
| **API 指纹去重** | 基于 Method+Path+ParamKeys 去重，同一 API 多次调用合并计数 |
| **API 风险推断** | 自动识别 AUTH/FILE/ADMIN/PAYMENT 等 8 类高危端点 |
| **功能点关联** | 4 种策略（URL路径匹配/参数名匹配/href匹配/form action）自动关联 API 与 DOM 元素 |
| **双 Tab 面板** | Popup 面板新增 API Tab，展示 Method 彩色标签、状态码、耗时和关联信息 |

---

## 安装方式（开发者模式加载）

1. 打开 Chrome 浏览器，进入 `chrome://extensions/`
2. 右上角开启 **开发者模式**
3. 点击 **加载已解压的扩展程序**
4. 选择项目中的 `extension/` 文件夹
5. 扩展安装完成，访问任意网页后点击工具栏中的 PentaMind 图标即可查看扫描结果

---

## 开发路线

| 版本 | 阶段 | 关键能力 |
|------|------|---------|
| **V1.0** ✅ | 感知层 | 功能点发现、风险分级 |
| **V1.1** ✅ | 感知层+ | 网络流量拦截、API 收集与功能点关联 |
| **V2.0** 🔜 | 评估层 | LLM 漏洞意图分析、半自动 Payload 注入 |
| **V3.0** 📋 | 探索层 | 全自动 Spider 页面爬探 |
| **V4.0** 📋 | Agent 层 | 宏录制、越权链路重放、智能 Agent |

---

## 免责声明

> ⚠️ 本工具仅用于**合法授权的安全测试**场景，严禁用于未授权的网络攻击行为。使用者须遵守所在地区的法律法规，一切责任由使用者自行承担。

---

## 协作说明

本项目为多 AI 协作开发项目（PentaMind Team）。各模块由不同成员负责，每次代码更新需同步更新文档。

- 扩展插件模块 `extension/` 由 **Antigravity** 负责
- 具体职责划分详见各模块头部注释及 `docs/` 目录