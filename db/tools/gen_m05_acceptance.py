"""M05 逐行验收 HTML 生成（订单）。

用法：python db/tools/gen_m05_acceptance.py [01]
产物：reports/M05-01-逐行验收.html

复用 M04 的渲染器（注释行绿字留空、代码行逐条说明、结构性代码留「—」），
只替换任务元数据与本任务的补充说明；通用规则在 m04_explainer。
"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m04_acceptance as g4  # noqa: E402

ROOT = g4.base.ROOT

ORDER = "cartoon-order/src/main/java/org/csu/mycartoonstore/order/"
TEST = "cartoon-order/src/test/java/org/csu/mycartoonstore/order/"
WEB = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/"
WEB_TEST = "cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/"

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
            (TEST + "service/CheckoutServiceTest.java", "CheckoutServiceTest.java", "20 个用例", "java"),        ],
    },
    "02": {
        "title": "M05-02 · 结算页与购物车入口",
        "out": "M05-02-逐行验收.html",
        "note": "内容：GET /checkout 渲染、快照写进会话、空车/失效回购物车、购物车入口开关、游客拦截。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 81, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，151 用例。"
                "store.css 只追加结算页样式，不逐行对照；POST 与下单事务在 M05-03。",
        "files": [
            (WEB + "controller/CheckoutController.java", "CheckoutController.java", "确认页路由与快照入会话", "java"),
            ("cartoon-user-web/src/main/resources/templates/checkout.html", "checkout.html",
             "确认页：步骤条、商品清单、收货表单、时限提示", "html"),
            ("cartoon-user-web/src/main/resources/templates/cart.html", "cart.html",
             "购物车入口：可结算才给去结算链接", "html"),
            (WEB + "config/WebConfig.java", "WebConfig.java", "把 /checkout 加入受保护路径", "java"),
            (WEB_TEST + "CheckoutPageTest.java", "CheckoutPageTest.java", "7 个用例", "java"),
        ],
    },
    "03": {
        "title": "M05-03 · 下单事务",
        "out": "M05-03-逐行验收.html",
        "note": "内容：POST /checkout、订单号生成、快照复核、锁车锁商品、写订单与明细、扣库存、删结算条目、事务回滚。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 87, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，157 用例。"
                "CartService/CartItemMapper 只新增两个方法（锁车、按 id 删条目），其完整逐行见 M04-01 页；"
                "本任务通过下单用例覆盖这两个方法的真实 SQL。",
        "files": [
            (ORDER + "service/OrderService.java", "OrderService.java", "下单事务：校验→锁→写→扣→删", "java"),
            (ORDER + "service/OrderNoGenerator.java", "OrderNoGenerator.java", "订单号生成与查重", "java"),
            (ORDER + "dto/PlacedOrder.java", "PlacedOrder.java", "下单返回值：订单 + 明细", "java"),
            (ORDER + "service/OrderQueryService.java", "OrderQueryService.java", "本人订单与明细查询", "java"),
            (WEB + "controller/CheckoutController.java", "CheckoutController.java", "提交订单：校验收货 → 调事务 → 跳详情", "java"),
            (WEB + "controller/OrderController.java", "OrderController.java", "订单详情：本人订单只读页", "java"),
            ("cartoon-user-web/src/main/resources/templates/order-detail.html", "order-detail.html",
             "订单详情页：状态、快照明细、收货、总额", "html"),
            (WEB_TEST + "PlaceOrderTest.java", "PlaceOrderTest.java", "6 个用例：正常/缺字段/车变/库存/回滚/归属", "java"),
        ],
    },
    "04": {
        "title": "M05-04 · 订单列表与详情补全",
        "out": "M05-04-逐行验收.html",
        "note": "内容：状态页签与角标、完整单号精确搜索、分页、空状态、时间线、状态标签、归属 404。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 96, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，166 用例。"
                "store.css 新增列表/时间线样式不逐行对照；支付与取消按钮在 M06 / M05-05。",
        "files": [
            (ORDER + "service/OrderListService.java", "OrderListService.java", "本人订单：状态/单号/分页/角标", "java"),
            (ORDER + "domain/OrderStatus.java", "OrderStatus.java", "五种状态与中文标签", "java"),
            (ORDER + "dto/OrderCard.java", "OrderCard.java", "列表卡片：首件缩略图与件数", "java"),
            (ORDER + "dto/OrderPage.java", "OrderPage.java", "一页结果与上下页判断", "java"),
            (WEB + "controller/OrderController.java", "OrderController.java", "列表与详情路由", "java"),
            ("cartoon-user-web/src/main/resources/templates/orders.html", "orders.html",
             "列表页：页签、搜索、卡片、空状态、分页", "html"),
            ("cartoon-user-web/src/main/resources/templates/order-detail.html", "order-detail.html",
             "详情页：时间线、金额标签、返回列表", "html"),
            (WEB_TEST + "OrderListTest.java", "OrderListTest.java", "9 个用例：空/归属/筛选/搜索/分页/400/快照/时间线/游客", "java"),
        ],
    },
    "05": {
        "title": "M05-05 · 取消订单与库存恢复",
        "out": "M05-05-逐行验收.html",
        "note": "内容：订单行锁、幂等取消、库存恢复与版本 +1、已支付不可取消、他人订单 404、车不回填。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 103, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，173 用例。"
                "ProductMapper.restoreStock 是 3 行注解 SQL，未纳入逐行；由取消用例验证真实库存变化。",
        "files": [
            (ORDER + "service/OrderService.java", "OrderService.java", "下单事务 + 取消订单", "java"),
            (WEB + "controller/OrderController.java", "OrderController.java", "取消路由与详情", "java"),
            ("cartoon-user-web/src/main/resources/templates/order-detail.html", "order-detail.html",
             "详情页：待支付显示取消按钮", "html"),
            (WEB_TEST + "CancelOrderTest.java", "CancelOrderTest.java",
             "7 个用例：恢复一次/重复取消/已支付/他人/不存在/按钮/页签", "java"),
        ],
    },
    "06": {
        "title": "M05-06 · 并发验证与收尾",
        "out": "M05-06-逐行验收.html",
        "note": "内容：同一车并发提交只成交一单、同一订单并发取消只恢复一次库存。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 105, Failures: 0；"
                "mvn clean verify → 10 模块 SUCCESS，175 用例。",
        "files": [
            (WEB_TEST + "ConcurrentOrderTest.java", "ConcurrentOrderTest.java",
             "2 个并发用例：下单与取消", "java"),
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
    (WEB + "config/WebConfig.java", '.addPathPatterns("/cart", "/cart/**", "/checkout", "/checkout/**",'):
        "受保护路径集中列在这里；M05-02 新增 /checkout",
    (WEB + "config/WebConfig.java", '"/orders", "/orders/**", "/profile", "/profile/**");'):
        "续行：订单与资料同样需要登录",
    (WEB_TEST + "CheckoutPageTest.java", "class CheckoutPageTest extends AbstractIntegrationTest {"):
        "集成测试：真实 MySQL + MockMvc",
    # ---- M05-03 下单事务：控制流与流式调用没有行尾注释，这里补说明 ----
    (ORDER + "service/OrderService.java", "public OrderService(CartService cartService,"):
        "构造器注入六个协作方",
    (ORDER + "service/OrderService.java", "CheckoutService checkoutService,"):
        "参数续行：结算服务",
    (ORDER + "service/OrderService.java", "ProductMapper productMapper,"):
        "参数续行：商品 Mapper",
    (ORDER + "service/OrderService.java", "SalesOrderMapper orderMapper,"):
        "参数续行：订单 Mapper",
    (ORDER + "service/OrderService.java", "SalesOrderItemMapper itemMapper,"):
        "参数续行：明细 Mapper",
    (ORDER + "service/OrderService.java", "if (snapshot == null || snapshot.getUserId() != userId) {"):
        "没有快照或不是本人：409 重新确认",
    (ORDER + "service/OrderService.java", "if (checkoutService.expired(snapshot)) {"):
        "确认超过 10 分钟：409",
    (ORDER + "service/OrderService.java", "List<CartLine> lines = cartService.lockForCheckout(userId);"):
        "锁住本人车行，再读当前价格与库存",
    (ORDER + "service/OrderService.java", "if (lines.isEmpty()) {"):
        "车空：409 回购物车",
    (ORDER + "service/OrderService.java", "if (!checkoutService.matches(snapshot, CartSummary.of(lines))) {"):
        "与确认快照逐项不一致：409",
    (ORDER + "service/OrderService.java", "List<Long> productIds = lines.stream()"):
        "收集本车商品 id",
    (ORDER + "service/OrderService.java", ".map(CartLine::getProductId)"):
        "取商品 id",
    (ORDER + "service/OrderService.java", ".distinct()"):
        "同一商品只锁一次",
    (ORDER + "service/OrderService.java", ".sorted()"):
        "按 id 升序加锁，避免死锁",
    (ORDER + "service/OrderService.java", ".toList();"):
        "收集成列表",
    (ORDER + "service/OrderService.java", "Map<Long, Product> products = productMapper.lockByIds(productIds).stream()"):
        "按 id 升序锁商品行",
    (ORDER + "service/OrderService.java", ".collect(Collectors.toMap(Product::getId, Function.identity()));"):
        "id → 商品，便于逐行校验",
    (ORDER + "service/OrderService.java", "Product product = products.get(line.getProductId());"):
        "取本行商品（已锁定）",
    (ORDER + "service/OrderService.java", "if (product.getStock() == null || product.getStock() < line.getQuantity()) {"):
        "库存不足：409，整体回滚",
    (ORDER + "service/OrderService.java", "BigDecimal total = lines.stream()"):
        "总额由服务端计算",
    (ORDER + "service/OrderService.java", ".map(CartLine::getLineAmount)"):
        "取每行小计",
    (ORDER + "service/OrderService.java", ".reduce(BigDecimal.ZERO.setScale(MoneyUtils.SCALE), BigDecimal::add);"):
        "累加为订单总额",
    (ORDER + "service/OrderService.java", "SalesOrder order = new SalesOrder();"):
        "组装订单主表",
    (ORDER + "service/OrderService.java", "List<SalesOrderItem> items = new ArrayList<>(lines.size());"):
        "按行数预分配明细列表",
    (ORDER + "service/OrderService.java", "SalesOrderItem item = new SalesOrderItem();"):
        "组装一行明细（快照字段）",
    (ORDER + "service/OrderService.java", "int rows = productMapper.deductStock(line.getProductId(), line.getQuantity());"):
        "乐观扣库存：影响 0 行即失败",
    (ORDER + "service/OrderService.java", "int deleted = cartService.removeCheckedItems(userId, lines.stream().map(CartLine::getItemId).toList());"):
        "删除本次结算的条目",
    (ORDER + "service/OrderService.java", "if (deleted != lines.size()) {"):
        "删除行数不符：车被并发改过，回滚",
    (ORDER + "service/OrderNoGenerator.java", "String candidate = PREFIX + TS.format(LocalDateTime.now(clock))"):
        "拼订单号：CO + 时间戳 + 4 位随机数",
    (WEB + "controller/CheckoutController.java", "public CheckoutController(CheckoutService checkoutService, CartService cartService,"):
        "构造器注入：结算、购物车、下单三个服务",
    (WEB + "controller/CheckoutController.java", "public String place(HttpSession session,"):
        "提交订单入口：收货字段来自表单",
    (WEB + "controller/CheckoutController.java", "Receiver receiver = ReceiverForm.parse(receiverName, receiverPhone, receiverAddress);"):
        "收货三项校验：必填/去空白/限长/电话含数字",
    (WEB_TEST + "PlaceOrderTest.java", "class PlaceOrderTest extends AbstractIntegrationTest {"):
        "集成测试：真实 MySQL + MockMvc",
    (WEB_TEST + "PlaceOrderTest.java", 'assertThat(count("SELECT COUNT(*) FROM sales_order_item i JOIN sales_order o ON o.id = i.order_id "'):
        "断言明细两条",
    (WEB_TEST + "PlaceOrderTest.java", 'assertThat(value("SELECT status FROM sales_order WHERE order_no = ?", String.class, orderNo))'):
        "断言初始状态为待支付",
    (WEB_TEST + "PlaceOrderTest.java", 'assertThat(decimal("SELECT total_amount FROM sales_order WHERE order_no = ?", orderNo))'):
        "断言订单总额",
    (WEB_TEST + "PlaceOrderTest.java", ".removeCheckedItems(anyLong(), anyList());"):
        "Spy 在删车这一步抛异常",
    (WEB_TEST + "PlaceOrderTest.java", "org.assertj.core.api.Assertions.assertThatThrownBy(() -> place(session, NAME, PHONE, ADDRESS))"):
        "MockMvc 把未处理异常原样抛出，这里断言异常冒出来",
    (WEB_TEST + "PlaceOrderTest.java", "private org.springframework.test.web.servlet.ResultActions place(MockHttpSession session,"):
        "夹具：用给定会话与收货信息提交订单",
    (WEB_TEST + "PlaceOrderTest.java", "String name, String phone, String address)"):
        "夹具参数续行",
    (ORDER + "service/OrderQueryService.java", "return itemMapper.selectList(Wrappers.<SalesOrderItem>lambdaQuery()"):
        "按订单 id 查明细，保持写入顺序",
    # ---- M05-04 订单列表 ----
    (ORDER + "service/OrderListService.java", "LambdaQueryWrapper<SalesOrder> filter = Wrappers.<SalesOrder>lambdaQuery()"):
        "查询条件：只查本人，状态与单号可选",
    (ORDER + "service/OrderListService.java", "List<SalesOrder> orders = orderMapper.selectList(filter"):
        "按时间倒序取当前页",
    (ORDER + "service/OrderListService.java", '.last("LIMIT " + PAGE_SIZE + " OFFSET " + (safePage - 1) * PAGE_SIZE));'):
        "SQL 层分页：LIMIT/OFFSET，不把全部订单读进内存",
    (ORDER + "service/OrderListService.java", "return new OrderPage(cards, safePage, PAGE_SIZE, total, totalPages,"):
        "把当前页、总数与角标交给页面",
    (ORDER + "service/OrderListService.java", 'statusCounts(userId), status == null ? "ALL" : status.name(), orderNo);'):
        "参数续行：角标、当前状态、搜索词",
    (ORDER + "service/OrderListService.java", "Map<String, Long> counts = new HashMap<>();"):
        "六个角标：ALL + 五种状态",
    (ORDER + "service/OrderListService.java", 'counts.put("ALL", orderMapper.selectCount(Wrappers.<SalesOrder>lambdaQuery()'):
        "全部订单数",
    (ORDER + "service/OrderListService.java", "counts.put(status.name(), orderMapper.selectCount(Wrappers.<SalesOrder>lambdaQuery()"):
        "每种状态的真实数量",
    (ORDER + "service/OrderListService.java", ".eq(SalesOrder::getUserId, userId)"):
        "参数续行：只统计本人",
    (ORDER + "service/OrderListService.java", ".eq(SalesOrder::getStatus, status.name())));"):
        "参数续行：按状态计数",
    (ORDER + "service/OrderListService.java", "if (orders.isEmpty()) {"):
        "空页直接返回，不再查明细",
    (ORDER + "service/OrderListService.java", "Map<Long, List<SalesOrderItem>> itemsByOrder = itemMapper.selectList("):
        "一次查完本页全部明细，避免 N+1",
    (ORDER + "service/OrderListService.java", "Wrappers.<SalesOrderItem>lambdaQuery()"):
        "参数续行：明细查询条件",
    (ORDER + "service/OrderListService.java", ".orderByAsc(SalesOrderItem::getId))"):
        "参数续行：按写入顺序，保证首件稳定",
    (ORDER + "service/OrderListService.java", ".stream()"):
        "转成流，按订单分组",
    (ORDER + "service/OrderListService.java", "for (SalesOrder order : orders) {"):
        "按查询顺序组装卡片",
    (ORDER + "service/OrderListService.java", "cards.add(new OrderCard(order.getOrderNo(), order.getStatus(), order.getTotalAmount(),"):
        "组装一张卡片：订单头 + 首件缩略图",
    (ORDER + "service/OrderListService.java", "order.getCreatedAt(),"):
        "参数续行：创建时间",
    (ORDER + "service/OrderListService.java", "if (page < 1) {"):
        "0 与负数页码：400",
    (ORDER + "service/OrderListService.java", "if (orderNo.length() > ORDER_NO_MAX) {"):
        "超过 VARCHAR(40)：400",
    (ORDER + "domain/OrderStatus.java", "public enum OrderStatus {"):
        "五种状态：与数据库 CHECK 一一对应",
    (ORDER + "dto/OrderCard.java", "public OrderCard(String orderNo, String status, BigDecimal totalAmount, LocalDateTime createdAt,"):
        "卡片构造器：状态码在内部转成中文标签",
    (ORDER + "dto/OrderPage.java", "public OrderPage(List<OrderCard> cards, int page, int size, long total, int totalPages,"):
        "一页结果的构造器",
    (WEB + "controller/OrderController.java", "HttpSession session, Model model) {"):
        "参数续行：会话与页面数据",
    (WEB + "controller/OrderController.java", "for (OrderStatus status : OrderStatus.values()) {"):
        "按枚举顺序生成页签",
    (WEB_TEST + "OrderListTest.java", "class OrderListTest extends AbstractIntegrationTest {"):
        "集成测试：真实 MySQL + MockMvc",
    (WEB_TEST + "OrderListTest.java", 'org.assertj.core.api.Assertions.assertThat(html.indexOf("CO202611161100000002"))'):
        "断言 11:00 的单排在 10:00 前",
    (WEB_TEST + "OrderListTest.java", 'org.assertj.core.api.Assertions.assertThat(html.replaceAll("\\\\s+", ""))'):
        "压缩空白后断言页签数量",
    (WEB_TEST + "OrderListTest.java", 'seedOrder(String.format("CO2026111610%08d", i), "PENDING_PAYMENT", "28.00",'):
        "循环造 11 单，单号与时间都可排序",
    (WEB_TEST + "OrderListTest.java", '+ "receiver_phone, receiver_address, created_at, payment_method, payment_no, paid_at, cancelled_at) "'):
        "参数续行：订单列名",
    (WEB_TEST + "OrderListTest.java", 'orderNo, owner, status, new java.math.BigDecimal(total), "张三", "13800000000",'):
        "订单值：收货信息固定",
    (WEB_TEST + "OrderListTest.java", '"某市某区某路 1 号", createdAt,'):
        "参数续行：地址与创建时间",
    (WEB_TEST + "OrderListTest.java", 'orderId, productId, "SKU-" + orderNo, name, "/images/covers/ol.svg",'):
        "明细值：快照名称、封面与 SKU",
    (WEB_TEST + "OrderListTest.java", "new java.math.BigDecimal(price), quantity,"):
        "参数续行：单价与数量",
    # ---- M05-05 取消订单 ----
    (ORDER + "service/OrderService.java", "OrderNoGenerator orderNoGenerator,"):
        "参数续行：订单号生成器",
    (ORDER + "service/OrderService.java", "SalesOrder order = orderMapper.lockByOrderNo(orderNo);"):
        "锁订单行：并发取消只有一个能迁移状态",
    (ORDER + "service/OrderService.java", "if (order == null || order.getUserId() != userId) {"):
        "不存在或非本人：统一 404",
    (ORDER + "service/OrderService.java", "if (CANCELLED.equals(order.getStatus())) {"):
        "幂等：已取消直接返回，不再恢复库存",
    (ORDER + "service/OrderService.java", "if (!PENDING_PAYMENT.equals(order.getStatus())) {"):
        "非待支付：409，已支付/已发货不可取消",
    (ORDER + "service/OrderService.java", "List<SalesOrderItem> items = itemMapper.selectList(Wrappers.<SalesOrderItem>lambdaQuery()"):
        "读该订单全部明细",
    (ORDER + "service/OrderService.java", "for (SalesOrderItem item : items) {"):
        "逐条恢复库存，数量来自下单快照",
    (WEB + "controller/OrderController.java", "public OrderController(OrderQueryService orderQueryService, OrderListService orderListService,"):
        "构造器注入：详情、列表、取消三个服务",
    (WEB + "controller/OrderController.java", 'redirect.addFlashAttribute("message",'):
        "取消结果提示：真取消 / 幂等",
    (WEB_TEST + "CancelOrderTest.java", "class CancelOrderTest extends AbstractIntegrationTest {"):
        "集成测试：真实 MySQL + MockMvc",
    (WEB_TEST + "CancelOrderTest.java", '+ "receiver_address, created_at, payment_method, payment_no, paid_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",'):
        "参数续行：订单列名",
    (WEB_TEST + "CancelOrderTest.java", 'orderNo, owner, status, new BigDecimal("56.00"), "张三", "13800000000",'):
        "订单值：收货信息固定",
    (WEB_TEST + "CancelOrderTest.java", '"某市某区某路 1 号", created,'):
        "参数续行：地址与创建时间",
    (WEB_TEST + "CancelOrderTest.java", 'paymentNo == null ? null : "MOCK", paymentNo,'):
        "支付凭证：只有已支付订单才有",
    (WEB_TEST + "CancelOrderTest.java", 'orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/cx.svg",'):
        "明细值：快照名称与封面",
    # ---- M05-06 并发验证 ----
    (WEB_TEST + "ConcurrentOrderTest.java", "class ConcurrentOrderTest extends AbstractIntegrationTest {"):
        "集成测试：真实 MySQL + MockMvc",
    (WEB_TEST + "ConcurrentOrderTest.java", 'assertThat(value("SELECT status FROM sales_order WHERE order_no = ?", String.class,'):
        "断言订单已取消",
    (WEB_TEST + "ConcurrentOrderTest.java", "} finally {"):
        "无论如何都收回线程池",
    (WEB_TEST + "ConcurrentOrderTest.java", '+ "receiver_phone, receiver_address, created_at) VALUES (?,?,?,?,?,?,?,?)",'):
        "参数续行：订单列名",
    (WEB_TEST + "ConcurrentOrderTest.java", 'orderNo, owner, "PENDING_PAYMENT", new BigDecimal("56.00"), "张三", "13800000000",'):
        "订单值：收货信息固定",
    (WEB_TEST + "ConcurrentOrderTest.java", 'orderId, productId, "SKU-" + orderNo, "商品" + orderNo, "/images/covers/cc.svg",'):
        "明细值：快照名称与封面",
}


# 模板里这些行的用途：逐行页不给「—」，HTML 也要一句具体说明。
HTML_NOTES = {
    '<ol class="steps">': "步骤条：购物车 → 确认订单 → 完成",
    '<li class="done">购物车</li>': "已完成：购物车",
    '<li class="on">确认订单</li>': "当前步骤：确认订单",
    '<li>完成</li>': "尚未到达：完成",
    "<div>": "内容分组容器",
    '<span class="en">CHECKOUT</span>': "栏目英文小标题",
    "<h2>确认订单</h2>": "页面标题",
    '<div class="checkout-grid">': "两栏布局：左清单、右收货与总额",
    '<div class="checkout-items">': "左栏：商品清单",
    "<h3>商品清单</h3>": "左栏标题",
    '<div class="checkout-row-text">': "行内文字区：名称与分类",
    '<span class="checkout-line"': "行小计金额（按当前价 × 数量）",
    '<p class="hint">清单来自数据库当前值，确认后若商品或购物车变化，提交会被要求重新确认。</p>':
        "说明清单取当前值、变化要重新确认",
    '<div class="checkout-side">': "右栏：收货信息与总额",
    "<h3>收货信息</h3>": "右栏标题",
    '<div class="receiver">': "收货表单容器",
    "<label>收货人": "收货人字段标签",
    '<input name="receiverName" maxlength="50" placeholder="1–50 个字符">': "收货人输入，≤50 字符",
    "<label>联系电话": "联系电话字段标签",
    '<input name="receiverPhone" maxlength="30" placeholder="至少包含数字，座机/手机均可">':
        "电话输入，≤30 且至少一位数字",
    "<label>详细地址": "详细地址字段标签",
    '<input name="receiverAddress" maxlength="255" placeholder="1–255 个字符">': "地址输入，≤255 字符",
    '<p class="hint">下单后地址会作为快照保存，之后修改商品不影响历史订单。</p>':
        "说明地址会写进订单快照",
    '<div class="checkout-total">': "总额区",
    "<span>应付总额</span>": "金额标签",
    '<p class="hint" th:text="|请在 ${confirmMinutes} 分钟内提交订单；超时后需要重新确认。|">':
        "时限提示：10 分钟内提交",
    '<button type="button" class="btn btn-primary" disabled': "提交按钮暂禁用（M05-03 接下单）",
    'th:title="下单事务在 M05-03 接入">提交订单（M05-03 接入）</button>': "按钮文案说明何时开放",
    "<p class=\"hint\">当前只完成“确认订单”这一步：地址校验与下单在 M05-03 实现。</p>":
        "说明本阶段范围",
    '<p class="auth-error" th:if="${error}" th:text="${error}">提示</p>':
        "从结算页被退回时的提示（空车/失效）",
    '<a class="btn btn-primary" th:if="${cart.settleable}" th:href="@{/checkout}">去结算</a>':
        "全部可用才给结算入口",
    '<button type="button" class="btn btn-primary" disabled': "失效时保持禁用",
    'th:unless="${cart.settleable}">去结算</button>': "继续禁用按钮的收尾",
    '<p class="hint" th:unless="${cart.settleable}">失效条目无法结算，请先处理。</p>':
        "说明为什么不能结算",
}


def is_comment_line(s: str) -> bool:
    """整行注释：保留绿色显示，右侧留空。"""
    return s.startswith(("//", "/*", "*", "*/", "<!--", "-->"))


def split_trailing(line: str) -> tuple[str, str]:
    """把行尾 // 注释拆出来：左列显示干净代码，注释文字放右列。"""
    s = line.rstrip()  # 去掉行尾空白，避免注释位置漂移。
    if is_comment_line(s.lstrip()):
        return line, ""  # 整行注释不拆。
    m = re.match(r"^(.*?\S)\s+//\s*(.+)$", s)  # 只认「空白 + //」，不误伤路径或字符串。
    if m and not m.group(1).endswith(("http:", "https:")):  # 只排除 http:// 这种 URL 写法。
        return m.group(1).rstrip(), m.group(2).strip()  # 代码 / 注释分开。
    return line, ""  # 没有行尾注释。


