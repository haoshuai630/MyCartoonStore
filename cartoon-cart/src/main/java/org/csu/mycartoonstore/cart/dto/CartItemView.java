package org.csu.mycartoonstore.cart.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 购物车行视图：条目本身 + 商品当前名称、封面、价格、库存、状态。
 * <p>价格不存购物车，每次查询取商品当前值。</p>
 */
@Getter
@Setter
public class CartItemView {

    private Long id;
    private Long productId;
    private Integer quantity;
    private Long version;
    private String productName;
    private String coverImage;
    private BigDecimal price;
    private Integer stock;
    private String productStatus;
    private String categoryName;
}
