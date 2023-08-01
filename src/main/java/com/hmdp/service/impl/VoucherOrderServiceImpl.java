package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.SnowflakeIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.redisUtils.RedisConstants.LOCK_KEY;
import static com.hmdp.utils.redisUtils.RedisConstants.VOUCHER_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Leaf
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Value("${SnowflakeIdWorkerSetting.worker-id}")
    private Integer workerId;
    @Value("${SnowflakeIdWorkerSetting.datacenter-id}")
    private Integer datacenterId;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate redisTemplate;

    private SnowflakeIdWorker idWorker;

    //加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        try {
            //初始化脚本就在resources下面的unlock.lua
            SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
            //设置返回值类型
            SECKILL_SCRIPT.setResultType(Long.class);
        } catch (Exception e) {
            log.error("加载lua脚本seckill失败");
            e.printStackTrace();
        }
    }

    @PostConstruct
    public void init() {
        log.info("------------------workerId-"+workerId+"--datacenterId-"+datacenterId+"------------------");
        try {
            idWorker = new SnowflakeIdWorker(workerId, datacenterId);
            seckill_order_executor.submit(new VoucherOrderHandler());
        } catch (Exception e) {
            log.error("初始化SnowflakeIdWorker失败");
            e.printStackTrace();
        }
    }

    private BlockingQueue<VoucherOrder> orders=new ArrayBlockingQueue<>(1024*1024);
    private static final ExecutorService seckill_order_executor = Executors.newSingleThreadExecutor();
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    //获取队列信息
                    VoucherOrder voucherOrder = orders.take();
                    //创建订单
                    handerVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("订单异常",e);
                }

            }
        }
    }

    IVoucherOrderService proxy;
    private void handerVoucherOrder(VoucherOrder voucherOrder){
        // 获取用户
        Long userId = voucherOrder.getUserId();
        String lockKey = LOCK_KEY + ":" + VOUCHER_KEY + userId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean trylock =lock.tryLock();
        if (!trylock)
            return;
        //获取自身代理对象(事务)，否则直接调方法事务无法生效
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }
    /**
     * 提取方法，方便加锁
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //查询用户是否已经抢过该优惠券(一人一单)
        int isRt = voucherOrderMapper.selectUserIdRepetition(voucherOrder.getUserId(), voucherOrder.getVoucherId());

        if (isRt > 0) {
            return;
        }
        //扣减库存
        //乐观锁解决库存超卖的问题
        boolean success = seckillVoucherService.inventoryUpdate(voucherOrder.getVoucherId());
        if (!success) {
            return;
        }
        //添加订单
        save(voucherOrder);
    }

    /**
     * 抢购优惠券(使用lua脚本)
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        // 执行lua脚本
        Long result = (Long) redisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                Long.toString(voucherId), Long.toString(userId)
        );
        // 判断结果为0(有购买资格)，1(库存不足)，2(购买过)

        if (result != 0L){
            return Result.fail(result==1?"库存不足":"您已抢购过这个商品");
        }
        // 为0就创建下单信息保存到阻塞队列
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = idWorker.nextId();
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        // 保存到阻塞队列
        orders.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 返回订单id
        return Result.ok(orderId);
    }



//    /**
//     * 抢购优惠券(手动判断执行)
//     *
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        Long userId = UserHolder.getUser().getId();
//        if (voucher == null)
//            return Result.fail("优惠券不存在");
//        //判断是否在秒杀时间
//        LocalDateTime now = LocalDateTime.now();
//        if (now.isBefore(voucher.getBeginTime()) && now.isAfter(voucher.getEndTime()))
//            //在开始时间之前或结束时间之后就返回错误信息
//            return Result.fail("不在抢购时间之内");
//        //判断库存是否够
//        if (voucher.getStock() < 1)
//            //库存不够报错
//            return Result.fail("库存不足");
//        //使用用户id为锁，这样相同id就会受阻，不同id就不会收到影响
//        //intern去常量池查找是否有一样的字符串
//        //synchronized (userId.toString().intern()) //synchronized锁对集群不起作用，分布式锁对集群有效
//        String lockKey = LOCK_KEY + ":" + VOUCHER_KEY + userId;
//        RLock lock = redissonClient.getLock(lockKey);
//        boolean trylock =lock.tryLock();
//        if (!trylock)
//            return Result.fail("系统繁忙，请稍后重试");
//        //获取自身代理对象(事务)，否则直接调方法事务无法生效
//        IVoucherOrderService proxy;
//        try {
//            proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId, userId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }


    /**
     * 提取方法，方便加锁
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        //查询用户是否已经抢过该优惠券(一人一单)
        int isRt = voucherOrderMapper.selectUserIdRepetition(userId, voucherId);

        if (isRt > 0) {
            return Result.fail("您已抢过该优惠券");
        }
        //扣减库存
        //乐观锁解决库存超卖的问题
        boolean success = seckillVoucherService.inventoryUpdate(voucherId);
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = idWorker.nextId();
        voucherOrder.setId(orderId);
        //用户id
        voucherOrder.setUserId(userId);
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //添加订单
        save(voucherOrder);
        //返回
        return Result.ok(orderId);

    }


}
