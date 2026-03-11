package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.ScanTask;
import com.pentamind.service.ScanTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.pentamind.service.TargetService;

/**
 * 后端调度与黑盒测试接口 (长耗时任务)
 */
@RestController
@RequestMapping("/api/task")
public class ScanTaskController {

    @Autowired
    private ScanTaskService scanTaskService;

    @Autowired
    private TargetService targetService;

    /**
     * 发起一个后端异步扫描任务
     * 前端发送 toolName（新）或 scanType（旧），自动兼容
     */
    @PostMapping("/start")
    public R<String> startTask(@RequestBody Map<String, Object> params) {
        Long targetId = Long.valueOf(params.getOrDefault("targetId", 0).toString());

        // 兼容前端 toolName 和旧版 scanType
        String toolName = params.containsKey("toolName")
                ? (String) params.get("toolName")
                : (String) params.get("scanType");

        // targetHost 支持直接传入，或者从 targetId 自动查
        String targetHost = (String) params.getOrDefault("targetHost", "");

        if (targetId == null || targetId == 0 || toolName == null) {
            return R.error("参数不完整：必须包含 targetId 和 toolName/scanType");
        }

        // 如果没传 targetHost，尝试从数据库目标表查询
        if (targetHost == null || targetHost.isBlank()) {
            com.pentamind.entity.Target t = targetService.getById(targetId);
            if (t != null) {
                targetHost = t.getUrl();
            }
        }

        if (targetHost == null || targetHost.isBlank()) {
            return R.error("无法获取目标地址，请确认目标已配置 URL 或 IP");
        }

        scanTaskService.submitScanTask(targetId, toolName, targetHost);
        return R.success("任务已下发：" + toolName + " → " + targetHost);
    }

    /**
     * 查询任务的扫描结果（供前端抽屉展示）
     * 将 ScanTask.result 字段解析为结构化列表返回
     */
    @GetMapping("/{id}/result")
    public R<java.util.Map<String, Object>> getResult(@PathVariable Long id) {
        ScanTask task = scanTaskService.getById(id);
        if (task == null)
            return R.error("任务不存在");

        String rawOutput = task.getResult() != null ? task.getResult() : "";
        // 按行解析原始输出，生成简单的 key/value 列表
        java.util.List<java.util.Map<String, String>> items = new java.util.ArrayList<>();
        for (String line : rawOutput.split("\n")) {
            line = line.trim();
            if (line.isEmpty())
                continue;
            java.util.Map<String, String> item = new java.util.LinkedHashMap<>();
            item.put("key", line);
            item.put("value", "");
            // 简单标注风险关键词
            if (line.contains("200") || line.contains("admin") || line.contains("upload")
                    || line.contains("backup") || line.contains(".bak") || line.contains(".env")) {
                item.put("risk", "HIGH");
            } else {
                item.put("risk", "");
            }
            items.add(item);
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("taskId", id);
        result.put("status", task.getStatus());
        result.put("summary", task.getResultSummary());
        result.put("items", items);
        result.put("rawOutput", rawOutput);
        return R.success(result);
    }

    /**
     * 查询当前租户的所有扫描任务
     */
    @GetMapping("/list")
    public R<Page<ScanTask>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String status) {

        Page<ScanTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ScanTask> wrapper = new LambdaQueryWrapper<>();
        if (targetId != null)
            wrapper.eq(ScanTask::getTargetId, targetId);
        if (status != null && !status.isEmpty())
            wrapper.eq(ScanTask::getStatus, status);
        wrapper.orderByDesc(ScanTask::getCreateTime);
        return R.success(scanTaskService.page(pageParam, wrapper));
    }

    /**
     * 删除任务记录
     */
    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable Long id) {
        ScanTask task = scanTaskService.getById(id);
        if (task == null)
            return R.error("任务不存在");
        if ("RUNNING".equals(task.getStatus()))
            return R.error("运行中的任务无法删除");
        scanTaskService.removeById(id);
        return R.success("删除成功");
    }
}
