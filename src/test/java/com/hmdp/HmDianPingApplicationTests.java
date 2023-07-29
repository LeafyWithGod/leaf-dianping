package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.User;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.SnowflakeIdWorker;
import com.hmdp.utils.redisUtils.RedisCache;
import com.hmdp.utils.redisUtils.RedisIdWorker;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.hmdp.utils.redisUtils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    private static final ExecutorService en = Executors.newFixedThreadPool(500);

    @Test
    public void setRedis(){
        redisCache.setCacheObject(CACHE_SHOP_KEY+1,"null");
    }


    @SneakyThrows
    @Test
    void testIdWorker(){
        SnowflakeIdWorker sfi = new SnowflakeIdWorker(1, 2);
        CountDownLatch cd = new CountDownLatch(300);
        Runnable task=()->{
            for (int i=0;i<100;i++){
                long order = sfi.nextId();
                System.out.println(order);
            }
            cd.countDown();
        };
        long l = System.currentTimeMillis();
        System.out.println();
        for (int i = 0; i < 300; i++) {
            en.submit(task);
        }
        cd.await();
        long l2 = System.currentTimeMillis();
        System.err.println(l2-l);
    }


    @Test
    public void getRedis(){
        Object test = redisCache.getCacheObject("test");
        System.out.println(test!=null);
        System.out.println(test);
    }

    @Test
    public void ifBeancopy(){
        User user1 = userService.getById(1);
        System.err.println("1 "+user1);
        redisCache.setCacheObject("user",user1);
        Object user2 = redisCache.getCacheObject("user");
        System.err.println("redis "+user2);
        User user = BeanUtil.copyProperties(user2, User.class);
        System.err.println("copy "+user);
    }

}
