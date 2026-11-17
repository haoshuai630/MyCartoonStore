package org.csu.mycartoonstore.payment.service; // 验证凭证号格式与查重重试，不碰数据库。

import org.csu.mycartoonstore.common.exception.BizException; // 三次撞号后的异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 断言 500。
import org.csu.mycartoonstore.order.mapper.SalesOrderMapper; // 查重用的 Mapper，这里用 Mock。
import org.junit.jupiter.api.BeforeEach; // 每个用例前重建 Mock。
import org.junit.jupiter.api.DisplayName; // 用例名进入测试报告。
import org.junit.jupiter.api.Test; // JUnit 用例入口。

import java.time.Clock; // 固定时钟让时间戳可断言。
import java.time.Instant; // 固定时刻。
import java.time.ZoneOffset; // 东八区：与页面、数据库时区一致。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.ArgumentMatchers.anyString; // 查重入参不确定，按任意字符串匹配。
import static org.mockito.Mockito.mock; // 造 Mapper Mock。
import static org.mockito.Mockito.times; // 断言调用次数。
import static org.mockito.Mockito.verify; // 断言调用。
import static org.mockito.Mockito.when; // 设定查重结果。

// M06-01：依据 docs/01 FR-P-01 第 3 条与 docs/03 §2.6 的 payment_no 规则。
// 覆盖：PAY 前缀 + 14 位时间戳 + 4 位随机、时间取自注入的时钟、撞号重试、三次失败给 500。
// 不覆盖写库与状态迁移（PaymentServiceTest）。
class PaymentNoGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-11-17T04:34:56Z"); // UTC 04:34:56 = 东八区 12:34:56。

    private SalesOrderMapper orderMapper; // 查重 Mock。
    private PaymentNoGenerator generator; // 被测对象。

    @BeforeEach // 每个用例前重置，避免桩状态互相污染。
    void setUp() {
        orderMapper = mock(SalesOrderMapper.class); // 假 Mapper：只关心 countByPaymentNo。
        generator = new PaymentNoGenerator(orderMapper, Clock.fixed(NOW, ZoneOffset.ofHours(8))); // 时钟固定在 12:34:56。
    }

    /** 正常生成：前缀、时间戳、长度都对，且查重只查一次。 */
    @Test
    @DisplayName("凭证号：PAY + 时间戳 + 4 位随机，共 21 位")
    void nextBuildsPayNoFromClock() {
        // 库里没有同号：第一次查重就能用。
        when(orderMapper.countByPaymentNo(anyString())).thenReturn(0);

        String paymentNo = generator.next(); // 被测动作：生成凭证号。

        // 断言：时间戳来自注入时钟，后 4 位是随机数字，总长 21。
        assertThat(paymentNo).startsWith("PAY20261117123456"); // 前缀 + 14 位时间戳。
        assertThat(paymentNo).hasSize(21); // 3 + 14 + 4。
        assertThat(paymentNo).matches("PAY\\d{18}"); // 时间戳与随机数都是纯数字。
        verify(orderMapper, times(1)).countByPaymentNo(anyString()); // 只有一次查重。
    }

    /** 撞号：第一次命中已有凭证，换一个随机数重试，第二次成功。 */
    @Test
    @DisplayName("撞号：换随机数重试，第二次成功")
    void nextRetriesWhenCandidateTaken() {
        // 第一次查重命中（返回 1），第二次为空。
        when(orderMapper.countByPaymentNo(anyString())).thenReturn(1, 0);

        String paymentNo = generator.next(); // 被测动作：应内部重试一次。

        // 断言：重试后仍拿到合法号，且查重被调用了两次。
        assertThat(paymentNo).startsWith("PAY20261117123456"); // 时间戳不变，随机数重摇。
        verify(orderMapper, times(2)).countByPaymentNo(anyString()); // 一次撞号、一次成功。
    }

    /** 连续 3 次都撞号：不再死循环，给 500 让上层提示稍后重试。 */
    @Test
    @DisplayName("连续三次撞号：500，提示稍后重试")
    void nextGivesUpAfterThreeCollisions() {
        // 无论查哪个号都命中：模拟极端撞号。
        when(orderMapper.countByPaymentNo(anyString())).thenReturn(1);

        // 断言：抛业务异常，错误码 INTERNAL，文案指向可重试。
        assertThatThrownBy(() -> generator.next()) // 连续撞号：必须抛错而不是死循环。
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.INTERNAL); // 生成失败属于系统侧。
                    assertThat(e.getMessage()).contains("支付凭证生成失败"); // 文案可直接展示。
                });
        verify(orderMapper, times(3)).countByPaymentNo(anyString()); // 最多只试 3 次。
    }
}
