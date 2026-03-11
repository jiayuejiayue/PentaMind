package com.pentamind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pentamind.config.ZhipuAiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 智谱 AI (GLM) 服务
 * 接口格式兼容 OpenAI Chat Completions API
 * 文档: https://open.bigmodel.cn/dev/api
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZhipuAiService {

    private final ZhipuAiProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送对话请求，返回模型回复文本
     *
     * @param systemPrompt 系统角色 Prompt
     * @param userMessage  用户消息内容
     * @return 模型回复的文本，失败时返回 null
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            String url = props.getBaseUrl() + "/chat/completions";

            // 构建消息列表
            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userMessage));

            // 构建请求体
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.7);
            body.put("max_tokens", 4096);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

            log.debug("[ZhipuAI] 发送请求 model={}, prompt长度={}", props.getModel(), userMessage.length());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").path(0).path("message").path("content").asText(null);
                log.debug("[ZhipuAI] 响应成功, 内容长度={}", content != null ? content.length() : 0);
                return content;
            } else {
                log.warn("[ZhipuAI] 响应异常: status={}, body={}", response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("[ZhipuAI] 请求失败", e);
            return null;
        }
    }

    /**
     * 快速单轮对话（无系统 Prompt）
     */
    public String ask(String question) {
        return chat(null, question);
    }

    /**
     * 渗透测试专项分析（预置安全专家 Prompt）
     *
     * @param analysisContent 待分析的页面内容/扫描结果
     * @return LLM 研判结论
     */
    public String analyzeSecurityContent(String analysisContent) {
        String systemPrompt = """
                你是一位经验丰富的 Web 安全渗透测试专家。
                请根据我提供的内容（可能是页面源码、扫描结果、HTTP 响应包等），
                分析可能存在的安全漏洞，指出漏洞类型、风险等级（critical/high/medium/low）、
                以及可能的验证方法或 PoC 思路。
                回答请使用简洁的结构化格式，不要过多赘述。
                """;
        return chat(systemPrompt, analysisContent);
    }
}
