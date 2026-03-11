package com.pentamind.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pentamind.common.R;
import com.pentamind.config.ZhipuAiProperties;
import com.pentamind.service.DasAiService;
import com.pentamind.service.PlaywrightAuthAgent;
import com.pentamind.service.ZhipuAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 分析控制器
 * 根据 application.yml 中 zhipu-ai.active-provider 配置自动切换 AI 提供商
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final DasAiService dasAiService;
    private final ZhipuAiService zhipuAiService;
    private final ZhipuAiProperties zhipuAiProperties;
    private final PlaywrightAuthAgent playwrightAgent; // 真实页面爬取
    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/predict")
    public R<Object> predictAttackSurface(@RequestBody Map<String, String> request) {
        String targetUrl = request.getOrDefault("url", "未知目标");
        String manualContext = request.getOrDefault("context", "");
        String provider = zhipuAiProperties.getActiveProvider();

        log.info("[Analysis] 当前 AI 提供商: {}, 目标: {}", provider, targetUrl);

        try {
            // ===== Step 1: Playwright 真实爬取页面，获取结构化特征 =====
            log.info("[Analysis] 启动 Playwright 爬虫爬取真实页面...");
            String pageFeatures = playwrightAgent.smartNavigateAndDetect(targetUrl);
            log.info("[Analysis] Playwright 爬取完成，特征长度={}", pageFeatures.length());

            // 拼接手动上下文（用户在下拉框选择的业务类型）
            String fullContext = pageFeatures;
            if (manualContext != null && !manualContext.isBlank()) {
                fullContext = "【用户补充的业务上下文】" + manualContext + "\n\n" + pageFeatures;
            }

            // ===== Step 2: 把真实页面特征交给 GLM-5 分析 =====
            if ("zhipu".equalsIgnoreCase(provider)) {
                String result = zhipuAiService.chat(buildSystemPrompt(), buildUserPrompt(targetUrl, fullContext));
                if (result == null || result.isBlank()) {
                    return R.error("智谱 AI 返回空结果");
                }
                String json = extractJson(result);
                log.debug("[Analysis] GLM-5 返回 JSON 长度={}", json.length());
                Object parsed = mapper.readTree(json);
                return R.success(parsed);
            } else {
                String jsonResult = dasAiService.analyzeAttackSurface(targetUrl, fullContext);
                Object resultNode = mapper.readTree(jsonResult);
                return R.success(resultNode);
            }
        } catch (Exception e) {
            log.error("[Analysis] AI 分析失败", e);
            return R.error("AI 分析失败: " + e.getMessage());
        }
    }

    /** 系统角色 Prompt：要求模型严格输出 JSON */
    private String buildSystemPrompt() {
        return """
                你是一位专业的 Web 安全渗透测试架构师，擅长对目标系统进行攻击面建模。
                你的任务是根据用户提供的目标 URL 和已知业务上下文，推断并绘制该目标的攻击面拓扑图。

                【输出格式要求 - 极其严格】
                你必须只输出一个合法的 JSON 对象，不能有任何 markdown 解释或额外文字。
                JSON 结构如下：
                {
                  "left": [树节点数组，代表防御/资产侧],
                  "right": [树节点数组，代表暴露面/攻击入口侧]
                }
                每个树节点格式为：
                { "name": "节点名称", "children": [子节点数组] }

                【left 分支】含 3~5 个节点，代表：技术栈、中间件、框架版本、服务器类型等
                【right 分支】含 3~6 个节点，代表：主要攻击入口，如登录页、文件上传、API接口、参数注入点等
                每个主节点下设 2~4 个子节点，表示具体的攻击技术或漏洞向量。

                再次强调：只输出 JSON，不要输出任何解释文字。
                """;
    }

    /** 用户消息 Prompt */
    private String buildUserPrompt(String targetUrl, String context) {
        String ctx = (context == null || context.isBlank()) ? "未知业务，盲打模式" : context;
        return String.format(
                "目标 URL: %s\n已知业务上下文: %s\n\n请为该目标生成攻击面预测拓扑 JSON。",
                targetUrl, ctx);
    }

    /** 从模型输出中提取 JSON，兼容模型返回 ```json ... ``` 的情况 */
    private String extractJson(String raw) {
        // 去掉 markdown 代码块
        String cleaned = raw.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        // 找到第一个 { 和最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    /** 快捷问答接口（直接对话，用于测试） */
    @PostMapping("/ask")
    public R<String> ask(@RequestBody Map<String, String> request) {
        String question = request.getOrDefault("question", "");
        String provider = zhipuAiProperties.getActiveProvider();
        String result = "zhipu".equalsIgnoreCase(provider)
                ? zhipuAiService.ask(question)
                : "暂不支持通过此接口调用恒脑 ask";
        return result != null ? R.success(result) : R.error("AI 无响应");
    }
}
