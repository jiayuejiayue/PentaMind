package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.ScanTask;
import com.pentamind.mapper.ScanTaskMapper;
import com.pentamind.service.ScanTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ScanTaskServiceImpl extends ServiceImpl<ScanTaskMapper, ScanTask> implements ScanTaskService {

    @Autowired
    private com.pentamind.service.ToolManager toolManager;

    @Autowired
    private com.pentamind.service.DirScanService dirScanService;

    @org.springframework.beans.factory.annotation.Value("${pentamind.tools-dir:tools/}")
    private String toolsBasePath;

    /**
     * 将任务分配给名为 "scanTaskExecutor" 的线程池，避免阻塞 HTTP 线程
     * 这构成了 PentaMind 长耗时渗透平台的基础状态机
     */
    @Async("scanTaskExecutor")
    @Override
    public void submitScanTask(Long targetId, String scanType, String targetHost) {
        log.info("【异步扫描引擎】收到调度指令. TargetID: {}, Type: {}, Host: {}", targetId, scanType, targetHost);

        // 1. 初始化并保存任务状态 (RUNNING)
        ScanTask task = new ScanTask();
        task.setTargetId(targetId);
        task.setName(scanType + " - " + targetHost);
        task.setScanType(scanType);
        task.setStatus("RUNNING");
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        this.save(task);

        try {
            // 2. 根据不同的 ScanType 路由到不同的命令行包装器 (ProcessBuilder)
            // 进度模拟: 防止真卡住太久, 虽然目前是阻塞 Process, 但应该有个大概进度
            task.setProgress(20);
            this.updateById(task);

            String resultOutput = "";
            java.util.Map<String, String> dynamicArgs = new java.util.HashMap<>();
            dynamicArgs.put("target", targetHost);

            // 工具目录（与 application.yml 中 pentamind.tools-dir 同源）

            // 根据 toolName 路由到对应的工具执行
            switch (scanType.toLowerCase()) {
                case "nmap_port_scan" -> resultOutput = toolManager.executeTool("nmap_port_scan", dynamicArgs);
                case "subfinder_domain" -> resultOutput = toolManager.executeTool("subfinder_domain", dynamicArgs);
                case "whatweb_fingerprint" ->
                    resultOutput = toolManager.executeTool("whatweb_fingerprint", dynamicArgs);
                case "httpx_alive_detect" -> resultOutput = toolManager.executeTool("httpx_alive_detect", dynamicArgs);
                case "gobuster_dir" -> {
                    // 并行调用 Gobuster + Dirsearch，结果自动去重合并
                    String ext = dynamicArgs.getOrDefault("extensions", "php,html,bak,txt,js,json");
                    String wl = dynamicArgs.getOrDefault("wordlist", null);
                    resultOutput = dirScanService.scanParallel(targetHost, wl, ext);
                }
                case "nuclei_vuln_scan" -> resultOutput = toolManager.executeTool("nuclei_vuln_scan", dynamicArgs);
                default -> {
                    log.warn("【扫描引擎】未知工具名: {}，使用模拟逻辑", scanType);
                    simulateLongRunningProcess(task, scanType, targetHost);
                    resultOutput = "未注册的工具: " + scanType;
                }
            }

            // 3. 扫描正常完成
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setResult(resultOutput);
            task.setResultSummary("自动化任务结束。控制台返回了 " + resultOutput.length() + " 字节的数据。");
            log.info("【异步扫描引擎】任务执行完成. TaskID: {}", task.getId());

        } catch (Exception e) {
            log.error("【异步扫描引擎】任务执行异常! TaskID: {}", task.getId(), e);
            task.setStatus("FAILED");
            task.setResultSummary("扫描发生错误：" + e.getMessage());
        } finally {
            // 4. 无论成功失败，更新最终状态与结束时间
            task.setEndTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            this.updateById(task);
        }
    }

    /**
     * 模拟第三方工具的长耗时调用 (如 Nmap / Subfinder 的黑盒运行)
     */
    private void simulateLongRunningProcess(ScanTask task, String scanType, String targetHost)
            throws InterruptedException {
        int totalSteps = 10;
        for (int i = 1; i <= totalSteps; i++) {
            Thread.sleep(1000); // 模拟每个小步骤耗时1秒

            // 更新数据库里的进度条，以便由于后续要开发的前端由于 websocket 或轮询可以感知进度
            task.setProgress(i * 10);
            task.setUpdateTime(LocalDateTime.now());
            this.updateById(task);
            log.debug("TaskID {} 进度: {}%", task.getId(), task.getProgress());
        }
    }
}
