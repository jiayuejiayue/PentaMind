package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.pentamind.common.R;
import com.pentamind.entity.ScanTask;
import com.pentamind.entity.Target;
import com.pentamind.mapper.ScanTaskMapper;
import com.pentamind.mapper.TargetMapper;
import com.pentamind.service.AgentOrchestrator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * AI 智能体渗透控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;
    private final ScanTaskMapper scanTaskMapper;
    private final TargetMapper targetMapper;

    @Data
    public static class AgentStartReq {
        private String target;
        private List<String> phases;
        private String username;
        private String password;
    }

    /**
     * 启动智能渗透任务
     */
    @PostMapping("/start")
    public R<Long> startAgentPentest(@RequestBody AgentStartReq req) {
        log.info("[Agent] 接收到智能渗透请求: target={}", req.getTarget());

        // 查找或创建 Target
        Target targetEntity = targetMapper.selectOne(new QueryWrapper<Target>().eq("url", req.getTarget()));
        if (targetEntity == null) {
            targetEntity = new Target();
            targetEntity.setUrl(req.getTarget());
            targetEntity.setName(req.getTarget().replace("http://", "").replace("https://", ""));
            targetEntity.setStatus(1);
            targetMapper.insert(targetEntity);
        }

        // 创建一条类型为 AGENT_PENTEST 的总控任务记录
        ScanTask task = new ScanTask();
        task.setTargetId(targetEntity.getId());
        task.setName("智能体自动渗透 - " + targetEntity.getName());
        task.setScanType("AGENT_PENTEST");
        task.setStatus("PENDING");
        task.setProgress(0);
        scanTaskMapper.insert(task);

        // 异步启动编排器
        agentOrchestrator.startOrchestration(task.getId(), req.getTarget(), req.getPhases(), req.getUsername(),
                req.getPassword());

        R<Long> resp = R.ok(task.getId());
        resp.setMsg("智能体已启动");
        return resp;
    }

    /**
     * 实时 SSE 日志推流接口
     */
    @GetMapping(value = "/stream/{taskId}", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamAgentProgress(@PathVariable Long taskId) {
        return agentOrchestrator.createSseEmitter(taskId);
    }

    /**
     * 获取渗透报告结果
     */
    @GetMapping("/report/{taskId}")
    public R<String> getAgentReport(@PathVariable Long taskId) {
        ScanTask task = scanTaskMapper.selectById(taskId);
        if (task == null) {
            return R.fail("未找到指定任务");
        }

        // 优先返回详细结果，如果没有则返回概要
        String report = task.getResult();
        if (report == null || report.isEmpty()) {
            report = task.getResultSummary();
        }

        return R.ok(report);
    }
}
