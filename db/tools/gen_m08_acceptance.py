"""M08 逐行验收 HTML 生成（P1 扩展）。

用法：python db/tools/gen_m08_acceptance.py [01]
产物：reports/M08-01-逐行验收.html

复用 M05/M06 的渲染规则（左列去行尾注释、右列一句说明、整行注释绿色、无「—」），
只登记 M08 的任务元数据与补充说明；模板与样式按 docs/10 §5 用汇报里的分区表覆盖。
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
ORDER = "cartoon-order/src/main/java/org/csu/mycartoonstore/order/"
ORDER_TEST = "cartoon-order/src/test/java/org/csu/mycartoonstore/order/"
WEB = "cartoon-user-web/src/main/java/org/csu/mycartoonstore/web/"
WEB_TEST = "cartoon-user-web/src/test/java/org/csu/mycartoonstore/web/"

TASKS = {
    "01": {
        "title": "M08-01 · 个人资料服务与页面",
        "out": "M08-01-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：资料字段校验（显示名 1–50、邮箱 ≤100、电话 ≤30，空串归一 null）、"
                "AccountService 只按会话 id 读取并定向更新三列、注册路径复用显示名校验（修掉直发 POST 超长 500）、"
                "GET/POST /profile 的页面与回填、页头「个人资料」入口、6 个账号单测 + 8 个页面用例。"
                "验证：mvn -pl cartoon-account,cartoon-user-web -am test → account 18、user-web 136，全绿。"
                "模板 profile.html 与 fragments/common.html 按 docs/10 §5 用汇报里的分区表说明，不逐行渲染。",
        "files": [
            (ACC + "dto/Profile.java", "Profile.java", "已校验的三项资料", "java"),
            (ACC + "dto/ProfileForm.java", "ProfileForm.java", "去空白与列宽校验", "java"),
            (ACC + "mapper/StoreUserMapper.java", "StoreUserMapper.java", "只更新三列的定向 SQL", "java"),
            (ACC + "service/AccountService.java", "AccountService.java", "资料读取、定向更新与注册显示名校验", "java"),
            (WEB + "controller/ProfileController.java", "ProfileController.java", "资料页回显与保存", "java"),
            (ACC_TEST + "service/AccountServiceTest.java", "AccountServiceTest.java", "6 个新增资料/注册用例", "java"),
            (WEB_TEST + "ProfilePageTest.java", "ProfilePageTest.java", "8 个页面与快照用例", "java"),
        ],
    },
    "02": {
        "title": "M08-02 · 修改密码",
        "out": "M08-02-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：新密码规则（8–32 字符、UTF-8 ≤72 字节、至少两类字符、无空白、不得与旧密码相同）、"
                "旧密码 BCrypt 比对与定向写散列、两次输入一致、成功后销毁当前会话并回登录页、"
                "/password 纳入登录拦截、资料页的「修改密码」入口、7 个账号单测 + 7 个页面用例。"
                "验证：mvn -pl cartoon-account,cartoon-user-web -am test → account 25、user-web 143，全绿。"
                "模板 password.html 与 profile.html 的入口改动按 docs/10 §5 用汇报里的分区表说明，不逐行渲染。",
        "files": [
            (ACC + "dto/PasswordForm.java", "PasswordForm.java", "新密码强度与两次一致", "java"),
            (ACC + "mapper/StoreUserMapper.java", "StoreUserMapper.java", "只写 password_hash 的定向 SQL", "java"),
            (ACC + "service/AccountService.java", "AccountService.java", "旧密码比对、规则校验与写散列", "java"),
            (WEB + "controller/PasswordController.java", "PasswordController.java", "改密页与成功后销毁会话", "java"),
            (WEB + "config/WebConfig.java", "WebConfig.java", "把 /password 纳入受保护路径", "java"),
            (ACC_TEST + "service/AccountServiceTest.java", "AccountServiceTest.java", "7 个改密用例", "java"),
            (WEB_TEST + "PasswordPageTest.java", "PasswordPageTest.java", "7 个页面与会话用例", "java"),
        ],
    },
    "03": {
        "title": "M08-03 · 确认收货",
        "out": "M08-03-逐行验收.html",
        "date": "2026-11-18",
        "note": "内容：SHIPPED → COMPLETED 并写首次 completed_at、已完成幂等、其他状态 409、他人订单与不存在统一 404、"
                "订单详情的「确认收货」按钮与二次确认、POST /orders/{orderNo}/complete 的提示与跳转、"
                "5 个服务单测 + 5 个页面用例。"
                "验证：mvn -pl cartoon-order,cartoon-user-web -am test → order 36、user-web 148，全绿。"
                "模板 order-detail.html 的按钮改动按 docs/10 §5 用汇报里的分区表说明，不逐行渲染。",
        "files": [
            (ORDER + "domain/CompleteResult.java", "CompleteResult.java", "确认收货两种结局", "java"),
            (ORDER + "service/OrderCompletionService.java", "OrderCompletionService.java", "状态迁移、幂等与归属", "java"),
            (ORDER_TEST + "service/OrderCompletionServiceTest.java", "OrderCompletionServiceTest.java", "5 个服务用例", "java"),
            (WEB + "controller/OrderController.java", "OrderController.java", "新增确认收货路由", "java"),
            (WEB_TEST + "CompleteOrderTest.java", "CompleteOrderTest.java", "5 个页面与状态用例", "java"),
        ],
    },
}

# 只有"没有行尾注释、规则解释器也认不出"的行才需要登记。
NOTES: dict[tuple[str, str], str] = {
    # ---- Profile：值对象 ----
    (ACC + "dto/Profile.java", "public class Profile {"):
        "只携带三项已校验的值，不写任何业务规则。",
    (ACC + "dto/Profile.java", "public Profile(String displayName, String email, String phone) {"):
        "三项都已校验：构造后只读，调用方不能再改。",
    # ---- ProfileForm：校验入口 ----
    (ACC + "dto/ProfileForm.java", "public final class ProfileForm {"):
        "资料字段的唯一校验入口：资料页与注册页共用同一个上限。",
    (ACC + "dto/ProfileForm.java", "public static Profile parse(String rawDisplayName, String rawEmail, String rawPhone) {"):
        "资料页规则：显示名必填，邮箱与电话可空。",
    (ACC + "dto/ProfileForm.java", "public static String optionalDisplayName(String raw) {"):
        "注册页规则：显示名可空，空则由调用方回退为用户名。",
    (ACC + "dto/ProfileForm.java", "private static String require(String raw, String field, int max) {"):
        "必填字段：空串与非空都只有这一处判断。",
    (ACC + "dto/ProfileForm.java", "private static String optional(String raw, String field, int max) {"):
        "可空字段：空串归一为 null，非空才校验列宽。",
    # ---- StoreUserMapper：新增的定向更新 ----
    (ACC + "mapper/StoreUserMapper.java", "int updateProfile(@Param(\"id\") long id,"):
        "定向更新本人资料三列，返回影响行数。",
    (ACC + "mapper/StoreUserMapper.java", "@Param(\"displayName\") String displayName,"):
        "续行：显示名，必填。",
    (ACC + "mapper/StoreUserMapper.java", "@Param(\"email\") String email,"):
        "续行：邮箱，可为 null。",
    (ACC + "mapper/StoreUserMapper.java", "@Param(\"phone\") String phone);"):
        "续行：电话，可为 null。",
    # ---- AccountService：资料方法 ----
    (ACC + "service/AccountService.java", "public class AccountService {"):
        "账号服务的唯一入口：登录、注册、资料与批量取名都在这里。",
    (ACC + "service/AccountService.java", "public StoreUser profile(long userId) {"):
        "读本人资料：只按会话 id 取，不存在时 404。",
    (ACC + "service/AccountService.java",
     "public StoreUser updateProfile(long userId, String rawDisplayName, String rawEmail, String rawPhone) {"):
        "保存本人资料：定向 UPDATE 三列，不整行回写。",
    (ACC + "service/AccountService.java", "private StoreUser requireUser(long userId) {"):
        "按 id 取账号，不存在统一 404：资料页与保存共用。",
    # ---- ProfileController ----
    (WEB + "controller/ProfileController.java", "public class ProfileController {"):
        "资料页控制器：只做绑定、会话刷新与跳转。",
    (WEB + "controller/ProfileController.java", "public ProfileController(AccountService accountService) {"):
        "构造器注入：只依赖账号服务。",
    (WEB + "controller/ProfileController.java", "public String page(HttpSession session, Model model) {"):
        "打开资料页：回显库里的当前值。",
    (WEB + "controller/ProfileController.java", "public String save("):
        "保存资料：成功 PRG，失败回表单并保留输入。",
    (WEB + "controller/ProfileController.java", "private void fill(Model model, StoreUser user) {"):
        "表单初值：三项可改字段 + 只读用户名。",
    (WEB + "controller/ProfileController.java", "private long currentUserId(HttpSession session) {"):
        "取当前用户：身份只来自服务端会话。",
    # ---- 测试：类与辅助 ----
    (ACC_TEST + "service/AccountServiceTest.java", "class AccountServiceTest {"):
        "账号服务单测：登录、注册与资料校验。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerLoginOk() {"):
        "既有用例：启用 CUSTOMER 密码正确即可登录。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerWrongPassword() {"):
        "既有用例：密码错与账号不存在同码同文案。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerUnknownUser() {"):
        "既有用例：账号不存在也不泄露存在性。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerDisabledRejected() {"):
        "既有用例：禁用账号不能登录。",
    (ACC_TEST + "service/AccountServiceTest.java", "void adminCannotLogInAsCustomer() {"):
        "既有用例：管理员不能从用户端登录（AC-05）。",
    (ACC_TEST + "service/AccountServiceTest.java", "void adminLoginOk() {"):
        "既有用例：管理端只接受启用的 ADMIN。",
    (ACC_TEST + "service/AccountServiceTest.java", "void customerCannotLogInAsAdmin() {"):
        "既有用例：顾客不能从管理端登录（FR-A-01）。",
    (ACC_TEST + "service/AccountServiceTest.java", "void disabledAdminRejected() {"):
        "既有用例：禁用管理员被拒，文案与密码错一致。",
    (ACC_TEST + "service/AccountServiceTest.java", "void updateProfileTrimsAndKeepsOtherFields() {"):
        "去空白与空值归一：只写三项，其余字段不变。",
    (ACC_TEST + "service/AccountServiceTest.java", "void updateProfileBlankDisplayName() {"):
        "显示名空白：400 且不写库。",
    (ACC_TEST + "service/AccountServiceTest.java", "void updateProfileTooLongEmail() {"):
        "邮箱超长：400，提示带上限。",
    (ACC_TEST + "service/AccountServiceTest.java", "void updateProfileUnknownUser() {"):
        "账号不存在：404。",
    (ACC_TEST + "service/AccountServiceTest.java", "void registerTooLongDisplayName() {"):
        "注册显示名超长：400，不写库。",
    (ACC_TEST + "service/AccountServiceTest.java", "void registerBlankDisplayNameFallsBackToUsername() {"):
        "注册显示名留空：回退为用户名。",
    (ACC_TEST + "service/AccountServiceTest.java", "private StoreUser user(String username, String rawPassword, String role, String status) {"):
        "夹具：造一个密码真实可校验、角色与状态由用例指定的账号。",
    (WEB_TEST + "ProfilePageTest.java", "class ProfilePageTest extends AbstractIntegrationTest {"):
        "资料页集成测试：真实 MySQL + MockMvc。",
    (WEB_TEST + "ProfilePageTest.java", "void profileRequiresLogin() throws Exception {"):
        "未登录：302 到登录页并带回跳地址。",
    (WEB_TEST + "ProfilePageTest.java", "void profileRenders() throws Exception {"):
        "回显：用户名、显示名与邮箱都出现在页面。",
    (WEB_TEST + "ProfilePageTest.java", "void profileSaves() throws Exception {"):
        "保存：库与会话显示名同时更新。",
    (WEB_TEST + "ProfilePageTest.java", "void profileClearsOptionalFields() throws Exception {"):
        "可空字段：空串存 null。",
    (WEB_TEST + "ProfilePageTest.java", "void profileRejectsBlankDisplayName() throws Exception {"):
        "显示名空白：页面提示且库不变。",
    (WEB_TEST + "ProfilePageTest.java", "void profileRejectsTooLongEmail() throws Exception {"):
        "邮箱超长：页面提示上限。",
    (WEB_TEST + "ProfilePageTest.java", "void profileIgnoresProtectedFields() throws Exception {"):
        "受保护字段：提交 username/role/status 也不落库。",
    (WEB_TEST + "ProfilePageTest.java", "void profileKeepsOrderSnapshot() throws Exception {"):
        "历史订单快照：改资料后收货信息不变。",
    # ---- M08-02：PasswordForm / 改密用例 ----
    (ACC + "dto/PasswordForm.java", "} else if (Character.isDigit(codePoint)) {"):
        "数字：第二类字符。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordOk() {"):
        "成功路径：只写新散列，新密码可校验。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordWrongOld() {"):
        "旧密码错：400 且不写库。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordSameAsOld() {"):
        "新旧相同：400。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordTooShort() {"):
        "长度不足：400。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordOneClass() {"):
        "只有一类字符：400。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordWhitespace() {"):
        "含空白：400。",
    (ACC_TEST + "service/AccountServiceTest.java", "void changePasswordUnknownUser() {"):
        "账号不存在：404。",
    (WEB_TEST + "PasswordPageTest.java", "void passwordRequiresLogin() throws Exception {"):
        "未登录：302 到登录页并带回跳地址。",
    (WEB_TEST + "PasswordPageTest.java", "void passwordRenders() throws Exception {"):
        "改密页渲染三个密码框与规则说明。",
    (WEB_TEST + "PasswordPageTest.java", "void wrongOldPasswordRejected() throws Exception {"):
        "旧密码错：回表单且散列不变。",
    (WEB_TEST + "PasswordPageTest.java", "void weakPasswordRejected() throws Exception {"):
        "纯字母：提示需要两类字符。",
    (WEB_TEST + "PasswordPageTest.java", "void mismatchRejected() throws Exception {"):
        "两次不一致：回表单且散列不变。",
    (WEB_TEST + "PasswordPageTest.java", "void changeSucceeds() throws Exception {"):
        "成功：跳登录、当前会话失效、新散列可校验。",
    (WEB_TEST + "PasswordPageTest.java", "void otherSessionStaysLoggedIn() throws Exception {"):
        "其他浏览器的会话不受影响。",
    # ---- M08-03：确认收货 ----
    (ORDER_TEST + "service/OrderCompletionServiceTest.java", "void completeShipped() {"):
        "已发货：完成并写首次完成时间。",
    (ORDER_TEST + "service/OrderCompletionServiceTest.java", "void completeAgainIsIdempotent() {"):
        "已完成：幂等且不写库。",
    (ORDER_TEST + "service/OrderCompletionServiceTest.java", "void completePaidRejected() {"):
        "待发货：409 且不写库。",
    (ORDER_TEST + "service/OrderCompletionServiceTest.java", "void completeOtherUserRejected() {"):
        "他人订单：404，不泄露存在性。",
    (ORDER_TEST + "service/OrderCompletionServiceTest.java", "void completeMissing() {"):
        "订单不存在：404。",
    (WEB_TEST + "CompleteOrderTest.java", "void shippedOrderCanBeCompleted() throws Exception {"):
        "确认收货：状态完成、时间落库、发货时间不变。",
    (WEB_TEST + "CompleteOrderTest.java", "void repeatIsIdempotent() throws Exception {"):
        "重复提交：提示已处理，完成时间不变。",
    (WEB_TEST + "CompleteOrderTest.java", "void pendingCannotBeCompleted() throws Exception {"):
        "待支付：409 且状态不变。",
    (WEB_TEST + "CompleteOrderTest.java", "void otherUsersOrderCannotBeCompleted() throws Exception {"):
        "他人订单：404 且状态不变。",
    (WEB_TEST + "CompleteOrderTest.java", "void buttonOnlyOnShippedOrder() throws Exception {"):
        "「确认收货」只出现在已发货详情页。",
    (WEB_TEST + "CompleteOrderTest.java",
     'orderNo, userId, status, new BigDecimal("28.00"), "收货人", "13900000000", "地址");'):
        "续行：必填列的值；待支付没有支付与发货时间。",
    (WEB_TEST + "CompleteOrderTest.java", "private String statusOf(String orderNo) {"):
        "读状态：断言迁移与未迁移。",
    (WEB_TEST + "CompleteOrderTest.java", "private Timestamp completedAtOf(String orderNo) {"):
        "读完成时间：幂等断言用。",
    (WEB_TEST + "CompleteOrderTest.java", "private Timestamp shippedAtOf(String orderNo) {"):
        "读发货时间：确认收货不改它。",
}

# 规则解释器产出的兜底句：正常应为 0 行，用它做自检。
FALLBACK = "结构行：参与语法组织"


def render(task_id: str, meta: dict) -> None:
    """渲染一页：左列干净代码（去掉行尾注释），右列放该行的说明。"""
    sections, nav, total, fallback = [], [], 0, 0  # 分节、导航、总行数、兜底行数。
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
                text = FALLBACK  # 最后兜底，正常应为 0 行。
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
        print(f"警告：{task_id} 有 {fallback} 行走了兜底说明，请补 NOTES")  # 兜底应为 0。

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
    print(f"written: {out.name}  rows={total}  fallback={fallback}")  # 控制台回显。


def main() -> None:
    wanted = sys.argv[1:] or list(TASKS)  # 默认生成全部任务。
    for task_id in wanted:
        render(task_id, TASKS[task_id])  # 逐个任务渲染。


if __name__ == "__main__":
    main()
