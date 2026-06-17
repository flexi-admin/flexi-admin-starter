package io.github.zmxckj.flexiadmin.schedule;

import io.github.zmxckj.flexiadmin.config.TaskSchedulerProperties;
import io.github.zmxckj.flexiadmin.entity.Task;
import io.github.zmxckj.flexiadmin.entity.TaskLog;
import io.github.zmxckj.flexiadmin.service.TaskService;
import io.github.zmxckj.flexiadmin.service.TaskLogService;
import io.github.zmxckj.flexiadmin.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class TaskSchedulerManager implements CommandLineRunner, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(TaskSchedulerManager.class);

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private TaskSchedulerProperties taskSchedulerProperties;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ApplicationContext applicationContext;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @Override
    public void run(String... args) {
        if (taskSchedulerProperties.isEnabled()) {
            loadAndScheduleTasks();
        } else {
            logger.info("定时任务调度器已禁用，不加载任务");
        }
    }

    public void loadAndScheduleTasks() {
        logger.info("开始加载定时任务...");
        List<Task> tasks = taskService.list();
        
        int scheduledCount = 0;
        for (Task task : tasks) {
            if (Boolean.TRUE.equals(task.getStatus()) && shouldScheduleTask(task)) {
                scheduleTask(task);
                scheduledCount++;
            }
        }
        logger.info("定时任务加载完成，共 {} 个任务，实际调度 {} 个", tasks.size(), scheduledCount);
    }

    private boolean shouldScheduleTask(Task task) {
        return taskSchedulerProperties.shouldRunTask(task.getClassName(), task.getBeanName());
    }

    public void scheduleTask(Task task) {
        try {
            cancelTask(task.getId());
            
            Runnable taskRunnable = createTaskRunnable(task);
            Runnable wrappedTask = createDistributedTask(task, taskRunnable);
            
            ScheduledFuture<?> future = taskScheduler.schedule(
                wrappedTask,
                new CronTrigger(task.getCronExpression())
            );
            
            scheduledTasks.put(task.getId(), future);
            logger.info("任务 [{}] 已调度，Cron: {}", task.getName(), task.getCronExpression());
        } catch (Exception e) {
            logger.error("调度任务 [{}] 失败: {}", task.getName(), e.getMessage(), e);
        }
    }

    private Runnable createTaskRunnable(Task task) throws Exception {
        if (task.getBeanName() != null && !task.getBeanName().isEmpty() 
            && task.getMethodName() != null && !task.getMethodName().isEmpty()) {
            return createBeanMethodRunnable(task);
        } else if (task.getClassName() != null && !task.getClassName().isEmpty()) {
            return createClassRunnable(task);
        } else {
            throw new IllegalArgumentException("任务必须配置 className 或 beanName + methodName");
        }
    }

    private Runnable createBeanMethodRunnable(Task task) {
        return () -> {
            try {
                Object bean = applicationContext.getBean(task.getBeanName());
                Method method = findMethod(bean.getClass(), task.getMethodName());
                method.setAccessible(true);
                method.invoke(bean);
            } catch (Exception e) {
                throw new RuntimeException("执行任务方法失败", e);
            }
        };
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (Method method : currentClass.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        throw new RuntimeException("找不到无参方法: " + methodName + " in " + clazz);
    }

    private Runnable createClassRunnable(Task task) throws Exception {
        Class<?> taskClass = Class.forName(task.getClassName());
        return (Runnable) taskClass.getDeclaredConstructor().newInstance();
    }

    private Runnable createDistributedTask(Task task, Runnable originalTask) {
        return () -> {
            // 创建任务执行日志
            TaskLog taskLog = createTaskLogStart(task);
            long startTime = System.currentTimeMillis();
            
            try {
                if (!taskSchedulerProperties.isDistributedLockEnabled()) {
                    logger.info("分布式锁已禁用，直接执行任务 [{}]", task.getName());
                    executeTask(task, originalTask, taskLog, startTime);
                    return;
                }
                
                // 使用更简单的锁 key，避免 class name 和 bean name 的切换问题
                String lockKey = "task:" + task.getId();
                
                logger.info("尝试获取任务 [{}] 的分布式锁，key={}", task.getName(), lockKey);
                
                // 尝试获取锁，超时时间设为3秒，避免长时间等待
                boolean lockAcquired = redisUtils.tryLock(lockKey, 3, 30, TimeUnit.SECONDS);
                if (!lockAcquired) {
                    String message = "无法获取任务的分布式锁，跳过本次执行";
                    logger.warn("任务 [{}] {}", task.getName(), message);
                    updateTaskLogFailed(taskLog, startTime, message);
                    return;
                }
                
                logger.info("成功获取任务 [{}] 的分布式锁", task.getName());
                
                try {
                    executeTask(task, originalTask, taskLog, startTime);
                } finally {
                    logger.info("释放任务 [{}] 的分布式锁", task.getName());
                    redisUtils.unlock(lockKey);
                }
            } catch (Exception e) {
                logger.error("任务 [{}] 执行异常: {}", task.getName(), e.getMessage(), e);
                updateTaskLogFailed(taskLog, startTime, e.getMessage());
                throw e;
            }
        };
    }

    private TaskLog createTaskLogStart(Task task) {
        TaskLog taskLog = new TaskLog();
        taskLog.setTenantId(task.getTenantId());
        taskLog.setTaskId(task.getId());
        taskLog.setTaskName(task.getName());
        taskLog.setTaskClassName(task.getClassName());
        taskLog.setTaskBeanName(task.getBeanName());
        taskLog.setTaskMethodName(task.getMethodName());
        taskLog.setCronExpression(task.getCronExpression());
        taskLog.setStatus("RUNNING");
        taskLog.setStartTime(java.time.LocalDateTime.now());
        taskLogService.save(taskLog);
        return taskLog;
    }

    private void executeTask(Task task, Runnable originalTask, TaskLog taskLog, long startTime) {
        logger.info("任务 [{}] 开始执行", task.getName());
        originalTask.run();
        logger.info("任务 [{}] 执行完成", task.getName());
        
        // 更新任务执行日志 - 成功
        updateTaskLogSuccess(taskLog, startTime, "任务执行成功");
    }

    private void updateTaskLogSuccess(TaskLog taskLog, long startTime, String resultMessage) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        taskLog.setEndTime(java.time.LocalDateTime.now());
        taskLog.setDuration(duration);
        taskLog.setStatus("SUCCESS");
        taskLog.setResultMessage(resultMessage);
        taskLogService.updateById(taskLog);
    }

    private void updateTaskLogFailed(TaskLog taskLog, long startTime, String errorMessage) {
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        taskLog.setEndTime(java.time.LocalDateTime.now());
        taskLog.setDuration(duration);
        taskLog.setStatus("FAILED");
        taskLog.setErrorMessage(errorMessage);
        taskLogService.updateById(taskLog);
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = scheduledTasks.remove(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            logger.info("任务 [{}] 已取消", taskId);
        }
    }

    public void refreshTask(Task task) {
        if (Boolean.TRUE.equals(task.getStatus()) && shouldScheduleTask(task)) {
            scheduleTask(task);
        } else {
            cancelTask(task.getId());
        }
    }

    public void runTaskNow(Long taskId) {
        try {
            logger.info("开始立即执行任务 [{}]", taskId);
            Task task = taskService.getById(taskId);
            if (task == null) {
                logger.error("任务 [{}] 不存在", taskId);
                throw new RuntimeException("任务不存在: " + taskId);
            }

            logger.info("找到任务 [{}]，beanName={}, methodName={}, className={}", 
                    task.getName(), task.getBeanName(), task.getMethodName(), task.getClassName());

            Runnable taskRunnable = createTaskRunnable(task);
            Runnable wrappedTask = createDistributedTask(task, taskRunnable);
            
            logger.info("准备立即执行任务 [{}]", task.getName());
            // 直接同步执行，而不是异步，方便调试
            wrappedTask.run();
            logger.info("立即执行任务 [{}] 提交完成", task.getName());
        } catch (Exception e) {
            logger.error("立即执行任务 [{}] 失败: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("立即执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        logger.info("正在关闭定时任务调度器...");
        
        // 先取消所有已调度的任务
        int cancelCount = 0;
        for (Map.Entry<Long, ScheduledFuture<?>> entry : scheduledTasks.entrySet()) {
            Long taskId = entry.getKey();
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isDone()) {
                future.cancel(false);
                cancelCount++;
                logger.debug("已取消任务 [{}]", taskId);
            }
        }
        scheduledTasks.clear();
        logger.info("已取消 {} 个定时任务", cancelCount);
        
        logger.info("定时任务调度器已关闭");
    }
}
