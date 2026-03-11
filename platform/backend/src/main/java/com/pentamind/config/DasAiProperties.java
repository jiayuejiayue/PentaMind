package com.pentamind.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "das-ai")
public class DasAiProperties {

    /**
     * 服务基础 URL
     */
    private String baseUrl;

    /**
     * 聊天对话接口 URL
     */
    private String chatUrl;

    /**
     * 应用 Key
     */
    private String appKey;

    /**
     * 应用的 Secret
     */
    private String appSecret;

    /**
     * 用户的 userAppKey
     */
    private String userAppKey;

    /**
     * 恒脑平台智能体的唯一 ID
     */
    private String agentId;
}
