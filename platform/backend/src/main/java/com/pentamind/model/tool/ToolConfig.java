package com.pentamind.model.tool;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 安全测试工具的 YAML 描述模型
 */
@Data
public class ToolConfig {
    private String name;
    private String description;
    private String author;
    private String version;

    // 可执行的命令，如 "nmap" 或 "python"
    private String command;

    // 内置的固定参数，如 "-sS", "-sV"
    private List<String> args;

    // 动态参数（供调用者或 LLM 传参时参照）
    private Map<String, ToolParam> parameters;

    // 输出后处理期望
    private ToolOutput output;

    @Data
    public static class ToolParam {
        private String type; // string, int, boolean
        private String description;
        private boolean required;
        private String prefix; // 如指定要带 "-p ", 可为空
        private String position; // "append" 默认追加到最后
    }

    @Data
    public static class ToolOutput {
        private String format; // text, json, xml
        private String guidance; // LLM 研判该输出时需要的 Prompt 引导语
    }
}
