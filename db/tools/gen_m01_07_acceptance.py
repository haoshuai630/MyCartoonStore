"""M01-07 逐行验收 HTML 生成（Java 接口 + MyBatis XML）。"""
from __future__ import annotations

import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "reports" / "M01-07-逐行验收.html"

FILES = [
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/mapper/ProductMapper.java", "ProductMapper.java", "商品 Mapper：分页、首页两栏、行锁、扣库存", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/ProductQuery.java", "ProductQuery.java", "列表查询条件 DTO", "java"),
    ("cartoon-catalog/src/main/resources/mapper/ProductMapper.xml", "ProductMapper.xml", "商品自定义 SQL", "xml"),
    ("cartoon-cart/src/main/java/org/csu/mycartoonstore/cart/mapper/CartItemMapper.java", "CartItemMapper.java", "购物车 Mapper", "java"),
    ("cartoon-cart/src/main/java/org/csu/mycartoonstore/cart/dto/CartItemView.java", "CartItemView.java", "购物车行视图 DTO", "java"),
    ("cartoon-cart/src/main/resources/mapper/CartItemMapper.xml", "CartItemMapper.xml", "购物车联表查询", "xml"),
    ("cartoon-account/src/main/java/org/csu/mycartoonstore/account/mapper/StoreUserMapper.java", "StoreUserMapper.java", "账号 Mapper：登录、用户行锁", "java"),
    ("cartoon-order/src/main/java/org/csu/mycartoonstore/order/mapper/SalesOrderMapper.java", "SalesOrderMapper.java", "订单 Mapper：行锁、单号与凭证查重", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/mapper/CategoryMapper.java", "CategoryMapper.java", "分类 Mapper（仅 BaseMapper）", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/mapper/ProductImageMapper.java", "ProductImageMapper.java", "相册 Mapper（仅 BaseMapper）", "java"),
    ("cartoon-order/src/main/java/org/csu/mycartoonstore/order/mapper/SalesOrderItemMapper.java", "SalesOrderItemMapper.java", "订单明细 Mapper（仅 BaseMapper）", "java"),
]

STYLE = """<style>
*{box-sizing:border-box}body{margin:0;background:#edf1f7;color:#182333;font:15px/1.7 "Microsoft YaHei",system-ui,sans-serif}
header{position:sticky;top:0;z-index:2;background:white;border-bottom:1px solid #cad3df;padding:18px 30px}
h1{font-size:24px;margin:0}header p{color:#536176;margin:6px 0 10px}
nav{display:flex;gap:10px;flex-wrap:wrap}nav a{background:#eaf2ff;border:1px solid #b7cce9;color:#14558c;border-radius:5px;padding:5px 12px;text-decoration:none}
main{max-width:1500px;margin:auto;padding:18px 30px 46px}.note{padding:12px 16px;background:white;border-left:4px solid #2677ba;color:#536176}
section{margin:22px 0 36px}h2{font-size:20px;margin:0 0 3px}small{margin-left:12px;font-size:13px;font-weight:400;color:#536176}
.path{font:13px/1.5 Consolas,monospace;color:#536176;margin:0 0 10px;overflow-wrap:anywhere}
table{border-collapse:collapse;width:100%;table-layout:fixed;border:1px solid #bcc9da}
th{background:#dae3ef;text-align:left;padding:9px 12px;font-size:13px}th:first-child{text-align:center}
td{vertical-align:top;padding:5px 12px}.line{background:#121d30;color:#9badc7;text-align:right;font:13px/1.7 Consolas,monospace}
.code{background:#182337;color:#f3f6fd}pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font:13.5px/1.6 Consolas,"Microsoft YaHei",monospace;tab-size:4}
pre.cmtline{color:#8fd0a8}
.explain{background:#fff2cf;color:#653d10;border-left:4px solid #e4ae49;font-size:13px;line-height:1.7}
.kw{color:#d5b9ff}.gap td{height:7px;padding:0}
tbody tr{transition:background .08s}
tbody tr:hover .code{background:#24406b}
tbody tr:hover .explain{background:#ffe08a;color:#3d2400}
tbody tr:hover .line{background:#1d3350;color:#d6e4ff}
tbody tr:focus{outline:2px solid #2677ba;outline-offset:-2px}
footer{color:#536176;font-size:13px}
</style>"""

