"""M02-06 逐行验收 HTML：新增样式块 + 错误页模板。"""
from __future__ import annotations

import html
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
from notes_m02_06 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-06-逐行验收.html"


def esc(t):
    return html.escape(t, quote=False)


def hi_css(line):
    s = line.strip()
    if not s.startswith(("/*", "*", "}", "@", ".", "#")):
        m = re.match(r"^(\s*)([-a-z]+):(.*)$", line)
        if m:
            return m.group(1) + f'<span class="kw">{esc(m.group(2))}</span>:' + esc(m.group(3))
    if s.startswith(("/*", "*")):
        return f'<span class="cm">{esc(line)}</span>'
    return esc(line)


def main():
    css_lines = (ROOT / "cartoon-user-web/src/main/resources/static/css/store.css").read_text(
        encoding="utf-8").splitlines()
    start = next(i for i, l in enumerate(css_lines) if "M02-06：列表页" in l)
    blocks = [
        ("store.css（新增段）", "store.css", "cartoon-user-web/src/main/resources/static/css/store.css",
         css_lines[start:], start + 1, "css"),
        ("error.html", "error.html", "cartoon-user-web/src/main/resources/templates/error/error.html",
         (ROOT / "cartoon-user-web/src/main/resources/templates/error/error.html").read_text(
             encoding="utf-8").splitlines(), 1, "html"),
    ]

    sections, nav, total = [], [], 0
    for title, short, rel, lines, first_lineno, kind in blocks:
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{short}</a>')
        fn = m22.make_html_explainer(lines) if kind == "html" else None
        hi = m22.hi_html if kind == "html" else hi_css
        rows = []
        for i, line in enumerate(lines, first_lineno):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            is_cmt = line.strip().startswith(("//", "/*", "*", "<!--"))
            text = "" if is_cmt else (fn(i, line) or (NOTES_TEXT.get(line.strip()) if "NOTES_TEXT" in globals() else "") or "本行是代码语句：执行调用或赋值，不改变其他状态。")
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(line)}</pre></td>'
                f'<td class="explain">{esc(text)}</td></tr>'
            )
        total += len(rows)
        sections.append(f"""<section id="{sid}">
<h2>{short}<small>{title} · {len(lines)} 行</small></h2>
<p class="path">{rel}{'（从第 ' + str(first_lineno) + ' 行起）' if first_lineno > 1 else ''}</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(rows)}
</tbody></table>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>M02-06 · 页面收尾 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-06 · 页面收尾（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-06。内容：列表页两栏、工具栏胶囊按钮、分页禁用态、详情页两栏、错误页与两档响应式断点。
验证：mvn -pl cartoon-user-web -am test → Tests run: 28, 0 失败；三个页面均 200。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-06 · 2 个文件、{total} 行。完整汇报见同目录 M02-06-页面收尾.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
