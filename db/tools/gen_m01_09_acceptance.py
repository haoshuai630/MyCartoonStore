"""M01-09 逐行验收 HTML 生成（复用 M01-07 的渲染函数）。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M01-09-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/DataAccessTest.java",
     "DataAccessTest.java", "11 个集成测试：约束 + Mapper", "java"),
]


def main() -> None:
    sections, nav, total = [], [], 0
    for rel, title, subtitle, _kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = base.make_java_explainer(lines)
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            e = fn(i, line)
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{base.hi_java(line)}</pre></td>'
                f'<td class="explain">{base.esc(e)}</td></tr>'
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
<title>M01-09 · 集成测试 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M01-09 · 集成测试（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-09。依据 06 篇 §3.2 集成测试要求、03 篇七表约束。
验证：mvn -pl cartoon-user-web -am test → Tests run: 14, Failures: 0, Errors: 0, BUILD SUCCESS（11 + 3）。
测试库 MyCartoonStore_test 每用例重建；断言全部落在真实数据库行为上。</p>
<p class="note" style="margin-top:8px">本任务还修改了父 POM 的编译配置（JDK 23+ 需要显式启用 Lombok 注解处理），属于构建配置，见汇报 §4。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M01-09 · 1 个文件、{total} 行。
完整汇报见同目录 M01-09-集成测试.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
