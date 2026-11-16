package org.csu.mycartoonstore.cart.dto;

import lombok.Getter;
import org.csu.mycartoonstore.common.money.MoneyUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车汇总：行 + 件数 + 金额 + 能否结算。
 *
 * <p>三个数容易混，这里写清口径：
 * {@code badgeQuantity} 是页头徽标（全部行数量之和，含失效行）；
 * {@code validQuantity} 是真正可结算的件数；
 * {@code totalAmount} 只累加可结算行的小计。</p>
 */
@Getter
public class CartSummary {

    private final List<CartLine> lines;

    /** 页头徽标：全部条目数量之和，含失效条目（FR-C-01 §7） */
    private final int badgeQuantity;

    /** 可结算件数 */
    private final int validQuantity;

    /** 应付总额 = 可结算行小计之和；无运费、无优惠 */
    private final BigDecimal totalAmount;

    /** 是否所有行都可用；空车视为 true，用 isSettleable() 判空 */
    private final boolean allAvailable;

    private final int invalidCount;

    private CartSummary(List<CartLine> lines, int badgeQuantity, int validQuantity,
                        BigDecimal totalAmount, boolean allAvailable, int invalidCount) {
        this.lines = lines;
        this.badgeQuantity = badgeQuantity;
        this.validQuantity = validQuantity;
        this.totalAmount = totalAmount;
        this.allAvailable = allAvailable;
        this.invalidCount = invalidCount;
    }

    /** 由行列表汇总；金额一次算完，模板不再做乘法 */
    public static CartSummary of(List<CartLine> lines) {
        int badge = 0;
        int valid = 0;
        int invalid = 0;
        BigDecimal total = BigDecimal.ZERO.setScale(MoneyUtils.SCALE);
        for (CartLine line : lines) {
            badge += line.getQuantity();
            if (line.isAvailable()) {
                valid += line.getQuantity();
                total = total.add(line.getLineAmount());
            } else {
                invalid++;
            }
        }
        return new CartSummary(List.copyOf(lines), badge, valid, total, invalid == 0, invalid);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** 车非空且全部可用才允许去结算（FR-C-02） */
    public boolean isSettleable() {
        return !lines.isEmpty() && allAvailable;
    }
}
