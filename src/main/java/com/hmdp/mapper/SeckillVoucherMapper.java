package com.hmdp.mapper;

import com.hmdp.entity.SeckillVoucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 Mapper 接口
 * </p>
 *
 * @author Leaf
 * @since 2022-01-04
 */
public interface SeckillVoucherMapper extends BaseMapper<SeckillVoucher> {
    boolean inventoryUpdate(@Param("voucherId") Long voucherId);
}