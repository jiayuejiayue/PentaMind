package com.pentamind.controller;

import com.pentamind.common.R;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 插件数据回连 Controller
 * 接收来自 Extension、PathFinder 等插件上报的数据
 */
@RestController
@RequestMapping("/api/plugin")
public class PluginController {

    /**
     * 接收插件上报的扫描结果
     * 插件独立运行时，将数据回传平台进行统一管理
     */
    @PostMapping("/report")
    public R<Void> receiveReport(@RequestBody Map<String, Object> report) {
        String pluginName = (String) report.getOrDefault("plugin", "unknown");
        String dataType = (String) report.getOrDefault("type", "unknown");
        // TODO: 根据 pluginName 和 dataType 分发到对应的 Service 处理
        System.out.printf("[PluginReport] plugin=%s, type=%s%n", pluginName, dataType);
        return R.ok();
    }

    /**
     * 获取插件配置
     * 插件从平台拉取最新配置（如扫描策略、字典等）
     */
    @GetMapping("/config/{pluginName}")
    public R<Map<String, Object>> getPluginConfig(@PathVariable String pluginName) {
        Map<String, Object> config = new HashMap<>();
        config.put("pluginName", pluginName);
        config.put("version", "1.0.0");
        config.put("enabled", true);
        // TODO: 从数据库读取插件配置
        return R.ok(config);
    }

    /** 插件心跳 */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat(@RequestBody Map<String, Object> data) {
        return R.ok();
    }
}
