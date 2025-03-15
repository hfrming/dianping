package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //解决缓存穿透
        //queryWithPassThrough(id)
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_NULL_TTL, TimeUnit.MINUTES);
        //用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //用逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, LOCK_SHOP_KEY, this::getById, CACHE_NULL_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

//    public Shop queryWithLogicalExpire(Long id) {
//        //查redis该商户信息
//        String redisSring = redisTemplate.opsForValue().get(LOCK_SHOP_KEY + id);
//        //不存在，直接返回
//        if (StrUtil.isBlank(redisSring)) {
//            return null;
//        }
//        //存在，判断是否超时
//        RedisData redisData = JSONUtil.toBean(redisSring, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            return shop;
//        }
//        String key = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(key);
//        if(isLock) {
//            String redisSring1 = redisTemplate.opsForValue().get(LOCK_SHOP_KEY + id);
//            RedisData redisData1 = JSONUtil.toBean(redisSring1, RedisData.class);
//            Shop shop1 = JSONUtil.toBean((JSONObject) redisData1.getData(), Shop.class);
//            LocalDateTime expireTime1 = redisData.getExpireTime();
//            if (expireTime1.isAfter(LocalDateTime.now())) {
//                return shop1;
//            }
//            try {
//                CACHE_REBUILD_EXECUTOR.submit(() -> {
//                    this.saveShop2Redis(id, 300L);
//                });
//            } catch (Exception e) {
//                throw new RuntimeException(e);
//            } finally {
//                unLock(key);
//            }
//        }
//        return shop;
//    }

//    public Shop queryWithMutex(Long id) {
//        Map<Object, Object> map = redisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//        if(map.containsKey("isNull")) {
//            return null;
//        }
//        if(!map.isEmpty()) {
//            return BeanUtil.fillBeanWithMap(map, new Shop(), true);
//        }
//        String key = "lock:shop:" + id;
//        boolean isLock = tryLock(key);
//        try {
//            if(!isLock) {
//                Thread.sleep(50);
//                queryWithMutex(id);
//            }
//            Shop shop = getById(id);
//            if(shop == null) {
//                redisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, "isNull", "");
//                redisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            Map<String, Object> shopMap = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
//                    .setIgnoreNullValue(true)
//                    .setFieldValueEditor((filedKey, filedVal) -> {
//                                if(filedVal == null) {
//                                    return null;
//                                } return filedVal.toString();
//                            }
//                    )
//            );
//            redisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, shopMap);
//            return shop;
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(key);
//        }
//    }

//    public Shop queryWithPassThrough(Long id) {
//        //查redis该商户信息
//        Map<Object, Object> shopMap = redisTemplate.opsForHash().entries(CACHE_SHOP_KEY + id);
//        //存在，直接返回
//        if(shopMap.containsKey("isNull")) {
//            return null;
//        }
//        if (!shopMap.isEmpty()) {
//            return BeanUtil.fillBeanWithMap(shopMap, new Shop(), true);
//        }
//        //不存在，查询数据库
//        Shop shop = getById(id);
//        //不存在商户，返回404
//        if(shop == null) {
//            redisTemplate.opsForHash().put(CACHE_SHOP_KEY + id, "isNull", "");
//            redisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        //存在商户，存入redis，并返回
//        Map<String, Object> map = BeanUtil.beanToMap(shop, new HashMap<>(), CopyOptions.create()
//                .setIgnoreNullValue(true)
//                .setFieldValueEditor((filedKey, filedVal) -> {
//                            if(filedVal == null) {
//                                return null;
//                            } return filedVal.toString();
//                        }
//                )
//        );
//        redisTemplate.opsForHash().putAll(CACHE_SHOP_KEY + id, map);
//        redisTemplate.expire(CACHE_SHOP_KEY + id, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    private boolean tryLock(String key) {
//        Boolean b = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(b);
//    }
//
//    private void unLock(String key) {
//        redisTemplate.delete(key);
//    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisTemplate.opsForValue().set(LOCK_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
    }
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        //修改数据库
        updateById(shop);
        if(shop.getId() == null) {
            return Result.fail("店铺id不能为空!");
        }
        //删除缓存
        redisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
