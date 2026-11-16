package org.csu.mycartoonstore.order.service; // 验证结算快照与提交前复核，不碰数据库。

import org.csu.mycartoonstore.cart.dto.CartItemView; // 用真实视图构造购物车行，不另造 DTO。
import org.csu.mycartoonstore.cart.dto.CartLine; // 复用购物车模块的行转换。
import org.csu.mycartoonstore.cart.dto.CartSummary; // 汇总提供失效标记与总额。
import org.csu.mycartoonstore.cart.service.CartService; // 被测服务的协作方，这里用 Mock。
import org.csu.mycartoonstore.common.exception.BizException; // 校验失败抛的业务异常。
import org.csu.mycartoonstore.common.exception.ErrorCode; // 断言 400 / 409 用。
import org.csu.mycartoonstore.order.dto.CheckoutSnapshot; // 被测快照类型。
import org.csu.mycartoonstore.order.dto.Receiver; // 收货校验的返回值。
import org.csu.mycartoonstore.order.dto.ReceiverForm; // 收货字段校验入口。
import org.junit.jupiter.api.BeforeEach; // 每个用例前重建 Mock。
import org.junit.jupiter.api.DisplayName; // 用例名进入测试报告。
import org.junit.jupiter.api.Test; // JUnit 用例入口。

import java.io.ByteArrayInputStream; // 反序列化来源。
import java.io.ByteArrayOutputStream; // 序列化目标，模拟 Session。
import java.io.ObjectInputStream; // 读回快照。
import java.io.ObjectOutputStream; // 写出快照。
import java.math.BigDecimal; // 价格与金额断言。
import java.time.Clock; // 固定时钟：确认时刻与超时判断都可复现。
import java.time.Instant; // 表示确认时刻。
import java.time.ZoneOffset; // 固定时区，避免本地时区影响断言。
import java.util.List; // 空车用 List.of()。

import static org.assertj.core.api.Assertions.assertThat; // 值断言。
import static org.assertj.core.api.Assertions.assertThatThrownBy; // 异常断言。
import static org.mockito.Mockito.mock; // 造 Mock 的车服务。
import static org.mockito.Mockito.when; // 设定车的内容。

// M05-01：依据 docs/01 FR-O-01 与 docs/02 §7.2；服务层部分。
// 覆盖快照内容、空车/失效拦截、五种车变化、他人快照、序列化与收货字段。
// 不覆盖 Controller 与下单事务（M05-02 / M05-03）。
class CheckoutServiceTest {

    private static final long USER = 1L; // 固定用 1 号用户，所有用例同一身份。
    private static final Instant CONFIRMED_AT = Instant.parse("2026-11-16T10:00:00Z"); // 固定确认时刻。

    private CartService cartService; // 购物车服务 Mock：只提供 summary。
    private CheckoutService service; // 被测对象。

    @BeforeEach // 每个用例前重置，避免 Mock 状态互相污染。
    void setUp() {
        cartService = mock(CartService.class); // 造一个假的车服务。
        service = new CheckoutService(cartService, Clock.fixed(CONFIRMED_AT, ZoneOffset.UTC)); // 时钟固定在 10:00。
    }

    /** 五型之外的第一条：快照按条目 id 升序，金额取购物车汇总。 */
    @Test
    @DisplayName("快照按条目 id 升序，金额取购物车汇总")
    void prepareBuildsSnapshotInItemOrder() {
        // 故意先给条目 20 再给 10：验证快照会按条目 id 重新排序。
        when(cartService.summary(USER)).thenReturn(summaryOf(
                view(20L, 9L, 1, "30.00", 10, "ON_SALE", 0L), // 条目 20：数量 1、¥30.00。
                view(10L, 8L, 2, "28.00", 10, "ON_SALE", 3L))); // 条目 10：数量 2、¥28.00、版本 3。

        CheckoutSnapshot snapshot = service.prepare(USER); // 被测动作：生成结算快照。

        // 断言：行按 id 升序（10、20），单价与购物车版本原样带入，总额为两行之和。
        assertThat(snapshot.getUserId()).isEqualTo(USER); // 快照归当前用户。
        assertThat(snapshot.getLines()).extracting(CheckoutSnapshot.Line::getCartItemId)
                .containsExactly(10L, 20L); // 条目标识按 id 升序。
        assertThat(snapshot.getLines().get(0).getPrice()).isEqualByComparingTo("28.00"); // 单价原样。
        assertThat(snapshot.getLines().get(0).getCartVersion()).isEqualTo(3L); // 版本原样。
        assertThat(snapshot.getTotalAmount()).isEqualByComparingTo("86.00"); // 56.00 + 30.00。
    }

