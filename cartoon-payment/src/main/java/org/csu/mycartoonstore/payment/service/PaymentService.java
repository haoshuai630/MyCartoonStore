package org.csu.mycartoonstore.payment.service; // 模拟支付：把待支付订单迁到 PAID 并写入凭证，失败只提示不写库。

import org.csu.mycartoonstore.common.exception.BizException; // 越权 404、状态冲突 409。
import org.csu.mycartoonstore.order.entity.SalesOrder; // 订单主表：状态与支付三件套都在这里。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 锁订单行、写回支付信息。
import org.csu.mycartoonstore.payment.domain.PaymentResult; // 三种结局：成功、已支付、失败。
import org.springframework.stereotype.Service; // 业务服务，Controller 只调它。
import org.springframework.transaction.annotation.Transactional; // 状态迁移与凭证写入同一事务。

import java.time.Clock; // 支付时间来源，测试可固定。
import java.time.LocalDateTime; // paid_at 的类型。

// M06-01：依据 docs/01 FR-P-01、docs/02 §7.4、docs/06 AC-20 与 AC-24。
// 只负责"把一笔待支付订单标记为已支付（或明确失败）"：锁订单行、校验归属与状态、写三件套。
// 不扣库存、不删购物车、不改明细：库存在下单时已经扣过，再扣一次就是事故。
// 不做页面与跳转（M06-02 的 Controller 负责）。
@Service // 业务服务：Controller 只调它，不直接碰 Mapper。
public class PaymentService {

    /** 唯一可被支付的起点：只有待支付能变成已支付。 */
    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";

    /** 已支付：重复支付走幂等分支，保留首次凭证与时间。 */
    private static final String PAID = "PAID";

    /** 已取消：明确不可支付。 */
    private static final String CANCELLED = "CANCELLED";

    /** 支付方式：教学模拟，固定值，数据库 CHECK 也只接受这个值。 */
    private static final String MOCK = "MOCK";

    private final SalesOrderMapper orderMapper; // 锁订单行与写回支付三件套。
    private final PaymentNoGenerator paymentNoGenerator; // 生成唯一凭证号。
    private final Clock clock; // paid_at 的时间来源。

    public PaymentService(SalesOrderMapper orderMapper,
                          PaymentNoGenerator paymentNoGenerator,
                          Clock clock) {
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.paymentNoGenerator = paymentNoGenerator; // 保存凭证生成器。
        this.clock = clock; // 保存时钟。
    }

    /**
     * 模拟支付成功：待支付 → PAID，写入 MOCK、凭证号与首次支付时间。
     *
     * <p>先锁订单行再判状态：并发支付同一单时，第二个事务看到 PAID 走幂等分支，
     * 不会重新生成凭证；已取消或已发货则一律 409。</p>
     *
     * @return {@link PaymentResult#PAID} 本次迁移成功；{@link PaymentResult#ALREADY_PAID} 之前已支付
     */
    @Transactional // 锁行、判状态、写三件套必须同一事务，否则锁一释放状态就可能变。
    public PaymentResult paySuccess(long userId, String orderNo) {
        SalesOrder order = lockOwned(orderNo, userId); // 行锁 + 归属：并发支付/取消在这里排队。
        if (PAID.equals(order.getStatus())) { // 重复提交：不重新生成凭证、不改支付时间。
            return PaymentResult.ALREADY_PAID; // 已付过：交给页面跳详情，凭证保持原样。
        }
        if (!PENDING_PAYMENT.equals(order.getStatus())) { // 已取消 / 已发货 / 已完成都不能支付。
            throw BizException.conflict("当前状态不可支付"); // 只剩这三种会走到这里。
        }

        order.setStatus(PAID); // 支付成功即"待发货"，发货留给 M07。
        order.setPaymentMethod(MOCK); // 教学模拟，固定 MOCK。
        order.setPaymentNo(paymentNoGenerator.next()); // 服务端生成，页面不参与。
        order.setPaidAt(LocalDateTime.now(clock)); // 首次支付时间，取统一时钟。
        orderMapper.updateById(order); // 三件套同一 UPDATE 写入，CHECK 不会看到半套。
        return PaymentResult.PAID; // 本次真的支付成功。
    }

    /**
     * 模拟支付失败：不写库、不生成凭证，只校验这次操作是否允许。
     *
     * <p>失败是页面上的一次显式动作：订单保持待支付，用户可以重试或取消。
     * 对已支付的订单再点失败不改变任何字段，返回"已支付"让页面改跳详情。</p>
     *
     * @return {@link PaymentResult#FAILED} 仍是待支付；{@link PaymentResult#ALREADY_PAID} 已经付过
     */
    @Transactional // 只读也要拿行锁：与支付/取消串行，避免读到中间态。
    public PaymentResult payFailure(long userId, String orderNo) {
        SalesOrder order = lockOwned(orderNo, userId); // 同一个归属与锁口径。
        if (CANCELLED.equals(order.getStatus())) { // 取消后连"失败"都不该被接受。
            throw BizException.conflict("当前状态不可支付"); // 已取消：失败动作也不再受理。
        }
        if (!PENDING_PAYMENT.equals(order.getStatus())) { // PAID / SHIPPED / COMPLETED：已经付过。
            return PaymentResult.ALREADY_PAID; // 已经付过：失败动作改为跳详情。
        }
        return PaymentResult.FAILED; // 待支付：什么都不写，页面提示可重试。
    }

    /**
     * 锁订单行并校验归属：不存在与不是本人都给 404，不泄露订单是否存在。
     */
    private SalesOrder lockOwned(String orderNo, long userId) {
        SalesOrder order = orderMapper.lockByOrderNo(orderNo); // FOR UPDATE：同一订单的支付/取消串行。
        if (order == null || order.getUserId() != userId) { // 他人订单与不存在同码同文案。
            throw BizException.notFound("订单不存在");
        }
        return order; // 返回已锁定的本人订单。
    }
}
