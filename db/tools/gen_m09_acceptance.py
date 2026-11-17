"""M09 逐行验收 HTML 生成（验收交付）。

用法：python db/tools/gen_m09_acceptance.py [01]
产物：reports/M09-01-逐行验收.html

复用 M08 的渲染器（左列去行尾注释、右列一句说明、整行注释绿色、无「—」），
只登记 M09 的任务元数据与补充说明；pom 与模板改动按 docs/10 §5 用汇报里的分区表覆盖，
不逐行渲染。
"""
from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m08_acceptance as g8  # noqa: E402

ROOT = g8.ROOT
g5 = g8.g5  # M05 的渲染模块：高亮、行尾注释切分、解释器都在这里。

COMMON = "cartoon-common/src/main/java/org/csu/mycartoonstore/common/"
COMMON_TEST = "cartoon-common/src/test/java/org/csu/mycartoonstore/common/"
WEB_CONFIG = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/config/"
WEB_CTRL = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/controller/"
WEB_TEST = "cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/"
ADMIN_CONFIG = "cartoon-admin-web/src/main/java/org/csu/mycartoonstore/admin/config/"
ADMIN_CTRL = "cartoon-admin-web/src/main/java/org/csu/mycartoonstore/admin/controller/"
ADMIN_TEST = "cartoon-admin-web/src/test/java/org/csu/mycartoonstore/admin/"
CAT = "cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/"
CAT_TEST = "cartoon-catalog/src/test/java/org/csu/mycartoonstore/catalog/"
TEST_SUPPORT = "cartoon-test-support/src/main/java/org/csu/mycartoonstore/testsupport/"

