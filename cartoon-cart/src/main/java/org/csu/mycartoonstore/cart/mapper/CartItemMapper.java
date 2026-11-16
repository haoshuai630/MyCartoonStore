package org.csu.mycartoonstore.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
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

    /** 页头徽标：全部条目数量之和，空车返回 0 而不是 null */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM cart_item WHERE user_id = #{userId}")
    int sumQuantityByUser(@Param("userId") Long userId);

    /**
     * 改数量：把归属与版本一起写进 WHERE。
     * <p>影响 0 行说明"不是本人的条目"或"页面已过期"。Service 先查归属：
     * 不是本人与不存在统一 404（不暴露存在性），版本不符给 409。</p>
     */
    @Update("UPDATE cart_item SET quantity = #{quantity}, version = version + 1 "
            + "WHERE id = #{itemId} AND user_id = #{userId} AND version = #{version}")
    int updateQuantity(@Param("userId") Long userId, @Param("itemId") Long itemId,
                       @Param("quantity") int quantity, @Param("version") long version);

    /** 移除：同样带 user_id 与 version，避免越权与旧页面覆盖 */
    @Delete("DELETE FROM cart_item WHERE id = #{itemId} AND user_id = #{userId} AND version = #{version}")
    int deleteByIdAndUser(@Param("userId") Long userId, @Param("itemId") Long itemId,
                          @Param("version") long version);
}
