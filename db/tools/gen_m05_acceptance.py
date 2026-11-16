"""M05 逐行验收 HTML 生成（订单）。

用法：python db/tools/gen_m05_acceptance.py [01]
产物：reports/M05-01-逐行验收.html

复用 M04 的渲染器（注释行绿字留空、代码行逐条说明、结构性代码留「—」），
只替换任务元数据与本任务的补充说明；通用规则在 m04_explainer。
"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m04_acceptance as g4  # noqa: E402

ROOT = g4.base.ROOT

ORDER = "cartoon-order/src/main/java/org/csu/mycartoonstore/order/"
TEST = "cartoon-order/src/test/java/org/csu/mycartoonstore/order/"

TASKS = {
    "01": {
        "title": "M05-01 · 结算服务与快照",
        "out": "M05-01-逐行验收.html",
        "note": "内容：结算快照、空车/失效拦截、提交前逐项复核、10 分钟确认时限、收货字段校验、快照可序列化。"
                "验证：mvn -pl cartoon-order -am test → Tests run: 20, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，144 用例。",
        "files": [
            (ORDER + "service/CheckoutService.java", "CheckoutService.java", "生成快照、复核车是否变化、确认时限", "java"),
            (ORDER + "dto/CheckoutSnapshot.java", "CheckoutSnapshot.java", "结算确认快照（可序列化，含确认时刻）", "java"),
            (ORDER + "dto/Receiver.java", "Receiver.java", "校验后的收货信息", "java"),
            (ORDER + "dto/ReceiverForm.java", "ReceiverForm.java", "收货字段校验", "java"),
            (ORDER + "config/OrderTimeConfig.java", "OrderTimeConfig.java", "统一时钟 Bean", "java"),
            (TEST + "service/CheckoutServiceTest.java", "CheckoutServiceTest.java", "20 个用例", "java"),
        ],
    },
}

NOTES: dict[tuple[str, str], str] = {
    (ORDER + "service/CheckoutService.java", "CartSummary summary = cartService.summary(userId);"):
        "读本人全车：名称、价格、库存都取数据库当前值",
    (ORDER + "service/CheckoutService.java", "if (summary.isEmpty()) {"):
        "空车没有可确认的内容，直接拒绝而不是渲染空页",
    (ORDER + "service/CheckoutService.java", "if (!summary.isAllAvailable()) {"):
        "任一失效条目都不能整车结算（FR-C-02），先回购物车处理",
    (ORDER + "service/CheckoutService.java", "return snapshotOf(userId, summary);"):
        "把当前车固化成快照，供提交时比对",
    (ORDER + "service/CheckoutService.java", "if (snapshot == null || snapshot.getUserId() != userId) {"):
        "没有快照、或快照不是本人的：一律按已变化处理",
    (ORDER + "service/CheckoutService.java", "CartSummary current = cartService.summary(userId);"):
        "复核必须重新读库，不能信任会话里的金额",
    (ORDER + "service/CheckoutService.java", "CheckoutSnapshot now = snapshotOf(userId, current);"):
        "用同一套规则生成当前快照，再逐项比",
    (ORDER + "service/CheckoutService.java", "if (before.size() != after.size()) {"):
        "条目数不同：有人加车或删了车",
    (ORDER + "service/CheckoutService.java", "if (!before.get(i).sameAs(after.get(i))) {"):
        "同位置逐项比：商品、数量、购物车版本、价格",
    (ORDER + "service/CheckoutService.java",
     "return snapshot.getTotalAmount().compareTo(now.getTotalAmount()) == 0;"):
        "总额也要一致；compareTo 避免 28.0 与 28.00 的刻度差异",
    (ORDER + "service/CheckoutService.java", "lines.add(new CheckoutSnapshot.Line("):
        "只取服务端算出的行数据，页面回传值一律不用",
    (ORDER + "dto/CheckoutSnapshot.java",
     "sorted.sort((a, b) -> Long.compare(a.getCartItemId(), b.getCartItemId()));"):
        "按条目 id 排序：查询顺序不同不算变化",
    (ORDER + "dto/CheckoutSnapshot.java", "this.lines = List.copyOf(sorted);"):
        "不可变列表：会话里的快照不能被后续请求改掉",
    (ORDER + "dto/CheckoutSnapshot.java", "return other != null"):
        "逐项比对，任一项不同就算车变了",
    (ORDER + "dto/CheckoutSnapshot.java", "&& price.compareTo(other.price) == 0;"):
        "价格用 compareTo，28.0 与 28.00 视为相同",
    (ORDER + "dto/CheckoutSnapshot.java",
     "return Objects.hash(cartItemId, productId, quantity, cartVersion, price.stripTrailingZeros());"):
        "hash 与 equals 口径一致：价格先去掉尾零",
    (ORDER + "dto/ReceiverForm.java", "static final int NAME_MAX = 50;"):
        "与 receiver_name VARCHAR(50) 对齐",
    (ORDER + "dto/ReceiverForm.java", "static final int PHONE_MAX = 30;"):
        "与 receiver_phone VARCHAR(30) 对齐；不强行限定 11 位手机号",
    (ORDER + "dto/ReceiverForm.java", "static final int ADDRESS_MAX = 255;"):
        "与 receiver_address VARCHAR(255) 对齐",
    (ORDER + "dto/ReceiverForm.java", 'String value = raw == null ? "" : raw.strip();'):
        "null 当空串：必填判断只写一处",
    (ORDER + "dto/ReceiverForm.java", "if (value.isEmpty()) {"):
        "空与全空白都算没填",
    (ORDER + "dto/ReceiverForm.java", "if (value.length() > max) {"):
        "超长拒绝而不是截断，避免用户以为存进去了",
    (ORDER + "dto/ReceiverForm.java", "if (phone.codePoints().noneMatch(Character::isDigit)) {"):
        "轻校验：至少一位数字，拦纯文字但不限定号码格式",
    (TEST + "service/CheckoutServiceTest.java",
     'assertThat(ReceiverForm.parse("张三", "010-12345678", "某市某路 1 号").getPhone())'):
        "断言座机号码原样保存",
    (TEST + "service/CheckoutServiceTest.java",
     'assertThat(ReceiverForm.parse("张三", "+86 138 0000 0000", "某市某路 1 号").getPhone())'):
        "断言国际号原样保存",
    (TEST + "service/CheckoutServiceTest.java", "when(cartService.summary(USER)).thenReturn(summaryOf("):
        "准备当前车（可多行，按可变参数传入）",
    (TEST + "service/CheckoutServiceTest.java", "CheckoutSnapshot snapshot = service.prepare(USER);"):
        "被测动作：生成结算快照",
    (TEST + "service/CheckoutServiceTest.java", "assertThat(service.unchanged(USER, snapshot)).isTrue();"):
        "断言车没变、可以下单",
    (TEST + "service/CheckoutServiceTest.java", "assertThat(service.unchanged(USER, snapshot)).isFalse();"):
        "断言车已变化、必须重新确认",
    (TEST + "service/CheckoutServiceTest.java", "when(cartService.summary(USER)).thenReturn(summaryOf(view(10L, 8L, 3, \"28.00\", 10, \"ON_SALE\", 1L)));"):
        "同一行数量 2→3、版本 0→1：模拟另一个标签页改车",
    (TEST + "service/CheckoutServiceTest.java", "private CartSummary summaryOf(CartItemView... views) {"):
        "夹具：把若干购物车视图转成汇总",
    (TEST + "service/CheckoutServiceTest.java",
     "return CartSummary.of(java.util.Arrays.stream(views).map(CartLine::from).toList());"):
        "复用购物车模块的转换，保证与页面同一口径",
    (TEST + "service/CheckoutServiceTest.java", "private CartItemView view("):
        "夹具：造一行购物车视图（数量、单价、库存、状态、版本）",
    (TEST + "service/CheckoutServiceTest.java", "ByteArrayOutputStream bytes = new ByteArrayOutputStream();"):
        "把快照真的序列化一遍：模拟放进 Session",
    (TEST + "service/CheckoutServiceTest.java", "restored = (CheckoutSnapshot) in.readObject();"):
        "反序列化后应能继续复核，证明会话里存得住",
    (TEST + "service/CheckoutServiceTest.java", "assertThat(restored.getLines()).hasSize(2);"):
        "行数与行序都保持不变",
    (ORDER + "service/CheckoutService.java", "if (snapshot == null) {"):
        "没有快照：会话丢了或从未确认过，按已过期处理",
    (ORDER + "service/CheckoutService.java",
     "return !clock.instant().isBefore(snapshot.getConfirmedAt().plus(CONFIRM_TTL));"):
        "恰好第 10 分钟算过期：时限是「10 分钟内」，不含右边界",
    (ORDER + "service/CheckoutService.java", "if (expired(snapshot)) {"):
        "超时同样不可用：即使内容没变，也要重新确认",
    (ORDER + "dto/CheckoutSnapshot.java", "this.confirmedAt = confirmedAt;"):
        "记录确认时刻；提交时用它判断是否超过 10 分钟",
    (ORDER + "service/CheckoutService.java", "public CheckoutService(CartService cartService, Clock clock) {"):
        "构造器注入：购物车服务与时钟（测试可用固定时钟）",
    (TEST + "service/CheckoutServiceTest.java", "assertThat(snapshot.getConfirmedAt()).isEqualTo(CONFIRMED_AT);"):
        "断言快照记住了确认时刻",
    (TEST + "service/CheckoutServiceTest.java",
     "assertThat(at(CONFIRMED_AT.plusSeconds(599)).expired(snapshot)).isFalse();"):
        "第 599 秒仍在 10 分钟内",
    (TEST + "service/CheckoutServiceTest.java",
     "assertThat(at(CONFIRMED_AT.plusSeconds(600)).expired(snapshot)).isTrue();"):
        "正好第 600 秒算过期（边界）",
    (TEST + "service/CheckoutServiceTest.java",
     "assertThat(at(CONFIRMED_AT.plusSeconds(601)).expired(snapshot)).isTrue();"):
        "第 601 秒已过期",
    (TEST + "service/CheckoutServiceTest.java",
     "assertThat(at(CONFIRMED_AT.plusSeconds(660)).unchanged(USER, snapshot)).isFalse();"):
        "10:11 再提交：内容未变但确认已过期",
    (TEST + "service/CheckoutServiceTest.java", "assertThat(service.expired(null)).isTrue();"):
        "没有快照也要判过期",
    (TEST + "service/CheckoutServiceTest.java",
     "return new CheckoutService(cartService, Clock.fixed(instant, ZoneOffset.UTC));"):
        "同一辆车、不同时钟：专门验证时限",
}


def main() -> None:
    g4.NOTES.update(NOTES)
    wanted = sys.argv[1:] or list(TASKS)
    for task_id in wanted:
        g4.render(task_id, TASKS[task_id])


if __name__ == "__main__":
    main()