# 手写补充：行尾注释已经能回答"为什么"的行不再重复登记；
# 这里只补行尾注释覆盖不到的结构行与测试意图。
NOTES = {
    # ---- M09-01：令牌本体 ----
    (COMMON + "security/CsrfToken.java", "public final class CsrfToken {"):
        "全静态工具类：不允许被实例化，也不做成 Bean。",
    (COMMON + "security/CsrfToken.java", "public static final String SESSION_KEY = \"csrfToken\"; // 只放会话，不落库、不写 Cookie。"):
        "会话键取值直接决定两端能否共用同一套过滤器与模板。",
    (COMMON + "security/CsrfToken.java", "public static final String PARAM_NAME = \"_csrf\"; // 模板、过滤器、测试三处引用同一常量。"):
        "参数名与 docs/04 §1 一致，改名会同时影响 29 个表单。",
    # ---- M09-01：过滤器 ----
    (COMMON + "security/CsrfFilter.java", "public class CsrfFilter extends OncePerRequestFilter {"):
        "继承一次性过滤器：错误分发不会重复校验同一个请求。",
    (COMMON + "security/CsrfFilter.java", "protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)"):
        "主流程只有三条分支：静态资源、读请求、写请求。",
    # ---- M09-01：测试 ----
    (COMMON_TEST + "security/CsrfTokenTest.java", "class CsrfTokenTest {"):
        "不启 Spring：令牌是纯函数，坏掉时一眼能定位。",
    (COMMON_TEST + "security/CsrfTokenTest.java", "void generateIsRandom() {"):
        "100 次生成互不相同：令牌不能是固定值。",
    (COMMON_TEST + "security/CsrfTokenTest.java", "void matchesSameToken() {"):
        "同一令牌自比：页面回传的正是签发出去的那个。",
    (COMMON_TEST + "security/CsrfTokenTest.java", "void rejectsDifferentToken() {"):
        "两个会话的令牌不能互相通用。",
    (COMMON_TEST + "security/CsrfTokenTest.java", "void rejectsNullAndEmpty() {"):
        "会话没令牌与表单没带令牌都按失败处理。",
    (COMMON_TEST + "security/CsrfTokenTest.java", "void rejectsDifferentLength() {"):
        "前缀相同但长度不同也要拒绝。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "class CsrfFilterTest {"):
        "不启 Spring：用 Mock 请求直接驱动过滤器，只验证放行与拦截。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void getIssuesToken() throws ServletException, IOException {"):
        "GET /login：签发令牌、放行、200。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void tokenIsStableWithinSession() throws ServletException, IOException {"):
        "同一会话第二次请求不换令牌，另一个页面才不会失效。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void staticResourceSkipsSession() throws ServletException, IOException {"):
        "CSS 请求放行且不建会话。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void staticResourceWithContextPath() throws ServletException, IOException {"):
        "管理端的 /admin 前缀先截掉再判断。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void postWithoutSessionForbidden() throws ServletException, IOException {"):
        "没有会话的 POST 直接 403，不进 Controller。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void postWithoutParamForbidden() throws ServletException, IOException {"):
        "有会话但表单没带隐藏域：403。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void postWithWrongTokenForbidden() throws ServletException, IOException {"):
        "令牌不匹配：403。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void postWithCorrectTokenPasses() throws ServletException, IOException {"):
        "令牌一致：放行到下游。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void lowercaseMethodIsChecked() throws ServletException, IOException {"):
        "小写 post 也要校验，不能靠大小写绕过。",
    (COMMON_TEST + "security/CsrfFilterTest.java", "void otherUnsafeMethodsAreChecked() throws ServletException, IOException {"):
        "PUT / DELETE 同样属于写请求。",
    # ---- M09-02：两端接入 ----
    (WEB_CONFIG + "CsrfFilterConfig.java", "public FilterRegistrationBean<CsrfFilter> csrfFilter() {"):
        "用户端注册：全站路径、固定顺序。",
    (ADMIN_CONFIG + "CsrfFilterConfig.java", "public FilterRegistrationBean<CsrfFilter> csrfFilter() {"):
        "管理端注册：容器会拼上 /admin 前缀。",
    (WEB_CTRL + "CsrfAdvice.java", "public String csrfToken(HttpServletRequest request) {"):
        "用户端注入：模板隐藏域取的就是这个值。",
    (ADMIN_CTRL + "CsrfAdvice.java", "public String csrfToken(HttpServletRequest request) {"):
        "管理端注入：与用户端同一会话键。",
    # ---- M09-03：测试夹具与用例 ----
    (TEST_SUPPORT + "CsrfTestSupport.java", "public static RequestPostProcessor csrf() {"):
        "测试里一处调用即补全会话与参数。",
    (WEB_TEST + "CsrfProtectionTest.java", "void loginPageCarriesToken() throws Exception {"):
        "登录页：隐藏域与会话值一致。",
    (WEB_TEST + "CsrfProtectionTest.java", "void tokenStableAcrossPages() throws Exception {"):
        "同一会话两个页面：令牌不变。",
    (WEB_TEST + "CsrfProtectionTest.java", "void postWithoutTokenIsForbidden() throws Exception {"):
        "无令牌 POST：403。",
    (WEB_TEST + "CsrfProtectionTest.java", "void postWithWrongTokenIsForbidden() throws Exception {"):
        "错令牌：403。",
    (WEB_TEST + "CsrfProtectionTest.java", "void postWithTokenPasses() throws Exception {"):
        "正确令牌：登录照旧 302。",
    (WEB_TEST + "CsrfProtectionTest.java", "void logoutWithoutTokenIsForbidden() throws Exception {"):
        "退出也要令牌：跨站不能把人登出。",
    (WEB_TEST + "CsrfProtectionTest.java", "void guestPostIsForbiddenNotRedirect() throws Exception {"):
        "游客无令牌：403 而非 302。",
    (WEB_TEST + "CsrfProtectionTest.java", "void forbiddenPageRenders() throws Exception {"):
        "403 页面文案：错误控制器 + HTML 协商。",
    (WEB_TEST + "CsrfProtectionTest.java", "void formPagesCarryToken() throws Exception {"):
        "商品页与购物车页的写表单都带令牌。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void loginPageCarriesToken() throws Exception {"):
        "后台登录页：隐藏域与会话值一致。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void createCategoryWithoutTokenIsForbidden() throws Exception {"):
        "无令牌新增分类：403 且不写库。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void forbiddenPageRenders() throws Exception {"):
        "后台 403 页面文案。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void createCategoryWithTokenPasses() throws Exception {"):
        "正确令牌：新增照旧落库。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void listPageCarriesToken() throws Exception {"):
        "列表页删除表单带令牌。",
    (ADMIN_TEST + "CsrfProtectionTest.java", "void logoutWithoutTokenIsForbidden() throws Exception {"):
        "后台退出也要令牌。",
    (WEB_TEST + "XssEscapingTest.java", "void productPageEscapesScript() throws Exception {"):
        "详情页：名称与简介被转义。",
    (WEB_TEST + "XssEscapingTest.java", "void categoryPageEscapesScript() throws Exception {"):
        "列表页：名称被转义。",
    (WEB_TEST + "XssEscapingTest.java", "void keywordEchoEscapesScript() throws Exception {"):
        "关键字回显：输入框里不能出现可执行标签。",
    # ---- M09-04：登录换会话 ID ----
    (WEB_CTRL + "AuthController.java", "public String login(@RequestParam String username,"):
        "登录入口：校验通过后先换会话 ID，再写身份。",
    (WEB_TEST + "LoginPageTest.java", "void loginRotatesSessionId() throws Exception {"):
        "登录前后会话 ID 不同，身份与令牌都在。",
    (WEB_TEST + "LoginPageTest.java", "void csrfTokenSurvivesSessionRotation() throws Exception {"):
        "换 ID 后登录前签发的令牌仍可用于退出。",
    (WEB_TEST + "LoginPageTest.java", "void adminCannotLogIn() throws Exception {"):
        "管理员登录用户端：同文案且不写登录态（AC-05）。",
    (WEB_TEST + "LoginPageTest.java", 'username, passwordEncoder.encode(rawPassword), "爱丽丝", role, status);'):
        "续行：密码存 BCrypt 散列，显示名固定，角色与状态由调用方给。",
    # ---- M09-05：描述与封面 ----
    (CAT_TEST + "service/ProductAdminServiceTest.java", "void createTooLongDescription() {"):
        "描述 2001 字：400 且不写库。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", "void createBlankDescriptionStoredAsNull() {"):
        "空白描述：入库为 NULL。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", "void createExternalCover() {"):
        "外链封面：400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", "void createBadCoverPath() {"):
        "上跳目录与缺前缀：都拒绝。",
    (ADMIN_TEST + "ProductAdminPageTest.java", "void createTooLongDescription() throws Exception {"):
        "页面层：超长描述回表单 400。",
    (ADMIN_TEST + "ProductAdminPageTest.java", "void createExternalCover() throws Exception {"):
        "页面层：外链封面回表单 400。",
}

