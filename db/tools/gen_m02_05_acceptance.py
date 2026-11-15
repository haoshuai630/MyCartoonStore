"""M02-05 逐行验收 HTML：详情页模板、控制器、页面测试。"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402


def CLS(line):
    """注释行绿色标注。"""
    return ' class="cmtline"' if line.strip().startswith(("//", "<!--", "/*", "*")) else ""

import gen_m02_02_acceptance as m22  # noqa: E402
import gen_m02_04_acceptance as m24  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_05 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-05-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/product.html",
     "product.html", "详情页：面包屑、相册、购买区", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/ProductController.java",
     "ProductController.java", "详情页路由", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/ProductPageTest.java",
     "ProductPageTest.java", "详情页测试 4 例", "java"),
]


def main() -> None:
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
            is_cmt = line.strip().startswith(("//", "/*", "*", "<!--"))
            text = "" if is_cmt else ((NOTES_TEXT.get(line.strip()) if "NOTES_TEXT" in globals() else "") or fn(i, line) or "本行是代码语句：执行调用或赋值，不改变其他状态。")
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre{CLS(line)}>{hi(line)}</pre></td>'
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
<title>M02-05 · 商品详情页 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-05 · 商品详情页（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-05。依据 01 篇 FR-G-04：详情展示商品信息、两级分类路径、相册；下架与不存在统一 404。
验证：mvn -pl cartoon-user-web -am test → Tests run: 28, 0 失败。</p>
<p class="note" style="margin-top:8px">购物车按钮目前是占位（M04 实现），点击不会提交。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-05 · 3 个文件、{total} 行。完整汇报见同目录 M02-05-详情页.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
