package org.csu.mycartoonstore.order.service; // 订单查询：本人订单详情与明细，不做状态迁移。

import com.baomidou.mybatisplus.core.toolkit.Wrappers; // 按 order_id 查明细。
import org.csu.mycartoonstore.common.exception.BizException; // 不存在与他人订单一律 404。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表实体。
import org.csu.mycartoonstore.order.entity.SalesOrderItem; // 订单明细实体。
import org.csu.mycartoonstore.order.mapper.SalesOrderItemMapper; // 查明细。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 按订单号查主表。
import org.springframework.stereotype.Service; // 业务服务。

import java.util.List; // 明细列表。

// M05-03：下单成功后需要落到一个真实页面，这里先提供最小只读详情。
// 只做"按订单号查本人订单与明细"；列表、筛选、分页与更完整的详情在 M05-04。
@Service // 业务服务：控制器只调它。
public class OrderQueryService {

    private final SalesOrderMapper orderMapper; // 主表查询。
    private final SalesOrderItemMapper itemMapper; // 明细查询。

    public OrderQueryService(SalesOrderMapper orderMapper, SalesOrderItemMapper itemMapper) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.itemMapper = itemMapper; // 保存明细 Mapper。
    }

    /**
     * 按订单号查本人订单：不存在、不是本人都统一 404，不泄露订单是否存在。
     */
    public SalesOrder detail(long userId, String orderNo) {
        SalesOrder order = orderMapper.findByOrderNo(orderNo); // 订单号全局唯一。
        if (order == null || order.getUserId() != userId) { // 不存在 / 他人订单。
            throw BizException.notFound("订单不存在"); // 同码同文案，不暴露存在性。
        }
        return order; // 返回本人订单。
    }

    /** 订单明细：按 id 升序，保持下单时的行顺序。 */
    public List<SalesOrderItem> items(long orderId) {
        return itemMapper.selectList(Wrappers.<SalesOrderItem>lambdaQuery()
                .eq(SalesOrderItem::getOrderId, orderId) // 只查该订单。
                .orderByAsc(SalesOrderItem::getId)); // 按写入顺序展示。
    }
}
