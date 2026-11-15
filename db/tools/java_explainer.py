"""Java 源码逐行说明生成器。

规则顺序：从最具体到最笼统；任何一条命中就返回。
最后兜底为"属于某方法的一步"，保证不出现空白、也不复述签名。
"""

from __future__ import annotations

import re

ANNOTATION_TEXT = {
    "@Mapper": "标记为 MyBatis Mapper，由 @MapperScan 注册",
    "@Param": "给参数起名，XML 里用 #{名字} 引用",
    "@Select": "注解 SQL：查询",
    "@Update": "注解 SQL：更新",
    "@Test": "JUnit 测试方法",
    "@BeforeEach": "每个用例前执行",
    "@DisplayName": "用例的可读名称",
    "@Autowired": "由 Spring 注入依赖",
    "@SpringBootTest": "启动完整上下文",
    "@ActiveProfiles": "启用 test 配置",
}


def javadoc_above(lines, idx):
    """向上找最近的 /** ... */ 块，取正文；中间允许隔着 @注解 行。"""
    i = idx - 1
    block = []
    while i >= 0:
        s = lines[i].strip()
        if s.startswith("@") or s.startswith("+"):
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


def display_name_above(lines, idx):
    for k in range(idx - 1, max(idx - 8, -1), -1):
        m = re.search(r'@DisplayName\("([^"]+)"\)', lines[k])
        if m:
            return "用例：" + m.group(1)
        if lines[k].strip().startswith("}"):
            break
    return ""


def looks_like_method(s):
    head = s.split("(")[0]
    return (
        "(" in s
        and s.rstrip().endswith(("{", ";"))
        and " " in head.strip()
        and "=" not in head
        and not s.startswith((".", "if", "for", "while", "return", "throw", "new ", "assert", "try", "} catch"))
    )


def enclosing_methods(lines):
    """扫描一遍，记录每行所属的方法名与说明，供兜底使用。"""
    owner = {}
    current = ""
    for i, line in enumerate(lines, 1):
        s = line.strip()
        if looks_like_method(s):
            doc = javadoc_above(lines, i - 1) or display_name_above(lines, i - 1)
            m = re.search(r"(\w+)\s*\(", s)
            name = m.group(1) if m else "方法"
            current = name + ("：" + doc if doc else "")
        owner[i] = current
    return owner


# 少数无法用规则概括的行，按代码原文补说明
EXTRA = {
    'StoreUser findByUsername(@Param("username") String username);': "按用户名查账号，登录时使用",
    'SalesOrder lockByOrderNo(@Param("orderNo") String orderNo);': "按订单号加行锁，支付/取消/发货前调用",
    'SalesOrder findByOrderNo(@Param("orderNo") String orderNo);': "按订单号查订单（不加锁）",
    'public CatalogService(ProductMapper productMapper,': "构造器：注入商品、分类、相册三个 Mapper",
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
}

