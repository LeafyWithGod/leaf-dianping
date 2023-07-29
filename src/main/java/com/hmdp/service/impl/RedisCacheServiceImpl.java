package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.service.IRedisCacheService;
import com.hmdp.utils.redisUtils.RedisCache;
import com.hmdp.utils.redisUtils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.redisUtils.RedisConstants.*;

/**
 * 直接操作数据库类
 *
 * - 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
 * - 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓
 * 存击穿问题
 * - 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
 * - 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
 */

@Service
public class RedisCacheServiceImpl implements IRedisCacheService {

    @Autowired
    private RedisCache redisCache;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将任意Java对象存储在string类型的key中，并且可以设置TTL过期时间
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        if (key==null||value==null)
            return;
        if (time==null||unit==null)
            redisCache.setCacheObject(key,value);
        if (time!=null&&unit!=null)
            redisCache.setCacheObject(key,value,time,unit);
    }

    /**
     * 将任意Java对象存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        redisCache.setCacheObject(key,value,time,unit);
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     * @param keyPrefix   前缀key
     * @param id          id
     * @param type        查询类型字节码
     * @param dbFallback  查询类方法
     * @param time        缓存存入时间
     * @param unit        时间颗粒度
     * @param <R>         返回类型
     * @param <ID>        id类型
     * @return
     */
    public <R,ID> R getObject_null(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key=keyPrefix+id;
        Object cacheObject = redisCache.getCacheObject(key);
        if (cacheObject!=null&&cacheObject.equals("null"))
            //如果为空值直接返回null
            return null;
        if (cacheObject!=null)
            //如果有值转化返回
            return BeanUtil.copyProperties(cacheObject,type);
        //如果为空,查询数据库
        R apply = dbFallback.apply(id);
        if (apply==null){
            //如果数据库不存在存入空值,2分钟过期
            redisCache.setCacheObject(key,"null",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //如果数据库存在则直接存入redis
        redisCache.setCacheObject(key,apply,time,unit);
        return apply;
    }

    /**
     * 查询-先去reidis里面查询没有再去mysql
     * 基于互斥锁实现缓存击穿(使用缓存空值解决缓存穿透)
     * @param keyPrefix   前缀key
     * @param id          id
     * @param type        查询类型字节码
     * @param dbFallback  查询类方法
     * @param time        缓存存入时间
     * @param unit        时间颗粒度
     * @param <R>         返回类型
     * @param <ID>        id类型
     * @return
     */
    public <R,ID> R getObject_Mutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;
        R apply = null;
        try {
            while (true) {
                Object cacheObject = redisCache.getCacheObject(key);
                //判断缓存是否命中
                if (cacheObject != null && cacheObject.equals("null"))
                    //如果为空值直接返回null
                    return null;
                if (cacheObject != null)
                    //如果有值转化返回
                    return BeanUtil.copyProperties(cacheObject, type);
                boolean trylock = redisCache.trylock(lockKey,LOCK_SHOP_TTL);
                if (trylock)
                    break;
                Thread.sleep(50);
            }
            //获取锁成功，查询数据库
            apply = dbFallback.apply(id);
            Thread.sleep(200);
            if (apply == null) {
                //如果数据库不存在存入空值,2分钟过期
                redisCache.setCacheObject(key, "null", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库存在写入redis
            redisCache.setCacheObject(key, apply, time, unit);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放锁
            redisCache.onlock(lockKey);
        }
        return apply;
    }

    /**
     * 查询-先去reidis里面查询没有再去mysql
     *基于逻辑过期实现缓存击穿(使用缓存空值解决缓存穿透)
     * @param keyPrefix   前缀key
     * @param id          id
     * @param type        查询类型字节码
     * @param dbFallback  查询类方法
     * @param time        缓存存入时间
     * @param unit        时间颗粒度
     * @param <R>         返回类型
     * @param <ID>        id类型
     * @return
     */
    public <R,ID> R getObject_Expire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit){
        String key = keyPrefix + id;
        String lockKey = LOCK_SHOP_KEY + id;
        Object cacheObject = redisCache.getCacheObject(key);
        if (cacheObject==null||cacheObject.equals("null"))
            //无值直接返回null
         return null;
        //有值判断是否过期
        RedisData redisData = (RedisData) cacheObject;
        R data = BeanUtil.copyProperties(redisData.getData(), type);
        if (redisData.getExpireTime().isAfter(LocalDateTime.now()))
            //未过期直接返回
            return data;
        //过期开始获取锁
        boolean trylock = redisCache.trylock(lockKey,LOCK_SHOP_TTL);
        if (!trylock)
            //未获取到锁直接返回
            return data;
        //获取到锁开启独立线程重建缓存
        CACHE_REBUILD_EXECUTOR.submit(()->{
            try {
                //获取数据库内容
                R apply = dbFallback.apply(id);
                //写入redis
                redisCache.setCacheObject(key, apply, time, unit);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //释放锁
                redisCache.onlock(lockKey);
            }
        });
        return data;
    }


}
