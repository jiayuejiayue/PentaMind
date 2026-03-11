package com.pentamind.service.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * MCP 多路复用客户端服务 (Stdio 通信模式)
 * 能够同时拉起并路由管理多个异构的 MCP Server (如 Python 安全节点、Node.js 浏览器节点等)
 */
@Slf4j
@Service
public class McpClientService {

    @Value("${pentamind.mcp.python-path:python}")
    private String pythonPath;

    @Value("${pentamind.mcp.server-script:../mcp-server/main.py}")
    private String serverScript;

    private final ObjectMapper mapper = new ObjectMapper();

    // 存储所有的 MCP 连接节点，按命名空间隔离存储
    private final Map<String, McpConnection> connections = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("[MCP] ==============================================");
        log.info("[MCP] 初始化多节点 MCP 客户端资源池...");

        // 1. 初始化 Python 安全工具主节点
        try {
            List<String> cmd = Arrays.asList(pythonPath, serverScript);
            McpConnection secConn = new McpConnection("security", cmd);
            secConn.start();
            connections.put("security", secConn);
        } catch (Exception e) {
            log.error("[MCP] Security Python 主节点启动失败", e);
        }

        // 2. 初始化 Node.js Puppeteer 官方浏览器节点
        // 自动拉起官方的 server-puppeteer
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            // 使用相对于 backend 运行目录的 node_modules 局部安装路径
            List<String> cmd = isWindows
                    ? Arrays.asList("..\\mcp-server\\node_modules\\.bin\\mcp-server-puppeteer.cmd")
                    : Arrays.asList("../mcp-server/node_modules/.bin/mcp-server-puppeteer");

