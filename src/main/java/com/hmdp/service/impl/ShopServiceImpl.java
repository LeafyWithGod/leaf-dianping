package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IRedisCacheService;
import com.hmdp.service.IShopService;
import com.hmdp.utils.redisUtils.RedisCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.redisUtils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Leaf
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private IRedisCacheService redisCacheService;


    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryShopByid(Long id) {
        Shop shop = redisCacheService.getObject_Mutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop==null)
            return Result.fail("商铺不存在");
        return Result.ok(shop);
    }


    /**
     * 更新商铺信息:先更改数据库再删除缓存
     * @param shop 商铺数据
     * @return 无
     */
    @Override
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id==null)
            return Result.fail("商铺为空");
        //更新商铺
        updateById(shop);
        //删除缓存
        redisCache.deleteObject(CACHE_SHOP_KEY+ id);
        return Result.ok();
    }

//    /**
//     * 根据id查询商铺信息（基于互斥锁实现的缓存穿透问题）
//     * @param id 商铺id
//     * @return 商铺详情数据
//     */
//    public Result queryRedisShopByid(Long id) {
//        Shop shop=null;
//        try {
//            //先去redis里面去查询商铺是否存在
//            while (true){
//                shop = redisCache.getCacheObject(CACHE_SHOP_KEY + id);
//                if (shop!=null&&shop.getId()!=null)
//                    //有就直接返回
//                    return Result.ok(shop);
//                //尝试获取互斥锁
//                boolean trylock = redisCache.trylock(LOCK_SHOP_KEY + id);
//                if (trylock) {
//                    //获取到锁就结束循环
//                    break;
//                }else{
//                    //没获取到就休眠
//                    Thread.sleep(50);
//                }
//            }
//            //没有就去mysql里面查找
//            shop=getById(id);
//            Thread.sleep(200);
//            if (shop==null){
//                /**
//                 * 解决缓存击穿问题
//                 * 当数据库也为空，则向redis存入一个value为空的键值对，这样缓存击穿问题就解决了
//                 */
//                Shop nullShop = new Shop();
//                nullShop.setId(id);
//                redisCache.setCacheObject(CACHE_SHOP_KEY+id,nullShop,CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return Result.fail("商铺不存在");
//            }
//            //存入redis
//            redisCache.setCacheObject(CACHE_SHOP_KEY+id,shop,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            return Result.fail("服务器故障，请稍后重试");
//        } finally {
//            //释放锁
//            redisCache.onlock(LOCK_SHOP_KEY+id);
//        }
//        //返回数据
//        return Result.ok(shop);
//    }


//    /**
//     * 根据id查询商铺信息（基于逻辑过期实现的缓存穿透问题）
//     * @param id 商铺id
//     * @return 商铺详情数据
//     */
//    private static final ExecutorService CACHE_REBUILD = Executors.newFixedThreadPool(10);
//    public Shop queryRedisShopByid(Long id) {
//        RedisData shopData = redisCache.getCacheObject(CACHE_SHOP_KEY + id);

//        //注意！这里第一次进来都为空，此方法不可用
//        Shop shop = (Shop) shopData.getData();
//        if (shopData != null && shop.getId() != null) {
//            //缓存命中检查是否逻辑过期
//            if (shopData.getExpireTime().isAfter(LocalDateTime.now()))
//                //未过期直接返回
//                return shop;
//            //过期进行缓存重建
//            //获取锁
//            boolean trylock = redisCache.trylock(CACHE_SHOP_KEY + id);
//            if (trylock){
//                //获取成功就另开一个线程进行缓存重建
//                try {
//                    CACHE_REBUILD.submit(()->{
//                        RedisData redisData = this.saveRedisData(id, 20L);
//                        redisCache.setCacheObject(CACHE_SHOP_KEY + id,redisData);
//                    });
//                } catch (Exception e) {
//                    e.printStackTrace();
//                } finally {
//                    //释放锁
//                    redisCache.onlock(CACHE_SHOP_KEY+id);
//                }
//            }
//            return shop;
//        }
//        //没有就去mysql里面查找
//        shop = getById(id);
//        RedisData redisData = new RedisData();
//        if (shop == null) {
//            /**
//             * 解决缓存击穿问题
//             * 当数据库也为空，则向redis存入一个value为空的键值对，这样缓存击穿问题就解决了
//             */
//            Shop nullShop = new Shop();
//            nullShop.setId(id);
//            redisData.setData(nullShop);
//            redisData.setExpireTime(LocalDateTime.now().plusSeconds(20L));
//            redisCache.setCacheObject(CACHE_SHOP_KEY + id, redisData, CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return shop;
//        }
//        //存入redis
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(20L));
//        redisCache.setCacheObject(CACHE_SHOP_KEY + id, redisData, CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //返回数据
//        return shop;
//    }
//    /**
//     * 封装redisData
//     * @param id  需要封装的商铺id
//     * @param expireS 逻辑过期保存的id
//     * @return
//     */
//    public RedisData saveRedisData(Long id,Long expireS){
//        Shop shop = getById(id);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireS));
//        return redisData;
//    }

}
