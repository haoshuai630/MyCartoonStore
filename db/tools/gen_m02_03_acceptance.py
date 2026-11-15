"""M02-03 逐行验收 HTML：首页模板、控制器、页面测试。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_03 import NOTES  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-03-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/main.html",
     "main.html", "首页：作品入口 + 新品 3 + 精选 4", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/HomeController.java",
     "HomeController.java", "首页路由与取数", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/HomePageTest.java",
     "HomePageTest.java", "首页渲染测试 3 例", "java"),
]


FORCE = {
    ("main.html", 1): "HTML5 文档类型",
    ("HomeController.java", 23): "构造器注入：保存 CatalogService 字段",
    ("HomePageTest.java", 76): "计数器与游标",
}

def main() -> None:
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else m22.make_html_explainer(lines)
        hi = base.hi_java if kind == "java" else m22.hi_html
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(line)}</pre></td>'
                f'<td class="explain">{m22.esc(NOTES.get((title, i)) or fn(i, line))}</td></tr>'
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
<title>M02-03 · 首页接真实数据 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-03 · 首页接真实数据（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-03。依据 01 篇 FR-G-01：作品入口取一级分类，新品按 created_at 倒序取 3，精选按 sort_order 升序取 4。
验证：mvn -pl cartoon-user-web -am test → Tests run: 20, 0 失败；首页在空库显示空状态，下架商品不出现。</p>
<p class="note" style="margin-top:8px">「—」表示该行是结构性代码（import、注解、括号、无 javadoc 的字段），没有需要解释的决策；只给承担业务含义的行写说明。</p>
<p class="note" style="margin-top:8px">CatalogService 新增的 newest/featured 两个透传方法（共 8 行）未单独对照，逻辑就是调用 Mapper。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-03 · 3 个文件、{total} 行。完整汇报见同目录 M02-03-首页.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()