g8.NOTES.update(NOTES)  # 复用 M08 渲染器的说明优先级：手写 > 行尾注释 > 规则解释器。

TASKS = {
    "01": {
        "title": "M09-01 · CSRF 令牌公共件",
        "out": "M09-01-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：会话键与参数名常量、UUID 令牌生成、恒定时间比对、读请求签发令牌、"
                "写请求校验并失败 403、静态资源放行且不建会话、方法名统一大写、context-path 归一；"
                "5 个令牌单测 + 10 个过滤器用例。"
                "验证：mvn -pl cartoon-common test → Tests run: 25, Failures: 0, Errors: 0, Skipped: 0。"
                "pom.xml 新增 spring-context(provided) 与两端注册、403 页面、表单隐藏域属于 M09-02，"
                "按 docs/10 §5 用汇报里的分区表说明，不逐行渲染。",
        "files": [
            (COMMON + "security/CsrfToken.java", "CsrfToken.java", "会话键、参数名、生成与恒定时间比对", "java"),
            (COMMON + "security/CsrfFilter.java", "CsrfFilter.java", "读发令牌、写验令牌、静态资源放行", "java"),
            (COMMON_TEST + "security/CsrfTokenTest.java", "CsrfTokenTest.java", "5 个令牌用例", "java"),
            (COMMON_TEST + "security/CsrfFilterTest.java", "CsrfFilterTest.java", "10 个过滤器用例", "java"),
        ],
    },
    "02": {
        "title": "M09-02 · 两端接入与 403 页面",
        "out": "M09-02-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：两端注册 CsrfFilter（全站、最高优先级）、CsrfAdvice 注入 csrfToken、隐藏域片段、403 页面、"
                "29 处写表单接入、docs/02 §6.3 与 docs/04 §1 口径改为已启用、运行指南把 CSRF 挪到能测一栏。"
                "验证：临时端口 18080/18081 起真实容器，探针 → 页面含令牌、无令牌 403 且渲染 403 页、静态资源无 Set-Cookie、令牌跨请求稳定。"
                "模板与 29 处表单按 docs/10 §5 用汇报里的分区表说明，不逐行渲染。",
        "files": [
            (WEB_CONFIG + "CsrfFilterConfig.java", "CsrfFilterConfig.java", "用户端注册过滤器", "java"),
            (ADMIN_CONFIG + "CsrfFilterConfig.java", "CsrfFilterConfig.java", "管理端注册过滤器", "java"),
            (WEB_CTRL + "CsrfAdvice.java", "CsrfAdvice.java", "用户端注入令牌", "java"),
            (ADMIN_CTRL + "CsrfAdvice.java", "CsrfAdvice.java", "管理端注入令牌", "java"),
        ],
    },
    "03": {
        "title": "M09-03 · 测试夹具与 AC-31 用例",
        "out": "M09-03-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：CsrfTestSupport（会话令牌 + 参数）、20 个测试文件 108 处 POST 补 .with(csrf())、"
                "两端 CsrfProtectionTest（9 + 6 例）、XssEscapingTest（3 例）、AdminLoginTest 的「顾客不能登录」改断言登录态。"
                "验证：mvn test → 438 用例，10 模块 SUCCESS，0 失败 0 跳过。"
                "108 处机械改动与既有测试文件的改动按规则说明，不逐行渲染。",
        "files": [
            (TEST_SUPPORT + "CsrfTestSupport.java", "CsrfTestSupport.java", "测试侧令牌后置处理器", "java"),
            (WEB_TEST + "CsrfProtectionTest.java", "CsrfProtectionTest.java", "用户端 9 个页面级用例", "java"),
            (ADMIN_TEST + "CsrfProtectionTest.java", "CsrfProtectionTest.java", "管理端 6 个页面级用例", "java"),
            (WEB_TEST + "XssEscapingTest.java", "XssEscapingTest.java", "3 个转义用例", "java"),
        ],
    },
    "04": {
        "title": "M09-04 · 用户端登录重建会话",
        "out": "M09-04-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：AuthController.login 改为先 getSession(true) 再 changeSessionId() 后写身份，与管理端同构；"
                "新增 2 条用例（登录前后会话 ID 不同且令牌仍在、换 ID 后登录前令牌仍可用于退出）；"
                "docs/02 §6.2 的实现状态改为两端一致。"
                "验证：mvn -pl cartoon-user-web -am test → 162 用例，全绿。"
                "LoginPageTest 的其它既有用例与模板改动不重复渲染。",
        "files": [
            (WEB_CTRL + "AuthController.java", "AuthController.java", "登录时轮换会话 ID", "java"),
            (WEB_TEST + "LoginPageTest.java", "LoginPageTest.java", "新增 2 个会话用例", "java"),
        ],
    },
    "05": {
        "title": "M09-05 · 商品描述与封面路径收紧",
        "out": "M09-05-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：新增 DESCRIPTION_MAX=2000 与 normalizeDescription（空白存 NULL、超长 400）；"
                "normalizeCover 增加站内路径判断（拒绝外链、协议相对地址、.. 上跳、缺 images/ 前缀）；"
                "create/update 改为使用校验后的描述；4 个服务用例 + 2 个页面用例；docs/01 FR-A-03 与 docs/03 §2.3 同步。"
                "验证：mvn -pl cartoon-catalog,cartoon-admin-web -am test → catalog 78、admin-web 78，全绿。"
                "只渲染改动的行段（常量、create/update 两处调用、两个校验方法、新增用例），其余 M07 已验收代码不重复。",
        "files": [
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java", "描述上限常量", "java", 51, 56),
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java", "新增时的描述与封面校验", "java", 125, 130),
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java", "编辑时的描述与封面校验", "java", 165, 170),
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java", "描述与封面校验方法", "java", 339, 370),
            (CAT_TEST + "service/ProductAdminServiceTest.java", "ProductAdminServiceTest.java", "4 个校验用例", "java", 179, 233),
            (ADMIN_TEST + "ProductAdminPageTest.java", "ProductAdminPageTest.java", "2 个页面用例", "java", 130, 166),
        ],
    },
}


