package io.github.zmxckj.flexiadmin.service.impl;

import io.github.zmxckj.flexiadmin.entity.Appid;
import io.github.zmxckj.flexiadmin.mapper.AppidMapper;
import io.github.zmxckj.flexiadmin.security.SecurityUtils;
import io.github.zmxckj.flexiadmin.service.AppidService;
import io.github.zmxckj.flexiadmin.utils.RedisUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class AppidServiceImpl implements AppidService {

    @Autowired
    private AppidMapper appidMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Override
    public Appid findByAppId(String appId, Long tenantId) {
        String key = "appid:" + appId + ":" + (tenantId != null ? tenantId : "0");
        Appid appid = redisUtils.get(key, Appid.class);
        if (appid != null) {
            return appid;
        }

        appid = appidMapper.selectByAppId(appId, tenantId);
        if (appid != null) {
            redisUtils.set(key, appid, 3600, TimeUnit.SECONDS);
        }

        return appid;
    }
}
