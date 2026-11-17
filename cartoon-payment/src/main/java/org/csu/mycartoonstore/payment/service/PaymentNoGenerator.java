package org.csu.mycartoonstore.payment.service; // 支付凭证生成：PAY + 时间戳 + 4 位随机，查重后交付。

import org.csu.mycartoonstore.common.exception.BizException; // 连续撞号时给 500 可重试文案。
import org.csu.mycartoonstore.common.exception.ErrorCode; // INTERNAL 错误码。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 按 payment_no 查重。
import org.springframework.stereotype.Component; // 注册为组件，注入给支付服务。

import java.time.Clock; // 时间戳取统一时钟，测试可固定。
import java.time.LocalDateTime; // 生成 yyyyMMddHHmmss。
import java.time.format.DateTimeFormatter; // 固定 14 位时间格式。
import java.util.concurrent.ThreadLocalRandom; // 4 位随机数，不用共享 Random 实例。

// M06-01：依据 docs/01 FR-P-01 第 3 条与 docs/03 §2.6 的 payment_no 注释。
// 只负责"生成一个库里当前不存在的凭证号"；写库由 PaymentService 在同一事务里完成。
// 不保证跨进程唯一：数据库 uk_order_payment_no 是最后防线，撞上时事务回滚重来。
@Component // 交给 Spring 管理，支付服务构造器自动注入。
public class PaymentNoGenerator {

    /** 凭证前缀：与订单号的 CO 区分，人工核对时一眼能看出是支付凭证。 */
    private static final String PREFIX = "PAY";

    /** 时间戳格式：到秒，共 14 位，总长 3 + 14 + 4 = 21，落在 VARCHAR(40) 内。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 最多重试 3 次：4 位随机撞车概率极低，连续 3 次命中视为异常而不是继续死循环。 */
    private static final int MAX_RETRY = 3;

    /** 随机数上界：10000 取 0000～9999，正好补足 4 位。 */
    private static final int RANDOM_BOUND = 10_000;

    private final SalesOrderMapper orderMapper; // 查重用：只读 count，不写库。
    private final Clock clock; // 时间来源，测试可固定。

    public PaymentNoGenerator(SalesOrderMapper orderMapper, Clock clock) { // 构造器注入两个协作方。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.clock = clock; // 保存时钟。
    }

    /**
     * 生成一个当前库中不存在的支付凭证号。
     *
     * <p>先查重再返回，不依赖"插入失败再重试"：查到 0 条才算数，
     * 生成与写入之间仍可能被别人抢先，唯一键会兜住这种极端情况。</p>
     */
    public String next() {
        for (int i = 0; i < MAX_RETRY; i++) { // 最多试 3 次。
            String candidate = PREFIX + TS.format(LocalDateTime.now(clock))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(RANDOM_BOUND)); // 时间戳 + 4 位随机。
            if (orderMapper.countByPaymentNo(candidate) == 0) { // 库里没有才用。
                return candidate; // 拿到就返回，调用方负责写入。
            }
        }
        throw new BizException(ErrorCode.INTERNAL, "支付凭证生成失败，请稍后重试"); // 3 次都撞：交给上层 500。
    }
}