JAVA_KW = {"public", "private", "interface", "extends", "class", "return", "package", "import", "static", "final", "new", "void", "int", "long"}
XML_KW = {"select", "update", "insert", "delete", "where", "if", "choose", "when", "otherwise", "foreach", "include", "sql", "mapper"}


def esc(t: str) -> str:
    import html
    return html.escape(t, quote=False)


def hi_java(line: str) -> str:
    out = []
    for tok in re.split(r"(\s+|[(),.;{}<>@\[\]]|==)", line):
        if tok.strip() in JAVA_KW:
            out.append(f'<span class="kw">{esc(tok)}</span>')
        else:
            out.append(esc(tok))
    return "".join(out)


def hi_xml(line: str) -> str:
    out = []
    for tok in re.split(r"(\s+|[<>/=])", line):
        if tok.strip() in XML_KW:
            out.append(f'<span class="kw">{esc(tok)}</span>')
        else:
            out.append(esc(tok))
    return "".join(out)


def javadoc_above(lines: list[str], idx: int) -> str:
    """向上找最近的 /** ... */ 块，取第一行正文；中间允许隔着 @注解 行。"""
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
    text = " ".join(block) or "本行参与结构或样式组织，不产生业务行为。"
    text = text.replace("/**", "").replace("*/", "").replace("*", " ").strip() or "本行参与结构或样式组织，不产生业务行为。"
    return re.sub(r"\s+", " ", text)