def import_hint(s: str) -> str:
    """import 行没有注释时的说明；先去掉可能的行尾注释。"""
    body = s.split("//")[0].strip().removeprefix("import ").removeprefix("static ").removesuffix(";").strip()
    name = body.split(".")[-1]  # 取简单类名。
    return ("静态导入 " + name) if s.startswith("import static") else ("引入 " + name)


def html_hint(s: str) -> str:
    """模板行的说明：先查手工表，再兜底闭合标签。"""
    if s in HTML_NOTES:
        return HTML_NOTES[s]  # 手工写过的行直接用。
    if s.startswith("</"):
        tag = re.match(r"</(\w+)", s)  # 闭合标签说明结束的是什么。
        return f"结构闭合：结束 <{tag.group(1)}> 容器" if tag else "结构闭合：结束容器"
    return "区块：页面上的一个内容单元"  # 最后兜底，避免出现「—」。


def preceding_comment(lines: list[str], idx: int) -> str:
    """取类声明上方的 // 块首行，作为类这一行的说明。"""
    block = []  # 收集连续的 // 行。
    i = idx - 1  # idx 是 1-based 行号，这里指向上一行。
    while i >= 0:
        s = lines[i].strip()  # 上一行内容。
        if not s.startswith("//"):
            break  # 不是 // 注释就停。
        block.append(s[2:].strip())  # 去掉 // 后入栈。
        i -= 1  # 继续往上。
    return block[-1] if block else ""  # 最早的一行是类职责说明。


