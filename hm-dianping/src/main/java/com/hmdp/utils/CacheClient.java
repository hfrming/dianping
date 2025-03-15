package com.hmdp.utils;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate redisTemplate;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void set(Object obj, String key) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(obj));
    }

    public void set(Object obj, String key, Long expireTime, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(obj), expireTime, timeUnit);
    }

    public void setWithLogicalExpire(Object obj, String key, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(obj);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //redis击穿问题
    public <T, ID> T queryWithPassThrough(
            String prefix, ID id, Class<T> type,Function<ID, T> function, Long expireTime, TimeUnit timeUnit) {
        //查redis该商户信息
        String obj = redisTemplate.opsForValue().get(prefix + id);
        //存在，直接返回
        if(StrUtil.isNotBlank(obj)) {
            return JSONUtil.toBean(obj, type);
        }
        if (obj != null) {
            return null;
        }
        //不存在，查询数据库
        T apply = function.apply(id);
        //不存在商户，返回404
        if(apply == null) {
            redisTemplate.opsForValue().set(prefix + id, "", expireTime, timeUnit);
            return null;
        }
        //存在商户，存入redis，并返回
        this.set(apply, prefix + id, expireTime, timeUnit);
        return apply;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T, ID> T queryWithLogicalExpire(
            String prefix, ID id, Class<T> type, String lockPrefix, Function<ID, T> function, Long expireTime, TimeUnit timeUnit) {
        String key = prefix + id;
        //查redis该商户信息
        String redisSring = redisTemplate.opsForValue().get(key);
        //不存在，直接返回
        if (StrUtil.isBlank(redisSring)) {
            return null;
        }
        //存在，判断是否超时
        RedisData redisData = JSONUtil.toBean(redisSring, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expire = redisData.getExpireTime();
        if (expire.isAfter(LocalDateTime.now())) {
            return t;
        }
        String lockKey = lockPrefix + id;
        boolean isLock = tryLock(lockKey);
        if(isLock) {
            String redisSring1 = redisTemplate.opsForValue().get(key);
            RedisData redisData1 = JSONUtil.toBean(redisSring1, RedisData.class);
            T t1 = JSONUtil.toBean((JSONObject) redisData1.getData(), type);
            LocalDateTime expireTime1 = redisData.getExpireTime();
            if (expireTime1.isAfter(LocalDateTime.now())) {
                return t1;
            }
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    T apply = function.apply(id);
                    this.setWithLogicalExpire(apply, lockKey, expireTime, timeUnit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }
        }
        return t;
    }

    private boolean tryLock(String key) {
        Boolean b = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void unLock(String key) {
        redisTemplate.delete(key);
    }
}
