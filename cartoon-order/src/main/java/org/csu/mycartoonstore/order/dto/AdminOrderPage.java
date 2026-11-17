package org.csu.mycartoonstore.order.dto; // 后台订单一页：全站订单 + 分页信息 + 回显的筛选条件。

import lombok.Getter; // 生成 getter。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。

import java.util.List; // 当前页。

// M07-08：依据 docs/01 FR-A-05、docs/05 §4.9。
// 与用户端的 OrderPage 分开：这里没有"状态角标"，但要能回显全站的订单号/状态筛选。
@Getter // 只读视图对象。
public class AdminOrderPage {

    /** 当前页订单（按创建时间倒序） */
    private final List<SalesOrder> orders;

    /** 当前页码 */
    private final int page;

    /** 每页数量 */
    private final int pageSize;

    /** 符合条件的总数 */
    private final long total;

    /** 总页数 */
    private final int totalPages;

    /** 回显的状态筛选值：ALL 或五种状态 */
    private final String status;

    /** 回显的订单号（可空） */
    private final String orderNo;

    /** 组装一页。 */
    public AdminOrderPage(List<SalesOrder> orders, int page, int pageSize,
                          long total, int totalPages, String status, String orderNo) {
        this.orders = orders; // 当前页数据。
        this.page = page; // 页码。
        this.pageSize = pageSize; // 每页数量。
        this.total = total; // 总数。
        this.totalPages = totalPages; // 总页数。
        this.status = status; // 状态筛选回显。
        this.orderNo = orderNo; // 单号筛选回显。
    }

    /** 是否还有上一页 */
    public boolean isHasPrevious() {
        return page > 1; // 第一页没有上一页。
    }

    /** 是否还有下一页 */
    public boolean isHasNext() {
        return page < totalPages; // 最后一页没有下一页。
    }

    /** 当前页是否为空 */
    public boolean isEmpty() {
        return orders == null || orders.isEmpty(); // 空结果给空状态。
    }
}
