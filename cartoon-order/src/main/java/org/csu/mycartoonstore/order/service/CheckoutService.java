package org.csu.mycartoonstore.order.service; // 结算确认：把确认页看到的内容固化成快照并复核。

import org.csu.mycartoonstore.cart.dto.CartLine; // 取行的条目 id、商品 id、数量、版本、单价。
import org.csu.mycartoonstore.cart.dto.CartSummary; // 汇总提供失效标记、件数与总额。
import org.csu.mycartoonstore.cart.service.CartService; // 读车只走购物车模块的公开服务，不碰它的 Mapper。
import org.csu.mycartoonstore.common.exception.BizException; // 空车与失效条目用业务异常转成 4xx。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 返回给会话保存的快照类型。
import org.springframework.stereotype.Service; // 注册为业务服务，由 Spring 注入控制器。

import java.time.Clock; // 取当前时刻：测试可注入固定时钟，避免依赖真实时间。
import java.time.Duration; // 表示 10 分钟确认时限。
import java.util.ArrayList; // 按行数预分配，避免循环里反复扩容。
import java.util.List; // 快照行列表。

// M05-01：依据 docs/01 FR-O-01、docs/02 §7.2 与 docs/tasks/M05-任务清单.md。
// 负责把"确认页看到的内容"固化成快照，并在提交前判断车/价是否变过、确认是否超时。
// 不写库、不锁价、不占库存、不生成订单——下单事务属于 M05-03。
@Service // 业务服务：控制器只调它，不自己算金额。
public class CheckoutService {

    /** 确认时限：从打开确认页起 10 分钟内必须提交订单，超时重新确认 */
    public static final Duration CONFIRM_TTL = Duration.ofMinutes(10);

    private final CartService cartService; // 读车入口：价格、库存、失效都以它返回的当前值为准。
    private final Clock clock; // 取确认时刻与提交时刻；测试注入固定时钟。

    public CheckoutService(CartService cartService, Clock clock) { // 构造器注入，测试里可替换成 Mock 与固定时钟。
        this.cartService = cartService; // 保存购物车服务，后续 prepare/unchanged 都用它。
        this.clock = clock; // 保存时钟，确认与超时判断都用同一个来源。
    }

    /** 生成结算快照：空车与失效条目都直接拒绝。 */
    public CheckoutSnapshot prepare(long userId) {
        // 快照只认数据库当前值：名称、价格、库存都可能刚被改过。
        CartSummary summary = cartService.summary(userId);
        // 空车没有可确认的内容，直接拒绝而不是渲染一张空确认页。
        if (summary.isEmpty()) {
            throw BizException.badRequest("购物车为空，请先选购商品");
        }
        // 任一失效条目都不能整车结算（FR-C-02）：先回购物车处理。
        if (!summary.isAllAvailable()) {
            throw BizException.conflict("有失效条目，请先移除或更换后再结算");
        }
        return snapshotOf(userId, summary); // 把当前车固化成快照，供提交时比对。
    }

    /**
     * 确认是否已超过 10 分钟。
     * <p>没有快照、不是本人的快照都按"已过期"处理，宁可让用户重新确认。</p>
     */
    public boolean expired(CheckoutSnapshot snapshot) {
        // 没有快照说明会话丢了或从未确认过，不能提交。
        if (snapshot == null) {
            return true;
        }
        // 恰好第 10 分钟算过期：时限是"10 分钟内"，不含边界。
        return !clock.instant().isBefore(snapshot.getConfirmedAt().plus(CONFIRM_TTL));
    }

    /** 提交前复核：条目、数量、版本、价格逐项一致且未超时才返回 true。 */
    public boolean unchanged(long userId, CheckoutSnapshot snapshot) {
        // 没有快照、或快照不是本人的：一律按已变化处理。
        if (snapshot == null || snapshot.getUserId() != userId) {
            return false;
        }
        // 超时的确认不能再用：即使内容没变，也要求重新确认。
        if (expired(snapshot)) {
            return false;
        }
        // 复核必须重新读库：会话里的快照只用来比，不能拿它当金额。
        CartSummary current = cartService.summary(userId);
        // 当前车失效了同样算"已变化"，让用户回去处理而不是带着坏行下单。
        if (!current.isAllAvailable()) {
            return false;
        }

        // 用同一套转换规则生成当前快照，两边才处在同一口径上。
        CheckoutSnapshot now = snapshotOf(userId, current);
        List<CheckoutSnapshot.Line> before = snapshot.getLines(); // 确认时的行（已按条目 id 排序）。
        List<CheckoutSnapshot.Line> after = now.getLines(); // 当前车的行（同样排序）。
        // 条目数不同：有人加车或删车。
        if (before.size() != after.size()) {
            return false;
        }
        // 两边都按条目 id 排过序，可以逐位置比较。
        for (int i = 0; i < before.size(); i++) {
            // 同一位置上只要商品、数量、版本、价格有一项不同，就算车变了。
            if (!before.get(i).sameAs(after.get(i))) {
                return false;
            }
        }
        // 总额也要比：行价都一致但总额不同，说明快照本身被改过。
        return snapshot.getTotalAmount().compareTo(now.getTotalAmount()) == 0;
    }

    /** 把当前车转成快照：单价取商品当前价，不取任何页面回传值。 */
    private CheckoutSnapshot snapshotOf(long userId, CartSummary summary) {
        List<CheckoutSnapshot.Line> lines = new ArrayList<>(summary.getLines().size()); // 按车行数预分配。
        // 只搬服务端算好的值：单价、数量、版本都来自购物车查询结果。
        for (CartLine line : summary.getLines()) {
            lines.add(new CheckoutSnapshot.Line(
                    line.getItemId(), // 条目 id：提交时按它比对与删除。
                    line.getProductId(), // 商品 id：防止同一行被换成别的商品。
                    line.getQuantity(), // 数量：改过就要重新确认。
                    line.getVersion(), // 购物车版本：同数量改两次也能被发现。
                    line.getPrice())); // 当前单价；金额一律由服务端计算。
        }
        return new CheckoutSnapshot(userId, lines, summary.getTotalAmount(), clock.instant()); // 记下确认时刻。
    }
}
