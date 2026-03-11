package com.pentamind.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pentamind.common.R;
import com.pentamind.entity.Subdomain;
import com.pentamind.service.SubdomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subdomain")
public class SubdomainController {

    @Autowired
    private SubdomainService subdomainService;

    @GetMapping("/list")
    public R<Page<Subdomain>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long targetId) {
        LambdaQueryWrapper<Subdomain> wrapper = new LambdaQueryWrapper<>();
        if (targetId != null) {
            wrapper.eq(Subdomain::getTargetId, targetId);
        }
        wrapper.orderByDesc(Subdomain::getCreateTime);
        return R.ok(subdomainService.page(new Page<>(page, size), wrapper));
    }

    @PostMapping
    public R<Void> add(@RequestBody Subdomain entity) {
        subdomainService.save(entity);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        subdomainService.removeById(id);
        return R.ok();
    }
}
