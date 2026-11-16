"""M04 逐行验收 HTML 生成（购物车三个任务一次生成）。

用法：python db/tools/gen_m04_acceptance.py
产物：reports/M04-01-逐行验收.html、M04-02-逐行验收.html、M04-03-逐行验收.html

说明来源与 M02/M03 一致：优先用源码里的行尾注释与 javadoc，
本文件的 NOTES 覆盖"没写行尾注释但必须解释"的规则行；其余结构性代码渲染为「—」。
"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m01_07_acceptance as base  # noqa: E402
import gen_m02_02_acceptance as m22  # noqa: E402
from java_explainer import make_java_explainer  # noqa: E402

ROOT = base.ROOT

CART = "cartoon-cart/src/main/java/org/csu/mycartoonstore/cart/"
WEB = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/"
TEST = "cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/"

TASKS = {
    "01": {
        "title": "M04-01 · 购物车服务与数据访问",
        "out": "M04-01-逐行验收.html",
        "note": "内容：加车累加、数量 1–99、每车 20 种、库存校验、归属与版本校验、行小计与汇总口径。"
                "验证：mvn -pl cartoon-cart -am test → Tests run: 18, Failures: 0, Errors: 0。",
        "files": [
            (CART + "service/CartService.java", "CartService.java", "购物车规则：加车/改量/移除/汇总", "java"),
            (CART + "dto/CartLine.java", "CartLine.java", "购物车行视图与失效判定", "java"),
            (CART + "dto/CartSummary.java", "CartSummary.java", "徽标、可结算件数与总额", "java"),
            (CART + "mapper/CartItemMapper.java", "CartItemMapper.java", "按用户改量、删除、徽标求和", "java"),
            ("cartoon-cart/src/test/java/org/csu/mycartoonstore/cart/service/CartServiceTest.java",
             "CartServiceTest.java", "18 个用例：累加/上限/库存/归属/版本/汇总", "java"),
        ],
    },
    "02": {
        "title": "M04-02 · 购物车页与加入购物车",
        "out": "M04-02-逐行验收.html",
        "note": "内容：GET /cart 渲染、POST /cart/items 加车、页头徽标、详情页加车表单、游客加车回跳详情页。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 72, Failures: 0。"
                "样式文件 store.css 只追加购物车视觉规则，不逐行对照。",
        "files": [
            (WEB + "controller/CartController.java", "CartController.java", "购物车路由：页面 + 加车", "java"),
            (WEB + "interceptor/CartBadgeInterceptor.java", "CartBadgeInterceptor.java", "所有页面注入 cartCount", "java"),
            (WEB + "config/WebConfig.java", "WebConfig.java", "注册登录拦截器与徽标拦截器", "java"),
            (WEB + "interceptor/LoginInterceptor.java", "LoginInterceptor.java", "未登录回跳：GET 原地址、POST 来源页", "java"),
            ("cartoon-user-web/src/main/resources/templates/cart.html", "cart.html", "购物车页：行、合计、失效条目", "html"),
            ("cartoon-user-web/src/main/resources/templates/product.html", "product.html", "详情页加车表单", "html"),
            (TEST + "CartPageTest.java", "CartPageTest.java", "11 个用例：空车/行/徽标/加车/游客", "java"),
        ],
    },
    "03": {
        "title": "M04-03 · 改数量、移除与失效条目",
        "out": "M04-03-逐行验收.html",
        "note": "内容：改数量与移除的归属/版本校验、403 与 409 状态码、失效条目提示与结算禁用。"
                "验证：mvn -pl cartoon-user-web -am test → Tests run: 72, Failures: 0（含新增 10 例）。"
                "失效条目的模板行在 M04-02 的 cart.html 一节已逐行对照，本页不重复。",
        "files": [
            (TEST + "CartMutationTest.java", "CartMutationTest.java", "10 个用例：改量/移除/并发版本/上限/失效", "java"),
            (WEB + "controller/GlobalExceptionHandler.java", "GlobalExceptionHandler.java", "按错误码输出 4xx", "java"),
        ],
    },
}

# 关键行的补充说明：键是 (文件相对路径, 去首尾空白后的源码)
NOTES: dict[tuple[str, str], str] = {
    (CART + "service/CartService.java", "public static final int MAX_KINDS = 20;"):
        "每车最多 20 种 SKU；超限拒绝而不是静默丢掉新商品",
    (CART + "service/CartService.java", "public static final int MAX_QUANTITY = 99;"):
        "单 SKU 上限，与 cart_item 的 CHECK (quantity BETWEEN 1 AND 99) 对齐",
    (CART + "service/CartService.java", "public CartSummary summary(long userId) {"):
        "页面汇总：失效行也返回，由页面提示原因，不替用户隐藏",
    (CART + "service/CartService.java", "return views.stream().map(CartLine::from).toList();"):
        "视图转行对象：可用性与行小计只在这里算一次",
    (CART + "service/CartService.java", "return cartItemMapper.sumQuantityByUser(userId);"):
        "徽标走 SQL 求和，包含失效条目（FR-C-01 §7）",
    (CART + "service/CartService.java", "Product product = requireOnSaleProduct(productId);"):
        "先确认商品在售再加车；下架与不存在同为 404",
    (CART + "service/CartService.java", "if (existing != null) {"):
        "同商品已在车里：走累加分支，不再插第二条",
    (CART + "service/CartService.java", "mergeInto(existing, quantity, product);"):
        "累加后统一校验 99 上限与库存",
    (CART + "service/CartService.java", "} catch (DuplicateKeyException e) {"):
        "并发下另一会话刚插入同一商品：唯一键冲突后按累加重试一次，不把 500 抛给用户",
    (CART + "service/CartService.java", "int rows = cartItemMapper.updateQuantity(userId, itemId, quantity, version);"):
        "归属与版本都写进 WHERE：影响 0 行说明条目不属于本人或页面已过期",
    (CART + "service/CartService.java", "int rows = cartItemMapper.deleteByIdAndUser(userId, itemId, version);"):
        "删除同样带 user_id 与 version，旧页面删不掉新状态的条目",
    (CART + "service/CartService.java", "if (item == null) {"):
        "条目已不存在：404，不泄露它曾经属于谁",
    (CART + "service/CartService.java", "if (!item.getUserId().equals(userId)) {"):
        "不是本人 → 403；仅靠 SQL 影响行数无法区分 403 与 409，所以先查归属",
    (CART + "service/CartService.java", "if (item.getVersion() == null || item.getVersion() != version) {"):
        "版本对不上说明页面是旧的：409，绝不覆盖其他会话改过的新数量",
    (CART + "service/CartService.java", "int newQuantity = existing.getQuantity() + added;"):
        "校验累加后的总数，不只校验本次输入（FR-C-01 §2）",
    (CART + "service/CartService.java", "ensureStock(newQuantity, product);"):
        "累加后仍不能超过库存，否则页面会立刻变失效",
    (CART + "service/CartService.java", "int stock = product.getStock() == null ? 0 : product.getStock();"):
        "库存为空按 0 处理：宁可拒绝，也不放一个买不到的数量进车",
    (CART + "service/CartService.java", 'throw BizException.badRequest("商品暂时缺货");'):
        "库存 0 单独给缺货文案，避免出现「最多可买 0 件」这种怪提示",
    (CART + "service/CartService.java", "if (quantity > stock) {"):
        "库存不足时把可买上限告诉用户，而不是只说失败",
    (CART + "service/CartService.java", 'throw BizException.badRequest("数量必须是 1–99 的整数");'):
        "数量非法统一一句话：0、100、小数、非数字都不写库",
    (CART + "service/CartService.java", 'if (raw == null || raw.isBlank()) {'):
        "缺必填参数直接 400，不把空串当 0",
    (CART + "dto/CartLine.java", 'boolean onSale = "ON_SALE".equals(view.getProductStatus());'):
        "只有 ON_SALE 可结算；DRAFT 仍需展示并给出移除入口",
    (CART + "dto/CartLine.java", "boolean enough = view.getStock() != null && view.getQuantity() <= view.getStock();"):
        "数量超过当前库存即失效：购物车不预占库存，库存可能被别处买走",
    (CART + "dto/CartLine.java", "BigDecimal amount = MoneyUtils.multiply(view.getPrice(), view.getQuantity());"):
        "行小计按商品当前价重算，购物车不存价格",
    (CART + "dto/CartSummary.java", "badge += line.getQuantity();"):
        "徽标含失效条目，与页面提示的「共 N 件」一致",
    (CART + "dto/CartSummary.java", "if (line.isAvailable()) {"):
        "失效行只计件数，不计可结算件数与金额",
    (CART + "dto/CartSummary.java", "return new CartSummary(List.copyOf(lines), badge, valid, total, invalid == 0, invalid);"):
        "全可用才允许去结算；行列表做不可变拷贝，防止模板层改数据",
    (WEB + "controller/CartController.java", "CartSummary summary = cartService.summary(currentUserId(session));"):
        "整页渲染：合计与失效判断都在服务层算好，模板只展示",
    (WEB + "controller/CartController.java", 'redirect.addFlashAttribute("message", "已加入购物车");'):
        "一次性提示，刷新不会重复显示",
    (WEB + "controller/CartController.java", "throw new BizException(ErrorCode.UNAUTHORIZED);"):
        "兜底：拦截规则被改动时给 401，而不是 NullPointerException → 500",
    (WEB + "interceptor/CartBadgeInterceptor.java", 'if (viewName != null && viewName.startsWith("redirect:")) {'):
        "重定向不渲染页面，跳过可以省一次求和查询",
    (WEB + "interceptor/CartBadgeInterceptor.java",
     "int count = userId instanceof Long id ? cartService.badgeQuantity(id) : 0;"):
        "游客固定 0；登录用户查真实件数",
    (WEB + "interceptor/LoginInterceptor.java", 'if ("POST".equalsIgnoreCase(request.getMethod())) {'):
        "POST 地址不能直接 GET（会 405），改记来源页，登录后回到详情页",
    (WEB + "interceptor/LoginInterceptor.java", "if (port != request.getServerPort()) {"):
        "端口不同也算外站来源，防止被构造成登录后跳站外",
    (WEB + "controller/GlobalExceptionHandler.java", "response.setStatus(e.code().status());"):
        "状态码与错误码一一对应：403 越权、409 冲突不再被压成 400",
    ("cartoon-user-web/src/main/resources/templates/cart.html",
     'th:classappend="${line.invalid} ? \'invalid\'"'):
        "失效行整行浅红底，和可结算行一眼分开",
    ("cartoon-user-web/src/main/resources/templates/cart.html",
     'th:if="${line.invalid}"'):
        "只在失效时显示原因（已下架 / 仅剩 n 件）",
    ("cartoon-user-web/src/main/resources/templates/product.html",
     'th:action="@{/cart/items}"'):
        "加车提交到购物车接口；游客被拦截器接走，登录后回本页",
    ("cartoon-user-web/src/main/resources/templates/product.html",
     'th:if="${session.userId == null}"'):
        "游客先说明「要再点一次」，避免登录回来后以为已经加过",
}


def is_comment(stripped: str) -> bool:
    return stripped.startswith(("//", "/*", "*", "*/", "<!--", "-->"))


def import_hint(stripped: str) -> str:
    body = stripped.removeprefix("import ").removeprefix("static ").removesuffix(";").strip()
    name = body.split(".")[-1]
    return ("静态导入 " + name) if stripped.startswith("import static") else ("引入 " + name)


def render(task_id: str, meta: dict) -> None:
    sections, nav, total = [], [], 0
    for rel, title, subtitle, kind in meta["files"]:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()
        sid = "s" + str(len(nav) + 1)
        nav.append(f'<a href="#{sid}">{title}</a>')
        fn = make_java_explainer(lines) if kind == "java" else m22.make_html_explainer(lines)
        hi = base.hi_java if kind == "java" else m22.hi_html
        rows = []
        for i, line in enumerate(lines, 1):
            stripped = line.strip()
            if not stripped:
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            if is_comment(stripped):
                # 注释行绿色、右列留空：注释本身已经是说明，再翻译一遍是废话
                rows.append(
                    f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                    f'<td class="code"><pre class="cmtline">{m22.esc(line)}</pre></td>'
                    f'<td class="explain"></td></tr>'
                )
                continue
            text = NOTES.get((rel, stripped)) or fn(i, line)
            if not text and stripped.startswith("import "):
                text = import_hint(stripped)
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
<title>{meta["title"]}（逐行验收）</title>
{base.STYLE}</head>
<body>
<header>
<h1>{meta["title"]}（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码，右列是该行的说明。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">{m22.esc(meta["note"])}</p>
<p class="note" style="margin-top:8px">说明口径：行尾注释与 javadoc 优先；没有注释的规则行由 <code>gen_m04_acceptance.py</code> 的 NOTES 补充；
import、注解、括号等结构性代码渲染为「—」。</p>
{chr(10).join(sections)}
</main>
<footer>2026-11-16 · {meta["title"]} · {len(meta["files"])} 个文件、{total} 行。完整汇报见同目录的 Markdown 汇报。</footer>
</body></html>
"""
    out = ROOT / "reports" / meta["out"]
    out.write_text(doc, encoding="utf-8")
    print(f"written: {out.name}  rows={total}")


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)
    for task_id in wanted:
        render(task_id, TASKS[task_id])


if __name__ == "__main__":
    main()
