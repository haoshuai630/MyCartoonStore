package org.csu.mycartoonstore.order.dto; // 下单结果：订单主表 + 本次写入的明细。

import lombok.Getter;
import org.csu.mycartoonstore.order.entity.SalesOrder;
import org.csu.mycartoonstore.order.entity.SalesOrderItem;

import java.util.List;

// M05-03：下单事务的返回值，Controller 只用它拼跳转与页面数据。
// 只读快照：事务提交后调用方不再修改订单与明细。
@Getter // 两个字段都只读。
public class PlacedOrder {

    private final SalesOrder order; // 已写入的订单（含 orderNo、总额、状态）。
    private final List<SalesOrderItem> items; // 已写入的明细（含快照与行金额）。

    public PlacedOrder(SalesOrder order, List<SalesOrderItem> items) { // 构造器。
        this.order = order; // 订单主表。
        this.items = List.copyOf(items); // 不可变列表，避免调用方改动。
    }
}
