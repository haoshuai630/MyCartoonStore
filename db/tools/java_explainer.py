"""Java 源码逐行说明生成器。

原则：只写"为什么/约束/取值"，不复述代码。
- 有 javadoc 的方法/字段：用 javadoc
- 测试夹具：写出具体参数（哪个 SKU、多少钱、什么状态）
- 结构性代码（import、注解、括号、无注释的字段）：不写，页面渲染为「—」
"""

from __future__ import annotations

import re

from import_notes import IMPORT_NOTES

EXTRA = {
    'this.catalogService = catalogService;': "构造器注入：保存 CatalogService 字段",
    'int n = 0, i = 0;': "计数器与游标",
    'if (product == null || !"ON_SALE".equals(product.getStatus())) {': "不存在与已下架合并成同一个 404，不区分原因，避免泄露商品是否存在",
    'if (leaf == null) {': "分类缺失说明数据不一致，宁可 404 也不渲染半截分类路径",
    'if (raw == null || raw.isBlank()) {': "空值/空白表示未传该参数，按缺省处理",
    'if (value <= 0) {': "0 与负数不是合法分类 id",
    'if (!allowed.contains(value)) {': "白名单之外一律 400，不做大小写纠正或静默降级",
    'if (raw == null) {': "null 视为不搜索",
    'if (value.isEmpty()) {': "全是空白的关键字等价于没有关键字",
    'if (value.length() > KEYWORD_MAX_LENGTH) {': "超长拒绝而不是截断，避免用户以为搜到了全部结果",
    'if (keyword == null) {': "null 直接透传，由 SQL 侧决定不拼 LIKE 条件",
    'if (total <= 0) {': "total 为 0 时总页数直接给 0，避免臆造一个不存在的第 1 页",
    'public CatalogService(ProductMapper productMapper,': "构造器：注入商品、分类、相册三个 Mapper",
    'StoreUser findByUsername(@Param("username") String username);': "按用户名查账号，登录时使用",
    'SalesOrder lockByOrderNo(@Param("orderNo") String orderNo);': "按订单号加行锁，支付/取消/发货前调用",
    'SalesOrder findByOrderNo(@Param("orderNo") String orderNo);': "按订单号查订单（不加锁）",
}


def javadoc_above(lines, idx):
    i = idx - 1
    block = []
    while i >= 0:
        s = lines[i].strip()
        if s.startswith("@"):
            i -= 1
            continue
        if s.startswith(("*", "/**", "*/")):
            block.append(s)
            if s.startswith("/**"):
                break
            i -= 1
            continue
        break
    text = " ".join(block)
    text = text.replace("/**", "").replace("*/", "").replace("*", " ").strip()
    return re.sub(r"\s+", " ", text)


def looks_like_method(s):
    head = s.split("(")[0]
    return (
        "(" in s
        and s.rstrip().endswith(("{", ";"))
        and " " in head.strip()
        and "=" not in head
        and not s.startswith((".", "if", "for", "while", "return", "throw", "new ", "assert", "try", "} catch"))
    )


def brace_notes(lines):
    """给单独的 } 标出它结束的是方法还是类（按花括号深度判断）。"""
    notes = {}
    depth = 0
    for i, line in enumerate(lines, 1):
        s = line.strip()
        before = depth
        depth += s.count("{") - s.count("}")
        if s == "}":
            notes[i] = "结束类定义" if depth == 0 else ("结束方法" if depth == 1 else "结束内部代码块")
    return notes