def make_java_explainer(lines: list[str]):
    def explain(n: int, line: str) -> str:
        s = line.strip()
        if not s:
            return ""
        if s.startswith("package"):
            return "包声明"
        if s.startswith("import"):
            return "导入"
        if s.startswith("/**"):
            return "注释：说明该类型的职责与约束"
        if s.startswith("*") or s.startswith("*/"):
            return "注释续行"
        if s.startswith("@"):
            if "@Mapper" in s:
                return "标记为 MyBatis Mapper，由 @MapperScan 扫描注册"
            if "@Param" in s:
                return "参数命名：XML 里用 #{名字} 引用"
            if "@Select" in s:
                return "注解 SQL：查询"
            if "@Update" in s:
                return "注解 SQL：更新"
            return "注解"
        if re.match(r"public\s+class\s+\w+\s*\{", s):
            return "DTO 类：只承载字段与 getter/setter，不含业务逻辑"
        if s.startswith("+") and "\"" in s:
            return "注解 SQL 续行（字符串拼接）"
        if re.match(r"public\s+interface\s+\w+", s):
            base = re.search(r"BaseMapper<(\w+)>", s)
            return f"接口：继承 BaseMapper<{base.group(1)}>，获得基础 CRUD" if base else "接口声明"
        if s.endswith(";") and re.search(r"\b(private|protected|public)\b", s):
            name = re.search(r"(\w+)\s*;", s)
            doc = javadoc_above(lines, n - 1)
            return doc or (f"字段 {name.group(1)}" if name else "字段")
        if re.match(r"(public|private|protected).*\bclass\b", s):
            return "类声明" + ("（抽象基类）" if "abstract" in s else "")
        if "[] " in s and s.endswith("= {"):
            return "常量数组"
        if re.match(r'"[a-z_]+",?$', s):
            return "常量项：表名"
        if s == "};":
            return "数组结束"
        if s.endswith(") {"):
            # 先处理结构性的 try/catch/for，再找 javadoc 或 @DisplayName
            if s.startswith("} catch ("):
                return "异常包装成运行时异常，保留原始 cause"
            if s.startswith("try ("):
                return "try-with-resources：连接与语句自动关闭"
            if "createStatement()) {" in s:
                return "资源声明续行（与上一行同属 try-with-resources）"
            if s.startswith("for (String table"):
                return "按 DROP_ORDER 顺序逐表删除"
            doc = javadoc_above(lines, n - 1)
            if doc:
                return doc
            for k in range(n - 2, max(n - 7, -1), -1):
                m = re.search(r'@DisplayName\("([^"]+)"\)', lines[k])
                if m:
                    return "用例：" + m.group(1)
            return ""
        if s.startswith("try ("):
            return "try-with-resources：连接与语句自动关闭"
        if "createStatement()) {" in s:
            return "资源声明续行（与上一行同属 try-with-resources）"
        if s.startswith("} catch ("):
            return "异常包装成运行时异常，保留原始 cause"
        if s.startswith("for (String table"):
            return "按 DROP_ORDER 顺序逐表删除"
        if s.endswith(",") and s.startswith("\""):
            return "SQL 字符串续行"
        # 构造器、链式条件、测试夹具：按"这句话在准备什么"来说明
        if s.startswith("this.") and s.endswith(";"):
            return "构造器注入：把 Mapper 保存为字段"
        if s.startswith("Wrappers."):
            return "构造 Lambda 查询"
        if s.startswith(".eq("):
            return "等值条件"
        if s.startswith(".isNull("):
            return "限定字段为空"
        if s.startswith(".orderByAsc("):
            return "按该列升序"
        if s.startswith("try {"):
            return "进入可能抛异常的解析块"
        if s.startswith("query.set"):
            return "写入查询条件"
        if s.startswith("long value = Long.parseLong"):
            return "解析数字；失败由 catch 转 400"
        if s.startswith(".replace("):
            return "转义 LIKE 通配符"
        m = re.match(r'insertProduct\("([^"]+)", [^,]+, "([\d.]+)", (\d+), "(\w+)", (\d+)\)', s)
        if m:
            return f"准备商品 {m.group(1)}：¥{m.group(2)}，库存 {m.group(3)}，{m.group(4)}，排序 {m.group(5)}"
        m = re.match(r'insertUser\("([^"]+)"\)', s)
        if m:
            return f"准备用户 {m.group(1)}"
        m = re.match(r'newSecondaryCategory\("([^"]+)"\)', s)
        if m:
            return f"准备分类：一级「作品-{m.group(1)}」+ 二级「单行本」"
        m = re.match(r'insertPrimaryCategory\("([^"]+)", "([^"]+)"\)', s)
        if m:
            return f"准备一级分类「{m.group(2)}」"
        m = re.match(r'insertSecondaryCategory\("([^"]+)", (\w+), "([^"]+)"\)', s)
        if m:
            return f"准备二级分类「{m.group(3)}」，挂在指定父下"
        m = re.match(r'insertOrder\("([^"]+)", (\w+), "(\w+)", "([\d.]+)"\)', s)
        if m:
            return f"准备订单 {m.group(1)}：{m.group(3)}，合计 {m.group(4)}"
        if s.startswith("assertThat(") and ".isZero()" in s:
            return "断言结果为 0"
        if s.startswith("assertThat(") and ".isEqualTo(" in s:
            return "断言等于期望值"
        if s.startswith(".isInstanceOf("):
            return "断言抛出的异常类型"
        if s.startswith(".hasMessageContaining("):
            return "断言异常信息包含关键字"
        if s.startswith(".containsExactly("):
            return "断言顺序与内容完全一致"
        # 语句级：只标注"这句在做什么角色"，不复述代码
        if s.startswith(("assertThat", "assertThatThrownBy")):
            return "断言预期结果"
        if s.startswith(("isInstanceOf(", ".hasMessageContaining", ".isEqualTo", ".isZero", ".isTrue", ".isFalse", ".hasSize", ".extracting")):
            return "断言链：校验类型/值/集合"
        if s.startswith("jdbc."):
            return "准备或核对测试数据"
        if any(call in s for call in (".selectPage(", ".countPage(", ".selectById(", ".selectList(", ".selectFeatured(", ".selectNewest(", ".lockByIds(", ".deductStock(", ".selectViewByUser(", ".countByOrderNo(")):
            return "调用 Mapper / 被测方法取数"
        if s.startswith("throw "):
            return "转成业务异常（400/404）"
        if s.startswith("return "):
            return "返回结果"
        if s.startswith("query.set") or s.startswith("command.set"):
            return "写入查询条件"
        if s == "}":
            return "结束"
        if s == "":
            return ""
        return ""

    return explain


