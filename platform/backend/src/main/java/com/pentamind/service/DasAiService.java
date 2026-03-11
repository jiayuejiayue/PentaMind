package com.pentamind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pentamind.config.DasAiProperties;
import lombok.extern.slf4j.Slf4j;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 恒脑安全大模型 (Das AI) 服务调用类
 */
@Slf4j
@Service
public class DasAiService {

    @Autowired
    private DasAiProperties dasAiProperties;

    @Autowired
    private PlaywrightAuthAgent authAgent;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据目标 URL 和探测的指纹上下文，让大模型分析可行的攻击面
     */
    public String analyzeAttackSurface(String targetUrl, String userContext) {
        log.info("【恒脑AI大模型】开始真实侦察与攻击面推断请求. Target: {}", targetUrl);

        // 1. 真实探测：访问目标提取页面关键特征
        String detectedFeatures = detectTargetFeatures(targetUrl);

        // 2. 组装最终发给 LLM 的智能推断 Prompt
        String combinedContext = (userContext != null && !userContext.isEmpty() ? "用户提示:" + userContext + "; " : "")
                + "探测特征:" + detectedFeatures;

        String prompt = "你是一个专业的高级红队专家大脑。" +
                "目标地址：" + targetUrl + "。" +
                "结合以下真实探测的页面上下文进行分析：" + combinedContext + "。" +
                "请务必返回且只返回一个严格的 JSON 格式的推荐测试链路图：" +
                "{ \"left\": [ { \"name\": \"大类1\", \"children\": [{\"name\": \"具体测试点\"}] } ], " +
                "\"right\": [ { \"name\": \"大类2\", \"children\": [{\"name\": \"具体测试点\"}] } ] }";

        // 3. 构建恒脑签名及发起对应 Agent 的真实请求
        String resultJson = executeDasAiAgent(prompt);
        log.info("【恒脑AI大模型】实际返回清洗后结果: \n{}", resultJson);

        // 放宽检测标准：只要恒脑返回的是带有 JSON 括号且包含任何层级描述即可接受
        if (resultJson != null && resultJson.contains("{")
                && (resultJson.contains("left") || resultJson.contains("right") || resultJson.contains("name"))) {
            // 如果成功抓取到格式正确的 JSON，直接返回
            log.info("【恒脑AI大模型】格式检验通过，投送至前端.");
            return resultJson;
        } else {
            // 4. 作为强效的兜底保障机制：如果恒脑大模型返回由于各种原因(Token 或格式不对)未命中 JSON，则走内置分析
            log.warn("未能从大模型提取到完整的拓扑 JSON，正应用引擎兜底特征推测...");
            return simulateLlmInference(targetUrl, combinedContext);
        }
    }

