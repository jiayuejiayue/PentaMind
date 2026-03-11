package com.pentamind.service;

import com.pentamind.entity.ScanTask;
import com.pentamind.mapper.ScanTaskMapper;
import com.pentamind.service.mcp.McpClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;

/**
 * 智能渗透状态机与编排核心 (阶段二核心)
 * 负责任务的阶段流转、工具的 MCP 调度，以及通过 SSE 推送实时日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final ScanTaskMapper scanTaskMapper;
    private final McpClientService mcpClient;
    private final DasAiService dasAiService; // 主脑预留
    private final ZhipuAiService zhipuAiService; // 解读层预留

    // 存放每个 taskId 对应的 SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 独立线程池运行 Orchestrator 状态机
    private final ExecutorService agentPool = Executors.newCachedThreadPool();

    /**
     * 为客户端创建 SSE 长连接
     */
    public SseEmitter createSseEmitter(Long taskId) {
        // 设置永不超时
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(taskId);
        });
        emitter.onError(e -> {
            emitter.completeWithError(e);
            emitters.remove(taskId);
        });

        return emitter;
    }

    /**
     * 向前端发送实时日志与节点状态更新
     */
    public void pushEvent(Long taskId, String level, String nodeId, String status, String message) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                // 构建 JSON 数据：{ "level": "info", "nodeId": "nmap", "status": "running", "msg":
                // "..." }
                String jsonEvent = String.format(
                        "{\"level\":\"%s\",\"nodeId\":\"%s\",\"status\":\"%s\",\"msg\":\"%s\"}",
                        level, nodeId, status, message.replace("\"", "\\\"").replace("\n", " "));
                emitter.send(SseEmitter.event().data(jsonEvent));
            } catch (IOException e) {
                log.warn("[Agent] SSE 推送失败 taskId: {}, 原因: {}", taskId, e.getMessage());
                emitters.remove(taskId);
            }
        }

        // 同时也写一份本地日志
        if ("error".equals(level)) {
            log.error("[Task-{}] [{}] {}", taskId, nodeId, message);
        } else {
            log.info("[Task-{}] [{}] {}", taskId, nodeId, message);
        }
    }

    /**
     * 启动完整的智能连贯测试循环
     */
    public void startOrchestration(Long taskId, String targetUrl, List<String> phases, String username,
            String password) {
        agentPool.submit(() -> runStateMachine(taskId, targetUrl, phases, username, password));
    }

    /**
     * 状态机主循环
     */
    private void runStateMachine(Long taskId, String targetUrl, List<String> phases, String username, String password) {
        try {
            updateTaskStatus(taskId, "RUNNING", 5);
            pushEvent(taskId, "info", "start", "success", "初始化渗透任务: " + targetUrl);

            // 存放全链路信息的文本缓存，用于最终生成 AI 报告
            String nmapOutput = "";
            String subfinderOutput = "";
            String dirsearchOutput = "";
            String httpxOutput = "";
            String nucleiOutput = "";

            // ================= 阶段 1: 浏览器动态分析 =================
            if (phases.contains("browser")) {
                pushEvent(taskId, "info", "browser", "running", "启动 Puppeteer 浏览器节点进行动态智能探查...");
                try {
                    String navRes = mcpClient.callTool("puppeteer_navigate", Map.of("url", targetUrl));
                    log.debug("[Agent] puppeteer 导航响应: {}", navRes);

                    String shotRes = mcpClient.callTool("puppeteer_screenshot", Map.of("name", "screenshot_01"));
                    pushEvent(taskId, "info", "browser", "running",
                            "底层框架首屏快照捕获。长度: " + shotRes.length() + " 字。现启动 AI 模型 (Browser-Use) 接管浏览器进行功能与表单探测...");

                    String taskDesc = "目标站点是 " + targetUrl
                            + "。请仔细浏览该页面，查找存在的后台管理入口、登录表单或敏感功能按钮，并用简短的话总结出这些敏感路径。结束后立即返回。";
                    String aiExploreRes = mcpClient.callTool("browser_use_explore", Map.of("task", taskDesc));

                    pushEvent(taskId, "success", "browser", "success",
                            "智能体分析完成。AI深度探查结论: " + aiExploreRes);
                } catch (Exception e) {
                    pushEvent(taskId, "error", "browser", "error", "浏览器节点探测失败: " + e.getMessage());
                }
                updateTaskStatus(taskId, "RUNNING", 20);
            }

            // ================= 阶段 2: 资产与服务扫描 (并发通过 MCP) =================
            if (phases.contains("recon")) {
                pushEvent(taskId, "info", "nmap", "running", "启动 Nmap 端口探测，分配异步线程...");
                pushEvent(taskId, "info", "dirsearch", "running", "启动 Dirsearch 敏感目录爆破，分配异步线程...");
                pushEvent(taskId, "info", "subfinder", "running", "启动 Subfinder 子域名收集，分配异步线程...");

                // 借助 CompletableFuture 和自定义线程池 (agentPool) 实现 MCP 物理工具池并发调用
                CompletableFuture<String> nmapFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        String nmapRes = mcpClient.callTool("nmap_port_scan",
                                Map.of("target", targetUrl, "ports", "80,443,8080"));
                        pushEvent(taskId, "success", "nmap", "success", "Nmap 真机扫描完成！");
                        return nmapRes;
                    } catch (Exception e) {
                        pushEvent(taskId, "error", "nmap", "error", "Nmap 探测失败: " + e.getMessage());
                        return "【Nmap执行异常】";
                    }
                }, agentPool);

                CompletableFuture<String> subfinderFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        String domain = targetUrl.replace("http://", "").replace("https://", "");
                        if (domain.contains(":"))
                            domain = domain.substring(0, domain.indexOf(":"));
                        String subRes = mcpClient.callTool("subfinder_subdomain", Map.of("domain", domain));
                        pushEvent(taskId, "success", "subfinder", "success", "Subfinder 真机收集完成！");
                        return subRes;
                    } catch (Exception e) {
                        pushEvent(taskId, "error", "subfinder", "error", "Subfinder 探测失败: " + e.getMessage());
                        return "【Subfinder执行异常】";
                    }
                }, agentPool);

                CompletableFuture<String> dirsearchFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        String dirRes = mcpClient.callTool("dirsearch_dir",
                                Map.of("target", targetUrl, "config", "-e php,html,js -x 400,404,403,500"));
                        pushEvent(taskId, "warning", "dirsearch", "success", "Dirsearch 真机爆破完成！");
                        return dirRes;
                    } catch (Exception e) {
                        pushEvent(taskId, "error", "dirsearch", "error", "Dirsearch 探测失败: " + e.getMessage());
                        return "【Dirsearch执行异常】";
                    }
                }, agentPool);

                // 同步等待当前阶段所有的并发漏扫节点运行完毕
                CompletableFuture.allOf(nmapFuture, subfinderFuture, dirsearchFuture).join();

                try {
                    nmapOutput = nmapFuture.get();
                } catch (Exception ignore) {
                }
                try {
                    subfinderOutput = subfinderFuture.get();
                } catch (Exception ignore) {
                }
                try {
                    dirsearchOutput = dirsearchFuture.get();
                } catch (Exception ignore) {
                }

                updateTaskStatus(taskId, "RUNNING", 60);
            }

            // ================= 阶段 3: 敏感路径窗探 =================
            if (phases.contains("probe")) {
                pushEvent(taskId, "info", "probe", "running", "使用 httpx 进行 Web 服务存活性和指纹深层探测...");
                try {
                    httpxOutput = mcpClient.callTool("httpx_alive_detect", Map.of("target", targetUrl));
                    pushEvent(taskId, "success", "probe", "success",
                            "存活探测完成，已提取有效 Web 指纹，返回信息长度: " + httpxOutput.length() + " 字符");
                } catch (Exception e) {
                    pushEvent(taskId, "error", "probe", "error", "存活探测异常: " + e.getMessage());
                }
                updateTaskStatus(taskId, "RUNNING", 75);
            }

            // ================= 阶段 4: 针对性与功能渗透 =================
            if (phases.contains("vuln")) {
                pushEvent(taskId, "warning", "vuln", "running", "启动 Nuclei 定向漏洞扫描，加载 PoC 模板引擎...");
                try {
                    nucleiOutput = mcpClient.callTool("nuclei_vuln_scan",
                            Map.of("target", targetUrl, "severity", "critical,high,medium"));
                    pushEvent(taskId, "error", "vuln", "success",
                            "Nuclei 探测结束，返回风险点数据长度: " + nucleiOutput.length() + " 字符");
                } catch (Exception e) {
                    pushEvent(taskId, "error", "vuln", "error", "漏洞扫描异常: " + e.getMessage());
                }
                updateTaskStatus(taskId, "RUNNING", 90);
            }

            // ================= 阶段 5: AI 报告 =================
            pushEvent(taskId, "info", "report", "running", "正在呼叫主脑大模型 GLM 汇总全链路评估报告...");

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请你作为高级安全渗透测试专家，为目标 ").append(targetUrl).append(" 产出一份结构化的 Markdown 安全评估摘要报告。\n");
            promptBuilder.append("以下是各个引擎扫描回传的真实原始数据，请据此归纳：\n\n");

            if (!nmapOutput.isEmpty() && !nmapOutput.contains("【Nmap执行异常】"))
                promptBuilder.append("【Nmap 端口分析结果】\n").append(nmapOutput).append("\n\n");
            if (!dirsearchOutput.isEmpty() && !dirsearchOutput.contains("【Dirsearch执行异常】"))
                promptBuilder.append("【Dirsearch 目录/文件爆破结果】\n").append(dirsearchOutput).append("\n\n");
            if (!subfinderOutput.isEmpty() && !subfinderOutput.contains("【Subfinder执行异常】"))
                promptBuilder.append("【Subfinder 子域分析结果】\n").append(subfinderOutput).append("\n\n");
            if (!httpxOutput.isEmpty())
                promptBuilder.append("【Httpx 存活与指纹探测结果】\n").append(httpxOutput).append("\n\n");
            if (!nucleiOutput.isEmpty())
                promptBuilder.append("【Nuclei 真实漏洞扫描结果】\n").append(nucleiOutput).append("\n\n");

            promptBuilder.append(
                    "请输出排版精美的 Markdown 报告，包含【资产探测摘要】、【漏洞研判详情】和【次世代 AI 加固建议】。要求数据真实来源于上述扫描结果，若没有扫出漏洞，则不要凭空捏造。不要过多废话，直接输出 Markdown 文本。");

            String aiReport = zhipuAiService.ask(promptBuilder.toString());
            if (aiReport == null || aiReport.isBlank()) {
                aiReport = "# AI 报告生成失败\n抱歉，调用大模型时发生错误或返回为空，无法自动生成报告。";
            }

            ScanTask finalTask = scanTaskMapper.selectById(taskId);
            if (finalTask != null) {
                finalTask.setResult(aiReport);
                scanTaskMapper.updateById(finalTask);
            }

            pushEvent(taskId, "success", "report", "success", "评估报告生成完毕，请查阅！");

            updateTaskStatus(taskId, "COMPLETED", 100);

            // 发送终止封包，前端可据此关闭 SSE 连接
            pushEvent(taskId, "info", "CLOSE", "success", "EOT");

        } catch (Exception e) {
            log.error("渗透任务 {} 执行异常中断", taskId, e);
            pushEvent(taskId, "error", "report", "error", "任务状态机崩溃: " + e.getMessage());
            updateTaskStatus(taskId, "FAILED", 0);
        }
    }

    private void updateTaskStatus(Long taskId, String status, int progress) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setProgress(progress);
            scanTaskMapper.updateById(task);
        }
    }
}