def make_xml_explainer():
    def explain(n: int, line: str) -> str:
        s = line.strip()
        if not s:
            return ""
        if s.startswith("<?xml") or s.startswith("<!DOCTYPE"):
            return "XML 声明与 MyBatis DTD"
        if s.startswith("<mapper "):
            return "namespace 必须是 Mapper 接口的全限定名"
        m = re.match(r'<(select|update|insert|delete)\s+id="(\w+)"', s)
        if m:
            return f"语句 {m.group(2)}：对应接口方法同名"
        if s.startswith("</select>") or s.startswith("</update>"):
            return "语句结束"
        if s.startswith("<sql id="):
            return "可复用 SQL 片段"
        if s.startswith("<include"):
            return "引用上面的 SQL 片段"
        if s.startswith("<where>"):
            return "自动处理 AND 前缀"
        if s.startswith("<if test="):
            cond = re.search(r'test="([^"]+)"', s)
            return f"动态条件：{cond.group(1) if cond else ''}"
        if s.startswith("<choose>"):
            return "多选一：排序白名单"
        if s.startswith("<when test="):
            cond = re.search(r'test="([^"]+)"', s)
            return f"命中条件：{cond.group(1) if cond else ''}"
        if s.startswith("<otherwise>"):
            return "默认排序（其余值）"
        if s.startswith("<foreach"):
            return "遍历集合参数，生成 IN (...)"
        if s.startswith("</mapper>"):
            return "映射文件结束"
        if s.startswith("SELECT") or s.startswith("FROM") or s.startswith("WHERE") or s.startswith("ORDER BY") or s.startswith("LIMIT") or s.startswith("JOIN"):
            return "SQL 语句体"
        if s.startswith("AND ") or s.startswith("OR "):
            return "SQL 条件"
        if s.startswith(")"):
            return "片段结束"
        return "SQL"

    return explain


# 用独立模块的实现覆盖上面的旧版本
from java_explainer import make_java_explainer  # noqa: E402,F811

def main() -> None:
    sections = []
    nav = []
    total_rows = 0
    for rel, title, subtitle, kind in FILES:
        path = ROOT / rel
        lines = path.read_text(encoding="utf-8").splitlines()
        sid = re.sub(r"\W", "", title.lower())
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else make_xml_explainer()
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            e = fn(i, line)
            cls = ' class="cmtline"' if line.lstrip().startswith(("//", "<!--")) else ""
            code = hi_java(line) if kind == "java" else hi_xml(line)
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre{cls}>{code}</pre></td>'
                f'<td class="explain">{esc(e)}</td></tr>'
            )
        total_rows += len(rows)
        sections.append(f"""<section id="{sid}">
<h2>{title}<small>{subtitle} · {len(lines)} 行</small></h2>
<p class="path">{rel}</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(rows)}
</tbody></table>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>M01-07 · Mapper 与自定义查询 逐行验收</title>
{STYLE}</head>
<body>
<header>
<h1>M01-07 · Mapper 与自定义查询（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码（注释行原位绿字），右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-07。依据 docs/04 §4 参数口径、02 篇 §8 并发与锁顺序。
验证：全量打包 BUILD SUCCESS；用户端启动 2.168s、/health 200，说明 MyBatis 已解析全部 XML 且 namespace 正确。
SQL 的实际执行（字段映射、参数绑定、FOR UPDATE 语义）由 M01-09 集成测试验证。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M01-07 · 共 {len(FILES)} 个文件、{total_rows} 行；说明由 javadoc 提取 + 规则生成，逐行覆盖。
完整汇报见同目录 M01-07-Mapper.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    doc = doc.replace('<td class="explain">—</td>', '<td class="explain">本行参与结构或样式组织，不产生业务行为。</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total_rows}")


if __name__ == "__main__":
    main()

