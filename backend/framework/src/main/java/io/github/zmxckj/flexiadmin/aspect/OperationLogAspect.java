package io.github.zmxckj.flexiadmin.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.zmxckj.flexiadmin.annotation.Log;
import io.github.zmxckj.flexiadmin.entity.OperationLog;
import io.github.zmxckj.flexiadmin.security.SecurityUtils;
import io.github.zmxckj.flexiadmin.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Around("@annotation(io.github.zmxckj.flexiadmin.annotation.Log) || @within(io.github.zmxckj.flexiadmin.annotation.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = null;
        Throwable throwable = null;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {
            throwable = t;
        } finally {
            try {
                saveOperationLog(joinPoint, throwable);
            } catch (Exception e) {
                log.warn("记录操作日志失败: {}", e.getMessage());
            }
        }
        if (throwable != null) {
            throw throwable;
        }
        return result;
    }

    private void saveOperationLog(ProceedingJoinPoint joinPoint, Throwable throwable) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnno = method.getAnnotation(Log.class);
        if (logAnno == null) {
            Class<?> clazz = joinPoint.getTarget().getClass();
            logAnno = clazz.getAnnotation(Log.class);
        }
        if (logAnno == null) {
            return;
        }

        String operation = logAnno.operation();
        if (operation == null || operation.isEmpty()) {
            operation = logAnno.value();
        }
        if (operation == null || operation.isEmpty()) {
            operation = method.getDeclaringClass().getSimpleName() + "#" + method.getName();
        }
        if (throwable != null) {
            operation = operation + "（失败）";
        }

        String username = SecurityUtils.getCurrentUsername();
        Long tenantId = SecurityUtils.getCurrentTenantId();
        String ip = getClientIp();
        String params = null;
        if (logAnno.recordParams()) {
            params = buildParams(joinPoint, method, logAnno.sensitiveFields());
        }

        OperationLog logEntity = new OperationLog();
        logEntity.setTenantId(tenantId);
        logEntity.setUsername(username);
        logEntity.setOperation(operation);
        logEntity.setIp(ip);
        logEntity.setParams(params);
        logEntity.setCreateTime(LocalDateTime.now());

        operationLogService.saveAsync(logEntity);
    }

    private String buildParams(ProceedingJoinPoint joinPoint, Method method, String[] sensitiveFields) {
        try {
            String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || paramNames.length == 0 || args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> paramsMap = new HashMap<>();
            for (int i = 0; i < paramNames.length; i++) {
                String name = paramNames[i];
                Object value = args[i];
                if (isSensitive(name, sensitiveFields)) {
                    paramsMap.put(name, "******");
                    continue;
                }
                if (isMultipartFile(value)) {
                    paramsMap.put(name, describeMultipart(value));
                    continue;
                }
                if (isMultipartFileArray(value)) {
                    paramsMap.put(name, describeMultipartArray(value));
                    continue;
                }
                if (isIgnorable(value)) {
                    paramsMap.put(name, "<" + (value == null ? "null" : value.getClass().getSimpleName()) + ">");
                    continue;
                }
                paramsMap.put(name, maskIfNeeded(value, sensitiveFields));
            }

            ObjectNode node = objectMapper.valueToTree(paramsMap);
            for (String field : sensitiveFields) {
                if (node.has(field)) {
                    node.put(field, "******");
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("序列化请求参数失败: {}", e.getMessage());
            return null;
        }
    }

    private boolean isSensitive(String name, String[] sensitiveFields) {
        if (name == null || sensitiveFields == null) {
            return false;
        }
        String lowerName = name.toLowerCase();
        return Arrays.stream(sensitiveFields).anyMatch(f -> f != null && f.toLowerCase().equals(lowerName));
    }

    private boolean isMultipartFile(Object value) {
        if (value == null) {
            return false;
        }
        return value instanceof MultipartFile
                || (value.getClass().getName().startsWith("org.springframework.web.multipart")
                    && !value.getClass().isArray());
    }

    private boolean isMultipartFileArray(Object value) {
        if (value == null) {
            return false;
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) {
                Object item = java.lang.reflect.Array.get(value, i);
                if (item instanceof MultipartFile) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof java.util.Collection) {
            for (Object item : (java.util.Collection<?>) value) {
                if (item instanceof MultipartFile) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, Object> describeMultipart(Object value) {
        Map<String, Object> info = new HashMap<>();
        if (value == null) {
            return info;
        }
        try {
            if (value instanceof MultipartFile) {
                MultipartFile file = (MultipartFile) value;
                info.put("originalFilename", file.getOriginalFilename());
                info.put("name", file.getName());
                info.put("contentType", file.getContentType());
                info.put("size", file.getSize());
                info.put("empty", file.isEmpty());
            } else {
                info.put("type", value.getClass().getSimpleName());
            }
        } catch (Exception e) {
            info.put("type", value.getClass().getSimpleName());
        }
        return info;
    }

    private java.util.List<Map<String, Object>> describeMultipartArray(Object value) {
        java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
        if (value == null) {
            return list;
        }
        try {
            if (value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) {
                    Object item = java.lang.reflect.Array.get(value, i);
                    list.add(describeMultipart(item));
                }
            } else if (value instanceof java.util.Collection) {
                for (Object item : (java.util.Collection<?>) value) {
                    list.add(describeMultipart(item));
                }
            }
        } catch (Exception e) {
            list.add(java.util.Collections.singletonMap("error", e.getMessage()));
        }
        return list;
    }

    private boolean isIgnorable(Object value) {
        if (value == null) {
            return true;
        }
        return value instanceof HttpServletRequest
                || value instanceof jakarta.servlet.http.HttpServletResponse
                || value.getClass().getName().startsWith("org.springframework.web.multipart.MultipartHttpServletRequest");
    }

    private Object maskIfNeeded(Object value, String[] sensitiveFields) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence) {
            String str = value.toString();
            for (String field : sensitiveFields) {
                if (field == null) {
                    continue;
                }
                String lower = field.toLowerCase();
                if (str.toLowerCase().contains(lower + "=") || str.toLowerCase().contains("\"" + lower + "\"")) {
                    try {
                        return maskInJsonLikeString(str, sensitiveFields);
                    } catch (Exception e) {
                        return str;
                    }
                }
            }
            return str;
        }
        return value;
    }

    private String maskInJsonLikeString(String input, String[] sensitiveFields) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        for (String field : sensitiveFields) {
            if (field == null || field.isEmpty()) {
                continue;
            }
            String lowerField = field.toLowerCase();
            // 匹配 "field" : "value" 或 field=value 等带引号/不带引号的形式
            // 带引号字符串值: "fieldName" : "xxx"
            result = result.replaceAll(
                    "(?i)([\"']?" + java.util.regex.Pattern.quote(lowerField) + "[\"']?\\s*[:=]\\s*\")([^\"]*)(\")",
                    "$1******$3");
            // 不带引号的非字符串值: "fieldName" : xxx 或 fieldName=xxx
            result = result.replaceAll(
                    "(?i)([\"']?" + java.util.regex.Pattern.quote(lowerField) + "[\"']?\\s*[:=]\\s*)([^,;&\\s\\}]]+)",
                    "$1******");
        }
        return result;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
}
