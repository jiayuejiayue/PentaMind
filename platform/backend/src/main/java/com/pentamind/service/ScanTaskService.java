package com.pentamind.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pentamind.entity.ScanTask;

public interface ScanTaskService extends IService<ScanTask> {
    /**
     * 创建并异步分配执行扫描任务
     *
     * @param targetId   扫描的主资产 ID
     * @param scanType   扫描工具类型/子阶段类型 (NMAP, DIRSEARCH, TSCAN)
     * @param targetHost 目标主机或 URL
     */
    void submitScanTask(Long targetId, String scanType, String targetHost);
}
