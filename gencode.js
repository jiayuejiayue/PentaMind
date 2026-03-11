const fs = require('fs');
const path = require('path');

const basePath = 'e:/webSafe/AI_agent/PentaMind/platform/backend/src/main/java/com/pentamind';

const templates = [
    {
        type: 'entity',
        path: 'entity',
        ext: '.java',
        content: (name, lower, tableName) => `package com.pentamind.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("${tableName}")
public class ${name} {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long targetId;
    @TableField(exist = false)
    private String targetName; // 用于关联查询展示
    
    // 特定属性需要在生成后手动补充，这里先放基准字段
    private String source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
`
    },
    {
        type: 'mapper',
        path: 'mapper',
        ext: 'Mapper.java',
        content: (name, lower, tableName) => `package com.pentamind.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pentamind.entity.${name};
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ${name}Mapper extends BaseMapper<${name}> {
}
`
    },
    {
        type: 'service',
        path: 'service',
        ext: 'Service.java',
        content: (name, lower, tableName) => `package com.pentamind.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pentamind.entity.${name};

public interface ${name}Service extends IService<${name}> {
}
`
    },
    {
        type: 'serviceImpl',
        path: 'service/impl',
        ext: 'ServiceImpl.java',
        content: (name, lower, tableName) => `package com.pentamind.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pentamind.entity.${name};
import com.pentamind.mapper.${name}Mapper;
import com.pentamind.service.${name}Service;
import org.springframework.stereotype.Service;

@Service
public class ${name}ServiceImpl extends ServiceImpl<${name}Mapper, ${name}> implements ${name}Service {
}
`
    },
    {
        type: 'controller',
        path: 'controller',
        ext: 'Controller.java',
        content: (name, lower, tableName) => `package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.${name};
import com.pentamind.service.${name}Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/${lower}")
public class ${name}Controller {

    @Autowired
    private ${name}Service ${lower}Service;

    @GetMapping("/list")
    public R<Page<${name}>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long targetId) {
        LambdaQueryWrapper<${name}> wrapper = new LambdaQueryWrapper<>();
        if (targetId != null) {
            wrapper.eq(${name}::getTargetId, targetId);
        }
        wrapper.orderByDesc(${name}::getCreateTime);
        return R.ok(${lower}Service.page(new Page<>(page, size), wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody ${name} entity) {
        ${lower}Service.save(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ${lower}Service.removeById(id);
        return R.ok();
    }
}
`
    }
];

const modules = [
    { name: 'Subdomain', lower: 'subdomain', tableName: 'pm_subdomain' },
    { name: 'Port', lower: 'port', tableName: 'pm_port' },
    { name: 'Path', lower: 'path', tableName: 'pm_path' }
];

modules.forEach(mod => {
    templates.forEach(tpl => {
        const dir = path.join(basePath, tpl.path);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });

        // We do not overwrite if exists to be safe
        const file = path.join(dir, mod.name + tpl.ext);
        if (!fs.existsSync(file)) {
            fs.writeFileSync(file, tpl.content(mod.name, mod.lower, mod.tableName), 'utf8');
            console.log('Created:', file);
        }
    });
});
