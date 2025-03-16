package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock{

    private StringRedisTemplate redisTemplate;
    private String name;
    public static final String KEY_PREFIX = "lock:";
    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    public static final DefaultRedisScript<Long> REDIS_SCRIPT;
    static {
        REDIS_SCRIPT = new DefaultRedisScript<>();
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        REDIS_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        long id = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, ID_PREFIX + id, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unLock() {
        redisTemplate.execute(
                REDIS_SCRIPT
                , Collections.singletonList(KEY_PREFIX + name)
                , ID_PREFIX + Thread.currentThread().getId());

//        String id = ID_PREFIX + Thread.currentThread().getId();
//        String curId = redisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(id.equals(curId)) {
//            redisTemplate.delete(KEY_PREFIX + name);
//        }
    }
}
