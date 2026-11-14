package org.csu.mycartoonstore.common.money;

import org.csu.mycartoonstore.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyUtilsTest {

    @Test
    @DisplayName("整数和小数都能解析成两位小数")
    void parseValid() {
        assertThat(MoneyUtils.parse("28", "价格")).isEqualByComparingTo(new BigDecimal("28.00"));
        assertThat(MoneyUtils.parse(" 28.5 ", "价格")).isEqualByComparingTo(new BigDecimal("28.50"));
        assertThat(MoneyUtils.parse("28.000", "价格")).isEqualByComparingTo(new BigDecimal("28.00"));
    }

    @Test
    @DisplayName("超过两位小数直接拒绝，不做四舍五入")
    void parseTooPrecise() {
        assertThatThrownBy(() -> MoneyUtils.parse("28.999", "价格"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("两位小数");
    }

    @Test
    @DisplayName("零、负数、非数字都拒绝")
    void parseInvalid() {
        assertThatThrownBy(() -> MoneyUtils.parse("0", "价格")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> MoneyUtils.parse("-1", "价格")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> MoneyUtils.parse("abc", "价格")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> MoneyUtils.parse("  ", "价格")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("行金额 = 单价 × 数量")
    void multiply() {
        assertThat(MoneyUtils.multiply(new BigDecimal("28.00"), 3))
                .isEqualByComparingTo(new BigDecimal("84.00"));
    }

    @Test
    @DisplayName("展示格式固定两位小数")
    void format() {
        assertThat(MoneyUtils.format(new BigDecimal("84"))).isEqualTo("¥84.00");
    }
}
