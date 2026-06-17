package io.github.zmxckj.flexiadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "flexi.task-scheduler")
public class TaskSchedulerProperties {

    private boolean enabled = true;

    private boolean distributedLockEnabled = true;

    private List<String> includeTasks = new ArrayList<>();

    private List<String> excludeTasks = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDistributedLockEnabled() {
        return distributedLockEnabled;
    }

    public void setDistributedLockEnabled(boolean distributedLockEnabled) {
        this.distributedLockEnabled = distributedLockEnabled;
    }

    public List<String> getIncludeTasks() {
        return includeTasks;
    }

    public void setIncludeTasks(List<String> includeTasks) {
        this.includeTasks = includeTasks;
    }

    public List<String> getExcludeTasks() {
        return excludeTasks;
    }

    public void setExcludeTasks(List<String> excludeTasks) {
        this.excludeTasks = excludeTasks;
    }

    public boolean shouldRunTask(String taskClassName) {
        if (!enabled) {
            return false;
        }

        if (!excludeTasks.isEmpty() && excludeTasks.contains(taskClassName)) {
            return false;
        }

        if (!includeTasks.isEmpty()) {
            return includeTasks.contains(taskClassName);
        }

        return true;
    }

    /**
     * 判断是否应该运行该任务（同时支持 class name 和 bean name 两种方式）
     */
    public boolean shouldRunTask(String className, String beanName) {
        if (!enabled) {
            return false;
        }

        // 优先用 beanName 判断，如果有配置的话
        String identifier = (beanName != null && !beanName.isEmpty()) ? beanName : className;

        // 检查是否在排除列表中
        if (!excludeTasks.isEmpty() && excludeTasks.contains(identifier)) {
            return false;
        }

        // 检查是否在包含列表中（如果有配置）
        if (!includeTasks.isEmpty()) {
            return includeTasks.contains(identifier);
        }

        return true;
    }
}
