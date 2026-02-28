package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.Target;
import com.pentamind.service.TargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目标管理 Controller
 */
@RestController
@RequestMapping("/api/target")
@RequiredArgsConstructor
public class TargetController {

    private final TargetService targetService;

    /** 分页查询 */
    @GetMapping("/list")
    public R<Page<Target>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String name) {
        LambdaQueryWrapper<Target> wrapper = new LambdaQueryWrapper<>();
        if (name != null && !name.isBlank()) {
            wrapper.like(Target::getName, name);
        }
        wrapper.orderByDesc(Target::getCreateTime);
        return R.ok(targetService.page(new Page<>(page, size), wrapper));
    }

    /** 查询详情 */
    @GetMapping("/{id}")
    public R<Target> getById(@PathVariable Long id) {
        return R.ok(targetService.getById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Void> add(@RequestBody Target target) {
        targetService.save(target);
        return R.ok();
    }

    /** 修改 */
    @PutMapping
    public R<Void> update(@RequestBody Target target) {
        targetService.updateById(target);
        return R.ok();
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        targetService.removeById(id);
        return R.ok();
    }

    /** 批量删除 */
    @DeleteMapping("/batch")
    public R<Void> deleteBatch(@RequestBody List<Long> ids) {
        targetService.removeByIds(ids);
        return R.ok();
    }
}
