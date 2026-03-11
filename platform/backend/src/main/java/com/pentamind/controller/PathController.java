package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.Path;
import com.pentamind.service.PathService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/path")
public class PathController {

    @Autowired
    private PathService pathService;

    @GetMapping("/list")
    public R<Page<Path>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long targetId) {
        LambdaQueryWrapper<Path> wrapper = new LambdaQueryWrapper<>();
        if (targetId != null) {
            wrapper.eq(Path::getTargetId, targetId);
        }
        wrapper.orderByDesc(Path::getCreateTime);
        return R.ok(pathService.page(new Page<>(page, size), wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody Path entity) {
        pathService.save(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        pathService.removeById(id);
        return R.ok();
    }
}
