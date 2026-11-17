package org.csu.mycartoonstore.order.domain; // 确认收货动作的两种结局。

// M08-03：依据 docs/01 FR-O-05。
// 只区分"本次真的完成了"与"之前已经完成"；不可完成的状态由 Service 抛 409，不在这两个值里。
public enum CompleteResult {

    /** 本次把 SHIPPED 迁到 COMPLETED */
    COMPLETED,

    /** 之前已经确认收货：保持首次完成时间，只提示"已处理" */
    ALREADY_COMPLETED
}
