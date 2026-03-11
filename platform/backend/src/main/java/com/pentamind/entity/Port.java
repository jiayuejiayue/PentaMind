package com.pentamind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("pm_port")
public class Port {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long targetId;
    @TableField(exist = false)
    private String targetName; // 用于关联查询展示

    private String ip;
    private Integer port;
    private String protocol;
    private String service;
    private String version;
    private String state;

    // 特定属性需要在生成后手动补充，这里先放基准字段
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
