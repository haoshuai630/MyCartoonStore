"""M02-04 逐行验收 HTML：列表页模板、控制器、异常处理、页面测试。"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_04 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-04-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/category.html",
     "category.html", "列表页：侧栏 + 类型 + 排序 + 分页", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/CategoryController.java",
     "CategoryController.java", "列表页路由", "java"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/GlobalExceptionHandler.java",
     "GlobalExceptionHandler.java", "业务异常 → 400/404", "java"),
    ("cartoon-user-web/src/main/resources/templates/error/error.html",
     "error.html", "统一错误页", "html"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/CategoryPageTest.java",
     "CategoryPageTest.java", "列表页测试 4 例", "java"),
]


def stack_notes(lines):
    """给闭合标签标出它结束的是哪个块，避免整段都是「—」。"""
    notes = {}
    stack = []
    for i, line in enumerate(lines, 1):
        s = line.strip()
        for m in re.finditer(r"<(\w+)([^>]*)>", s):
            tag, attrs = m.group(1), m.group(2)
            if tag.lower() in ("img", "input", "br", "meta", "link"):
                continue
            cls = re.search(r'class="([^"]+)"', attrs)
            stack.append((tag, cls.group(1) if cls else ""))
        for m in re.finditer(r"</(\w+)>", s):
            tag = m.group(1)
            label = ""
            for j in range(len(stack) - 1, -1, -1):
                if stack[j][0] == tag:
                    label = stack[j][1]
                    stack.pop(j)
                    break
            notes[i] = f"结束 {tag}" + (f".{label}" if label else "") + " 容器"
    return notes


def main() -> None:
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else m22.make_html_explainer(lines)
        hi = base.hi_java if kind == "java" else m22.hi_html
        closers = stack_notes(lines) if kind == "html" else {}
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
<title>M02-04 · 列表页 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-04 · 列表页（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-04。依据 01 篇 FR-G-02/03：分类、类型、排序、关键字、分页五个条件可组合。
验证：mvn -pl cartoon-user-web -am test → Tests run: 24, 0 失败。</p>
<p class="note" style="margin-top:8px">「—」表示该行是结构性代码（import、花括号、闭合标签），没有需要解释的决策。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-04 · 5 个文件、{total} 行。完整汇报见同目录 M02-04-列表页.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()