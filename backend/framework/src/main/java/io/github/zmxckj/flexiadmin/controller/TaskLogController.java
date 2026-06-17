package io.github.zmxckj.flexiadmin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.zmxckj.flexiadmin.annotation.Log;
import io.github.zmxckj.flexiadmin.common.R;
import io.github.zmxckj.flexiadmin.entity.TaskLog;
import io.github.zmxckj.flexiadmin.security.RequirePermission;
import io.github.zmxckj.flexiadmin.service.TaskLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/task-log")
public class TaskLogController {

    @Autowired
    private TaskLogService taskLogService;

    // 任务日志列表（支持按任务ID筛选）
    @RequirePermission("task-log:list")
    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "10") Integer pageSize, @RequestParam(required = false) Long taskId, @RequestParam(required = false) String status) {
        Page<TaskLog> taskLogPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<TaskLog> wrapper = new LambdaQueryWrapper<>();
        
        if (taskId != null) {
            wrapper.eq(TaskLog::getTaskId, taskId);
        }
        
        if (StringUtils.hasText(status)) {
            wrapper.eq(TaskLog::getStatus, status);
        }
        
        wrapper.orderByDesc(TaskLog::getStartTime);
        taskLogService.page(taskLogPage, wrapper);
        
        Map<String, Object> response = new HashMap<>();
        response.put("list", taskLogPage.getRecords());
        response.put("total", taskLogPage.getTotal());
        return R.success(response);
    }

    // 获取单个任务日志详情
    @RequirePermission("task-log:list")
    @GetMapping("/{id}")
    public R<TaskLog> getById(@PathVariable Long id) {
        TaskLog taskLog = taskLogService.getById(id);
        return R.success(taskLog);
    }

    // 删除任务日志
    @RequirePermission("task-log:delete")
    @Log(operation = "删除任务日志")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        taskLogService.removeById(id);
        return R.success();
    }
}
