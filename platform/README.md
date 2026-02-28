# PentaMind Platform

> 🖥️ 渗透测试管理平台 - 前端 + 后端

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3.5 + Vite 5 + Element Plus 2.13 + Vue Router 4 + Pinia 2 |
| 后端 | Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 |
| 包管理 | pnpm (前端) / Maven 3.9 (后端) |

## 快速启动

### 1. 数据库初始化
```bash
mysql -h 127.0.0.1 -P 3307 -u root -proot < backend/sql/init.sql
```

### 2. 后端
```bash
cd backend
# 确保设置 JAVA_HOME
mvn spring-boot:run
# 运行在 http://localhost:8080
```

### 3. 前端
```bash
cd frontend
pnpm install
pnpm dev
# 运行在 http://localhost:5173
```

## 目录结构

```
platform/
├── frontend/               # Vue 3 前端
│   ├── src/
│   │   ├── layout/         # 若依风格三栏布局
│   │   ├── router/         # 路由配置（7模块26页面）
│   │   ├── store/          # Pinia 状态管理
│   │   ├── views/          # 业务页面
│   │   ├── components/     # 公共组件
│   │   └── styles/         # 主题 CSS 变量
│   ├── package.json
│   └── vite.config.js
└── backend/                # Spring Boot 后端
    ├── src/main/java/com/pentamind/
    │   ├── controller/     # REST API
    │   ├── service/        # 业务逻辑层
    │   ├── mapper/         # MyBatis-Plus DAO
    │   ├── entity/         # 数据实体
    │   ├── config/         # CORS、分页等配置
    │   └── common/         # 统一响应 R
    ├── src/main/resources/
    │   └── application.yml
    ├── sql/init.sql         # 数据库初始化脚本
    └── pom.xml
```

## API 端点

### 仪表盘（数据来自数据库实时查询）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dashboard/overview` | 概览统计（目标数/漏洞分布/任务数/报告数） |
| GET | `/api/dashboard/recent-scans` | 近期扫描活动（最新10条） |
| GET | `/api/dashboard/health` | 系统健康检查 |

### 目标管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/target/list` | 目标分页列表 |
| POST | `/api/target` | 新增目标 |
| PUT | `/api/target` | 修改目标 |
| DELETE | `/api/target/{id}` | 删除目标 |

### 插件数据联动

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/plugin/list` | 插件列表（自动判断在线/离线） |
| POST | `/api/plugin/heartbeat` | 插件心跳（首次自动注册，60秒超时离线） |
| POST | `/api/plugin/report` | 插件数据上报 |
| GET | `/api/plugin/config/{name}` | 获取插件配置 |

> 前端仪表盘每 30 秒自动刷新，所有数据均来自后端 API 实时查询。

