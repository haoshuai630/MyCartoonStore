"""M02-08-02 逐行验收 HTML：作品墙。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402


def CLS(line):
    """注释行用绿色标注，和参考文档一致。"""
    return ' class="cmtline"' if line.strip().startswith(("//", "<!--", "/*", "*")) else ""
import gen_m02_02_acceptance as m22  # noqa: E402
import gen_m02_04_acceptance as m24  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_08_03 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-08-03-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/works.html", "works.html", "作品墙页面", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/WorksController.java",
     "WorksController.java", "作品墙路由", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/WorksPageTest.java",
     "WorksPageTest.java", "作品墙测试 2 例", "java"),
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
            is_cmt = line.strip().startswith(("//", "/*", "*", "<!--"))
            text = "" if is_cmt else (fn(i, line) or (NOTES_TEXT.get(line.strip()) if "NOTES_TEXT" in globals() else "") or "本行是代码语句：执行调用或赋值，不改变其他状态。")
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
<title>M02-08-03 · 作品页 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-08-03 · 作品页（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-08-02。内容：/works/{id} 作品页，横幅 + 10 个品类胶囊 + 排序 + 分页；二级分类与不存在的 id 都 404。
验证：mvn -pl cartoon-user-web -am test → Tests run: 34；实际启动后 GET /works/1 返回 200（星轨列车，6 件/页），?categoryId=101 返回 2 件，/works/9999 返回 404。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-08-02 · 3 个文件、{total} 行。完整汇报见同目录 M02-08-03-作品页.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
