"""M02-02 逐行验收 HTML：页面片段、控制器、渲染测试。"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-02-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/fragments/common.html",
     "common.html", "公共片段：head / header / footer", "html"),
    ("cartoon-user-web/src/main/resources/templates/main.html",
     "main.html", "首页骨架（M02-03 填数据）", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/HomeController.java",
     "HomeController.java", "首页路由", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/LayoutRenderTest.java",
     "LayoutRenderTest.java", "布局渲染测试 3 例", "java"),
]


def esc(t: str) -> str:
    import html
    return html.escape(t, quote=False)


def hi_html(line: str) -> str:
    out = []
    for tok in re.split(r"(\s+|[<>/=])", line):
        if tok.strip() in {"html", "head", "body", "div", "header", "footer", "section", "main", "nav", "a", "img", "form", "input", "button", "title", "link", "span", "b", "h1", "h2", "p", "em", "small"}:
            out.append(f'<span class="kw">{esc(tok)}</span>')
        else:
            out.append(esc(tok))
    return "".join(out)


def make_html_explainer(lines: list[str]):
    """只解释"这一行为什么这么写"，结构性标签留空。"""

    def explain(n: int, line: str) -> str:
        s = line.strip()
        if not s:
            return ""
        if s.startswith("</"):
            return "结构闭合：结束上面的容器；不产生新的内容。"
        if s.upper().startswith("<!DOCTYPE"):
            return "HTML5 文档类型：浏览器按标准模式渲染"
        if s.startswith("<html"):
            return "根元素；xmlns:th 声明命名空间，模板里的 th:* 才生效"
        if s == "<body>":
            return "页面可见内容的起点"
        if s in ("</body>", "</html>"):
            return "文档结束"
        if s.startswith("<!--"):
            return ""
        if 'class="wrap"' in s or 'class="wrap ' in s:
            return "内容宽度容器：统一左右留白与最大宽度"
        if 'class="section-head"' in s:
            return "栏目头：标题在左、总数或更多入口在右"
        if 'class="work-cover"' in s:
            return "作品卡封面区：使用该作品第一卷封面"
        if 'class="work-body"' in s:
            return "作品卡文字区：名称、code、品类数与在售数"
        if 'class="work-meta"' in s:
            return "品类数与在售数并排展示"
        if s == "<main>":
            return "页面主体区"
        if 'class="results"' in s:
            return "结果区：工具栏、商品网格、分页"
        if 'class="types"' in s:
            return "类型切换：全部 / 单行本 / 周边"
        if 'class="sorts"' in s:
            return "排序切换：默认 / 价格升降 / 最新"
        if 'class="result-meta"' in s:
            return "结果总数，并在有关键字时回显"
        if 'class="body"' in s:
            return "卡片文字区：SKU、名称、价格、库存"
        if 'class="foot"' in s:
            return "卡片底部：价格与库存"
        if 'class="filters"' in s:
            return "筛选侧栏"
        if "th:fragment" in s:
            name = re.search(r'th:fragment="(\w+)', s)
            return f"定义可复用片段 {name.group(1)}，其他页面用 th:replace 引用"
        if "th:replace" in s:
            return "引用公共片段，避免每个页面重复写页头页脚"
        if "th:each" in s:
            return "遍历后端传入的集合渲染"
        if "th:if" in s:
            return "空集合时不渲染该区块（空状态由另一段负责）"
        if "th:href" in s:
            return "站内链接，由 Thymeleaf 补上下文路径"
        if "th:src" in s:
            return "站内静态资源，路径与数据库里的 cover_image 一致"
        if "th:text" in s and "formatDecimal" in s:
            return "价格统一渲染为两位小数"
        if "th:text" in s:
            return "文本由后端数据渲染，不写死"
        if 'class="hero"' in s:
            return "品牌横幅：只做展示与入口，不直接下单"
        if 'class="strip"' in s:
            return "作品入口：一级分类，点进对应作品列表"
        if 'class="section"' in s:
            return "首页栏目：新品上架 / 精选推荐"
        if 'class="card"' in s:
            return "商品卡：封面、名称、价格与库存状态"
        if "th:classappend" in s:
            return "库存为 0 时加 zero 样式，显示售罄"
        return ""

    return explain

def main() -> None:
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else make_html_explainer(lines)
        hi = base.hi_java if kind == "java" else hi_html
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            cls = ' class="cmtline"' if line.lstrip().startswith(("<!--", "//")) else ""
            is_cmt = line.strip().startswith(("//", "/*", "*", "<!--"))
            text = "" if is_cmt else (fn(i, line) or (NOTES_TEXT.get(line.strip()) if "NOTES_TEXT" in globals() else "") or "本行是代码语句：执行调用或赋值，不改变其他状态。")
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre{cls}>{hi(line)}</pre></td>'
                f'<td class="explain">{esc(fn(i, line))}</td></tr>'
            )
        total += len(rows)
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
<title>M02-02 · 公共布局与静态资源 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-02 · 公共布局与静态资源（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-02。依据 05 篇 §3 公共布局与 §6 视觉规范。
验证：mvn -pl cartoon-user-web -am test → Tests run: 17, Failures: 0, Errors: 0；GET /main 200、/css/store.css 200、/images/products/STARLINE-C01/cover.svg 200。</p>
<p class="note" style="margin-top:8px">未逐行对照：<code>static/css/store.css</code>（13 KB，样式规则，视觉效果见页面）与 161 个生成的 SVG 素材（由 <code>db/tools/gen_assets.py</code> 批量产出，命名与数据库路径一致）。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-02 · 4 个文件、{total} 行。完整汇报见同目录 M02-02-布局资源.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
