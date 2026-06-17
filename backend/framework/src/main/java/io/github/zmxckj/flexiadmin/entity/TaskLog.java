package io.github.zmxckj.flexiadmin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_task_log")
public class TaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long taskId;
    private String taskName;
    private String taskClassName;
    private String taskBeanName;
    private String taskMethodName;
    private String cronExpression;
    private String status;
    private String errorMessage;
    private String resultMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long duration;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
