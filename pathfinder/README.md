# PentaMind PathFinder

> 🔍 专注于目录和文件路径扫描的独立安全工具，支持目录爆破、敏感文件发现、备份文件检测和 API 端点枚举。

---

## 功能特性

| 模块 | 描述 |
|------|------|
| **目录爆破扫描** | 高效并发探测目标站点的隐藏目录和文件 |
| **敏感文件发现** | 内置 .env / .git / phpinfo / 数据库配置等 50+ 敏感文件规则 |
| **备份文件检测** | 基于域名动态生成备份文件名，支持 zip/tar.gz/rar/7z/sql |
| **API 端点枚举** | 自动探测 REST API / GraphQL / Swagger / Actuator 等端点 |
| **智能 404 校准** | 自动识别自定义 404 页面，减少误报 |
| **敏感内容匹配** | 13 条正则规则检测响应内容中的密钥/密码/配置泄露 |
| **Web UI 面板** | 深色安全风格前端，SSE 实时进度推送 |
| **CLI 命令行** | 支持脚本化批量扫描 |

---

## 快速开始

### Web UI 模式
```bash
cd pathfinder
npm start
# 浏览器访问 http://localhost:3000
```

### CLI 命令行模式
```bash
cd pathfinder
node src/cli.js -t https://example.com -c 30
```

### CLI 参数
| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-t, --target` | 目标地址（必填） | — |
| `-c, --concurrency` | 并发数 | 20 |
| `--timeout` | 请求超时 (ms) | 8000 |
| `-e, --ext` | 追加扩展名（可多次） | — |
| `-d, --dict` | 自定义字典文件 | — |
| `--no-common` | 不加载通用目录字典 | false |
| `--no-sensitive` | 不加载敏感文件字典 | false |
| `--no-backup` | 不加载备份文件字典 | false |
| `--no-api` | 不加载 API 端点字典 | false |

---

## 项目结构

```
pathfinder/
├── package.json          # 项目配置
├── src/
│   ├── dictionary.js     # 字典管理模块（4类内置 + 自定义）
│   ├── scanner.js        # 路径扫描引擎（并发池 + 内容检测）
│   ├── server.js         # Web 服务器（API + SSE + 静态文件）
│   └── cli.js            # CLI 命令行入口
├── ui/
│   ├── index.html        # Web UI 面板
│   ├── style.css         # 深色安全风格样式
│   └── app.js            # 前端交互逻辑
└── README.md
```

---

## 免责声明

> ⚠️ 仅用于合法授权的安全测试，严禁用于未授权攻击。
