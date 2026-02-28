# PentaMind

> 🔐 AI 驱动的渗透测试工具生态 —— 浏览器插件 + 独立扫描工具 + 统一管理平台

---

## 模块矩阵

| 模块 | 路径 | 定位 | 独立运行 | 状态 |
|------|------|------|:--------:|------|
| **Extension** | [`extension/`](extension/) | 浏览器插件：DOM 功能点发现 + API 拦截 | ✅ | V1.1 ✅ |
| **PathFinder** | [`pathfinder/`](pathfinder/) | 独立工具：目录爆破 + 敏感文件扫描 | ✅ | V1.0 ✅ |
| **Platform** | [`platform/`](platform/) | 管理平台：Vue 3 前端 + Spring Boot 后端 | ⚠️ 需数据库 | V1.0 ✅ |

> 每个插件可**单独下载使用**，也可通过平台的插件回连 API 实现统一数据管理。  
> 详见 [`docs/architecture.md`](docs/architecture.md)

---

## 快速开始

### Extension（浏览器插件）
1. Chrome 打开 `chrome://extensions/` → 开启**开发者模式**
2. **加载已解压的扩展程序** → 选择 `extension/` 目录
3. 访问任意网页，点击工具栏  PentaMind 图标

### PathFinder（路径扫描）
```bash
cd pathfinder
npm start              # Web UI → http://localhost:3000
# 或
node src/cli.js -t https://target.com -c 30
```

### Platform（管理平台）
```bash
# 1. 初始化数据库
mysql -h 127.0.0.1 -P 3307 -u root -proot < platform/backend/sql/init.sql

# 2. 启动后端（需 JDK 17+, Maven 3.9+）
cd platform/backend && mvn spring-boot:run

# 3. 启动前端
cd platform/frontend && pnpm install && pnpm dev
# 前端 → http://localhost:5173  |  后端 → http://localhost:8080
```

---

## 核心功能

### 🔍 Extension V1.1
- DOM 静态遍历 + 交互元素指纹
- 关键词风险匹配 + MutationObserver 动态监听
- XHR/Fetch 网络拦截 + API 收集去重 + 功能点关联

### 🗂️ PathFinder V1.0
- 4 类内置字典（通用目录/敏感文件/备份文件/API 端点）
- 并发扫描引擎 + 自动 404 校准 + 敏感内容检测
- Web UI + CLI 双模式

### 🖥️ Platform V1.0
- 若依风格三栏布局（侧边栏可拖拽调整）
- 7 大功能模块：仪表盘 / 信息收集 / 功能点检测 / 漏洞扫描 / 测试管理 / 智能分析 / 系统配置
- 深色/浅色主题切换
- 插件数据回连 API

---

## 开发路线

| 版本 | 阶段 | 关键能力 |
|------|------|---------|
| Extension V1.0 ✅ | 感知层 | 功能点发现、风险分级 |
| Extension V1.1 ✅ | 感知层+ | 网络拦截、API 收集与关联 |
| PathFinder V1.0 ✅ | 独立工具 | 目录爆破、敏感文件、备份检测 |
| Platform V1.0 ✅ | 管理中心 | 若依布局、导航体系、后端架构 |
| Extension V2.0 🔜 | 评估层 | LLM 漏洞意图分析 |
| V3.0 📋 | 探索层 | 全自动 Spider 页面爬探 |
| V4.0 📋 | Agent 层 | 宏录制、越权重放、智能 Agent |

---

## 项目结构

```
PentaMind/
├── extension/           # 浏览器插件（独立）
├── pathfinder/          # 路径扫描工具（独立）
├── platform/
│   ├── frontend/        # Vue 3 + Element Plus
│   └── backend/         # Spring Boot + MySQL
├── docs/
│   └── architecture.md  # 架构说明
└── README.md
```

---

## 协作说明

- 各模块开发互不越界，修改前确认归属
- 每次代码更新同步更新对应模块文档
- 插件保持独立可用，平台作为统一管理支撑

## 免责声明

> ⚠️ 仅用于**合法授权的安全测试**，严禁用于未授权攻击。使用者须遵守所在地区法律法规。