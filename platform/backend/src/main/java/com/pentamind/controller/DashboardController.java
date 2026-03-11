package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pentamind.common.R;
import com.pentamind.entity.ScanTask;
import com.pentamind.entity.Target;
import com.pentamind.mapper.ScanTaskMapper;
import com.pentamind.mapper.TargetMapper;
import com.pentamind.service.ToolManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 仪表盘数据 Controller - 从数据库查询真实统计数据
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

        @Autowired
        private TargetMapper targetMapper;

        @Autowired
        private ScanTaskMapper scanTaskMapper;

        @Autowired
        private ToolManager toolManager;

        /** 概览统计 - 从数据库查询真实数据 */
        @GetMapping("/overview")
        public R<Map<String, Object>> overview() {
                Map<String, Object> data = new HashMap<>();

                // 目标统计
                long targetCount = targetMapper.selectCount(null);
                data.put("targetCount", targetCount);

                // 漏洞统计（按风险等级）
                long criticalVulns = targetMapper.selectCount(
                                new LambdaQueryWrapper<Target>().eq(Target::getRiskLevel, "CRITICAL"));
                long highVulns = targetMapper.selectCount(
                                new LambdaQueryWrapper<Target>().eq(Target::getRiskLevel, "HIGH"));
                long mediumVulns = targetMapper.selectCount(
                                new LambdaQueryWrapper<Target>().eq(Target::getRiskLevel, "MEDIUM"));
                long lowVulns = targetMapper.selectCount(
                                new LambdaQueryWrapper<Target>().eq(Target::getRiskLevel, "LOW"));
                // 汇总所有目标的漏洞数
                List<Target> allTargets = targetMapper.selectList(null);
                int totalVulns = allTargets.stream().mapToInt(t -> t.getVulnCount() != null ? t.getVulnCount() : 0)
                                .sum();
                data.put("vulnCount", totalVulns);
                data.put("criticalVulns", criticalVulns);
                data.put("highVulns", highVulns);
                data.put("mediumVulns", mediumVulns);
                data.put("lowVulns", lowVulns);

                // 扫描任务统计
                long taskCount = scanTaskMapper.selectCount(null);
                long runningTasks = scanTaskMapper.selectCount(
                                new LambdaQueryWrapper<ScanTask>().eq(ScanTask::getStatus, "RUNNING"));
                data.put("taskCount", taskCount);
                data.put("runningTasks", runningTasks);

                // 报告数量（已完成的任务视为有报告）
                long reportCount = scanTaskMapper.selectCount(
                                new LambdaQueryWrapper<ScanTask>().eq(ScanTask::getStatus, "COMPLETED"));
                data.put("reportCount", reportCount);

                return R.ok(data);
        }

        /** 近期扫描活动 - 最新10条任务 */
        @GetMapping("/recent-scans")
        public R<List<Map<String, Object>>> recentScans() {
                List<ScanTask> tasks = scanTaskMapper.selectList(
                                new LambdaQueryWrapper<ScanTask>()
                                                .orderByDesc(ScanTask::getCreateTime)
                                                .last("LIMIT 10"));

                List<Map<String, Object>> result = new ArrayList<>();
                for (ScanTask task : tasks) {
                        Map<String, Object> item = new HashMap<>();
                        // 获取关联目标
                        Target target = task.getTargetId() != null ? targetMapper.selectById(task.getTargetId()) : null;
                        item.put("target", target != null ? target.getUrl() : "未知目标");
                        item.put("type", task.getScanType());
                        item.put("status", task.getStatus());
                        item.put("vulns", task.getFindingCount() != null ? task.getFindingCount() : 0);
                        item.put("time", task.getCreateTime());
                        result.add(item);
                }
                return R.ok(result);
        }

        /** 获取系统内所有的安全工具列表 */
        @GetMapping("/tools")
        public R<List<Map<String, Object>>> getTools() {
                List<com.pentamind.model.tool.ToolConfig> configs = toolManager.getAvailableToolsForLlm();
                List<Map<String, Object>> result = new ArrayList<>();
                for (com.pentamind.model.tool.ToolConfig c : configs) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", c.getName());
                        map.put("version", c.getVersion() != null ? c.getVersion() : "1.0");
                        map.put("description", c.getDescription());
                        map.put("command", c.getCommand());
                        result.add(map);
                }
                return R.ok(result);
        }

        /** 系统健康检查 */
        @GetMapping("/health")
        public R<Map<String, Object>> health() {
                Map<String, Object> data = new HashMap<>();
                data.put("status", "UP");
                data.put("version", "1.0.0");
                data.put("platform", "PentaMind");
                data.put("targets", targetMapper.selectCount(null));
                data.put("tasks", scanTaskMapper.selectCount(null));
                return R.ok(data);
        }
}
