package org.csu.mycartoonstore.order.domain; // 发货动作的三种结局。

// M07-08：依据 docs/01 FR-A-06、docs/02 §7.6。
// 只区分"本次真的发货了"与"之前已经发过"；不可发货的状态由 Service 抛 409，不在这两个值里。
public enum ShipResult {

    /** 本次把 PAID 迁到 SHIPPED */
    SHIPPED,

    /** 之前已经发过货：保持首次发货时间，只提示"已处理" */
    ALREADY_SHIPPED
}
