# PentaMind 项目架构

## 整体架构

```mermaid
graph TB
    subgraph Browser ["浏览器环境"]
        EXT["Extension 插件<br/>DOM扫描 / API拦截"]
    end

    subgraph Platform ["管理平台"]
        FE["Frontend<br/>Vue 3 + Element Plus"]
        BE["Backend<br/>Spring Boot + MySQL"]
    end

    subgraph Tools ["独立工具"]
        PF["PathFinder<br/>目录扫描 / 敏感文件发现"]
    end

    EXT -->|数据上报| BE
    PF -->|数据上报| BE
    FE -->|REST API| BE
    BE -->|配置下发| EXT
    BE -->|配置下发| PF
```

## 模块说明

| 模块 | 路径 | 定位 | 是否可独立运行 |
|------|------|------|:-:|
| **Extension** | `extension/` | 浏览器插件，注入页面进行功能点发现和 API 拦截 | ✅ |
| **PathFinder** | `pathfinder/` | 独立 Node.js 工具，目录爆破和敏感文件扫描 | ✅ |
| **Platform Frontend** | `platform/frontend/` | Vue 3 管理面板，统一展示和操控各模块 | ⚠️ 需后端 |
| **Platform Backend** | `platform/backend/` | Spring Boot API 服务，数据存储和插件调度 | ⚠️ 需数据库 |

## 插件独立性原则

每个插件**可以完全脱离平台独立工作**：

1. **Extension** - 直接在 Chrome 中加载 `extension/` 目录即可使用
2. **PathFinder** - 直接 `node src/cli.js -t <url>` 或 `npm start` 启动 Web UI

当平台运行时，插件通过以下接口实现数据回连：

```
POST /api/plugin/heartbeat  ← 心跳保活（首次自动注册插件）
POST /api/plugin/report     ← 上报扫描结果
GET  /api/plugin/config/:name ← 拉取最新配置
GET  /api/plugin/list       ← 查询所有插件状态
```

### 心跳机制

- 插件每隔 N 秒调用 `POST /api/plugin/heartbeat`，携带 `plugin`、`version`、`type` 字段
- 后端收到心跳后更新 `pm_plugin.last_heartbeat` 时间戳
- **首次心跳自动注册**：如果 `pm_plugin` 表中不存在该插件名，自动创建记录
- **60 秒超时离线**：查询插件列表时，最后心跳超过 60 秒的插件自动标记为 OFFLINE

### 仪表盘数据联动

- 前端仪表盘（`overview.vue`）通过 API 获取真实数据，不使用硬编码
- 统计卡片：`GET /api/dashboard/overview` → 目标数、漏洞分布、任务数
- 近期扫描：`GET /api/dashboard/recent-scans` → 最新 10 条扫描任务
- 插件状态：`GET /api/plugin/list` → 在线/离线实时状态
- **30 秒自动刷新**：页面挂载后定时轮询所有接口

## 数据库设计

| 表名 | 用途 |
|------|------|
| `pm_target` | 扫描目标（URL、类型、状态、风险等级） |
| `pm_scan_task` | 扫描任务（类型、进度、结果JSON） |
| `pm_vulnerability` | 漏洞记录（类型、严重度、Payload、证据） |
| `pm_plugin` | 插件状态（版本、在线状态、心跳时间） |

## 开发协作

- 各模块互不越界，修改前确认负责归属
- 每次提交需同步更新对应模块的 README 或文档
- 独立模块的 README 包含其自身的启动和使用方式
