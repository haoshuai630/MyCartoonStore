package org.csu.mycartoonstore.order.dto; // 订单一页：卡片、分页信息与状态角标。

import lombok.Getter;

import java.util.List;
import java.util.Map;

// M05-04：依据 docs/05 §4.6 的页签、卡片与分页。
// 只读：把"当前页查什么、总共有多少"一并交给页面，控制器不再算分页。
@Getter // 字段只读。
public class OrderPage {

    private final List<OrderCard> cards; // 当前页卡片。
    private final int page; // 当前页（1 起）。
    private final int size; // 每页条数。
    private final long total; // 当前筛选条件下的总数。
    private final int totalPages; // 总页数（至少 1）。
    private final Map<String, Long> statusCounts; // ALL 与五种状态的数量。
    private final String status; // 当前状态（ALL 或状态码）。
    private final String orderNo; // 当前搜索的完整订单号（可为空）。

    public OrderPage(List<OrderCard> cards, int page, int size, long total, int totalPages,
                     Map<String, Long> statusCounts, String status, String orderNo) { // 构造器。
        this.cards = List.copyOf(cards); // 不可变，避免页面改到查询结果。
        this.page = page; // 当前页。
        this.size = size; // 每页条数。
        this.total = total; // 总数。
        this.totalPages = totalPages; // 总页数。
        this.statusCounts = Map.copyOf(statusCounts); // 角标数量。
        this.status = status; // 当前状态。
        this.orderNo = orderNo; // 搜索词。
    }

    /** 是否有上一页。 */
    public boolean isHasPrev() {
        return page > 1; // 第一页没有上一页。
    }

    /** 是否有下一页。 */
    public boolean isHasNext() {
        return page < totalPages; // 最后一页没有下一页。
    }
}