    /**
     * 请求恒脑开放平台的 Agent Execute 接口
     */
    private String executeDasAiAgent(String prompt) {
        try {
            String appKey = dasAiProperties.getAppKey();
            String appSecret = dasAiProperties.getAppSecret();
            String agentId = dasAiProperties.getAgentId(); // 获取正确的智能体ID

            // 如果平台传入的是空则用内置的恒脑应用ID替代测试
            if (agentId == null || agentId.isEmpty()) {
                agentId = "86832399-331c-4b8e-8cc5-e1eaa5d4fbe9";
            }

            // 1. 签名算法 HmacSHA256
            long timestamp = System.currentTimeMillis();
            String data = String.format("%d\n%s\n%s", timestamp, appSecret, appKey);
            Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSHA256.init(secretKeySpec);
            byte[] signBytes = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String sign = timestamp + Base64.getEncoder().encodeToString(signBytes);

            // 2. 构造头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("appKey", appKey);
            headers.set("sign", sign);

            // 3. 构造 Body
            Map<String, Object> inputs = new HashMap<>();
            inputs.put("input", prompt); // 多种可能的参数名兜底
            inputs.put("query", prompt);
            inputs.put("prompt", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("sid", UUID.randomUUID().toString());
            body.put("id", agentId);
            body.put("inputs", inputs);
            body.put("stream", false);

            String url = dasAiProperties.getBaseUrl() + "/open/api/v2/agent/execute";

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            log.info("请求恒脑 URL: {}", url);
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.info("恒脑接口返回 200, 原始 Body: {}", responseBody);

                // 尝试解析并获取回复内容
                JsonNode rootNode = objectMapper.readTree(responseBody);
                if (rootNode.has("data") && rootNode.get("data").has("session")
                        && rootNode.get("data").get("session").has("messages")) {
                    ArrayNode messages = (ArrayNode) rootNode.get("data").get("session").get("messages");
                    if (messages.size() > 0) {
                        String botReply = messages.get(messages.size() - 1).get("content").asText();
                        log.info("收到恒脑模型成功应答.");
                        // 清洗提取 Json
                        return extractJsonFromText(botReply);
                    }
                }
            } else {
                log.error("恒脑接口返回非 200: {}", response.getBody());
            }
        } catch (Exception e) {
            log.error("调用恒脑大模型核心层发生异常: {}", e.getMessage());
        }
        return null;
    }

    private String extractJsonFromText(String text) {
        try {
            int startIndex = text.indexOf("{");
            int endIndex = text.lastIndexOf("}");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                return text.substring(startIndex, endIndex + 1);
            }
        } catch (Exception e) {
            log.warn("提取 JSON 失败", e);
        }
        return text;
    }

    /**
     * 真实访问目标，侦察敏感特征 (使用 Playwright 无头浏览器)
     */
    private String detectTargetFeatures(String url) {
        if (!url.startsWith("http"))
            return "未知格式目标";
        return authAgent.smartNavigateAndDetect(url);
    }

    private String simulateLlmInference(String url, String context) {
        boolean hasLogin = context.contains("认证") || context.contains("登录") || context.contains("密码");
        boolean hasUpload = context.contains("上传") || context.contains("upload");

        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");

        jsonBuilder.append("\"left\": [");
        jsonBuilder.append(
                "{ \"name\": \"前端信息泄露分析\", \"children\": [{\"name\": \"JS/SourceMap 还原\"}, {\"name\": \"硬编码 Token 提取\"}] },");
        jsonBuilder
                .append("{ \"name\": \"基础探针\", \"children\": [{\"name\": \"高危端口扫描\"}, {\"name\": \"指纹与 WAF 识别\"}] }");
        jsonBuilder.append("],");

        jsonBuilder.append("\"right\": [");
        if (hasLogin) {
            jsonBuilder.append(
                    "{ \"name\": \"认证与越权靶向\", \"children\": [{\"name\": \"万能密码 SQLi\"}, {\"name\": \"逻辑越权测试\"}, {\"name\": \"弱口令/验证码爆破\"}] }");
            if (hasUpload) {
                jsonBuilder.append(",");
            }
        } else {
            jsonBuilder.append(
                    "{ \"name\": \"业务逻辑黑盒探索\", \"children\": [{\"name\": \"未授权 API 探测\"}, {\"name\": \"敏感信息遍历\"}] }");
            if (hasUpload) {
                jsonBuilder.append(",");
            }
        }

        if (hasUpload) {
            jsonBuilder.append(
                    "{ \"name\": \"文件上传突破\", \"children\": [{\"name\": \"免杀 Webshell 绕过上传\"}, {\"name\": \"解析漏洞测试\"}] }");
        }
        if (!hasUpload && !hasLogin) {
            jsonBuilder.append(",");
            jsonBuilder.append("{ \"name\": \"通用 Web 框架漏洞\", \"children\": [{\"name\": \"组件反序列化盲打\"}] }");
        }

        jsonBuilder.append("]");
        jsonBuilder.append("}");

        return jsonBuilder.toString();
    }
}
