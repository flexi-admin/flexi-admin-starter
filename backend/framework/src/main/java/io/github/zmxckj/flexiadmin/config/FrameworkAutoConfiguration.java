package io.github.zmxckj.flexiadmin.config;

import io.github.zmxckj.flexiadmin.excel.service.ExcelExportService;
import io.github.zmxckj.flexiadmin.excel.service.ExcelImportService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
@EnableScheduling
@ComponentScan(basePackages = "io.github.zmxckj.flexiadmin")
@MapperScan("io.github.zmxckj.flexiadmin.mapper")
public class FrameworkAutoConfiguration {

    @Bean
    public ExcelImportService excelImportService() {
        return new ExcelImportService();
    }

    @Bean
    public ExcelExportService excelExportService() {
        return new ExcelExportService();
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("task-scheduler-");
        // 关闭时等待任务完成，但配合 @PreDestroy 先取消所有任务
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        // 设置更短的超时时间，因为任务已经被取消
        scheduler.setAwaitTerminationSeconds(10);
        return scheduler;
    }
}
