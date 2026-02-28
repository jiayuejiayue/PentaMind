package com.pentamind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 扫描目标实体
 */
@Data
@TableName("pm_target")
public class Target {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标名称 */
    private String name;

    /** 目标URL */
    private String url;

    /** 目标类型：WEB / API / APP */
    private String type;

    /** 状态：0-待测试 1-测试中 2-已完成 */
    private Integer status;

    /** 风险等级：CRITICAL / HIGH / MEDIUM / LOW / INFO */
    private String riskLevel;

    /** 发现漏洞数 */
    private Integer vulnCount;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