            McpConnection browserConn = new McpConnection("puppeteer", cmd);
            browserConn.start();
            connections.put("puppeteer", browserConn);
        } catch (Exception e) {
            log.error("[MCP] Puppeteer Node.js 节点启动失败, 请确保系统已安装 Node.js(npx)", e);
        }

        log.info("[MCP] ==============================================");
    }

    /**
     * 根据工具名路由到不同的物理执行进程中
     */
    private McpConnection routeConnection(String toolName) {
        if (toolName.startsWith("puppeteer_")) {
            return connections.get("puppeteer");
        }
        // 剩下的工具如 nmap, nuclei 等全部交给 python 端处理
        return connections.get("security");
    }

    /**
     * 调用系统内挂载的任一 MCP 工具
     * 
     * @param toolName  工具名 (如: nmap_port_scan, puppeteer_navigate)
     * @param arguments 参数字典 {target: 'x', ...}
     * @return 执行后的返回字符串
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpConnection conn = routeConnection(toolName);
        if (conn == null) {
            throw new RuntimeException("未能找到负责此工具的 MCP 活跃连接池: " + toolName);
        }
        return conn.callTool(toolName, arguments);
    }

    @PreDestroy
    public void destroy() {
        log.info("[MCP] 系统关闭，正在摧毁所有的底层通信进程...");
        for (McpConnection conn : connections.values()) {
            conn.close();
        }
    }

    /**
     * 内部类：独立封装管理单个系统进程中的 jsonrpc 生命周期与 Stdio IO流
     */
    private class McpConnection {
        private final String name;
        private final List<String> command;
        private Process process;
        private BufferedWriter writer;
        private BufferedReader reader;
        private Thread listenerThread;
        private final AtomicInteger messageIdGenerator = new AtomicInteger(1);
        private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

        public McpConnection(String name, List<String> command) {
            this.name = name;
            this.command = command;
        }

        public void start() throws Exception {
            log.info("[MCP-{}] 正在唤起进程: {}", name, String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            this.process = pb.start();
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            listenerThread = new Thread(this::listenForResponses, "MCP-Listener-" + name);
            listenerThread.setDaemon(true);
            listenerThread.start();

            initializeMcpProtocol();
        }

        private void initializeMcpProtocol() throws Exception {
            ObjectNode initReq = mapper.createObjectNode();
            initReq.put("jsonrpc", "2.0");
            int id = messageIdGenerator.getAndIncrement();
            initReq.put("id", id);
            initReq.put("method", "initialize");

            ObjectNode params = initReq.putObject("params");
            params.put("protocolVersion", "2024-11-05");
            params.putObject("capabilities");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "PentaMind-" + name);
            clientInfo.put("version", "1.0");

            CompletableFuture<JsonNode> future = new CompletableFuture<>();
            pendingRequests.put(id, future);
            send(initReq);

            future.get(30, TimeUnit.SECONDS); // 允许较长的 npx 启动握手时间
            log.info("[MCP-{}] 初始化响应成功", name);

            ObjectNode initializedNotification = mapper.createObjectNode();
            initializedNotification.put("jsonrpc", "2.0");
            initializedNotification.put("method", "notifications/initialized");
            send(initializedNotification);
            log.info("[MCP-{}] 协议层已就绪!", name);
        }

        private void listenForResponses() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank())
                        continue;
                    try {
                        JsonNode node = mapper.readTree(line);
                        if (node.has("id")) {
                            int id = node.get("id").asInt();
                            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                            if (future != null) {
                                if (node.has("error")) {
                                    future.completeExceptionally(new RuntimeException(node.get("error").toString()));
                                } else {
                                    future.complete(node.get("result"));
                                }
                            }
                        } else if (node.has("method")) {
                            log.debug("[MCP-{}] 通知包: {}", name, line);
                        }
                    } catch (Exception e) {
                        log.warn("[MCP-{}] 无法解析下行数据: {}", name, line, e);
                    }
                }
            } catch (Exception e) {
                log.warn("[MCP-{}] IO阻塞监听线程退出: {}", name, e.getMessage());
            }
        }

        private synchronized void send(JsonNode node) throws Exception {
            String msg = mapper.writeValueAsString(node);
            log.debug("[MCP-{}] 发送上游请求: {}", name, msg);
            writer.write(msg);
            writer.write("\n");
            writer.flush();
        }

        public String callTool(String toolName, Map<String, Object> arguments) {
            log.info("[MCP-{}] 转发工具执行指令 {} ...", name, toolName);
            try {
                ObjectNode req = mapper.createObjectNode();
                req.put("jsonrpc", "2.0");
                int id = messageIdGenerator.getAndIncrement();
                req.put("id", id);
                req.put("method", "tools/call");

                ObjectNode params = req.putObject("params");
                params.put("name", toolName);
                ObjectNode argsNode = params.putObject("arguments");
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    argsNode.putPOJO(entry.getKey(), entry.getValue());
                }

                CompletableFuture<JsonNode> future = new CompletableFuture<>();
                pendingRequests.put(id, future);

                long start = System.currentTimeMillis();
                send(req);

                // 根据业务设定，某些工具等待可以久一点
                JsonNode resultNode = future.get(15, TimeUnit.MINUTES);
                long cost = System.currentTimeMillis() - start;

                if (resultNode.has("content") && resultNode.get("content").isArray()) {
                    ArrayNode contentArray = (ArrayNode) resultNode.get("content");
                    if (contentArray.size() > 0 && contentArray.get(0).has("text")) {
                        String text = contentArray.get(0).get("text").asText();
                        log.info("[MCP-{}] >> 工具返回 (耗时 {} ms), text len={}", name, cost, text.length());
                        return text;
                    }
                }
                if (resultNode.has("isError") && resultNode.get("isError").asBoolean()) {
                    log.warn("[MCP-{}] ! 工具声明内部错误: {}", name, resultNode);
                }

                return resultNode.toString();

            } catch (Exception e) {
                log.error("[MCP-{}] 执行中断: {}", name, toolName, e);
                throw new RuntimeException("MCP Execution failed for " + toolName + " : " + e.getMessage());
            }
        }

        public void close() {
            if (process != null)
                process.destroy();
            if (listenerThread != null)
                listenerThread.interrupt();
        }
    }
}
