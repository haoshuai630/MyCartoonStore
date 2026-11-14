package org.csu.mycartoonstore.cart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * cart_item：按用户持久化的购物车条目。
 * <p>不保存价格、不占库存；同用户同商品只有一条，重复加入累加数量。
 * version 用于数量修改的旧页面检测。</p>
 */
@Getter
@Setter
@TableName("cart_item")
public class CartItem {

    /** id BIGINT AUTO_INCREMENT */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** user_id，所属用户 */
    private Long userId;

    /** product_id，商品 */
    private Long productId;

    /** quantity INT，1–99 */
    private Integer quantity;

    /** version BIGINT，数量修改时 +1 */
    private Long version;

    /** created_at */
    private LocalDateTime createdAt;

    /** updated_at */
    private LocalDateTime updatedAt;
}
