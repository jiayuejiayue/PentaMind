#!/usr/bin/env python3
"""
PentaMind MCP Tools Server
==========================
将所有安全工具封装为标准 MCP Tools，通过 stdio 模式供 Java 后端调用。

启动方式（stdio 模式，供 Java 进程调用）:
    python main.py

启动方式（SSE 模式，供远程节点调用）:
    python main.py --sse

运行前须安装依赖:
    pip install -r requirements.txt
"""

import asyncio
import os
import sys
# 强行载入 --user 对应安装目录 避免 Java 调用时丢失
sys.path.append(r"C:\Users\86157\AppData\Roaming\Python\Python311\site-packages")

import subprocess
import logging
import argparse
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv
import mcp.server.stdio
from mcp.server import Server
from mcp.server.sse import SseServerTransport
from mcp.types import Tool, TextContent
from pydantic import AnyUrl

from browser_use import ChatOpenAI
from browser_use import Agent, Browser

# ---- 环境配置 ----
load_dotenv()
LOG_LEVEL   = os.getenv("LOG_LEVEL", "INFO").upper()
wordlist_dir_path = __file__
wordlist_default_path = Path(__file__).parent.parent / "backend" / "tools" / "wordlists"
WORDLIST_DIR = Path(os.getenv("WORDLIST_DIR", wordlist_default_path))

# 读取代理配置
HTTP_PROXY = os.getenv("HTTP_PROXY", "")
HTTPS_PROXY = os.getenv("HTTPS_PROXY", "")

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    handlers=[logging.StreamHandler(sys.stderr)],
)
logger = logging.getLogger("pentamind.mcp")

# ---- 辅助函数 ----

async def _run_cmd(cmd: list[str], timeout: int = 600) -> str:
    """异步执行命令行工具，返回 stdout+stderr 合并内容。"""
    logger.info(">>> 执行: %s", " ".join(cmd))
    try:
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
        )
        stdout, _ = await asyncio.wait_for(proc.communicate(), timeout=timeout)
        output = stdout.decode("utf-8", errors="replace")
        logger.info("<<< 退出码 %s，输出 %d 字节", proc.returncode, len(output))
        return output or "(无输出)"
    except asyncio.TimeoutError:
        logger.warning("工具执行超时 (%ds): %s", timeout, cmd[0])
        return f"[超时] 工具 {cmd[0]} 执行超过 {timeout} 秒，已中止。"
    except FileNotFoundError:
        logger.error("工具未找到: %s（请确认已安装并在 PATH 中）", cmd[0])
        return f"[错误] 工具 {cmd[0]} 未安装或不在系统 PATH 中。"
    except Exception as e:
        logger.error("执行异常: %s", e)
        return f"[错误] {e}"


def _wordlist(name: str) -> str:
    """将逻辑字典名（common/big/api）解析为绝对路径。"""
    mapping = {
        "common": str(WORDLIST_DIR / "common.txt"),
        "big":    str(WORDLIST_DIR / "big.txt"),
        "api":    str(WORDLIST_DIR / "api.txt"),
    }
    return mapping.get(name.lower(), name)   # fallback 直接当路径用


# ---- MCP Server 实例 ----
server = Server("pentamind-tools")


# ====================  工具定义  ====================

