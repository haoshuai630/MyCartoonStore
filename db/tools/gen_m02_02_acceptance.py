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
    def explain(n: int, line: str) -> str:
        s = line.strip()
        if not s:
            return ""
        if s.startswith("<!DOCTYPE"):
            return "HTML5 文档类型"
        if s.startswith("<!--"):
            return "注释：说明本段的用途或后续任务"
        if s.startswith("<html"):
            return "根元素；声明 Thymeleaf 命名空间，模板里才能用 th:* 属性"
        if "th:fragment" in s:
            name = re.search(r'th:fragment="(\w+)', s)
            return f"定义可复用片段 {name.group(1)}，其他页面用 th:replace 引用"
        if "th:replace" in s:
            return "引用公共片段，避免每个页面重复写页头页脚"
        if "th:href" in s:
            return "站内链接，由 Thymeleaf 补上下文路径"
        if "th:src" in s:
            return "站内静态资源，路径与数据库里的 cover_image 一致"
        if "th:text" in s:
            return "文本由后端数据渲染，不写死"
        if s.startswith("<title"):
            return "页面标题：由片段参数拼接品牌名"
        if s.startswith("<link"):
            return "引入站点样式表"
        if s.startswith("</head"):
            return "head 结束"
        if re.match(r"<(div|header|footer|section|main|nav|form|h1|h2|p|span)\b", s):
            tag = re.match(r"<(\w+)", s).group(1)
            return f"{tag} 容器开始"
        if s.startswith("</"):
            return "结束标签"
        return "结构/内容行"

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
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
