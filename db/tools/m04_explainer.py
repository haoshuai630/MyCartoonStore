"""M04 逐行说明的补充规则：从代码参数里取事实，替掉生成器的兜底句。

原则与 java_explainer 一致：说明回答"为什么/约束/取值"，不复述代码。
这里的规则只处理"生成器没有专门规则、但代码本身带着信息"的行：
测试夹具的具体参数、断言的目标值、异常的文案与状态码。
"""
from __future__ import annotations

import re

# java_explainer 在没有规则时给的几句话，出现它们就说明需要这里补
GENERIC = (
    "位于当前方法内",
    "语句：执行本行",
    "参数续行",
    "由构造器注入或在本类内初始化",
    "转成业务异常（400/404）",
)

TYPE_NOTES = {
    "CartItemMapper": "购物车数据访问",
    "ProductMapper": "商品数据访问（只读当前状态与库存）",
    "CartService": "购物车服务",
    "MockMvc": "MockMvc：不起端口直接请求页面",
    "JdbcTemplate": "JdbcTemplate：直接建/查测试数据",
}

ERROR_NAMES = {
    "badRequest": "400",
    "notFound": "404",
    "forbidden": "403",
    "conflict": "409",
}


def is_generic(text: str) -> bool:
    return any(marker in text for marker in GENERIC)


def _product(inner: str) -> str:
    m = re.match(r'(\w+),\s*"([\d.]+)",\s*(\d+),\s*"(\w+)"', inner.strip())
    if not m:
        return ""
    return f"商品 {m.group(1)}：¥{m.group(2)}、库存 {m.group(3)}、{m.group(4)}"


def _item(inner: str) -> str:
    m = re.match(r"([\w.]+),\s*([\w.]+),\s*([\w.]+),\s*(\d+),\s*([\w.]+)", inner.strip())
    if not m:
        return ""
    return (f"条目 {m.group(1)}：用户 {m.group(2)}、商品 {m.group(3)}、"
            f"数量 {m.group(4)}、版本 {m.group(5)}")


def _view(inner: str) -> str:
    m = re.match(r'([\w.]+),\s*([\w.]+),\s*(\d+),\s*"([\d.]+)",\s*(\d+),\s*"(\w+)",\s*([\w.]+)',
                 inner.strip())
    if not m:
        return ""
    return (f"视图行：数量 {m.group(3)}、价格 {m.group(4)}、"
            f"库存 {m.group(5)}、{m.group(6)}、版本 {m.group(7)}")


def _value(expr: str) -> str:
    expr = expr.strip()
    for factory, render in (("product(", _product), ("item(", _item), ("view(", _view)):
        if expr.startswith(factory):
            inner = expr[len(factory):-1]
            text = render(inner)
            if text:
                return text
    if expr == "null":
        return "不存在（null）"
    if expr.isdigit():
        return f"返回 {expr}"
    return ""


