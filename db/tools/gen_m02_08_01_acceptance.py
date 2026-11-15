"""M02-08-01 逐行验收 HTML：作品数据层。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402


def CLS(line):
    """注释行用绿色标注，和参考文档一致。"""
    return ' class="cmtline"' if line.strip().startswith(("//", "<!--", "/*", "*")) else ""
import gen_m02_02_acceptance as m22  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m02_08_01 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-08-01-逐行验收.html"

FILES = [
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/WorkSummary.java",
     "WorkSummary.java", "作品墙卡片 DTO", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/WorkDetail.java",
     "WorkDetail.java", "作品页头部 DTO", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/mapper/CategoryMapper.java",
     "CategoryMapper.java", "作品统计 SQL", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/ProductQuery.java",
     "ProductQuery.java", "新增 workId 条件", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/ProductSearchCommand.java",
     "ProductSearchCommand.java", "新增 workId 参数", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/service/CatalogService.java",
     "CatalogService.java", "作品墙/作品页取数", "java"),
]


def main():
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines)
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            text = fn(i, line) or NOTES_TEXT.get(line.strip()) or "" or "本行参与结构或样式组织，不产生业务行为。"
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre{CLS(line)}>{base.hi_java(line)}</pre></td>'
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
<title>M02-08-01 · 作品数据层 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-08-01 · 作品数据层（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-08-01。内容：每部作品 4 个品类（单行本/模型/抱枕/其他周边）、作品墙统计 SQL、按作品过滤。
验证：mvn -pl cartoon-catalog -am test → Tests run: 14, 0 失败。</p>
<p class="note" style="margin-top:8px">种子数据与 Mapper XML 的改动不在此页逐行对照：SQL 由生成器产出（db/tools/gen_demo_sql.py），XML 只加了一个 if 条件。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-08-01 · 6 个文件、{total} 行。完整汇报见同目录 M02-08-01-作品数据层.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()