package io.github.zmxckj.flexiadmin.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.zmxckj.flexiadmin.dto.UserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${flexi.redis.key-prefix:flexi:}")
    private String keyPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final ThreadLocal<String> LOCK_HOLDER = new ThreadLocal<>();

    private static final Long RELEASE_SUCCESS = 1L;

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end";

    private String buildKey(String key) {
        return keyPrefix + key;
    }

    public void cacheUserInfo(String username, UserInfoDTO userInfo, long expiration) {
        try {
            String jsonUserInfo = objectMapper.writeValueAsString(userInfo);
            redisTemplate.opsForValue().set(buildKey("user:" + username), jsonUserInfo, expiration, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize user info: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize user info", e);
        }
    }

    public UserInfoDTO getUserInfo(String username) {
        String cachedUserInfo = redisTemplate.opsForValue().get(buildKey("user:" + username));
        if (cachedUserInfo == null) {
            return null;
        }
        
        try {
            return objectMapper.readValue(cachedUserInfo, UserInfoDTO.class);
        } catch (Exception e) {
            logger.error("Failed to parse user info: {}", e.getMessage(), e);
            return null;
        }
    }

    public void cacheUserPermissions(String username, Object permissions, long expiration) {
        try {
            String jsonPermissions = objectMapper.writeValueAsString(permissions);
            redisTemplate.opsForValue().set(buildKey("user:" + username + ":permissions"), jsonPermissions, expiration, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("Failed to serialize user permissions: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize user permissions", e);
        }
    }

    public Object getUserPermissions(String username) {
        String cachedPermissions = redisTemplate.opsForValue().get(buildKey("user:" + username + ":permissions"));
        if (cachedPermissions == null) {
            return null;
        }
        
        try {
            return objectMapper.readValue(cachedPermissions, Object.class);
        } catch (Exception e) {
            logger.error("Failed to parse user permissions: {}", e.getMessage(), e);
            return null;
        }
    }

    public void addTokenToBlacklist(String token, long expiration) {
        redisTemplate.opsForValue().set(buildKey("token:blacklist:" + token), "1", expiration, TimeUnit.SECONDS);
    }

    public boolean isTokenInBlacklist(String token) {
        return redisTemplate.hasKey(buildKey("token:blacklist:" + token));
    }

    public void removeUserInfo(String username) {
        redisTemplate.delete(buildKey("user:" + username));
    }

    public void removeUserPermissions(String username) {
        redisTemplate.delete(buildKey("user:" + username + ":permissions"));
    }

    public void refreshCacheExpiration(String key, long expiration) {
        redisTemplate.expire(buildKey(key), expiration, TimeUnit.SECONDS);
    }

    public void set(String key, Object value, long expiration, TimeUnit timeUnit) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(buildKey(key), jsonValue, expiration, timeUnit);
        } catch (Exception e) {
            logger.error("Failed to serialize value: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize value", e);
        }
    }

    public Object get(String key) {
        String cachedValue = redisTemplate.opsForValue().get(buildKey(key));
        if (cachedValue == null) {
            return null;
        }

        try {
            return objectMapper.readValue(cachedValue, Object.class);
        } catch (Exception e) {
            logger.error("Failed to parse value: {}", e.getMessage(), e);
            return null;
        }
    }

    public <T> T get(String key, Class<T> clazz) {
        String cachedValue = redisTemplate.opsForValue().get(buildKey(key));
        if (cachedValue == null) {
            return null;
        }

        try {
            return objectMapper.readValue(cachedValue, clazz);
        } catch (Exception e) {
            logger.error("Failed to parse value: {}", e.getMessage(), e);
            return null;
        }
    }

    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) {
        String key = buildKey("lock:" + lockKey);
        String lockValue = UUID.randomUUID().toString();
        
        logger.info("尝试获取锁: key={}, lockKey={}, leaseTime={} {}", key, lockKey, leaseTime, timeUnit);
        
        try {
            long waitMillis = timeUnit.toMillis(waitTime);
            long deadline = System.currentTimeMillis() + waitMillis;
            
            while (System.currentTimeMillis() < deadline) {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockValue, leaseTime, timeUnit);
                logger.debug("锁尝试结果: key={}, acquired={}", key, acquired);
                
                if (Boolean.TRUE.equals(acquired)) {
                    LOCK_HOLDER.set(lockValue);
                    logger.info("成功获取锁: key={}, lockKey={}", key, lockKey);
                    return true;
                }
                
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("获取锁被中断: key={}, lockKey={}", key, lockKey);
                    return false;
                }
            }
            
            logger.warn("获取锁超时: key={}, lockKey={}", key, lockKey);
            return false;
        } catch (Exception e) {
            logger.error("获取锁异常: key={}, lockKey={}, error={}", key, lockKey, e.getMessage(), e);
            throw new RuntimeException("获取锁失败: " + lockKey, e);
        }
    }

    public boolean tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        return tryLock(lockKey, 0, leaseTime, timeUnit);
    }

    public boolean unlock(String lockKey) {
        String key = buildKey("lock:" + lockKey);
        String lockValue = LOCK_HOLDER.get();
        if (lockValue == null) {
            return false;
        }
        
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(key), lockValue);
            
            if (RELEASE_SUCCESS.equals(result)) {
                logger.debug("Released lock: {}", lockKey);
                return true;
            }
            return false;
        } finally {
            LOCK_HOLDER.remove();
        }
    }
}