package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.redisUtils.RedisIdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Leaf
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Value("${SnowflakeIdWorkerSetting.worker-id}")
    private Integer workerId;
    @Value("${SnowflakeIdWorkerSetting.datacenter-id}")
    private Integer datacenterId;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 抢购优惠券
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher==null)
            return Result.fail("优惠券不存在");
        //判断是否在秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())&& now.isAfter(voucher.getEndTime()))
            //在开始时间之前或结束时间之后就返回错误信息
            return Result.fail("不在抢购时间之内");
        //判断库存是否够
        if (voucher.getStock()<1)
            //库存不够报错
            return Result.fail("库存不足");
        //扣减库存
        //乐观锁解决库存超卖的问题
        boolean success=seckillVoucherService.inventoryUpdate(voucherId);
        System.err.println("---------------------------------------"+success+","+voucher.getStock()+"---------------------------------------");
        if (!success){
            Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
//        SnowflakeIdWorker idWorker = new SnowflakeIdWorker(workerId,datacenterId);
        //订单id
        long orderId = redisIdWorker.nextId("voucher");
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //添加订单
        save(voucherOrder);
        //返回
        return Result.ok(orderId);
    }



}
