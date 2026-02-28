package com.pentamind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 插件状态实体
 */
@Data
@TableName("pm_plugin")
public class Plugin {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 插件名称 */
    private String name;

    /** 版本 */
    private String version;

    /** 类型: EXTENSION / PATHFINDER */
    private String type;

    /** 状态: ONLINE / OFFLINE */
    private String status;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 配置 JSON */
    private String config;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