    /** 空车没有可确认的内容：400，提示先选购。 */
    @Test
    @DisplayName("空车：400，提示先选购")
    void prepareRejectsEmptyCart() {
        // 一行都没有：没有可确认的内容。
        when(cartService.summary(USER)).thenReturn(CartSummary.of(List.of()));

        // 断言 400 与"购物车为空"文案。
        assertThatThrownBy(() -> service.prepare(USER))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST); // 参数/状态问题给 400。
                    assertThat(e.getMessage()).contains("购物车为空"); // 文案指向可操作的下一步。
                });
    }

    /** 有失效条目：409，先处理再结算。 */
    @Test
    @DisplayName("有失效条目：409，先处理再结算")
    void prepareRejectsInvalidLine() {
        // 第二行库存 0：数量 3 超过库存，属失效条目。
        when(cartService.summary(USER)).thenReturn(summaryOf(
                view(10L, 8L, 2, "28.00", 10, "ON_SALE", 0L), // 正常行。
                view(20L, 9L, 3, "30.00", 0, "ON_SALE", 0L))); // 库存 0 的失效行。

        // 断言 409：整车不可结算，提示先处理失效条目。
        assertThatThrownBy(() -> service.prepare(USER))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT); // 与 M04 的"失效不可结算"一致。
                    assertThat(e.getMessage()).contains("失效条目"); // 让用户知道回去处理什么。
                });
    }

    /** 车没变：复核通过。 */
    @Test
    @DisplayName("车没变：复核通过")
    void unchangedWhenNothingChanged() {
        // 车里一行：数量 2、¥28.00、上架。
        CartSummary summary = summaryOf(view(10L, 8L, 2, "28.00", 10, "ON_SALE", 0L));
        when(cartService.summary(USER)).thenReturn(summary); // 之后每次读都是这辆车。

        CheckoutSnapshot snapshot = service.prepare(USER); // 先按这辆车生成快照。

        // 再读一次仍是同一辆车：复核通过。
        assertThat(service.unchanged(USER, snapshot)).isTrue();
    }

    /** 另一个标签页改数量：复核失败，要求重新确认。 */
    @Test
    @DisplayName("数量被改：复核失败")
    void changedWhenQuantityDiffers() {
        // 快照基于数量 2。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 2, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER); // 记下确认时的样子。

        // 另一个标签页把数量 2 改成 3，购物车版本随之 +1。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 3, "28.00", 10, "ON_SALE", 1L)));

        // 复核失败：不能按旧确认下单。
        assertThat(service.unchanged(USER, snapshot)).isFalse();
    }

    /** 后台改价：复核失败，不静默按新价成交。 */
    @Test
    @DisplayName("价格被改：复核失败，不静默按新价成交")
    void changedWhenPriceDiffers() {
        // 快照基于 ¥28.00。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER); // 记下确认价。

        // 后台在确认之后把价格 28 改成 29：不能按快照价成交。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "29.00", 10, "ON_SALE", 0L)));

        // 复核失败：提示重新确认。
        assertThat(service.unchanged(USER, snapshot)).isFalse();
    }

    /** 条目被删或被加：复核失败。 */
    @Test
    @DisplayName("条目被删或被加：复核失败")
    void changedWhenItemSetDiffers() {
        // 快照基于两行。
        when(cartService.summary(USER)).thenReturn(summaryOf(
                view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L), // 第一行。
                view(20L, 9L, 1, "30.00", 10, "ON_SALE", 0L))); // 第二行。
        CheckoutSnapshot snapshot = service.prepare(USER); // 两行的快照。

        // 删掉一行：条目数变少。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        assertThat(service.unchanged(USER, snapshot)).isFalse(); // 条目不齐，必须重新确认。

        // 再加一行：条目数变多。
        when(cartService.summary(USER)).thenReturn(summaryOf(
                view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L), // 原第一行。
                view(20L, 9L, 1, "30.00", 10, "ON_SALE", 0L), // 原第二行。
                view(30L, 7L, 1, "19.00", 10, "ON_SALE", 0L))); // 新加的一行。
        assertThat(service.unchanged(USER, snapshot)).isFalse(); // 新增条目也算变化。
    }

    /** 确认后商品下架：复核失败。 */
    @Test
    @DisplayName("确认后商品失效：复核失败")
    void changedWhenCartBecomesInvalid() {
        // 快照基于上架商品。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER); // 生成快照。

        // 商品被下架：DRAFT 状态让整辆车不可结算。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "DRAFT", 0L)));

        // 复核失败。
        assertThat(service.unchanged(USER, snapshot)).isFalse();
    }

    /** 别人的快照不能用：复核失败。 */
    @Test
    @DisplayName("别人的快照不能用：复核失败")
    void snapshotOfOtherUserIsRejected() {
        // 先给 USER 生成一份合法快照。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER); // 快照归属 USER。

        // 快照属于 USER：换个 userId 复核、或根本没有快照，都必须拒绝。
        assertThat(service.unchanged(USER + 1, snapshot)).isFalse(); // 不是本人的快照。
        assertThat(service.unchanged(USER, null)).isFalse(); // 没有快照。
    }

    /** 快照可序列化：放进 Session 再取出来仍能复核。 */
    @Test
    @DisplayName("快照可序列化：放进 Session 再取出来仍能复核")
    void snapshotSurvivesSerialization() throws Exception {
        // 快照两行：条目 10 数量 2、条目 20 数量 1。
        when(cartService.summary(USER)).thenReturn(summaryOf(
                view(10L, 8L, 2, "28.00", 10, "ON_SALE", 1L), // 第一行。
                view(20L, 9L, 1, "30.00", 10, "ON_SALE", 0L))); // 第二行。
        CheckoutSnapshot snapshot = service.prepare(USER); // 生成快照。

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); // 序列化目标。
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { // 自动关闭流。
            out.writeObject(snapshot); // 模拟写进 Session。
        }
        CheckoutSnapshot restored; // 反序列化结果。
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { // 从字节读回。
            restored = (CheckoutSnapshot) in.readObject(); // 模拟从 Session 取出。
        }

        // 断言：行数不变、行序不变、仍能通过复核。
        assertThat(restored.getLines()).hasSize(2); // 两行都还在。
        assertThat(restored.getLines()).extracting(CheckoutSnapshot.Line::getCartItemId)
                .containsExactly(10L, 20L); // 行序保持。
        assertThat(restored.getConfirmedAt()).isEqualTo(CONFIRMED_AT); // 确认时刻也随快照保存。
        assertThat(service.unchanged(USER, restored)).isTrue(); // 取出的快照仍可用来复核。
    }

    /** 快照记住确认时刻：提交时据此判断是否超过 10 分钟。 */
    @Test
    @DisplayName("快照记住确认时刻")
    void snapshotRemembersConfirmationTime() {
        // 固定时钟下生成快照。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER); // 确认时刻取时钟。

        assertThat(snapshot.getConfirmedAt()).isEqualTo(CONFIRMED_AT); // 与注入的固定时钟一致。
    }

    /** 确认时限：10 分钟内有效，正好第 10 分钟算过期。 */
    @Test
    @DisplayName("确认时限：10 分钟内有效，边界算过期")
    void expiredAtTenMinuteBoundary() {
        // 先按 10:00 生成快照。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER);

        // 第 599 秒：还在 10 分钟内。
        assertThat(at(CONFIRMED_AT.plusSeconds(599)).expired(snapshot)).isFalse();
        // 正好第 600 秒：边界算过期，避免"卡点"提交。
        assertThat(at(CONFIRMED_AT.plusSeconds(600)).expired(snapshot)).isTrue();
        // 第 601 秒：已过期。
        assertThat(at(CONFIRMED_AT.plusSeconds(601)).expired(snapshot)).isTrue();
    }

    /** 超时的快照即使内容没变也不能下单。 */
    @Test
    @DisplayName("超时后复核失败：必须重新确认")
    void unchangedRejectsExpiredSnapshot() {
        // 10:00 生成快照。
        when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 1, "28.00", 10, "ON_SALE", 0L)));
        CheckoutSnapshot snapshot = service.prepare(USER);

        // 10:11 再提交：内容虽未变，确认已过期。
        assertThat(at(CONFIRMED_AT.plusSeconds(660)).unchanged(USER, snapshot)).isFalse();
    }

    /** 没有快照：按已过期处理，宁可让用户重新确认。 */
    @Test
    @DisplayName("没有快照：按已过期处理")
    void expiredWithoutSnapshot() {
        assertThat(service.expired(null)).isTrue(); // 会话丢了或从未确认过。
    }

    /** 收货信息：去首尾空白后保存。 */
    @Test
    @DisplayName("收货信息：去首尾空白后保存")
    void receiverTrimsWhitespace() {
        // 三项都带首尾空白：保存的必须是去掉空白后的值。
        Receiver receiver = ReceiverForm.parse("  张三  ", " 13800000000 ", " 某市某区某路 1 号 ");

        assertThat(receiver.getName()).isEqualTo("张三"); // 收货人去空白。
        assertThat(receiver.getPhone()).isEqualTo("13800000000"); // 电话去空白。
        assertThat(receiver.getAddress()).isEqualTo("某市某区某路 1 号"); // 地址去空白。
    }

    /** 收货信息：三项必填，空与全空白都拒绝。 */
    @Test
    @DisplayName("收货信息：三项必填，空与全空白都拒绝")
    void receiverRejectsBlankFields() {
        // 收货人：全空白。
        assertThatThrownBy(() -> ReceiverForm.parse(" ", "138", "地址"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST); // 缺字段给 400。
                    assertThat(e.getMessage()).contains("收货人"); // 文案指出是哪个字段。
                });
        // 电话：空串。
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "", "地址"))
                .hasMessageContaining("联系电话"); // 电话为空也要说清楚。
        // 地址：null。
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "138", null))
                .hasMessageContaining("详细地址"); // null 当空串处理。
    }

    /** 收货信息：超长按字段拒绝，不截断。 */
    @Test
    @DisplayName("收货信息：超长按字段拒绝，不截断")
    void receiverRejectsTooLongFields() {
        // 各超 1 个字符：收货人 51、电话 31、地址 256。
        assertThatThrownBy(() -> ReceiverForm.parse("名".repeat(51), "138", "地址"))
                .hasMessageContaining("50"); // 收货人上限 50。
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "1".repeat(31), "地址"))
                .hasMessageContaining("30"); // 电话上限 30。
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "138", "址".repeat(256)))
                .hasMessageContaining("255"); // 地址上限 255。
    }

    /** 收货信息：长度边界值可用。 */
    @Test
    @DisplayName("收货信息：长度边界值可用")
    void receiverAcceptsBoundaryLengths() {
        // 正好等于上限：必须通过，否则列宽与校验口径不一致。
        Receiver receiver = ReceiverForm.parse("名".repeat(50), "1".repeat(30), "址".repeat(255));

        assertThat(receiver.getName()).hasSize(50); // 收货人正好 50。
        assertThat(receiver.getPhone()).hasSize(30); // 电话正好 30。
        assertThat(receiver.getAddress()).hasSize(255); // 地址正好 255。
    }

    /** 收货信息：电话至少含一位数字，纯文字被拒。 */
    @Test
    @DisplayName("收货信息：电话必须包含数字")
    void receiverRejectsPhoneWithoutDigit() {
        // 纯中文与纯字母都不算电话。
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "电话见地址", "某市某路 1 号"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.BAD_REQUEST); // 仍是 400。
                    assertThat(e.getMessage()).contains("数字"); // 文案说明缺什么。
                });
        assertThatThrownBy(() -> ReceiverForm.parse("张三", "abcdefg", "某市某路 1 号"))
                .hasMessageContaining("数字"); // 纯字母同样拒绝。
    }

    /** 收货信息：座机、国际号等带分隔符的号码可用。 */
    @Test
    @DisplayName("收货信息：带分隔符的座机/国际号可用")
    void receiverAcceptsPhoneWithSeparators() {
        // 座机：区号 + 短横线。
        assertThat(ReceiverForm.parse("张三", "010-12345678", "某市某路 1 号").getPhone())
                .isEqualTo("010-12345678"); // 原样保存，不重排格式。
        // 国际号：加号与空格。
        assertThat(ReceiverForm.parse("张三", "+86 138 0000 0000", "某市某路 1 号").getPhone())
                .isEqualTo("+86 138 0000 0000"); // 仍不规定格式，只要求含数字。
    }

    /** 把若干购物车视图转成汇总；复用购物车模块的转换，保证与页面同一口径。 */
    private CartSummary summaryOf(CartItemView... views) {
        return CartSummary.of(java.util.Arrays.stream(views).map(CartLine::from).toList()); // 走真实转换。
    }

    /** 换一个时刻的被测服务：用来验证 10 分钟时限。 */
    private CheckoutService at(Instant instant) {
        return new CheckoutService(cartService, Clock.fixed(instant, ZoneOffset.UTC)); // 同一辆车、不同时钟。
    }

    /** 造一行购物车视图：数量、单价、库存、状态、版本按用例传。 */
    private CartItemView view(long itemId, long productId, int quantity, String price, int stock,
                              String status, long version) {
        CartItemView view = new CartItemView(); // 新建行视图。
        view.setId(itemId); // 条目 id。
        view.setProductId(productId); // 商品 id。
        view.setProductName("测试商品" + productId); // 名称带 id，断言好定位。
        view.setCoverImage("images/products/T/cover.svg"); // 封面路径不校验存在性。
        view.setCategoryName("单行本"); // 分类名。
        view.setPrice(new BigDecimal(price)); // 单价用 BigDecimal。
        view.setQuantity(quantity); // 数量。
        view.setStock(stock); // 当前库存。
        view.setProductStatus(status); // ON_SALE / DRAFT。
        view.setVersion(version); // 购物车版本。
        return view; // 返回构造好的视图。
    }
}