def render(task_id: str, meta: dict) -> None:
    """渲染一页：左列干净代码（去掉行尾注释），右列放该行的说明。"""
    sections, nav, total = [], [], 0  # 分节、导航、总行数。
    for rel, title, subtitle, kind in meta["files"]:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()  # 原始源码。
        sid = "s" + str(len(nav) + 1)  # 锚点 id。
        nav.append(f'<a href="#{sid}">{title}</a>')  # 顶部导航。
        fn = g4.make_java_explainer(lines) if kind == "java" else g4.m22.make_html_explainer(lines)  # 规则说明。
        hi = g4.base.hi_java if kind == "java" else g4.m22.hi_html  # 语法高亮。
        rows = []  # 本文件的行。
        for i, line in enumerate(lines, 1):
            s = line.strip()  # 去缩进后判断。
            if not s:
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            if is_comment_line(s):
                rows.append(
                    f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                    f'<td class="code"><pre class="cmtline">{g4.m22.esc(line)}</pre></td>'
                    f'<td class="explain"></td></tr>'
                )
                continue
            display, trailing = split_trailing(line)  # 左列代码 / 右列注释。
            code = display.strip()  # 用于查说明的代码文本。
            text = NOTES.get((rel, s)) or NOTES.get((rel, code)) or trailing  # 手写 > 行尾注释。
            if not text and kind == "java":
                text = g4.m04_explainer.explain(code, g4.javadoc_above(lines, i - 1))  # M04/M05 规则。
            if not text and kind == "html":
                text = html_hint(s)  # 模板按手工表/闭合标签说明。
            if not text:
                text = fn(i, line)  # 生成器的规则。
            if not text and code.startswith("import "):
                text = import_hint(code)  # import 兜底。
            if re.match(r"public (?:static )?(?:final )?class ", code) and (not text or "见类注释" in text):
                text = preceding_comment(lines, i) or text  # 类声明用上方 // 块的首行。
            if not text:
                text = "结构行：参与语法组织"  # 最后兜底，不出「—」。
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(display)}</pre></td>'
                f'<td class="explain">{g4.m22.esc(text)}</td></tr>'
            )
        total += len(rows)  # 累计行数。
        sections.append(f"""<section id="{sid}">
<h2>{title}<small>{subtitle} · {len(lines)} 行</small></h2>
<p class="path">{rel}</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（行尾注释已移到右列，整行注释保持绿色）</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(rows)}
</tbody></table>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>{meta["title"]}（逐行验收）</title>
{g4.base.STYLE}</head>
<body>
<header>
<h1>{meta["title"]}（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列代码已去掉行尾注释，注释文字放在右列；整行注释仍在原位、绿色显示。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">{g4.m22.esc(meta["note"])}</p>
<p class="note" style="margin-top:8px">格式约定：左列不出现行尾注释，避免与右列重复；每一条代码行都有说明，不出现「—」。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-16 · {meta["title"]} · {len(meta["files"])} 个文件、{total} 行。完整汇报见同目录的 Markdown 汇报。</footer>
</body></html>
"""
    out = ROOT / "reports" / meta["out"]  # 输出路径。
    out.write_text(doc, encoding="utf-8")  # 写文件。
    print(f"written: {out.name}  rows={total}")  # 控制台回显。


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        render(task_id, TASKS[task_id])  # 逐个任务渲染。


if __name__ == "__main__":
    main()
