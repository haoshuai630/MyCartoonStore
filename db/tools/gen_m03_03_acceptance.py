"""M02-08-01 逐行验收 HTML：作品数据层。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402
from notes_m03_03 import NOTES_TEXT  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M03-03-逐行验收.html"

FILES = [
    ("cartoon-user-web/src/main/resources/templates/auth/login.html", "login.html", "登录表单", "html"),
    ("cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/AuthController.java",
     "AuthController.java", "登录 / 登出 / 回跳", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/LoginPageTest.java",
     "LoginPageTest.java", "登录测试 7 例", "java"),
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
            text = NOTES_TEXT.get(line.strip()) or fn(i, line)
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{base.hi_java(line)}</pre></td>'
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
<title>M03-01 · 账号与密码 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M03-01 · 账号与密码（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-08-01。内容：登录写入会话、失败提示、禁用账号、站内回跳、登出清会话。
验证：mvn -pl cartoon-user-web -am test → Tests run: 46, 0 失败。</p>
<p class="note" style="margin-top:8px">种子数据与 Mapper XML 的改动不在此页逐行对照：SQL 由生成器产出（db/tools/gen_demo_sql.py），XML 只加了一个 if 条件。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-08-01 · 6 个文件、{total} 行。完整汇报见同目录 M03-03-登录登出.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()