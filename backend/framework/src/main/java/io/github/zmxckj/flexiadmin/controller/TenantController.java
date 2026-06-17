package io.github.zmxckj.flexiadmin.controller;

import io.github.zmxckj.flexiadmin.common.R;
import io.github.zmxckj.flexiadmin.entity.Tenant;
import io.github.zmxckj.flexiadmin.annotation.Log;
import io.github.zmxckj.flexiadmin.service.TenantService;
import io.github.zmxckj.flexiadmin.security.RequirePermission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant")
public class TenantController {
    
    @Autowired
    private TenantService tenantService;
    
    @GetMapping("/list")
    @RequirePermission("tenant:list")
    public R list() {
        List<Tenant> list = tenantService.list();
        return R.success(list);
    }
    
    @PostMapping("/add")
    @Log(operation = "新增租户")
    @RequirePermission("tenant:add")
    public R add(@RequestBody Tenant tenant) {
        boolean saved = tenantService.save(tenant);
        return saved ? R.success() : R.error("添加失败");
    }
    
    @PutMapping("/update")
    @Log(operation = "修改租户")
    @RequirePermission("tenant:update")
    public R update(@RequestBody Tenant tenant) {
        boolean updated = tenantService.updateById(tenant);
        return updated ? R.success() : R.error("更新失败");
    }
    
    @DeleteMapping("/delete/{id}")
    @Log(operation = "删除租户")
    @RequirePermission("tenant:delete")
    public R delete(@PathVariable Long id) {
        boolean deleted = tenantService.removeById(id);
        return deleted ? R.success() : R.error("删除失败");
    }
    
    @GetMapping("/get/{id}")
    @RequirePermission("tenant:list")
    public R get(@PathVariable Long id) {
        Tenant tenant = tenantService.getById(id);
        return tenant != null ? R.success(tenant) : R.error("租户不存在");
    }
}
