"""M09 逐行验收 HTML 生成（验收交付）。

用法：python db/tools/gen_m09_acceptance.py [01]
产物：reports/M09-01-逐行验收.html

复用 M08 的渲染器（左列去行尾注释、右列一句说明、整行注释绿色、无「—」），
只登记 M09 的任务元数据与补充说明；pom 与模板改动按 docs/10 §5 用汇报里的分区表覆盖，
不逐行渲染。
"""
from __future__ import annotations

import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import gen_m08_acceptance as g8  # noqa: E402

ROOT = g8.ROOT

COMMON = "cartoon-common/src/main/java/org/csu/mycartoonstore/common/"
COMMON_TEST = "cartoon-common/src/test/java/org/csu/mycartoonstore/common/"

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
}


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        g8.render(task_id, TASKS[task_id])  # 逐任务渲染。


if __name__ == "__main__":
    main()