def explain(s: str, javadoc: str = "") -> str | None:
    """返回一句针对本行的说明；认不出来时返回 None，交给生成器原样处理。"""

    # ---- 测试准备：when(...).thenReturn(...)
    m = re.match(r"when\((.+?)\)\.thenReturn\((.+)\);$", s)
    if m:
        call, value = m.group(1), m.group(2)
        target = _value(value)
        if "countByUser" in call:
            return f"车里已有 {value.strip()} 种商品"
        if "sumQuantityByUser" in call:
            return f"徽标求和返回 {value.strip()}"
        if "findByUserAndProduct" in call and target:
            return "已有条目：" + target
        if "selectById" in call and target:
            return "准备商品：" + target
        if "selectViewByUser" in call:
            return "准备购物车行列表（含可用与失效行）"
        if "insert" in call:
            return "插入时抛唯一键冲突，模拟并发加车"
        if "deleteByIdAndUser" in call or "updateQuantity" in call:
            return f"写库影响 {value.strip()} 行"
        return ("准备返回值：" + target) if target else None

    # ---- 断言验证：verify(...)
    m = re.match(r"verify\((\w+)(?:,\s*never\(\))?\)\.(\w+)\((.*)\);$", s)
    if m:
        repo, method, args = m.group(1), m.group(2), m.group(3)
        never = ", never()" in s
        prefix = "确认没有调用" if never else "确认调用"
        if method == "insert":
            return prefix + "插入（新增条目）"
        if method == "updateQuantity":
            nums = [a.strip() for a in args.split(",")]
            if len(nums) == 4:
                return f"{prefix} updateQuantity：用户 {nums[0]}、条目 {nums[1]}、数量 {nums[2]}、旧版本 {nums[3]}"
            return prefix + " updateQuantity"
        if method == "deleteByIdAndUser":
            return prefix + "按归属与版本删除"
        return f"{prefix} {method}"

    # ---- 断言：assertThat(...)
    m = re.match(r"assertThat\((.*)\)\.(isEqualTo|isZero|isTrue|isFalse|isEqualByComparingTo)\((.*)\);$", s)
    if m:
        target, op, expected = m.group(1), m.group(2), m.group(3)
        label = {
            "CartFixture.quantityOf(jdbc, itemId)": "库中该条目数量",
            "CartFixture.versionOf(jdbc, itemId)": "库中该条目版本",
            "CartFixture.countItems(jdbc, userId)": "该用户购物车条目数",
            "CartFixture.countItems(jdbc, owner)": "被越权用户的条目数",
            "summary.getBadgeQuantity()": "徽标件数",
            "summary.getValidQuantity()": "可结算件数",
            "summary.getTotalAmount()": "应付总额",
            "summary.getInvalidCount()": "失效条目数",
            "summary.isAllAvailable()": "是否全部可用",
            "summary.isEmpty()": "是否空车",
            "summary.isSettleable()": "是否可结算",
            "service.badgeQuantity(USER)": "徽标查询结果",
        }.get(target.strip(), target.strip())
        if op == "isEqualTo":
            return f"断言{label}等于 {expected}"
        if op == "isEqualByComparingTo":
            return f"断言{label}等于 {expected.strip(chr(34))}"
        if op == "isZero":
            return f"断言{label}为 0"
        if op == "isTrue":
            return f"断言{label}为真"
        if op == "isFalse":
            return f"断言{label}为假"

    # ---- 断言异常
    if s.startswith("assertThatThrownBy(() -> "):
        call = s[len("assertThatThrownBy(() -> "):].rstrip(")")
        return f"调用 {call}，期望抛出业务异常"
    if s.startswith(".isInstanceOfSatisfying("):
        return "断言异常类型，并继续检查错误码"
    if s.startswith("e -> assertThat(e.code())"):
        m = re.search(r"ErrorCode\.(\w+)", s)
        return f"错误码必须是 {m.group(1)}（{ERROR_NAMES.get(m.group(1), '')}）" if m else None
    if s.startswith(".hasMessageContaining("):
        msg = re.findall(r'"([^"]+)"', s)
        return f"断言提示包含「{msg[0]}」" if msg else None
    if s.startswith(".as("):
        return "失败时用这个标签定位是哪一组输入"

    # ---- 请求构造
    m = re.match(r'\.param\("(\w+)",\s*(.+)\)\)?$', s)
    if m:
        name, value = m.group(1), m.group(2).rstrip(")")
        return f"表单参数 {name}={value.replace('String.valueOf(', '').rstrip(')')}"
    if s.startswith(".session("):
        return "以该用户身份请求（会话里带 userId）"
    if s.startswith(".andExpect(redirectedUrl"):
        url = re.findall(r'"([^"]+)"', s)
        return f"断言重定向到 {url[0]}" if url else None
    if s.startswith("mockMvc.perform("):
        return "发起请求，随后断言状态与内容"

    # ---- 异常
    m = re.match(r'throw BizException\.(\w+)\("(.*)"\);$', s)
    if m:
        msg = re.sub(r'"\s*\+\s*([\w.]+)\s*\+\s*"', r"{\1}", m.group(2))
        return f"抛 {ERROR_NAMES.get(m.group(1), '业务异常')}：{msg}"
    m = re.match(r"throw BizException\.(\w+)\(\"(.+?)\" \+ (.+)\);$", s)
    if m:
        return f"抛 {ERROR_NAMES.get(m.group(1), '业务异常')}，文案拼接 {m.group(3)}"

    # ---- 测试夹具
    m = re.match(r'long (\w+) = CartFixture\.createUser\(jdbc, "(\w+)"\);$', s)
    if m:
        return f"建测试账号 {m.group(2)}，返回用户 id"
    m = re.match(r'long (\w+) = CartFixture\.createProduct\(jdbc, "([\w-]+)", "([\d.]+)", (\d+), "(\w+)"\);$', s)
    if m:
        return f"建商品 {m.group(2)}：¥{m.group(3)}、库存 {m.group(4)}、{m.group(5)}"
    m = re.match(r"long (\w+) = CartFixture\.addCartItem\(jdbc, (\w+), (\w+), (\d+)\);$", s)
    if m:
        return f"直接放 {m.group(4)} 件到 {m.group(2)} 的车里，返回条目 id"
    if s.startswith("MockHttpSession session = CartFixture.session("):
        return "同一个会话连发两次请求，验证累加而不是新增"
    m = re.match(r'jdbc\.update\("UPDATE cart_item SET version = (\d+) WHERE id = \?", (\w+)\);$', s)
    if m:
        return f"把版本改成 {m.group(1)}，模拟其他会话已修改"

    # ---- 夹具方法
    if s.startswith("private Product product("):
        return "夹具：按参数造一个商品（价格、库存、状态）"
    if s.startswith("private CartItem item("):
        return "夹具：按参数造一个购物车条目（归属、商品、数量、版本）"
    if s.startswith("private CartItemView view("):
        return "夹具：按参数造一行购物车视图（含状态与库存）"
    m = re.match(r"Product p = new Product\(\);$", s)
    if m:
        return "新建商品实体，逐项赋值后返回"
    if s.startswith("CartItem i = new CartItem();"):
        return "新建条目实体，逐项赋值后返回"
    m = re.match(r"p\.(set\w+)\(", s)
    if m:
        setting = {
            "setId": "设置商品 id",
            "setName": "设置商品名，断言里按名字找",
            "setPrice": "价格用 BigDecimal，避免 double 误差",
            "setStock": "设置库存：失效与超卖用例都靠它",
            "setStatus": "设置 ON_SALE / DRAFT：决定能否加车与结算",
        }.get(m.group(1), "逐项设置商品字段")
        return "夹具：" + setting
    m = re.match(r"i\.(set\w+)\(", s)
    if m:
        setting = {
            "setId": "设置条目 id",
            "setUserId": "设置归属用户，越权用例靠它",
            "setProductId": "设置商品 id",
            "setQuantity": "设置数量",
            "setVersion": "设置版本：旧页面用例靠它",
        }.get(m.group(1), "逐项设置条目字段")
        return "夹具：" + setting
    if s == "return p;":
        return "返回构造好的商品"
    if s == "return i;":
        return "返回构造好的条目"

    # ---- 字段与构造器
    m = re.match(r"(?:private|protected|public)\s+(?:final\s+)?([\w<>,.\[\] ]+?)\s+(\w+);$", s)
    if m:
        type_name, field = m.group(1).strip(), m.group(2)
        if field == "USER":
            return "测试固定用 1 号用户"
        if type_name in TYPE_NOTES:
            return f"{field}：{TYPE_NOTES[type_name]}"
        return f"{field}（{type_name}）" + (f"：{javadoc}" if javadoc else "")
    m = re.match(r"this\.(\w+) = (\w+);$", s)
    if m:
        return f"构造器注入：保存 {m.group(1)} 到字段"
    if s == "value = Integer.parseInt(raw.trim());":
        return "解析失败在 catch 里转 400，不吞异常"

    # ---- 简单返回
    simple_returns = {
        'return "redirect:/cart";': "成功后回购物车页（PRG：刷新不会重复提交）",
        "return !available;": "不可结算即失效行",
        "return lines.isEmpty();": "没有行就是空车",
        "return !lines.isEmpty() && allAvailable;": "非空且全部可用才允许结算",
        "return product;": "返回查到的商品",
        "return item;": "返回查到的条目",
        "return;": "本条路径不再继续（只加模型或只跳过徽标）",
    }
    if s in simple_returns:
        return simple_returns[s]

    m = re.match(r"if \(rows == 0\) \{$", s)
    if m:
        return "影响 0 行：并发下条目已被改动或删除"
    m = re.match(r"if \(existing == null\) \{$", s)
    if m:
        return "并发窗口里条目被删：按冲突让用户刷新"
    if s == "BigDecimal total = BigDecimal.ZERO.setScale(MoneyUtils.SCALE);":
        return "从 0.00 开始累加，避免 null 与精度问题"
    if s == "for (CartLine line : lines) {":
        return "逐行汇总：件数全算，金额只算可用行"

    # ---- 生产代码里的固定写法
    production = {
        'int quantity = parseQuantity(quantityRaw);': "数量先解析：不是 1–99 的整数直接 400",
        'long productId = parseId(productIdRaw, "商品编号");': "商品编号先解析：非正整数 400",
        'long itemId = parseId(itemIdRaw, "条目编号");': "条目编号先解析：非正整数 400",
        "long version = parseVersion(versionRaw);": "版本号先解析：缺失或非法按旧页面处理（409）",
        "ensureStock(quantity, product);": "库存校验：0 件缺货、超过库存给出可买上限",
        "ensureStock(newQuantity, product);": "累加后的数量同样不能超过库存",
        "CartItem item = requireOwnItem(userId, itemId);": "查条目并校验归属：不存在 404、非本人 403",
        "requireVersion(item, version);": "旧页面检测：版本不一致 409",
        "long value = Long.parseLong(raw.trim());": "解析成 long；格式错误在 catch 里转 400",
        "if (newQuantity > MAX_QUANTITY) {": "累加后超过 99：拒绝，原数量不动",
        "if (stock <= 0) {": "库存 0：按缺货处理，不给出可买上限",
        "if (cartItemMapper.countByUser(userId) >= MAX_KINDS) {": "已有 20 种：拦住第 21 种，提示先移除",
        "if (value < 1 || value > MAX_QUANTITY) {": "越界数量（0、负数、100）统一 400",
        "if (value < 0) {": "负版本号只可能来自伪造或旧页面：409",
        "CartItem item = new CartItem();": "组装新条目：归属、商品、数量、版本 0",
        "try {": "插入可能撞唯一键，所以包在 try 里",
        "cartItemMapper.insert(item);": "插库；并发撞唯一键由上面的 catch 兜底",
        "CartItem existing = cartItemMapper.findByUserAndProduct(userId, productId);":
            "先查已有条目：有则累加，无则按种类上限新增",
        "mergeInto(cartItemMapper.findByUserAndProduct(userId, productId), quantity, product);":
            "并发冲突后重查条目，按累加再试一次",
        "Product product = productMapper.selectById(item.getProductId());":
            "重查商品：加车后状态与库存可能已经变了",
        "CartItem item = cartItemMapper.selectById(itemId);":
            "按 id 查条目，先读归属才能区分 404 与 403",
        "int rows = cartItemMapper.updateQuantity(": "带旧版本更新，返回影响行数",
        "existing.getUserId(), existing.getId(), newQuantity, existing.getVersion());":
            "把条目自身的归属、id、新数量、旧版本传给 SQL",
        "return CartSummary.of(lines(userId));": "行列表交给汇总对象：件数与金额只算一次",
        "HttpSession session = request.getSession(false);":
            "不主动建会话：游客请求不该被建出一个空会话",
        "@RequestParam String version,": "版本号必填；缺参数由 Spring 直接给 400",
        "Integer.class, userId);": "查单值：数量或版本",
        "String status, long version) {": "方法参数续行：行视图剩余字段",
        "item.setUserId(userId);": "新条目归属当前用户",
        "item.setProductId(productId);": "新条目对应商品",
        "item.setQuantity(quantity);": "首次加入的数量",
        "item.setVersion(0L);": "新条目版本从 0 开始，页面带回 0 才能改",
        "String reason = null;": "可用时原因为空，失效时再填",
        "if (!onSale) {": "下架优先于库存判断：已下架就不必提示库存",
        'reason = "已下架";': "下架原因固定文案",
        'reason = "库存不足，仅剩 " + (view.getStock() == null ? 0 : view.getStock()) + " 件";':
            "库存不足时把剩余件数写进原因，用户能判断改多少",
        "return new CartLine(view.getId(), view.getProductId(), view.getProductName(), view.getCoverImage(),":
            "构造行对象：条目 + 商品当前信息一起传入",
        "view.getCategoryName(), view.getPrice(), view.getQuantity(),": "续行：分类、单价、数量",
        "view.getStock() == null ? 0 : view.getStock(), view.getProductStatus(), view.getVersion(),":
            "续行：库存（null 当 0）、状态、版本",
        "onSale && enough, reason, amount);": "可用 = 上架且有库存；原因与行小计一并传入",
        "private CartLine(Long itemId, Long productId, String productName, String coverImage, String categoryName,":
            "构造器：一行需要的全部字段（只读，构造后不再变）",
        "BigDecimal price, int quantity, int stock, String productStatus, long version,":
            "构造器参数续行：价格、数量、库存、状态、版本",
        "boolean available, String unavailableReason, BigDecimal lineAmount) {":
            "构造器参数续行：可用性、失效原因、行小计",
        "int badge = 0;": "三个计数器：徽标、可结算件数、失效条数",
        "int valid = 0;": "可结算件数",
        "int invalid = 0;": "失效条数",
        "valid += line.getQuantity();": "只有可用行才计入可结算件数",
        "total = total.add(line.getLineAmount());": "金额也只累加可用行的小计",
        "} else {": "不可用分支：只记失效条数",
        "invalid++;": "失效条数 +1，页面上要提示处理",
        "private CartSummary(List<CartLine> lines, int badgeQuantity, int validQuantity,":
            "构造器：行与三个汇总值由 of() 算好后传入",
        "BigDecimal totalAmount, boolean allAvailable, int invalidCount) {":
            "构造器参数续行：总额、是否全可用、失效条数",
        "class CartServiceTest {": "测试类：Mapper 全部 Mock，只验证 Service 分支",
        "cartItemMapper = mock(CartItemMapper.class);": "Mock 购物车 Mapper",
        "productMapper = mock(ProductMapper.class);": "Mock 商品 Mapper",
        "service = new CartService(cartItemMapper, productMapper);": "被测服务：注入两个 Mock",
        "for (String raw : List.of(\"0\", \"-1\", \"100\", \"1.5\", \"abc\", \"\")) {":
            "逐个非法输入验证：每一种都不能写库",
        ".thenReturn(null, item(77L, USER, 9L, 1, 0L));":
            "第一次 null（插入前），第二次已有条目（冲突后重查）",
        "CartItemView v = new CartItemView();": "夹具：新建一行购物车视图",
        "v.setProductName(\"测试商品\" + productId);": "夹具：名字带商品 id，断言里好定位",
    }
    if s in production:
        return production[s]

    m = re.match(r'throw BizException\.badRequest\(fieldName \+ "(.*)"\);$', s)
    if m:
        return f"抛 400：文案带上字段名（{m.group(1)}）"

    m = re.match(r"service\.(\w+)\((.*)\);$", s)
    if m:
        call_once = {
            "add": "调用加车",
            "changeQuantity": "调用改数量",
            "remove": "调用移除",
            "summary": "调用汇总",
            "badgeQuantity": "调用徽标查询",
        }.get(m.group(1), "调用 " + m.group(1))
        return f"{call_once}：{m.group(2)}"

    m = re.match(r"v\.(set\w+)\(", s)
    if m:
        setting = {
            "setId": "夹具：行 id",
            "setProductId": "夹具：商品 id",
            "setProductName": "夹具：商品名",
            "setCoverImage": "夹具：封面路径（测试不校验图片是否存在）",
            "setCategoryName": "夹具：分类名",
            "setPrice": "夹具：当前单价",
            "setQuantity": "夹具：数量",
            "setStock": "夹具：当前库存",
            "setProductStatus": "夹具：商品状态（ON_SALE / DRAFT）",
            "setVersion": "夹具：版本号",
        }.get(m.group(1), "夹具：逐项设置行字段")
        return setting

    if s.startswith('@Select("') or s.startswith('@Update("') or s.startswith('@Delete("'):
        if "SUM(quantity)" in s:
            return "SQL：按用户求数量之和，空车返回 0"
        if "version = version + 1" in s:
            return "SQL：改数量并把版本 +1，条件含归属与旧版本"
        if "DELETE FROM cart_item" in s:
            return "SQL：删除条目，条件含归属与版本"
        return "注解 SQL：条件里带归属，避免越权"
    if s.startswith('+ "WHERE id = #{itemId}'):
        return "SQL 续行：归属与版本条件，影响 0 行即冲突"
    if s.startswith('int updateQuantity(@Param'):
        return "改数量方法签名：四个参数都参与 WHERE 或 SET"
    if s.startswith('@Param("quantity") int quantity'):
        return "方法参数续行：新数量与旧版本"
    if s.startswith('int deleteByIdAndUser(@Param'):
        return "删除方法签名：归属与版本参与 WHERE"
    if s.startswith('@Param("version") long version);'):
        return "方法参数续行：旧版本"

    # ---- 控制器 / 拦截器 / 测试里的固定写法
    extra = {
        "int value;": "先声明，try 里再赋值",
        "private static final long USER = 1L;": "测试固定用 1 号用户",
        'return "cart";': "渲染购物车模板",
        '@GetMapping("/cart")': "路由：购物车页（登录后才能进）",
        '@PostMapping("/cart/items")': "路由：加入购物车",
        '@PostMapping("/cart/items/{itemId}/quantity")': "路由：改数量",
        '@PostMapping("/cart/items/{itemId}/remove")': "路由：移除条目",
        "public String addItem(@RequestParam String productId,": "加车入口：商品与数量按原始字符串交给服务层",
        '@RequestParam(defaultValue = "1") String quantity,': "数量缺省 1：详情页不填也能加一件",
        "@RequestParam String quantity,": "数量必填（改数量场景）",
        "public String changeQuantity(@PathVariable String itemId,": "改数量入口：条目 id 来自路径",
        "public String removeItem(@PathVariable String itemId,": "移除入口：条目 id 来自路径",
        "cartService.add(currentUserId(session), productId, quantity);": "取当前用户后交给服务层加车",
        "cartService.changeQuantity(currentUserId(session), itemId, quantity, version);":
            "取当前用户后交给服务层校验归属与版本",
        "cartService.remove(currentUserId(session), itemId, version);": "取当前用户后交给服务层删除",
        'redirect.addFlashAttribute("message", "数量已更新");': "一次性提示：数量已更新",
        'redirect.addFlashAttribute("message", "已移除该商品");': "一次性提示：已移除",
        "Object userId = session.getAttribute(LoginInterceptor.USER_ID);": "从会话取当前用户 id",
        "if (userId instanceof Long id) {": "只认 Long：会话可能没有该属性或类型不对",
        "return id;": "返回用户 id",
        "public class CartBadgeInterceptor implements HandlerInterceptor {": "徽标拦截器：渲染前把件数放进模型",
        "public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,":
            "postHandle：处理器执行完、视图渲染前",
        "ModelAndView modelAndView) {": "参数续行：这次请求的视图与模型",
        "if (modelAndView == null) {": "没有视图（如异步请求）就跳过",
        "String viewName = modelAndView.getViewName();": "视图名用来识别重定向",
        "Object userId = session == null ? null : session.getAttribute(LoginInterceptor.USER_ID);":
            "不建会话；游客按 0 处理",
        "modelAndView.addObject(MODEL_KEY, count);": "把 cartCount 放进模型，页头片段取用",
        '.addPathPatterns("/cart", "/cart/**", "/orders", "/orders/**", "/profile", "/profile/**");':
            "受保护路径集中列在这里，新增公开页不用改",
        "registry.addInterceptor(new CartBadgeInterceptor(cartService));": "徽标拦截器对所有路径生效",
        "return true;": "已登录：放行",
        "String encoded = URLEncoder.encode(returnTarget(request), StandardCharsets.UTF_8);":
            "把回跳地址编码后放进 redirect 参数",
        'response.sendRedirect(request.getContextPath() + "/login?redirect=" + encoded);':
            "302 到登录页",
        "return false;": "拦下请求，不再进入控制器",
        "String fromReferer = sameSitePath(request);": "POST 时取来源页作为回跳目标",
        "if (fromReferer != null) {": "来源可信就用它，否则退回原地址逻辑",
        "return fromReferer;": "回来源页（详情页）",
        "String target = request.getRequestURI();": "GET：用被拦的原地址",
        'target = target + "?" + request.getQueryString();': "查询串一并带回，翻页与筛选不丢",
        "return target;": "返回原地址",
        'String referer = request.getHeader("Referer");': "取 Referer",
        "if (referer == null || referer.isBlank()) {": "没有来源就退回原地址逻辑",
        "URI uri;": "先声明，try 里解析",
        "uri = URI.create(referer);": "解析失败按非法来源处理",
        "} catch (IllegalArgumentException e) {": "非法 URL：当作没有来源",
        "if (uri.getScheme() == null || uri.getHost() == null) {": "相对地址或畸形地址不接受",
        "if (!uri.getScheme().equalsIgnoreCase(request.getScheme())": "协议必须一致，防 https→http 混用",
        "|| !Objects.equals(uri.getHost(), request.getServerName())) {": "主机名也必须一致，否则不是同源",
        "int port = uri.getPort() == -1": "省略端口时按协议默认端口补",
        ": uri.getPort();": "写了端口就用写的那个",
        "String path = uri.getRawPath();": "只取路径与查询串，不取 scheme/host",
        "if (path == null || path.isBlank()) {": "没路径就回首页",
        'return "/main";': "兜底回首页",
        "return uri.getRawQuery() == null ? path : path + \"?\" + uri.getRawQuery();":
            "带查询串一起回跳",
        "class CartPageTest extends AbstractIntegrationTest {": "集成测试：真实 MySQL + MockMvc",
        "class CartMutationTest extends AbstractIntegrationTest {": "集成测试：写操作走真实 SQL",
        "when(cartItemMapper.findByUserAndProduct(USER, 9L))": "第一次查不到已有条目",
        "when(cartItemMapper.insert(any(CartItem.class)))": "让插入抛唯一键冲突",
        '.thenThrow(new DuplicateKeyException("uk_cart_user_product"));': "模拟并发加车撞唯一键",
        "when(cartItemMapper.selectViewByUser(USER)).thenReturn(List.of(": "准备三行：两件可用、一件库存 0、一件下架",
        "return v;": "返回构造好的视图行",
        "for (int i = 0; i < 20; i++) {": "放满 20 种商品",
        'long productId = CartFixture.createProduct(jdbc, "FULL-" + i, "28.00", 10, "ON_SALE");':
            "每轮建一个不同 SKU 的商品",
        "CartFixture.addCartItem(jdbc, userId, productId, 1);": "每种放 1 件",
        '.param("productId", String.valueOf(productId)).param("quantity", "1").session(session));':
            "同会话加 1 件",
        '.param("productId", String.valueOf(productId)).param("quantity", "2").session(session));':
            "同会话再加 2 件，验证累加",
        '.header("Referer", "http://localhost/product/" + productId))':
            "带同源 Referer，模拟从详情页点加车",
    }
    if s in extra:
        return extra[s]

    m = re.match(r"CartSummary summary = service\.summary\((.*)\);$", s)
    if m:
        return f"调用汇总：拿到件数、总额与失效数（用户 {m.group(1)}）"

    m = re.match(r"CartFixture\.addCartItem\(jdbc, (\w+), (\w+), (\d+)\);$", s)
    if m:
        return f"预置 {m.group(3)} 件到 {m.group(2)} 的车里"

    if s.startswith("view("):
        inner = s[len("view("):].rstrip(");,")
        text = _view(inner)
        if text:
            return text

    # ---- 构造器 / 分支 / 空车
    m = re.match(r"^public (\w+)\((.+)\) \{$", s)
    if m and m.group(1) not in {"String", "int", "long", "boolean", "void", "CartSummary"}:
        params = m.group(2)
        names = [p.strip().split()[-1] for p in params.split(",") if p.strip()]
        return f"构造器：注入 {('、'.join(names))}"
    if s == "} else if (!enough) {":
        return "不是下架，那就是数量超过库存"
    if s == "public boolean isEmpty() {":
        return "没有行即空车"

    # ---- 类声明
    m = re.match(r"public class (\w+) \{", s)
    if m:
        return javadoc or f"{m.group(1)}：见类注释"

    return None
