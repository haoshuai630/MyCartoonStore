package org.csu.mycartoonstore.order.dto; // 订单列表的一张卡片：主表摘要 + 首件商品缩略图。

import lombok.Getter;
import org.csu.mycartoonstore.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// M05-04：依据 docs/05 §4.6 的卡片（订单号、时间、状态、缩略图、总额、查看）。
// 只读展示：不参与状态迁移，也不含收货地址（列表不展示隐私）。
@Getter // 全部字段只读。
public class OrderCard {

    private final String orderNo; // 完整订单号。
    private final String status; // 数据库里的状态码。
    private final String statusLabel; // 状态中文标签。
    private final BigDecimal totalAmount; // 订单总额。
    private final LocalDateTime createdAt; // 创建时间。
    private final String firstProductName; // 首件商品名（快照）。
    private final String firstCover; // 首件封面（快照）。
    private final int itemCount; // 明细种类数。
    private final int totalQuantity; // 商品总件数。

    public OrderCard(String orderNo, String status, BigDecimal totalAmount, LocalDateTime createdAt,
                     String firstProductName, String firstCover, int itemCount, int totalQuantity) { // 构造器。
        this.orderNo = orderNo; // 订单号。
        this.status = status; // 状态码。
        this.statusLabel = OrderStatus.labelOf(status); // 状态中文。
        this.totalAmount = totalAmount; // 总额。
        this.createdAt = createdAt; // 创建时间。
        this.firstProductName = firstProductName; // 首件商品名。
        this.firstCover = firstCover; // 首件封面。
        this.itemCount = itemCount; // 明细种类数。
        this.totalQuantity = totalQuantity; // 总件数。
    }
}
