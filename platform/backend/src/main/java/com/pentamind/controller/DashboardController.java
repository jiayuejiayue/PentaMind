package com.pentamind.controller;

import com.pentamind.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 仪表盘数据 Controller - 提供概览统计数据
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    /** 获取概览统计 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        data.put("targetCount", 0);
        data.put("vulnCount", 0);
        data.put("taskCount", 0);
        data.put("reportCount", 0);
        data.put("criticalVulns", 0);
        data.put("highVulns", 0);
        data.put("mediumVulns", 0);
        data.put("lowVulns", 0);
        return R.ok(data);
    }

    /** 系统健康检查 */
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("version", "1.0.0");
        data.put("platform", "PentaMind");
        return R.ok(data);
    }
}
