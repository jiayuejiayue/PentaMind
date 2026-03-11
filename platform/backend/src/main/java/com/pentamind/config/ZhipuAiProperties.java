package com.pentamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 AI (GLM) 大模型服务配置
 * 接口兼容 OpenAI Chat Completions 格式
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "zhipu-ai")
public class ZhipuAiProperties {

    /**
     * API Key (Bearer Token)
     */
    private String apiKey;

    /**
     * 接口 Base URL，默认 https://open.bigmodel.cn/api/paas/v4
     */
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";

    /**
     * 使用的模型，如 glm-4-flash / glm-4 / glm-4-plus
     */
    private String model = "glm-4-flash";

    /**
     * 当前平台激活的 AI 提供商标识: zhipu | das-ai
     */
    private String activeProvider = "zhipu";
}
