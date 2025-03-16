package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        String key = "SHOP_TYPE";
        //查询缓存
        String s = redisTemplate.opsForValue().get(key);
        //存在TypeList则返回
        if(!StrUtil.isBlank(s)) {
            List<ShopType> list = JSONUtil.toList(s, ShopType.class);
            return Result.ok(list);
        }
        //不存在则查询数据库
        List<ShopType> list = query().orderByAsc("sort").list();
        //为空则返回失败
        if(list.isEmpty()) {
            return Result.fail("查询失败!");
        }
        //查询结果存入redis
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list));
        //返回结果
        return Result.ok(list);
    }
}
