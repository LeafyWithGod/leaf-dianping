package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Leaf
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result queryShopByid(Long id);

    Result updateShop(Shop shop);
}
