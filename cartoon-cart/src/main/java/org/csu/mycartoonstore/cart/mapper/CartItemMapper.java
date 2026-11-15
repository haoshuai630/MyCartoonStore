package org.csu.mycartoonstore.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.csu.mycartoonstore.cart.dto.CartItemView;
import org.csu.mycartoonstore.cart.entity.CartItem;

import java.util.List;

/**
 * 购物车 Mapper。
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /** 本人购物车全量（含失效条目），关联商品当前价格与库存 */
    List<CartItemView> selectViewByUser(@Param("userId") Long userId);

    /** 加车前查已有条目，用于累加数量 */
    @Select("SELECT * FROM cart_item WHERE user_id = #{userId} AND product_id = #{productId}")
    CartItem findByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 购物车种类数，用于 20 种上限 */
    @Select("SELECT COUNT(*) FROM cart_item WHERE user_id = #{userId}")
    int countByUser(@Param("userId") Long userId);
}
