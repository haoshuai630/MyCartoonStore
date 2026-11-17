package org.csu.mycartoonstore.order.domain; // 订单状态：五种状态与中文标签的唯一来源。

import org.csu.mycartoonstore.common.exception.BizException; // 非法状态转 400。

// M05-04：依据 docs/03 §2.6 的 status 注释与 docs/05 §4.6 的状态页签。
// 只做"状态 ↔ 标签"的映射与校验；状态迁移由 OrderService/M06/M07 负责。
public enum OrderStatus {

    PENDING_PAYMENT("待支付"), // 下单后、支付前。
    PAID("待发货"), // 模拟支付成功。
    SHIPPED("已发货"), // 管理端发货（P1）。
    COMPLETED("已完成"), // 确认收货（P1）。
    CANCELLED("已取消"); // 取消的待支付订单。

    private final String label; // 页面显示的中文。

    OrderStatus(String label) { // 每个状态自带标签。
        this.label = label; // 保存标签。
    }

    public String getLabel() { // 模板取标签。
        return label; // 返回中文。
    }

    /**
     * 把查询参数转成状态；ALL 或空表示不过滤，非法值给 400。
     */
    public static OrderStatus ofFilter(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equals(raw)) { // 默认与"全部"。
            return null; // null 表示不过滤。
        }
        for (OrderStatus status : values()) { // 只认枚举里的值。
            if (status.name().equals(raw)) { // 精确匹配。
                return status; // 命中即返回。
            }
        }
        throw BizException.badRequest("订单状态不合法"); // 其他值：400。
    }

    /** 根据库里的字符串取标签；未知值原样显示，避免页面空白。 */
    public static String labelOf(String code) {
        for (OrderStatus status : values()) { // 遍历五种状态。
            if (status.name().equals(code)) { // 命中。
                return status.getLabel(); // 返回中文。
            }
        }
        return code; // 未知值原样显示，便于发现问题。
    }
}