@server.list_tools()
async def list_tools() -> list[Tool]:
    """向 MCP Client 暴露所有可用工具的 Schema。"""
    return [
        Tool(
            name="nmap_port_scan",
            description="使用 Nmap 对目标进行端口扫描和服务指纹识别。返回开放的端口列表和服务版本信息。",
            inputSchema={
                "type": "object",
                "properties": {
                    "target": {
                        "type": "string",
                        "description": "目标 IP 或域名，如 192.168.1.1 或 example.com"
                    },
                    "ports": {
                        "type": "string",
                        "description": "端口范围，如 '80,443' 或 '1-1000'，默认为常见服务端口",
                        "default": "21,22,23,25,53,80,110,139,143,389,443,445,993,995,3306,3389,5432,6379,8080,8443,8888,27017"
                    },
                },
                "required": ["target"],
            },
        ),
        Tool(
            name="dirsearch_dir",
            description="使用 Dirsearch 对目标 Web 应用进行目录和文件枚举爆破，发现隐藏路径、备份文件、管理后台等。",
            inputSchema={
                "type": "object",
                "properties": {
                    "target": {
                        "type": "string",
                        "description": "目标 URL，如 http://example.com"
                    },
                    "extensions": {
                        "type": "string",
                        "description": "扫描的文件扩展名，逗号分隔，如 php,html,bak,txt",
                        "default": "php,html,bak,txt,js,json,xml"
                    },
                    "wordlist": {
                        "type": "string",
                        "description": "字典名（common/big/api）或绝对路径，默认 common",
                        "default": "common"
                    },
                    "threads": {
                        "type": "integer",
                        "description": "并发线程数，默认 20",
                        "default": 20
                    },
                    "max_time": {
                        "type": "integer",
                        "description": "最大扫描时间（秒），默认 540",
                        "default": 540
                    },
                },
                "required": ["target"],
            },
        ),
        Tool(
            name="subfinder_domain",
            description="使用 Subfinder 被动枚举目标的子域名，通过多个数据源进行子域名收集不发主动请求。",
            inputSchema={
                "type": "object",
                "properties": {
                    "domain": {
                        "type": "string",
                        "description": "根域名，如 example.com"
                    },
                },
                "required": ["domain"],
            },
        ),
        Tool(
            name="whatweb_fingerprint",
            description="使用 WhatWeb 识别目标网站使用的 Web 技术栈、框架、CMS、服务器版本等指纹信息。",
            inputSchema={
                "type": "object",
                "properties": {
                    "target": {
                        "type": "string",
                        "description": "目标 URL，如 http://example.com"
                    },
                },
                "required": ["target"],
            },
        ),
        Tool(
            name="httpx_alive_detect",
            description="使用 httpx 批量验证 URL/IP 的 HTTP 服务存活性，提取标题、状态码和技术指纹。",
            inputSchema={
                "type": "object",
                "properties": {
                    "target": {
                        "type": "string",
                        "description": "目标 URL 或域名，也可传入换行分隔的多个目标"
                    },
                },
                "required": ["target"],
            },
        ),
        Tool(
            name="nuclei_vuln_scan",
            description="使用 Nuclei 模板库对目标进行漏洞扫描，覆盖 CVE、配置错误、暴露接口等场景。",
            inputSchema={
                "type": "object",
                "properties": {
                    "target": {
                        "type": "string",
                        "description": "目标 URL 或 IP"
                    },
                    "severity": {
                        "type": "string",
                        "description": "过滤严重级别，如 critical,high,medium",
                        "default": "critical,high"
                    },
                    "tags": {
                        "type": "string",
                        "description": "模板标签过滤，如 cve,exposure"
                    },
                },
                "required": ["target"],
            },
        ),
        Tool(
            name="browser_use_explore",
            description="使用大语言模型(LLM)驱动浏览器，执行复杂的网页探索、表单交互及信息提取任务。",
            inputSchema={
                "type": "object",
                "properties": {
                    "task": {
                        "type": "string",
                        "description": "自然语言任务描述（如：'打开 http://example.com，找到登录页面，并返回表单包含的所有字段'）"
                    },
                },
                "required": ["task"],
            },
        ),
    ]


# ====================  工具执行  ====================

@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    """接收 MCP Client 的工具调用请求并路由到对应实现。"""

    logger.info("[Tool] 收到调用: %s, 参数: %s", name, arguments)
    output = await _dispatch(name, arguments)
    return [TextContent(type="text", text=output)]


async def _dispatch(name: str, args: dict) -> str:
    """根据工具名分发到具体执行逻辑。"""
    match name:
        case "nmap_port_scan":
            return await _nmap(args)
        case "dirsearch_dir":
            return await _dirsearch(args)
        case "subfinder_domain":
            return await _subfinder(args)
        case "whatweb_fingerprint":
            return await _whatweb(args)
        case "httpx_alive_detect":
            return await _httpx(args)
        case "nuclei_vuln_scan":
            return await _nuclei(args)
        case "browser_use_explore":
            return await _browser_use(args)
        case _:
            return f"[错误] 未知工具: {name}"


# ---------- 各工具实现 ----------

async def _nmap(args: dict) -> str:
    target = args["target"]
    ports  = args.get("ports", "21,22,23,25,53,80,110,139,143,389,443,445,993,995,3306,3389,5432,6379,8080,8443,8888,27017")
    cmd = ["nmap", "-sV", "-Pn", "-T4", "-p", ports]
    # Nmap 对 HTTP 代理支持有限，通常支持 SOCKS4 Proxies，此处暂留扩展接口，若用 http 代理可用 proxychains
    if HTTP_PROXY and HTTP_PROXY.startswith("http"):
        # nmap 支持 --proxies
        cmd += ["--proxies", HTTP_PROXY]
    cmd.append(target)
    return await _run_cmd(cmd, timeout=300)


async def _dirsearch(args: dict) -> str:
    target     = args["target"]
    extensions = args.get("extensions", "php,html,bak,txt,js,json,xml")
    wordlist   = _wordlist(args.get("wordlist", "common"))
    threads    = str(args.get("threads", 20))
    max_time   = str(args.get("max_time", 540))

    cmd = [
        "dirsearch",
        "-q", "--no-color",
        f"--max-time={max_time}",
        "-u", target,
        "-e", extensions,
        "-t", threads,
    ]
    # 若字典文件存在则追加 -w 参数
    if Path(wordlist).exists():
        cmd += ["-w", wordlist]
        
    if HTTP_PROXY:
        cmd += ["--proxy", HTTP_PROXY]

    return await _run_cmd(cmd, timeout=int(max_time) + 30)


