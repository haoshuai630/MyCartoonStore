"""M01-08 逐行验收 HTML 生成（复用 M01-07 的渲染函数）。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M01-08-逐行验收.html"

FILES = [
    ("cartoon-test-support/src/main/java/org/csu/mycartoonstore/testsupport/TestSchema.java",
     "TestSchema.java", "测试库重建：删表 + 执行建表脚本", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/support/AbstractIntegrationTest.java",
     "AbstractIntegrationTest.java", "集成测试基类", "java"),
    ("cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/SchemaSmokeTest.java",
     "SchemaSmokeTest.java", "基座自检：3 个用例", "java"),
    ("cartoon-user-web/src/test/resources/application-test.yaml",
     "application-test.yaml", "test profile：连 MyCartoonStore_test", "yaml"),
]


def hi_yaml(line: str) -> str:
    s = line.rstrip()
    if not s.strip():
        return ""
    if s.lstrip().startswith("#"):
        return f'<span class="cmt">{base.esc(s)}</span>'
    return base.esc(s)


def make_yaml_explainer(lines: list[str]):
    def explain(n: int, line: str) -> str:
        s = line.strip()
        if not s:
            return ""
        if s.startswith("#"):
            return "注释"
        if s.startswith("spring:") or s.startswith("mybatis-plus:") or s.startswith("logging:"):
            return "配置段"
        if s.startswith("url:"):
            return "连接测试库；createDatabaseIfNotExist 让库不存在时自动创建"
        if s.startswith("username:") or s.startswith("password:"):
            return "凭据，可用 DB_PASSWORD 覆盖"
        if s.startswith("driver-class-name:"):
            return "MySQL 驱动"
        if s.startswith("mapper-locations:"):
            return "从各业务模块 JAR 中加载 XML"
        if s.startswith("map-underscore-to-camel-case:"):
            return "下划线列名转驼峰属性"
        if s.startswith("level:") or s.startswith("org.csu"):
            return "日志级别"
        return "配置项"

    return explain


def main() -> None:
    sections, nav = [], []
    total = 0
    for rel, title, subtitle, kind in FILES:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        if kind == "java":
            fn = base.make_java_explainer(lines)
            hi = base.hi_java
        else:
            fn = make_yaml_explainer(lines)
            hi = hi_yaml
        rows = []
        for i, line in enumerate(lines, 1):
            if not line.strip():
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            e = fn(i, line)
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(line)}</pre></td>'
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
<title>M01-08 · 测试基座 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M01-08 · 测试基座（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-08。依据 06 篇 §3 测试方法与 docs/03 §5 数据规格。
验证：mvn -pl cartoon-user-web -am test → Tests run: 3, Failures: 0, Errors: 0, BUILD SUCCESS。
测试库 MyCartoonStore_test 与演示库完全分离；每个用例前重建七张表。</p>
<p class="note" style="margin-top:8px">未覆盖：<code>sql/schema-test.sql</code> 是从 <code>db/01-schema.sql</code> 生成的 DDL 副本，结构已在 M01-01 逐行验收，此处不重复。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M01-08 · 共 {len(FILES)} 个文件、{total} 行。
完整汇报见同目录 M01-08-测试基座.md。</footer>
</body></html>
"""
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
