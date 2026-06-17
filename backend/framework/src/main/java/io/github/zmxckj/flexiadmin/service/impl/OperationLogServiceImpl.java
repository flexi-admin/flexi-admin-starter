package io.github.zmxckj.flexiadmin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.zmxckj.flexiadmin.entity.OperationLog;
import io.github.zmxckj.flexiadmin.mapper.OperationLogMapper;
import io.github.zmxckj.flexiadmin.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog> implements OperationLogService {

    @Override
    public void saveAsync(OperationLog operationLog) {
        try {
            this.save(operationLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }
}