def make_java_explainer(lines):
    owner = enclosing_methods(lines)

    def explain(n, line):
        s = line.strip()
        if not s:
            return ""

        if s in EXTRA:
            return EXTRA[s]

        if s.startswith("package"):
            return "包声明"
        if s.startswith("import"):
            return "导入"
        if s.startswith("//"):
            return "分段注释"
        if s.startswith("/**"):
            return "注释：说明该类型的职责与约束"
        if s.startswith(("*", "*/")):
            return "注释续行"

        if s.startswith("@") or s.startswith("+"):
            for key, text in ANNOTATION_TEXT.items():
                if s.startswith(key):
                    return text
            return "注解"

        m = re.match(r"(public|private|protected)?\s*(final|abstract)?\s*(class|interface|record|enum)\s+(\w+)", s)
        if m:
            kind = m.group(3)
            if kind == "interface":
                base = re.search(r"BaseMapper<(\w+)>", s)
                return "接口：继承 BaseMapper<" + base.group(1) + ">，获得基础 CRUD" if base else "接口声明"
            if kind == "record":
                return "record：不可变数据载体，编译器生成构造器与访问器"
            return "类声明" + ("（抽象基类）" if "abstract" in s else "")

        if s.endswith(",") and re.match(r"^[A-Z][\w<>, ]*\s+\w+,$", s):
            return "record 组件"
        if s.endswith(") {") and re.match(r"^[A-Z][\w<>, ]*\s+\w+\)", s):
            return "record 组件（最后一个）"

        if looks_like_method(s):
            doc = javadoc_above(lines, n - 1) or display_name_above(lines, n - 1)
            return doc

        m = re.match(r"(private|protected|public)\s+[\w<>\[\], .]+\s+(\w+)\s*(=|;)", s)
        if m:
            return javadoc_above(lines, n - 1) or ("字段 " + m.group(2))
        if "[] " in s and s.endswith("= {"):
            return "常量数组"
        if re.match(r'"[a-z_]+",?$', s):
            return "常量项：表名"
        if s == "};":
            return "数组结束"

        if s.startswith("try ("):
            return "try-with-resources：连接/语句自动关闭"
        if "createStatement()) {" in s:
            return "资源声明续行"
        if s.startswith("} catch ("):
            return "异常包装成运行时异常，保留原始 cause"
        if s.startswith("for ("):
            return "循环处理"
        if s.startswith("throw "):
            return "转成业务异常（400/404）"
        if s.startswith("return "):
            return "返回结果"

        chain = {
            ".eq(": "等值条件",
            ".isNull(": "限定字段为空",
            ".orderByAsc(": "按该列升序",
            ".orderByDesc(": "按该列降序",
            ".isInstanceOf(": "断言抛出的异常类型",
            ".hasMessageContaining(": "断言异常信息包含关键字",
            ".containsExactly(": "断言顺序与内容完全一致",
            ".isEqualTo(": "断言等于期望值",
            ".isZero(": "断言结果为 0",
            ".hasSize(": "断言集合大小",
            ".extracting(": "取出字段做断言",
        }
        for prefix, text in chain.items():
            if s.startswith(prefix):
                return text
        if s.startswith("Wrappers."):
            return "构造 Lambda 查询"
        if s.startswith("this.") and s.endswith(";"):
            return "构造器注入：保存为字段"
        if s.startswith("query.set") or s.startswith("command.set"):
            return "写入查询条件"
        if s.startswith("long value = Long.parseLong"):
            return "解析数字；失败由 catch 转 400"
        if s.startswith(".replace("):
            return "转义 LIKE 通配符"
        if s.startswith("assertThat"):
            return "断言预期结果"

        m = re.match(r'insertProduct\("([^"]+)", [^,]+, "([\d.]+)", (\d+), "(\w+)", (\d+)\)', s)
        if m:
            return "准备商品 " + m.group(1) + "：¥" + m.group(2) + "，库存 " + m.group(3) + "，" + m.group(4) + "，排序 " + m.group(5)
        m = re.match(r'insertUser\("([^"]+)"\)', s)
        if m:
            return "准备用户 " + m.group(1)
        m = re.match(r'newSecondaryCategory\("([^"]+)"\)', s)
        if m:
            return "准备分类：一级「作品-" + m.group(1) + "」+ 二级「单行本」"
        m = re.match(r'insertPrimaryCategory\("([^"]+)", "([^"]+)"\)', s)
        if m:
            return "准备一级分类「" + m.group(2) + "」"
        m = re.match(r'insertSecondaryCategory\("([^"]+)", (\w+), "([^"]+)"\)', s)
        if m:
            return "准备二级分类「" + m.group(3) + "」"
        m = re.match(r'insertOrder\("([^"]+)", (\w+), "(\w+)", "([\d.]+)"\)', s)
        if m:
            return "准备订单 " + m.group(1) + "：" + m.group(3) + "，合计 " + m.group(4)
        if "jdbc." in s or "jdbcTemplate." in s:
            return "直接写库/查库，用于准备与核对测试数据"

        if s.endswith('");') and s.count(",") >= 3:
            return "SQL 参数续行"
        if s == "}":
            return "结束"
        if re.match(r"^\w+, \w+, ", s):
            return "参数续行"
        if re.match(r"^[a-z]\w*, \w+, ", s):
            return "SQL 参数续行"
        if owner.get(n):
            return "属于 " + owner[n] + " 的一步"
        if s.endswith(");"):
            return "语句续行"
        return "属于 " + (owner.get(n) or "该类") + " 的一步"

    return explain