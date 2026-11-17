package org.csu.mycartoonstore.order.service; // 确认收货：SHIPPED → COMPLETED，保留首次完成时间。

import org.csu.mycartoonstore.common.exception.BizException; // 404/409。
import org.csu.mycartoonstore.order.domain.CompleteResult; // 两种结局。
import org.csu.mycartoonstore.order.domain.OrderStatus; // 状态枚举。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 行锁与写回。
import org.springframework.stereotype.Service; // 业务服务。
import org.springframework.transaction.annotation.Transactional; // 同一事务。

import java.time.Clock; // 完成时间来源，测试可固定。
import java.time.LocalDateTime; // completed_at 类型。

// M08-03：依据 docs/01 FR-O-05、docs/02 §7.5、docs/06 AC-34。
// 负责：把本人已发货的订单迁到 COMPLETED 并写首次 completed_at；
// 不做：发货（M07-08）、库存与金额（确认收货都不碰）、页面渲染（OrderController）。
// 这是教学状态确认，不代表物流签收（docs/01 FR-O-05 的说明），因此不校验物流信息。
@Service // 业务服务：用户端控制器只调它。
public class OrderCompletionService {

    /** 唯一可确认收货的起点 */
    private static final String SHIPPED = OrderStatus.SHIPPED.name(); // 已发货。

    /** 已完成：重复提交不再改时间 */
    private static final String COMPLETED = OrderStatus.COMPLETED.name(); // 已完成。

    private final SalesOrderMapper orderMapper; // 锁订单行与写回状态。
    private final Clock clock; // 完成时间来源。

    public OrderCompletionService(SalesOrderMapper orderMapper, Clock clock) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.clock = clock; // 保存统一时钟。
    }

    /**
     * 确认收货：先锁订单行，再按状态决定迁移、幂等或拒绝。
     *
     * <p>并发下两次提交，第二个事务看到 COMPLETED 走幂等分支，不会把首次完成时间覆盖成更晚的时间；
     * 归属不匹配与订单不存在统一 404，不泄露订单是否存在（AC-18 的同一口径）。</p>
     */
    @Transactional // 锁行、判状态、写回同一事务。
    public CompleteResult complete(long userId, String orderNo) {
        SalesOrder order = orderMapper.lockByOrderNo(orderNo); // FOR UPDATE：同一订单串行。
        if (order == null || order.getUserId() != userId) { // 不存在与不是本人同一处理。
            throw BizException.notFound("订单不存在"); // 404。
        }
        if (COMPLETED.equals(order.getStatus())) { // 已经确认过。
            return CompleteResult.ALREADY_COMPLETED; // 提示已处理，保持首次时间。
        }
        if (!SHIPPED.equals(order.getStatus())) { // 待支付 / 待发货 / 已取消都不能确认。
            throw BizException.conflict("当前状态不可确认收货"); // 409。
        }
        order.setStatus(COMPLETED); // 迁移到已完成。
        order.setCompletedAt(LocalDateTime.now(clock)); // 首次完成时间。
        orderMapper.updateById(order); // 只写非空字段：金额、支付与发货信息不动。
        return CompleteResult.COMPLETED; // 本次真的完成了。
    }
}
