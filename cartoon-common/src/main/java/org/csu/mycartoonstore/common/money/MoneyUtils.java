package org.csu.mycartoonstore.common.money;

import org.csu.mycartoonstore.common.exception.BizException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额工具：统一两位小数、拒绝超精度输入、拒绝非正数。
 *
 * <p>规则来自 BR-12：金额一律 {@link BigDecimal} + DECIMAL(12,2)，
 * 不做静默四舍五入；超出精度的输入直接拒绝。</p>
 */
public final class MoneyUtils {

    public static final int SCALE = 2;

    private MoneyUtils() {
    }

    /**
     * 解析用户输入的金额。
     *
     * @param raw       原始字符串
     * @param fieldName 字段中文名，用于错误提示
     * @return 两位小数的正数
     */
    public static BigDecimal parse(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest(fieldName + "不能为空");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest(fieldName + "格式不正确");
        }
        if (value.signum() <= 0) {
            throw BizException.badRequest(fieldName + "必须大于 0");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw BizException.badRequest(fieldName + "最多两位小数");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** 行金额 = 单价 × 数量，结果为两位小数。 */
    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** 展示用格式：¥28.00。 */
    public static String format(BigDecimal amount) {
        return "¥" + amount.setScale(SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }
}
