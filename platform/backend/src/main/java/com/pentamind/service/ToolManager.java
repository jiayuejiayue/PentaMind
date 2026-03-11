package com.pentamind.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.pentamind.model.tool.ToolConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 自动化测试工具的 YAML 编排管理器
 */
@Slf4j
@Component
public class ToolManager {

    private final Map<String, ToolConfig> toolRegistry = new ConcurrentHashMap<>();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // 从 application.yml 注入绝对路径，彻底解决相对路径问题
    @Value("${pentamind.tools-dir:tools/}")
    private String TOOLS_DIR;

    @Autowired
    private com.pentamind.service.mcp.McpClientService mcpClient;

    @PostConstruct
    public void init() {
        loadTools();
    }

    /**
     * 从目录扫描并热加载 YAML 配置
     */
    public void loadTools() {
        toolRegistry.clear();
        log.info("开始加载自动化安全工具 YAML 模板...");
        try {
            // 获取项目根目录或当前运行目录下的 tools
            Path toolsPath = Paths.get(TOOLS_DIR);
            if (!Files.exists(toolsPath)) {
                log.warn("工具目录 {} 不存在，尝试创建。如果部署在 Jar 外请指定绝对路径。", toolsPath.toAbsolutePath());
                Files.createDirectories(toolsPath);
                return;
            }

            try (Stream<Path> paths = Files.list(toolsPath)) {
                paths.filter(path -> path.toString().endsWith(".yaml") || path.toString().endsWith(".yml"))
                        .forEach(this::registerTool);
            }
            log.info("成功加载 {} 个工具描述.", toolRegistry.size());
        } catch (Exception e) {
            log.error("加载 YAML 工具描述失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void registerTool(Path yamlPath) {
        try {
            // 先尝试解析为 List 格式（多工具打包在一个文件中）
            Object raw = yamlMapper.readValue(yamlPath.toFile(), Object.class);
            if (raw instanceof java.util.List) {
                List<ToolConfig> configs = yamlMapper.convertValue(raw,
                        yamlMapper.getTypeFactory().constructCollectionType(List.class, ToolConfig.class));
                for (ToolConfig config : configs) {
                    registerSingleTool(config, yamlPath);
                }
            } else {
                // 单个工具对象格式（兼容旧格式）
                ToolConfig config = yamlMapper.convertValue(raw, ToolConfig.class);
                registerSingleTool(config, yamlPath);
            }
        } catch (Exception e) {
            log.error("解析工具 YAML 文件失败: {}", yamlPath.getFileName(), e);
        }
    }

    private void registerSingleTool(ToolConfig config, Path yamlPath) {
        if (config != null && config.getName() != null && !config.getName().isEmpty()) {
            toolRegistry.put(config.getName(), config);
            log.info("- 注册工具: {} [{}] v{}", config.getName(), config.getDescription(), config.getVersion());
        }
    }

    /**
     * 获取工具的基础描述大纲，提供给 LLM 进行 Function Calling
     */
    public List<ToolConfig> getAvailableToolsForLlm() {
        return new ArrayList<>(toolRegistry.values());
    }

    /**
     * 根据提取到的工具名及外部传入的动态参数（由前台或 LLM 供给）执行命令
     *
     * @param toolName    YAML 中的 name
     * @param dynamicArgs 动态实参字典 (例如 key="target", value="127.0.0.1")
     * @return 命令行执行的 StdOut/StdErr 输出结果
     */
    public String executeTool(String toolName, Map<String, String> dynamicArgs) throws Exception {
        ToolConfig config = toolRegistry.get(toolName);
        if (config == null) {
            throw new IllegalArgumentException("未找到名为 " + toolName + " 的工具配置，请检查 tools 目录。");
        }

        // 把 dynamicArgs 转换成 Map<String, Object> 供 MCP 协议调用使用
        Map<String, Object> mcpArgs = new java.util.HashMap<>(dynamicArgs);

        log.info(">>> [ToolManager MCP 调度] 委托 MCP 节点执行工具 [{}], 参数: {}", toolName, mcpArgs);
        // 通过 MCP Client 异步通讯调用 Python 端服务
        return mcpClient.callTool(toolName, mcpArgs);
    }

    /**
     * 智能拼装：基础指令 + 固化 args + 根据 parameters 定义注入的实参
     */
    private List<String> buildCommand(ToolConfig config, Map<String, String> dynamicArgs) {
        List<String> cmd = new ArrayList<>();
        // 1. 基座命令 (如 python, nmap)
        cmd.add(config.getCommand());

        // 2. 默认固定参数
        if (config.getArgs() != null) {
            cmd.addAll(config.getArgs());
        }

        // 3. 提取 YAML 参数定义
        Map<String, ToolConfig.ToolParam> paramDefs = config.getParameters();
        if (paramDefs != null && dynamicArgs != null) {
            for (Map.Entry<String, ToolConfig.ToolParam> entry : paramDefs.entrySet()) {
                String paramName = entry.getKey();
                ToolConfig.ToolParam def = entry.getValue();

                // 看外界有没有传这个参数
                if (dynamicArgs.containsKey(paramName)) {
                    String passedValue = dynamicArgs.get(paramName);

                    // 看是否需要加上前缀 (-p 80) 还是直接追加 (80)
                    String prefix = def.getPrefix();
                    if (prefix != null && !prefix.trim().isEmpty()) {
                        // 取出如 "-p " 中的 "-p" 并作为独立参数入列，以防带有空格时进程解析失败
                        cmd.add(prefix.trim());
                        cmd.add(passedValue);
                    } else {
                        cmd.add(passedValue);
                    }
                } else {
                    if (def.isRequired()) {
                        throw new IllegalArgumentException("工具缺少必填的动态参数: " + paramName);
                    }
                }
            }
        }

        return cmd;
    }
}