async def _subfinder(args: dict) -> str:
    domain = args["domain"]
    cmd = ["subfinder", "-d", domain, "-silent"]
    # subfinder 本身没有特定参数只支持 HTTP_PROXY 环境变量，可以通过 proxy 功能
    # 目前较新版本的 subfinder 暂未发现 -proxy 等显式参数。因此环境变量注入为首选。这里不再追加 cmd 显式参数。
    return await _run_cmd(cmd, timeout=180)


async def _whatweb(args: dict) -> str:
    target = args["target"]
    cmd = ["whatweb", "--color=never", "-a", "3"]
    if HTTP_PROXY:
        cmd += ["--proxy", HTTP_PROXY.replace("http://","").replace("https://","")]
    cmd.append(target)
    return await _run_cmd(cmd, timeout=60)


async def _browser_use(args: dict) -> str:
    task_desc = args.get("task")
    if not task_desc:
        return "[错误] 缺少必需参数 'task'"

    logger.info(">>> 执行 Browser-Use: %s", task_desc)
    
    # 获取环境变量，默认使用全局设置的智谱 API
    api_key = os.getenv("ZHIPU_API_KEY", "sk-7242956f72b94d8699ea45222758ffcf")
    base_url = os.getenv("ZHIPU_BASE_URL", "https://maas-api.ai-yuanjing.com/openapi/compatible-mode/v1")

    try:
        llm = ChatOpenAI(
            base_url=base_url,
            api_key=api_key,
            model="glm-4",  
            temperature=0.0
        )
        
        browser = Browser(headless=True)
        agent = Agent(
            task=task_desc,
            llm=llm,
            browser=browser
        )
        
        # 运行 Agent 解决任务，它会返回一个完整执行历史列表对象
        history = await agent.run()
        
        # 提取历史对象中的最后一项终极输出，无则采用纯文本化
        final_text = history.final_result() if hasattr(history, 'final_result') else str(history)
        if final_text is None:
            final_text = f"任务由于底层模型报错或执行错误而没有返回最终结果：{history.errors() if hasattr(history, 'errors') else '未知错误'}"
        logger.info("<<< Browser-Use 探索完成，输出 %d 字节", len(str(final_text)))
        return str(final_text)
    except Exception as e:
        logger.error("Browser-Use 执行异常: %s", e)
        return f"[错误] {e}"


async def _httpx(args: dict) -> str:
    target = args["target"]
    cmd = ["httpx", "-silent", "-status-code", "-title", "-tech-detect", "-u", target]
    if HTTP_PROXY:
        cmd += ["-http-proxy", HTTP_PROXY]
    return await _run_cmd(cmd, timeout=120)


async def _nuclei(args: dict) -> str:
    target   = args["target"]
    severity = args.get("severity", "critical,high")
    tags     = args.get("tags")

    cmd = ["nuclei", "-u", target, "-silent", "-no-color", "-severity", severity]
    if tags:
        cmd += ["-tags", tags]
    if HTTP_PROXY:
        cmd += ["-p", HTTP_PROXY]
        
    return await _run_cmd(cmd, timeout=600)


# ====================  启动入口  ====================

async def run_stdio():
    """以 stdio 模式启动（供本机 Java 进程直接调用）。"""
    logger.info("PentaMind MCP Server 启动 [stdio 模式]")
    async with mcp.server.stdio.stdio_server() as (read_stream, write_stream):
        await server.run(
            read_stream,
            write_stream,
            server.create_initialization_options(),
        )


async def run_sse(host: str = "0.0.0.0", port: int = 8765):
    """以 SSE 模式启动（供远程 Java 后端通过网络调用）。"""
    import uvicorn
    from starlette.applications import Starlette
    from starlette.routing import Route, Mount

    sse = SseServerTransport("/messages")

    async def handle_sse(scope, receive, send):
        async with sse.connect_sse(scope, receive, send) as streams:
            await server.run(
                streams[0], streams[1],
                server.create_initialization_options(),
            )

    starlette_app = Starlette(
        routes=[
            Route("/sse", endpoint=handle_sse),
            Mount("/messages", app=sse.handle_post_message),
        ]
    )

    logger.info("PentaMind MCP Server 启动 [SSE 模式] http://%s:%d/sse", host, port)
    config = uvicorn.Config(starlette_app, host=host, port=port, log_level=LOG_LEVEL.lower())
    await uvicorn.Server(config).serve()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="PentaMind MCP Tools Server")
    parser.add_argument("--sse", action="store_true", help="以 SSE 模式运行（远程节点）")
    parser.add_argument("--host", default=os.getenv("MCP_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MCP_PORT", 8765)))
    opt = parser.parse_args()

    if opt.sse:
        asyncio.run(run_sse(opt.host, opt.port))
    else:
        asyncio.run(run_stdio())
