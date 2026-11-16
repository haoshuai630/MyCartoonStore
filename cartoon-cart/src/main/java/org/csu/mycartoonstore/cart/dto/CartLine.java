package org.csu.mycartoonstore.cart.dto;

import lombok.Getter;
import org.csu.mycartoonstore.common.money.MoneyUtils;

import java.math.BigDecimal;

/**
 * 购物车行：条目 + 商品当前信息 + 可用性判定。
 *
 * <p>可用性在查询时计算，不落库：商品下架或数量超过当前库存时标记为失效，
 * 失效行仍要显示（用户要看到原因并自己决定移除），但不参与结算金额。</p>
 */
@Getter
public class CartLine {

    /** cart_item.id，改数量与移除用它定位 */
    private final Long itemId;

    /** 商品 id，用于跳详情 */
    private final Long productId;

    private final String productName;
    private final String coverImage;
    private final String categoryName;

    /** 商品当前单价，不存购物车 */
    private final BigDecimal price;

    private final int quantity;

    /** 商品当前库存，用于判断不足与提示"仅剩 n 件" */
    private final int stock;

    /** 商品当前状态：ON_SALE / DRAFT */
    private final String productStatus;

    /** 读页面时的版本号，改数量/移除必须原样带回 */
    private final long version;

    /**
     * 单个条目是否可结算。
     * <p>下架与库存不足都算失效：前者后端已不允许下单，后者下单会超卖。</p>
     */
    private final boolean available;

    /** 失效原因，可结算时为空 */
    private final String unavailableReason;

    /** 行小计 = 当前单价 × 数量，两位小数 */
    private final BigDecimal lineAmount;

    private CartLine(Long itemId, Long productId, String productName, String coverImage, String categoryName,
                     BigDecimal price, int quantity, int stock, String productStatus, long version,
                     boolean available, String unavailableReason, BigDecimal lineAmount) {
        this.itemId = itemId;
        this.productId = productId;
        this.productName = productName;
        this.coverImage = coverImage;
        this.categoryName = categoryName;
        this.price = price;
        this.quantity = quantity;
        this.stock = stock;
        this.productStatus = productStatus;
        this.version = version;
        this.available = available;
        this.unavailableReason = unavailableReason;
        this.lineAmount = lineAmount;
    }

    /**
     * 由查询结果构造：失效原因与行小计都在这里一次算好，模板只做展示。
     */
    public static CartLine from(CartItemView view) {
        boolean onSale = "ON_SALE".equals(view.getProductStatus());
        boolean enough = view.getStock() != null && view.getQuantity() <= view.getStock();
        String reason = null;
        if (!onSale) {
            reason = "已下架";
        } else if (!enough) {
            reason = "库存不足，仅剩 " + (view.getStock() == null ? 0 : view.getStock()) + " 件";
        }
        BigDecimal amount = MoneyUtils.multiply(view.getPrice(), view.getQuantity());
        return new CartLine(view.getId(), view.getProductId(), view.getProductName(), view.getCoverImage(),
                view.getCategoryName(), view.getPrice(), view.getQuantity(),
                view.getStock() == null ? 0 : view.getStock(), view.getProductStatus(), view.getVersion(),
                onSale && enough, reason, amount);
    }

    /** 失效行在页面上的样式挂钩 */
    public boolean isInvalid() {
        return !available;
    }
}
