package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pentamind.common.R;
import com.pentamind.entity.Plugin;
import com.pentamind.entity.Target;
import com.pentamind.entity.ScanTask;
import com.pentamind.mapper.PluginMapper;
import com.pentamind.mapper.TargetMapper;
import com.pentamind.mapper.ScanTaskMapper;
import com.alibaba.fastjson2.JSON;
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

    @Autowired
    private TargetMapper targetMapper;

    @Autowired
    private ScanTaskMapper scanTaskMapper;

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

        // 解析并入库扫描结果
        if ("SCAN_RESULT".equals(dataType)) {
            String url = (String) report.getOrDefault("url", "unknown");
            int elementCount = (Integer) report.getOrDefault("elementCount", 0);
            int apiCount = (Integer) report.getOrDefault("apiCount", 0);
            int totalFinding = elementCount + apiCount;

            // 1. 自动写入/更新 Target
            Target target = targetMapper.selectOne(
                    new LambdaQueryWrapper<Target>().eq(Target::getUrl, url));

            if (target == null) {
                target = new Target();
                target.setUrl(url);
                try {
                    java.net.URL u = new java.net.URL(url);
                    target.setName(u.getHost());
                } catch (Exception e) {
                    target.setName(url);
                }
                target.setType("WEB");
                target.setStatus(2); // COMPLETED
                target.setRiskLevel("INFO");
                target.setVulnCount(0);
                targetMapper.insert(target);
            }

            // 2. 自动产生一条 ScanTask
            ScanTask task = new ScanTask();
            task.setTargetId(target.getId());
            task.setName(pluginName + " 页面功能点扫描");
            task.setScanType("FEATURE_SCAN");
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setFindingCount(totalFinding);
            task.setStartTime(LocalDateTime.now());
            task.setEndTime(LocalDateTime.now());

            // 将详细元素和API数据转为JSON存入结果
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("elements", report.get("elements"));
            resultData.put("apis", report.get("apis"));
            task.setResult(JSON.toJSONString(resultData));

            scanTaskMapper.insert(task);
        }
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
