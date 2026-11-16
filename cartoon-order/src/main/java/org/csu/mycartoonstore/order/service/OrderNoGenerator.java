package org.csu.mycartoonstore.order.service; // 订单号生成：CO + 时间戳 + 4 位随机数，查重重试。

import org.csu.mycartoonstore.common.exception.BizException; // 三次都撞号时给 500 可重试文案。
import org.csu.mycartoonstore.common.exception.ErrorCode; // INTERNAL 错误码。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 查订单号是否已存在。
import org.springframework.stereotype.Component; // 注册为组件，注入给下单服务。

import java.time.Clock; // 时间戳取统一时钟，测试可控。
import java.time.LocalDateTime; // 生成 yyyyMMddHHmmss。
import java.time.format.DateTimeFormatter; // 固定 14 位时间格式。
import java.util.concurrent.ThreadLocalRandom; // 4 位随机数，不共享 Random 实例。

// M05-03：依据 docs/03 §3「CO + yyyyMMddHHmmss + 4 位随机数字」。
// 只负责生成不重复的订单号；插入由 OrderService 在事务里完成。
@Component // 交给 Spring 管理。
public class OrderNoGenerator {

    /** 订单号前缀，与 docs/03 的示例一致。 */
    private static final String PREFIX = "CO";

    /** 时间戳格式：到秒，共 14 位。 */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 最多重试 3 次：随机号撞车概率极低，连续 3 次失败视为异常。 */
    private static final int MAX_RETRY = 3;

    private final SalesOrderMapper orderMapper; // 查重用。
    private final Clock clock; // 时间来源，测试可固定。

    public OrderNoGenerator(SalesOrderMapper orderMapper, Clock clock) { // 构造器注入。
        this.orderMapper = orderMapper; // 保存订单 Mapper。
        this.clock = clock; // 保存时钟。
    }

    /**
     * 生成一个当前库中不存在的订单号。
     *
     * <p>先查重再返回，不依赖"插入失败再重试"；数据库唯一索引仍作为最后防线。</p>
     */
    public String next() {
        for (int i = 0; i < MAX_RETRY; i++) { // 最多试 3 次。
            String candidate = PREFIX + TS.format(LocalDateTime.now(clock))
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10_000)); // 时间戳 + 4 位随机。
            if (orderMapper.countByOrderNo(candidate) == 0) { // 库里没有才用。
                return candidate; // 拿到就返回。
            }
        }
        throw new BizException(ErrorCode.INTERNAL, "订单号生成失败，请稍后重试"); // 3 次都撞：交给上层 500。
    }
}
