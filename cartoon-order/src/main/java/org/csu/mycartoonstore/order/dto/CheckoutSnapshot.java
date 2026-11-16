package org.csu.mycartoonstore.order.dto; // 结算确认快照：记住"确认时看到什么"，提交时用来判断车是否变了。

import lombok.Getter; // 生成 getter，省掉一堆只读方法的样板代码。

import java.io.Serial; // 标记序列化相关成员，配合 serialVersionUID 使用。
import java.io.Serializable; // 快照要放进 Servlet Session，必须可序列化。
import java.math.BigDecimal; // 金额一律 BigDecimal，不用 double。
import java.time.Instant; // 记录确认时刻，用于 10 分钟时限。
import java.util.ArrayList; // 复制一份再排序，不改调用方的列表。
import java.util.List; // 行列表类型。
import java.util.Objects; // 生成 hashCode 时组合多个字段。

// M05-01：依据 docs/01 FR-O-01、docs/02 §7.2。
// 只做"确认凭据"：不锁价、不占库存、不参与金额计算；放 Session 所以要可序列化。
// 行按 cartItemId 升序保存，提交时两边同序比对；确认时刻用于 10 分钟时限。
@Getter // 所有字段只读：快照生成后不允许再改。
public class CheckoutSnapshot implements Serializable {

    @Serial // 序列化相关字段的标记。
    private static final long serialVersionUID = 1L; // 会话序列化版本号：以后改字段时递增。

    /** 快照归属用户；提交时与当前会话比对，防止拿别人的快照下单 */
    private final long userId;

    /** 确认时车里的全部条目，按 cartItemId 升序 */
    private final List<Line> lines;

    /** 确认时页面显示的应付总额 */
    private final BigDecimal totalAmount;

    /** 确认时刻：超过 10 分钟未提交就要重新确认 */
    private final Instant confirmedAt;

    /** 构造时排序并冻结：快照生成后不再变化。 */
    public CheckoutSnapshot(long userId, List<Line> lines, BigDecimal totalAmount, Instant confirmedAt) {
        this.userId = userId; // 记下这是谁的车。
        // 复制后排序：不改调用方的列表，且保证提交时两边同序。
        List<Line> sorted = new ArrayList<>(lines);
        sorted.sort((a, b) -> Long.compare(a.getCartItemId(), b.getCartItemId())); // 按条目 id 升序。
        // 冻结行列表：会话里的快照不能被后续请求改掉。
        this.lines = List.copyOf(sorted);
        this.totalAmount = totalAmount; // 总额只作显示与比对，不参与下单金额计算。
        this.confirmedAt = confirmedAt; // 记录确认时刻，提交时判时限。
    }

    /** 快照里的一行：核对版本与价格用，不信任页面回传的任何金额。 */
    @Getter // 行也只读：给出 getter 即可。
    public static class Line implements Serializable {

        @Serial // 序列化相关字段的标记。
        private static final long serialVersionUID = 1L; // 行对象的序列化版本号。

        /** cart_item.id：删除已结算条目时按它定位 */
        private final long cartItemId;

        /** 商品 id：与当前车行比对是否同一件商品 */
        private final long productId;

        /** 确认时的数量：改过就要重新确认 */
        private final int quantity;

        /** 确认时的购物车版本：同数量改两次也能被发现 */
        private final long cartVersion;

        /** 确认时的单价：后台改价后不能按它成交，只用来发现变化 */
        private final BigDecimal price;

        /** 一行快照：字段全部来自服务端查询结果。 */
        public Line(long cartItemId, long productId, int quantity, long cartVersion, BigDecimal price) {
            this.cartItemId = cartItemId; // 条目定位用。
            this.productId = productId; // 商品身份用。
            this.quantity = quantity; // 数量快照。
            this.cartVersion = cartVersion; // 版本快照。
            this.price = price; // 单价快照，仅用于比对。
        }

        /** 与当前车行逐项比对：任一项不同都算变了。 */
        public boolean sameAs(Line other) {
            // 价格按数值比：28.0 与 28.00 是同一个价，不能卡在刻度上。
            return other != null
                    && cartItemId == other.cartItemId // 同一行：条目 id 必须一致。
                    && productId == other.productId // 同一件商品：不能偷偷换货。
                    && quantity == other.quantity // 数量没被改。
                    && cartVersion == other.cartVersion // 版本没被改（同数量改两次也能发现）。
                    && price.compareTo(other.price) == 0; // 价格按数值相等。
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true; // 同一个引用直接相等。
            }
            if (!(o instanceof Line other)) {
                return false; // 类型不对就不相等。
            }
            // equals 与 sameAs 同口径，避免测试与集合里出现两种"相等"。
            return sameAs(other);
        }

        @Override
        public int hashCode() {
            // 价格先去掉尾零，保证与 equals 的数值口径一致。
            return Objects.hash(cartItemId, productId, quantity, cartVersion, price.stripTrailingZeros());
        }
    }
}
