"""M07 逐行验收 HTML 生成（管理端）。

用法：python db/tools/gen_m07_acceptance.py [01]
产物：reports/M07-01-逐行验收.html

在 M06 的渲染规则上补一个能力：**只对照本任务改动过的行区间**。
M07-01 改的是 M03 时期的老文件，整文件对照会把大量与本任务无关的旧代码
也搬进逐行页；按区间对照才符合 docs/10 §5「覆盖本任务新增或修改的每一行」。
渲染规则（左列去行尾注释、右列一句说明、整行注释绿色、无「—」）与 M05/M06 相同。
"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m05_acceptance as g5  # noqa: E402

ROOT = g5.ROOT

ACC = "cartoon-account/src/main/java/org/csu/mycartoonstore/account/"
ACC_TEST = "cartoon-account/src/test/java/org/csu/mycartoonstore/account/"
WEB = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/"
WEB_TEST = "cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/"

TASKS = {
    "01": {
        "title": "M07-01 · 身份口径补齐（用户端）",
        "out": "M07-01-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：登录拆成用户端/管理端两个入口（CUSTOMER / ADMIN 各自校验），失败统一为"
                "「账号或密码错误，或账号不可用」；登录拦截器改为每个受保护请求按 id 重读 store_user，"
                "账号被禁用、被删或换成管理角色后旧会话立刻失效。"
                "验证：mvn -pl cartoon-account -am test → Tests run: 12, Failures: 0；"
                "mvn -pl cartoon-user-web -am test → Tests run: 128, Failures: 0（原 124，新增 4）。"
                "只对照本任务改动的行：AccountService / AccountServiceTest / LoginPageTest / CartFixture /"
                " AuthController 按改动区间对照；LoginInterceptor 只对照重写部分（旧的回跳与同源判断 M03 已验收、本次未改）；"
                " WebConfig 与 LoginInterceptorTest 整文件对照。",
        "files": [
            (ACC + "service/AccountService.java", "AccountService.java",
             "登录入口拆成 CUSTOMER / ADMIN，失败同码同文案", "java", [(29, 39), (49, 84)]),
            (WEB + "interceptor/LoginInterceptor.java", "LoginInterceptor.java",
             "每请求重读账号，禁用/换角色即时失效", "java", [(1, 13), (15, 19), (22, 58)]),
            (WEB + "config/WebConfig.java", "WebConfig.java",
             "把 AccountService 交给登录拦截器", "java", None),
            (WEB + "controller/AuthController.java", "AuthController.java",
             "用户端登录改调 authenticateCustomer", "java", [(76, 78)]),
            (ACC_TEST + "service/AccountServiceTest.java", "AccountServiceTest.java",
             "12 个用例：两端登录互斥与统一文案", "java", [(37, 118), (162, 172)]),
            (WEB_TEST + "LoginPageTest.java", "LoginPageTest.java",
             "登录页：禁用同文案、管理员被拒", "java", [(61, 99), (140, 149)]),
            (WEB_TEST + "LoginInterceptorTest.java", "LoginInterceptorTest.java",
             "7 个拦截用例：禁用、换角色、账号不存在", "java", None),
            (WEB_TEST + "support/CartFixture.java", "CartFixture.java",
             "夹具支持造指定角色与状态的账号", "java", [(20, 30)]),
        ],
    },
}

# 只有"没有行尾注释、规则解释器也认不出"的行才需要登记。
NOTES: dict[tuple[str, str], str] = {
    (ACC + "service/AccountService.java", "public StoreUser authenticateCustomer(String username, String rawPassword) { // 用户端登录入口专用。"):
        "用户端登录：只接受启用 CUSTOMER。",
    (ACC + "service/AccountService.java", "public StoreUser authenticateAdmin(String username, String rawPassword) { // 管理端登录入口专用。"):
        "管理端登录：只接受启用 ADMIN。",
    (ACC + "service/AccountService.java", "private StoreUser authenticate(String username, String rawPassword, String requiredRole) {"):
        "两个入口共用的校验：账号、密码、角色、状态，任一不符都抛 401。",
    (WEB_TEST + "support/CartFixture.java", "public static long createUser(JdbcTemplate jdbc, String username) {"):
        "老签名保留：默认造启用的 CUSTOMER，已有用例不用改。",
    (WEB_TEST + "support/CartFixture.java", "public static long createUser(JdbcTemplate jdbc, String username, String role, String status) {"):
        "新签名：角色与状态由用例指定，拦截用例要造 ADMIN 与 DISABLED。",
    # ---- 用例方法签名行：说明直接取同一条用例的 @DisplayName ----
    (ACC_TEST + "service/AccountServiceTest.java", "void customerLoginOk() {"):
        "用例：用户端启用 CUSTOMER 密码正确 → 登录成功。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerWrongPassword() {"):
        "用例：用户端密码错误 → 401，统一文案。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerUnknownUser() {"):
        "用例：用户端账号不存在 → 与密码错误同码同文案。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerDisabledRejected() {"):
        "用例：用户端禁用账号 → 401，不透露已停用。",
    (ACC_TEST + "service/AccountServiceTest.java", "void adminCannotLogInAsCustomer() {"):
        "用例：管理员不能登录用户端（AC-05）。",
    (ACC_TEST + "service/AccountServiceTest.java", "void adminLoginOk() {"):
        "用例：管理端启用 ADMIN 密码正确 → 登录成功。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerCannotLogInAsAdmin() {"):
        "用例：CUSTOMER 不能登录管理端（FR-A-01）。",
    (ACC_TEST + "service/AccountServiceTest.java", "void disabledAdminRejected() {"):
        "用例：管理端禁用 ADMIN → 401，统一文案。",
    (WEB_TEST + "LoginPageTest.java", "void adminCannotLogIn() throws Exception {"):
        "用例：管理员登录用户端被拒，且会话里不会写入 userId。",
    (WEB_TEST + "LoginPageTest.java", 'username, passwordEncoder.encode(rawPassword), "爱丽丝", role, status);'):
        "续行：把用例指定的角色写进库，密码仍用真实 BCrypt。",
    (WEB_TEST + "LoginInterceptorTest.java", "void disabledAfterLoginIsRejected() throws Exception {"):
        "用例：登录后被禁用，下一个受保护请求就被拒（AC-07）。",
    (WEB_TEST + "LoginInterceptorTest.java", "void adminSessionIsRejected() throws Exception {"):
        "用例：管理员会话访问用户端受保护页被拒（AC-05）。",
    (WEB_TEST + "LoginInterceptorTest.java", "void unknownUserIsRejected() throws Exception {"):
        "用例：会话里的 id 在库里不存在时按未登录处理。",
    # ---- 旧文件里规则解释器给不出"为什么"的行：逐条补上 ----
    (ACC + "service/AccountService.java", "if (!allowed) {"):
        "账号、密码、角色、状态任一不符都走失败分支。",
    (WEB + "interceptor/LoginInterceptor.java", "public class LoginInterceptor implements HandlerInterceptor {"):
        "登录拦截器：未登录或账号已失效就跳登录页。",
    (WEB + "interceptor/LoginInterceptor.java", "if (session != null) {"):
        "会话还在但身份不合格：作废它。",
    (WEB + "config/WebConfig.java", "public class WebConfig implements WebMvcConfigurer {"):
        "拦截器注册：受保护路径与徽标都挂在这里。",
    (WEB + "config/WebConfig.java", '.addPathPatterns("/cart", "/cart/**", "/checkout", "/checkout/**",'):
        "受保护路径：购物车与结算。",
    (WEB + "config/WebConfig.java", '"/orders", "/orders/**", "/payment", "/payment/**",'):
        "受保护路径：订单与支付。",
    (WEB + "controller/AuthController.java", "try {"):
        "登录失败抛 BizException，在这里转成表单提示。",
    (ACC_TEST + "service/AccountServiceTest.java",
     'when(userMapper.selectOne(any())).thenReturn(user("alice", "Cartoon123!", "CUSTOMER", "ENABLED"));'):
        "准备：库里返回启用的顾客 alice。",
    (ACC_TEST + "service/AccountServiceTest.java",
     'when(userMapper.selectOne(any())).thenReturn(user("carol", "Cartoon123!", "CUSTOMER", "DISABLED"));'):
        "准备：库里返回停用的顾客 carol。",
    (ACC_TEST + "service/AccountServiceTest.java",
     'when(userMapper.selectOne(any())).thenReturn(user("admin", "Cartoon123!", "ADMIN", "ENABLED"));'):
        "准备：库里返回启用的管理员 admin。",
    (ACC_TEST + "service/AccountServiceTest.java",
     'when(userMapper.selectOne(any())).thenReturn(user("admin", "Cartoon123!", "ADMIN", "DISABLED"));'):
        "准备：库里返回停用的管理员 admin。",
    (ACC_TEST + "service/AccountServiceTest.java", '.hasMessage("账号或密码错误，或账号不可用");'):
        "断言提示就是那句统一文案，不透露具体原因。",
    (ACC_TEST + "service/AccountServiceTest.java", 'StoreUser result = service.authenticateCustomer("alice", "Cartoon123!");'):
        "动作：走用户端登录。",
    (ACC_TEST + "service/AccountServiceTest.java", 'StoreUser result = service.authenticateAdmin("admin", "Cartoon123!");'):
        "动作：走管理端登录。",
    (ACC_TEST + "service/AccountServiceTest.java", "StoreUser u = new StoreUser();"):
        "夹具：拼一个待返回的账号对象。",
    (ACC_TEST + "service/AccountServiceTest.java", "return u;"):
        "交回给用例做断言。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setId(1L);"):
        "固定 id：会话与断言只用得到用户名与角色。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setUsername(username);"):
        "用户名：登录按它查。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setPasswordHash(encoder.encode(rawPassword));"):
        "真实 BCrypt 散列，matches 才能通过。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setDisplayName(username);"):
        "显示名：与登录判定无关。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setRole(role);"):
        "角色由用例指定，两端互斥靠它。",
    (ACC_TEST + "service/AccountServiceTest.java", "u.setStatus(status);"):
        "状态由用例指定：ENABLED / DISABLED。",
    (WEB_TEST + "LoginPageTest.java", 'seedUser("alice", "Cartoon123!", "ENABLED");'):
        "准备：启用的顾客 alice。",
    (WEB_TEST + "LoginPageTest.java", 'seedUser("carol", "Cartoon123!", "DISABLED");'):
        "准备：停用的顾客 carol。",
    (WEB_TEST + "LoginPageTest.java", 'seedUser("admin", "Cartoon123!", "ENABLED", "ADMIN");'):
        "准备：启用的管理员 admin。",
    (WEB_TEST + "LoginPageTest.java", 'assertThat(session.getAttribute("userId")).isNull();'):
        "被拒后会话里不能留下 userId。",
    (WEB_TEST + "LoginPageTest.java", 'seedUser(username, rawPassword, status, "CUSTOMER");'):
        "三参重载转调四参，默认普通顾客。",
    (WEB_TEST + "LoginPageTest.java", "MockHttpSession session = new MockHttpSession();"):
        "准备：手工会话，用来检查登录被拒后没写 userId。",
    (WEB_TEST + "support/CartFixture.java", 'return createUser(jdbc, username, "CUSTOMER", "ENABLED");'):
        "老签名转调新签名：默认启用 CUSTOMER。",
}


def render(task_id: str, meta: dict) -> None:
    """渲染一页：只取本任务改动的行区间，左列干净代码，右列说明。"""
    sections, nav, total, fallback = [], [], 0, 0  # 分节、导航、总行数、兜底行数。
    for rel, title, subtitle, kind, ranges in meta["files"]:
        lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()  # 原始源码。
        sid = "s" + str(len(nav) + 1)  # 锚点 id。
        nav.append(f'<a href="#{sid}">{title}</a>')  # 顶部导航。
        fn = g5.g4.make_java_explainer(lines) if kind == "java" else g5.g4.m22.make_html_explainer(lines)
        hi = g5.g4.base.hi_java if kind == "java" else g5.g4.m22.hi_html  # 语法高亮。
        wanted = [i for i in range(1, len(lines) + 1)
                  if not ranges or any(a <= i <= b for a, b in ranges)]  # 本任务对照的行。
        rows = []  # 本文件的行。
        for i in wanted:
            line = lines[i - 1]  # 原始行。
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
                fallback += 1
            rows.append(
                f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
                f'<td class="code"><pre>{hi(display)}</pre></td>'
                f'<td class="explain">{g5.g4.m22.esc(text)}</td></tr>'
            )
        total += len(rows)  # 累计行数。
        scope = "本任务改动行" if ranges else "全文件"
        sections.append(f"""<section id="{sid}">
<h2>{title}<small>{subtitle} · {scope} {len(rows)} 行</small></h2>
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
<p>一行源码一行记录，行号取自原文件。左列代码已去掉行尾注释，注释文字放在右列；整行注释仍在原位、绿色显示。</p>
<nav>{''.join(nav)}</nav>
</header>
<main>
<p class="note">{g5.g4.m22.esc(meta["note"])}</p>
<p class="note" style="margin-top:8px">格式约定：左列不出现行尾注释，避免与右列重复；每一条代码行都有说明，不出现「—」。</p>
{chr(10).join(sections)}
</main>
<footer>{meta["date"]} · {meta["title"]} · {len(meta["files"])} 个文件、{total} 行（兜底 {fallback} 行）。完整汇报见同目录的 Markdown 汇报。</footer>
</body></html>
"""
    out = ROOT / "reports" / meta["out"]  # 输出路径。
    out.write_text(doc, encoding="utf-8")  # 写文件。
    print(f"written: {out.name}  rows={total}  fallback={fallback}")  # 控制台回显。


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        render(task_id, TASKS[task_id])  # 逐个任务渲染。


if __name__ == "__main__":
    main()
