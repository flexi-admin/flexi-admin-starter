package io.github.zmxckj.flexiadmin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.zmxckj.flexiadmin.annotation.Log;
import io.github.zmxckj.flexiadmin.entity.Task;
import io.github.zmxckj.flexiadmin.common.R;
import io.github.zmxckj.flexiadmin.schedule.TaskSchedulerManager;
import io.github.zmxckj.flexiadmin.security.RequirePermission;
import io.github.zmxckj.flexiadmin.security.SecurityUtils;
import io.github.zmxckj.flexiadmin.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskSchedulerManager taskSchedulerManager;

    @GetMapping("/list")
    @RequirePermission("task:list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer pageSize) {
        Page<Task> taskPage = taskService.page(new Page<>(page, pageSize));
        Map<String, Object> response = new HashMap<>();
        response.put("list", taskPage.getRecords());
        response.put("total", taskPage.getTotal());
        return R.success(response);
    }

    @PostMapping
    @Log(operation = "新增任务")
    @RequirePermission("task:add")
    public R<?> add(@RequestBody Task task) {
        task.setTenantId(SecurityUtils.getCurrentTenantId());
        taskService.save(task);
        if (Boolean.TRUE.equals(task.getStatus())) {
            taskSchedulerManager.scheduleTask(task);
        }
        return R.success();
    }

    @PutMapping
    @Log(operation = "修改任务")
    @RequirePermission("task:update")
    public R<?> update(@RequestBody Task task) {
        taskService.updateById(task);
        taskSchedulerManager.refreshTask(task);
        return R.success();
    }

    @DeleteMapping("/{id}")
    @Log(operation = "删除任务")
    @RequirePermission("task:delete")
    public R<?> delete(@PathVariable Long id) {
        taskSchedulerManager.cancelTask(id);
        taskService.removeById(id);
        return R.success();
    }

    @GetMapping("/{id}")
    @RequirePermission("task:list")
    public R<Task> getById(@PathVariable Long id) {
        Task task = taskService.getById(id);
        return R.success(task);
    }

    @PostMapping("/refresh")
    @RequirePermission("task:update")
    public R<?> refresh() {
        taskSchedulerManager.loadAndScheduleTasks();
        return R.success();
    }

    @PostMapping("/{id}/run")
    @Log(operation = "启动任务")
    @RequirePermission("task:update")
    public R<?> runNow(@PathVariable Long id) {
        taskSchedulerManager.runTaskNow(id);
        return R.success();
    }
}