def render(task_id: str, meta: dict) -> None:
    """渲染一页：左列干净代码（去掉行尾注释），右列放该行的说明；文件项可带行号区间。"""
    sections, nav, total, fallback = [], [], 0, 0  # 分节、导航、总行数、兜底行数。
    for entry in meta["files"]:
        rel, title, subtitle, kind = entry[:4]  # 前四项固定。
        first, last = (entry[4], entry[5]) if len(entry) >= 6 else (1, None)  # 可选区间。
        all_lines = (ROOT / rel).read_text(encoding="utf-8").splitlines()  # 整份源码：javadoc 与解释器都要看全文。
        lines = all_lines[first - 1: last]  # 本次要渲染的行段。
        sid = "s" + str(len(nav) + 1)  # 锚点 id。
        nav.append(f'<a href="#{sid}">{title}</a>')  # 顶部导航。
        fn = g5.g4.make_java_explainer(all_lines) if kind == "java" else g5.g4.m22.make_html_explainer(all_lines)
        hi = g5.g4.base.hi_java if kind == "java" else g5.g4.m22.hi_html  # 语法高亮。
        rows = []  # 本段的行。
        for i in range(first, first + len(lines)):  # 行号保持源文件里的真实行号。
            line = all_lines[i - 1]
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
            text = g8.NOTES.get((rel, s)) or g8.NOTES.get((rel, code)) or trailing  # 手写 > 行尾注释。
            if not text and kind == "java":
                text = g5.g4.m04_explainer.explain(code, g5.g4.javadoc_above(all_lines, i - 1))
            if not text and kind == "html":
                text = g5.html_hint(s)
            if not text:
                text = fn(i, line)  # 规则解释器：注解、括号、类声明等。
            if not text and code.startswith("import "):
                text = g5.import_hint(code)
            if re.match(r"public (?:static )?(?:final )?class ", code) and (not text or "见类注释" in text):
                text = g5.preceding_comment(all_lines, i) or text
            if not text:
                text = g8.FALLBACK  # 最后兜底，正常应为 0 行。
                fallback += 1
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

    if fallback:
        print(f"警告：{task_id} 有 {fallback} 行走兜底说明，请补 NOTES")  # 兜底应为 0。

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
<footer>{meta["date"]} · {meta["title"]} · {len(meta["files"])} 个行段 · 合计 {total} 行。完整汇报见同目录的 Markdown 汇报。</footer>
</body></html>
"""
    out = ROOT / "reports" / meta["out"]  # 输出路径。
    out.write_text(doc, encoding="utf-8")  # 写文件。
    print(f"written: {out.name}  rows={total}  fallback={fallback}")  # 控制台回显。


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        render(task_id, TASKS[task_id])  # 逐任务渲染。


if __name__ == "__main__":
    main()