def make_java_explainer(lines):
    braces = brace_notes(lines)

    def explain(n, line):
        s = line.strip()
        if not s:
            return ""
        if s.startswith(".andExpect(status("):
            return "断言 HTTP 状态码"
        if s.startswith(".andExpect("):
            return "断言响应内容"
        if s.startswith("model.addAttribute("):
            return "把数据放进模型，模板里可直接取"
        if s.startswith("return n;"):
            return "返回出现次数"
        if s.startswith("while ((i = body.indexOf"):
            return "寻找下一次出现的位置"
        if s.startswith("insertProduct(String.format"):
            return "循环插入 7 件单行本"
        if s.startswith("private void insertProduct"):
            return "插入商品的辅助方法"
        if s.startswith("public String handleBiz"):
            return "异常处理方法：设置状态码并返回错误页"
        if s.startswith("?"):
            return "三元分支：NOT_FOUND 给 404"
        if s.startswith(": HttpServletResponse"):
            return "其余按 400 处理"
        if s.startswith("new BigDecimal(price)"):
            return "价格用 BigDecimal 传入"
        if s.startswith("sku, categoryId, name"):
            return "对应 INSERT 的列值"
        if s.startswith('+ "VALUES'):
            return "SQL 续行：占位符与列数一一对应"
        if s == "/**":
            return "说明块开始：下面几行讲职责与约束"
        if s == "*/":
            return "说明块结束"
        if s == "*":
            return "说明续行"
        if "mockMvc.perform(" in s:
            return "发起请求，后面几条断言检查渲染结果"
        if s == "}" and n in braces:
            return braces[n]
        if s.startswith("import "):
            return IMPORT_NOTES.get(s.rstrip(";"), "")
        if s in ANNOTATION_NOTES or s.split("(")[0] in ANNOTATION_NOTES:
            return ANNOTATION_NOTES.get(s.split("(")[0], "")
        # 源码里写了行尾注释就直接用：注释本身已经是"为什么"，不再另编
        m = re.search(r"//\s*(.+?)\s*$", line)
        if m and not s.startswith("//") and "http" not in line:
            return m.group(1)
        if s in EXTRA:
            return EXTRA[s]

        # 方法：只有写了 javadoc 才给说明
        if looks_like_method(s):
            return javadoc_above(lines, n - 1)

        # 字段：只有写了 javadoc 才给说明
        m = re.match(r"(private|protected|public)\s+[\w<>\[\], .]+\s+(\w+)\s*(=|;)", s)
        if m:
            return javadoc_above(lines, n - 1)

        # 业务异常：说明转成什么
        if s.startswith("throw "):
            return "转成业务异常（400/404）"

        # 测试夹具：把参数写成一句人话
        m = re.match(r'insertProduct\("([^"]+)", [^,]+, "([\d.]+)", (\d+), "(\w+)", (\d+)\)', s)
        if m:
            return f"准备商品 {m.group(1)}：¥{m.group(2)}，库存 {m.group(3)}，{m.group(4)}，排序 {m.group(5)}"
        m = re.match(r'insertUser\("([^"]+)"\)', s)
        if m:
            return f"准备用户 {m.group(1)}"
        m = re.match(r'newSecondaryCategory\("([^"]+)"\)', s)
        if m:
            return f"准备分类：一级「作品-{m.group(1)}」+ 二级「单行本」"
        m = re.match(r'seedCategory\("([^"]+)", "([^"]+)"\)', s)
        if m:
            return f"准备分类「{m.group(2)}」（code={m.group(1)}）"
        m = re.match(r'insertOrder\("([^"]+)", (\w+), "(\w+)", "([\d.]+)"\)', s)
        if m:
            return f"准备订单 {m.group(1)}：{m.group(3)}，合计 {m.group(4)}"

        # 直接写库的测试数据
        if "jdbc.update(" in s or "jdbc.queryForObject(" in s:
            return "直接写库/查库，准备或核对测试数据"

        # 其余一律不写：结构性代码留空
        return ""

    return explain

ANNOTATION_NOTES = {
    "@Test": "JUnit 用例：运行时由框架发现并执行",
    "@DisplayName": "用例名：测试报告里显示这句，失败时先看它",
    "@BeforeEach": "每个用例前执行，用来重置数据",
    "@Autowired": "由 Spring 按类型注入",
    "@AutoConfigureMockMvc": "开启 MockMvc，不占端口就能请求页面",
    "@SpringBootTest": "启动完整上下文",
    "@ActiveProfiles": "启用 test 配置",
    "@ControllerAdvice": "全局异常处理类，所有控制器共享",
    "@ExceptionHandler": "指定这个方法处理哪类异常",
}