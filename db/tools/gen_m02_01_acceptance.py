"""M02-01 逐行验收 HTML 生成（复用 M01-07 的渲染函数）。"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402

ROOT = base.ROOT
OUT = ROOT / "reports" / "M02-01-逐行验收.html"

FILES = [
    ("cartoon-common/src/main/java/org/csu/mycartoonstore/common/page/PageResult.java",
     "PageResult.java", "分页结果 record", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/ProductSearchCommand.java",
     "ProductSearchCommand.java", "列表原始参数 DTO", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/dto/ProductDetail.java",
     "ProductDetail.java", "详情视图 record", "java"),
    ("cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/service/CatalogService.java",
     "CatalogService.java", "查询服务：参数归一、详情、分类树", "java"),
]


# 手写说明：只补自动规则覆盖不到、且"为什么"值得写清楚的行
MANUAL = {
    ("PageResult.java", "if (total <= 0) {"): "total 为 0 时总页数直接给 0，避免臆造一个不存在的第 1 页",
    ("ProductDetail.java", "List<ProductImage> images) {"): "相册可能为空，模板用 hasGallery() 判断是否回退封面",
    ("CatalogService.java", "PageQuery pageQuery = PageQuery.of(command.getPage(), command.getPageSize(), DEFAULT_PAGE_SIZE);"): "先归一分页参数，非法页码/页大小在这里转 400",
    ("CatalogService.java", "ProductQuery query = buildQuery(command, pageQuery);"): "再把业务参数归一成 Mapper 可用对象",
    ("CatalogService.java", "List<Product> items = productMapper.selectPage(query);"): "查当前页数据",
    ("CatalogService.java", "long total = productMapper.countPage(query);"): "用同一套条件查总数，保证分页与记录数一致",
    ("CatalogService.java", "Product product = productMapper.selectById(productId);"): "按主键查商品",
    ("CatalogService.java", "Category leaf = categoryMapper.selectById(product.getCategoryId());"): "取商品所属的二级分类",
    ("CatalogService.java", "Category top = leaf.getParentId() == null ? leaf : categoryMapper.selectById(leaf.getParentId());"): "二级取父得到一级；若本身就是一级则复用",
    ("CatalogService.java", "String value = raw.trim();"): "去首尾空白后再判断，避免空格绕过白名单",
    ("CatalogService.java", "ProductQuery query = new ProductQuery();"): "组装 Mapper 参数对象",
    ("CatalogService.java", "// ---------------------------------------------------------------- 参数归一"): "以下为参数归一与校验的私有方法",
}

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
            text = MANUAL.get((title, line.strip())) or fn(i, line) or "本行参与结构或样式组织，不产生业务行为。"
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{base.hi_java(line)}</pre></td>'
                f'<td class="explain">{base.esc(text)}</td></tr>'
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
<title>M02-01 · 查询服务 逐行验收</title>
{base.STYLE}</head>
<body>
<header>
<h1>M02-01 · 查询服务与参数校验（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">所属模块：M02 商品浏览 / 任务 M02-01。依据 01 篇 FR-G-02/03、04 篇 §4 参数口径。
验证：mvn -pl cartoon-catalog -am test → Tests run: 10, Failures: 0, Errors: 0, BUILD SUCCESS。
测试类 CatalogServiceTest 的 10 个用例见汇报 §4。</p>
<p class="note" style="margin-top:8px">本任务还加了 <code>src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker</code>（内容 mock-maker-subclass），
用于规避 Mockito inline mock maker 在 JDK 27 上初始化失败的问题。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-15 · M02-01 · 4 个文件、{total} 行。
完整汇报见同目录 M02-01-查询服务.md。</footer>
</body></html>
"""
    doc = doc.replace('<td class="explain"></td>', '<td class="explain">—</td>')
    OUT.write_text(doc, encoding="utf-8")
    print(f"written: {OUT}  rows={total}")


if __name__ == "__main__":
    main()
