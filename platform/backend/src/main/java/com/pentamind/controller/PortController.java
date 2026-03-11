package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.Port;
import com.pentamind.service.PortService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/port")
public class PortController {

    @Autowired
    private PortService portService;

    @GetMapping("/list")
    public R<Page<Port>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long targetId) {
        LambdaQueryWrapper<Port> wrapper = new LambdaQueryWrapper<>();
        if (targetId != null) {
            wrapper.eq(Port::getTargetId, targetId);
        }
        wrapper.orderByDesc(Port::getCreateTime);
        return R.ok(portService.page(new Page<>(page, size), wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody Port entity) {
        portService.save(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        portService.removeById(id);
        return R.ok();
    }
}
