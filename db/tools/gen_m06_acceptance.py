"""M06 逐行验收 HTML 生成（模拟支付）。

用法：python db/tools/gen_m06_acceptance.py [01]
产物：reports/M06-01-逐行验收.html

复用 M05 的渲染规则（左列去行尾注释、右列一句说明、整行注释绿色、无「—」），
只登记 M06 的任务元数据与补充说明；通用规则仍在 gen_m05 / gen_m04 / m04_explainer。
"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m05_acceptance as g5  # noqa: E402

ROOT = g5.ROOT

PAY = "cartoon-payment/src/main/java/org/csu/mycartoonstore/payment/"
PAY_TEST = "cartoon-payment/src/test/java/org/csu/mycartoonstore/payment/"

TASKS = {
    "01": {
        "title": "M06-01 · 支付服务与凭证",
        "out": "M06-01-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：模拟支付成功/失败两个入口、订单行锁与归属 404、已支付幂等、已取消/已发货 409、"
                "PAY + 时间戳 + 4 位随机的凭证生成与查重重试。"
                "验证：mvn -pl cartoon-payment -am test → Tests run: 13, Failures: 0, Errors: 0。"
                "支付页、路由与入口在 M06-02；库存不变的断言由 M06-03 的集成用例从真实入口验证。",
        "files": [
            (PAY + "service/PaymentService.java", "PaymentService.java", "支付成功/失败的状态迁移与幂等", "java"),
            (PAY + "service/PaymentNoGenerator.java", "PaymentNoGenerator.java", "凭证号生成与查重重试", "java"),
            (PAY + "domain/PaymentResult.java", "PaymentResult.java", "三种支付结局", "java"),
            (PAY_TEST + "service/PaymentServiceTest.java", "PaymentServiceTest.java", "10 个状态迁移用例", "java"),
            (PAY_TEST + "service/PaymentNoGeneratorTest.java", "PaymentNoGeneratorTest.java", "3 个凭证生成用例", "java"),
        ],
    },
}

# 只有"没有行尾注释、规则解释器也认不出"的行才需要登记。
NOTES: dict[tuple[str, str], str] = {
    # ---- 类与方法的声明行：一句话说明职责，避免整段 javadoc 灌进右列 ----
    (PAY + "service/PaymentService.java", "public class PaymentService {"):
        "支付成功/失败的状态迁移与幂等；不碰库存、明细与购物车。",
    (PAY + "service/PaymentService.java", "public PaymentResult paySuccess(long userId, String orderNo) {"):
        "支付成功：待支付 → PAID 并写三件套；已支付走幂等分支。",
    (PAY + "service/PaymentService.java", "public PaymentResult payFailure(long userId, String orderNo) {"):
        "支付失败：只校验归属与状态，任何分支都不写库。",
    (PAY + "service/PaymentNoGenerator.java", "public class PaymentNoGenerator {"):
        "凭证号生成：PAY + 时间戳 + 4 位随机，生成前查重。",
    (PAY + "service/PaymentNoGenerator.java", "public String next() {"):
        "生成一个库里不存在的凭证号：查到 0 条才算数，最多试 3 次。",
    # ---- PaymentService：多行签名与语句续行 ----
    (PAY + "service/PaymentService.java",
     "public PaymentService(SalesOrderMapper orderMapper,"):
        "构造器注入：订单 Mapper、凭证生成器、时钟。",
    (PAY + "service/PaymentService.java",
     "PaymentNoGenerator paymentNoGenerator,"):
        "参数续行：凭证号生成器。",
    (PAY + "service/PaymentService.java",
     "Clock clock) {"):
        "参数续行：时钟，测试可固定。",
    (PAY + "service/PaymentService.java",
     "SalesOrder order = lockOwned(orderNo, userId);"):
        "先锁订单行，再校验归属与状态。",
    # ---- PaymentNoGenerator：拼号的续行 ----
    (PAY + "service/PaymentNoGenerator.java",
     "String candidate = PREFIX + TS.format(LocalDateTime.now(clock))"):
        "拼凭证号：PAY + 14 位时间戳。",
    (PAY + "service/PaymentNoGenerator.java",
     "+ String.format(\"%04d\", ThreadLocalRandom.current().nextInt(RANDOM_BOUND));"):
        "续行：4 位随机数字，补足到秒级时间戳之后。",
    # ---- PaymentServiceTest：多行断言续行 ----
    (PAY_TEST + "service/PaymentServiceTest.java",
     "service = new PaymentService(orderMapper, paymentNoGenerator,"):
        "被测对象：三个协作方都由用例控制。",
    (PAY_TEST + "service/PaymentServiceTest.java",
     "Clock.fixed(NOW, ZoneOffset.ofHours(8)));"):
        "续行：时钟固定，paid_at 才能精确断言。",
    (PAY_TEST + "service/PaymentServiceTest.java",
     ".isInstanceOfSatisfying(BizException.class, e -> {"):
        "断言异常类型并继续检查错误码与文案。",
    (PAY_TEST + "service/PaymentServiceTest.java",
     "e -> assertThat(e.code()).isEqualTo(ErrorCode.CONFLICT));"):
        "续行：只断言错误码",
    (PAY_TEST + "service/PaymentServiceTest.java",
     "e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));"):
        "续行：只断言 404",
}


def render(task_id: str, meta: dict) -> None:
    """渲染一页：左列干净代码（去掉行尾注释），右列放该行的说明。"""
    sections, nav, total = [], [], 0  # 分节、导航、总行数。
    for rel, title, subtitle, kind in meta["files"]:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()  # 原始源码。
        sid = "s" + str(len(nav) + 1)  # 锚点 id。
        nav.append(f'<a href="#{sid}">{title}</a>')  # 顶部导航。
        fn = g5.g4.make_java_explainer(lines) if kind == "java" else g5.g4.m22.make_html_explainer(lines)
        hi = g5.g4.base.hi_java if kind == "java" else g5.g4.m22.hi_html  # 语法高亮。
        rows = []  # 本文件的行。
        for i, line in enumerate(lines, 1):
            s = line.strip()  # 去缩进后判断。
            if not s:
                rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
                continue
            if g5.is_comment_line(s):
                rows.append(
                    f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                    f'<td class="code"><pre class="cmtline">{g5.g4.m22.esc(line)}</pre></td>'
                    f'<td class="explain"></td></tr>'
                )
                continue
            display, trailing = g5.split_trailing(line)  # 左列代码 / 右列注释。
            code = display.strip()  # 用于查说明的代码文本。
            text = NOTES.get((rel, s)) or NOTES.get((rel, code)) or trailing  # 手写 > 行尾注释。
            if not text and kind == "java":
                text = g5.g4.m04_explainer.explain(code, g5.g4.javadoc_above(lines, i - 1))
            if not text and kind == "html":
                text = g5.html_hint(s)
            if not text:
                text = fn(i, line)  # 规则解释器：注解、括号、类声明等。
            if not text and code.startswith("import "):
                text = g5.import_hint(code)
            if re.match(r"public (?:static )?(?:final )?class ", code) and (not text or "见类注释" in text):
                text = g5.preceding_comment(lines, i) or text
            if not text:
                text = "结构行：参与语法组织"  # 最后兜底，正常应为 0 行。
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(display)}</pre></td>'
                f'<td class="explain">{g5.g4.m22.esc(text)}</td></tr>'
            )
        total += len(rows)  # 累计行数。
        sections.append(f"""<section id="{sid}">
<h2>{title}<small>{subtitle} · {len(lines)} 行</small></h2>
<p class="path">{rel}</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（行尾注释已移到右列，整行注释保持绿色）</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(rows)}
</tbody></table>
</section>""")

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>{meta["title"]}（逐行验收）</title>
{g5.g4.base.STYLE}</head>
<body>
<header>
<h1>{meta["title"]}（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列代码已去掉行尾注释，注释文字放在右列；整行注释仍在原位、绿色显示。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">{g5.g4.m22.esc(meta["note"])}</p>
<p class="note" style="margin-top:8px">格式约定：左列不出现行尾注释，避免与右列重复；每一条代码行都有说明，不出现「—」。</p>
{chr(10).join(sections)}
</main>
<footer>{meta["date"]} · {meta["title"]} · {len(meta["files"])} 个文件、{total} 行。完整汇报见同目录的 Markdown 汇报。</footer>
</body></html>
"""
    out = ROOT / "reports" / meta["out"]  # 输出路径。
    out.write_text(doc, encoding="utf-8")  # 写文件。
    print(f"written: {out.name}  rows={total}")  # 控制台回显。


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        render(task_id, TASKS[task_id])  # 逐个任务渲染。


if __name__ == "__main__":
    main()
