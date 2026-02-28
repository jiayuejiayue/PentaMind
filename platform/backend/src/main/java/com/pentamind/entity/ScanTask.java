package com.pentamind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 扫描任务实体
 */
@Data
@TableName("pm_scan_task")
public class ScanTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联目标ID */
    private Long targetId;

    /** 任务名称 */
    private String name;

    /** 扫描类型：FEATURE_SCAN / PATH_SCAN / API_SCAN / VULN_SCAN / PARAM_FUZZ */
    private String scanType;

    /** 状态：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;

    /** 扫描进度 0-100 */
    private Integer progress;

    /** 扫描结果（JSON） */
    private String result;

    /** 发现数量 */
    private Integer findingCount;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
