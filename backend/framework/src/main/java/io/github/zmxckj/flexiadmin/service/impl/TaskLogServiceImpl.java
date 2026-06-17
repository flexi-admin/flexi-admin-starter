package io.github.zmxckj.flexiadmin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.zmxckj.flexiadmin.entity.TaskLog;
import io.github.zmxckj.flexiadmin.mapper.TaskLogMapper;
import io.github.zmxckj.flexiadmin.service.TaskLogService;
import org.springframework.stereotype.Service;

@Service
public class TaskLogServiceImpl extends ServiceImpl<TaskLogMapper, TaskLog> implements TaskLogService {
}
