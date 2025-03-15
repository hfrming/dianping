package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1735689600L;
    private static final long COUNT_BYTES = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String keyPrefix) {
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long l = nowSecond - BEGIN_TIMESTAMP;
        String format = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long r = redisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + format);
        return l << COUNT_BYTES | r;
    }
}
