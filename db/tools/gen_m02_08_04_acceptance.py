"""M02-08-02 逐行验收 HTML：作品墙。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
import gen_m02_04_acceptance as m24  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_08_04 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-08-04-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/category.html", "category.html", "列表页：筛选条 + 商品网格", "html"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/CategoryPageTest.java",
     "CategoryPageTest.java", "列表页测试 5 例", "java"),
]


def main():
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else m22.make_html_explainer(lines)
        hi = base.hi_java if kind == "java" else m22.hi_html
        closers = m24.stack_notes(lines) if kind == "html" else {}
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            text = NOTES_TEXT.get(line.strip()) or closers.get(i) or fn(i, line)
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(line)}</pre></td>'
                f'<td class="explain">{m22.esc(text)}</td></tr>'
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
<title>M02-08-04 · 列表页去侧栏 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-08-04 · 列表页去侧栏（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-08-02。内容：去掉侧栏，改顶部筛选条（作品下拉 + 类型 + 排序 + 关键字）；首页作品入口改指向 /works/{id}。
验证：mvn -pl cartoon-user-web -am test → Tests run: 35；实际启动后 GET /category 无侧栏、有作品下拉，?workId=2 正常，首页 5 个入口都指向 /works/{id}。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-08-02 · 3 个文件、{total} 行。完整汇报见同目录 M02-08-04-列表页收栏.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()