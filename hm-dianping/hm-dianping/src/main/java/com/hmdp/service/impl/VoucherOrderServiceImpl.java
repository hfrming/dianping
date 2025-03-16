package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService iSeckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询有没有该优惠券
        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        //优惠券开始时间
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("时间未开始");
        }
        //优惠券结束时间
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("时间已结束");
        }
        //创建锁对象
        SimpleRedisLock redisLock = new SimpleRedisLock(redisTemplate ,"order");
        //获取锁
        if (!redisLock.tryLock(5L)) {
            return Result.fail("一个人只能下一单！");
        }
        //释放锁
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.create(voucherId, seckillVoucher);
        } finally {
            redisLock.unLock();
        }
    }

    @Transactional
    public Result create(Long voucherId, SeckillVoucher seckillVoucher) {
        //判断是否是同一人抢了多长券
        int count = query()
                .eq("voucher_id", voucherId)
                .eq("user_id", UserHolder.getUser().getId())
                .count();
        if(count > 0) {
            return Result.fail("你已经抢过了");
        }
        //优惠券数量
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("优惠券已抢完");
        }
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("优惠券已抢完");
        }
        //增加优惠券订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        //返回订单id
        return Result.ok(voucherOrder.getId());
    }
}
