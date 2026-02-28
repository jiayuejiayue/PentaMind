package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pentamind.common.R;
import com.pentamind.entity.Plugin;
import com.pentamind.mapper.PluginMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 插件数据回连 Controller
 * 接收来自 Extension、PathFinder 等插件上报的数据
 * 管理插件心跳和在线状态
 */
@RestController
@RequestMapping("/api/plugin")
public class PluginController {

    @Autowired
    private PluginMapper pluginMapper;

    /** 获取所有插件状态 */
    @GetMapping("/list")
    public R<List<Plugin>> list() {
        List<Plugin> plugins = pluginMapper.selectList(
                new LambdaQueryWrapper<Plugin>().orderByDesc(Plugin::getLastHeartbeat));

        // 自动判断在线状态：最后心跳超过60秒视为离线
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(60);
        for (Plugin p : plugins) {
            if (p.getLastHeartbeat() == null || p.getLastHeartbeat().isBefore(threshold)) {
                p.setStatus("OFFLINE");
            } else {
                p.setStatus("ONLINE");
            }
        }
        return R.ok(plugins);
    }

    /**
     * 插件心跳 - 更新插件在线状态
     * 插件定期调用此接口保持在线
     */
    @PostMapping("/heartbeat")
    public R<Void> heartbeat(@RequestBody Map<String, Object> data) {
        String pluginName = (String) data.getOrDefault("plugin", "unknown");
        String version = (String) data.getOrDefault("version", "1.0.0");
        String type = (String) data.getOrDefault("type", "UNKNOWN");

        // 查找已有插件记录
        Plugin existing = pluginMapper.selectOne(
                new LambdaQueryWrapper<Plugin>().eq(Plugin::getName, pluginName));

        if (existing != null) {
            existing.setLastHeartbeat(LocalDateTime.now());
            existing.setStatus("ONLINE");
            existing.setVersion(version);
            pluginMapper.updateById(existing);
        } else {
            // 首次注册
            Plugin plugin = new Plugin();
            plugin.setName(pluginName);
            plugin.setVersion(version);
            plugin.setType(type);
            plugin.setStatus("ONLINE");
            plugin.setLastHeartbeat(LocalDateTime.now());
            pluginMapper.insert(plugin);
        }
        return R.ok();
    }

    /**
     * 接收插件上报的扫描结果
     */
    @PostMapping("/report")
    public R<Void> receiveReport(@RequestBody Map<String, Object> report) {
        String pluginName = (String) report.getOrDefault("plugin", "unknown");
        String dataType = (String) report.getOrDefault("type", "unknown");
        System.out.printf("[PluginReport] plugin=%s, type=%s, data_size=%d%n",
                pluginName, dataType, report.size());
        // TODO: 根据 pluginName 和 dataType 分发到对应的 Service 处理
        return R.ok();
    }

    /** 获取插件配置 */
    @GetMapping("/config/{pluginName}")
    public R<Map<String, Object>> getPluginConfig(@PathVariable String pluginName) {
        Plugin plugin = pluginMapper.selectOne(
                new LambdaQueryWrapper<Plugin>().eq(Plugin::getName, pluginName));

        Map<String, Object> config = new HashMap<>();
        config.put("pluginName", pluginName);
        if (plugin != null) {
            config.put("version", plugin.getVersion());
            config.put("enabled", true);
            config.put("config", plugin.getConfig());
        } else {
            config.put("version", "unknown");
            config.put("enabled", false);
        }
        return R.ok(config);
    }
}
