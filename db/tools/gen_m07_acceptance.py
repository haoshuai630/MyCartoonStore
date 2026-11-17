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
ADM = "cartoon-admin-web/src/main/java/org/csu/mycartoonstore/admin/"
ADM_RES = "cartoon-admin-web/src/main/resources/templates/"
ADM_TEST = "cartoon-admin-web/src/test/java/org/csu/mycartoonstore/admin/"
CAT = "cartoon-catalog/src/main/java/org/csu/mycartoonstore/catalog/"
CAT_TEST = "cartoon-catalog/src/test/java/org/csu/mycartoonstore/catalog/"
CAT_XML = "cartoon-catalog/src/main/resources/mapper/"
ORD = "cartoon-order/src/main/java/org/csu/mycartoonstore/order/"
ORD_TEST = "cartoon-order/src/test/java/org/csu/mycartoonstore/order/"

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
    "02": {
        "title": "M07-02 · 管理端登录与控制台",
        "out": "M07-02-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：管理端身份拦截器（每请求重读 ADMIN + ENABLED，路径全站覆盖 + 白名单）、"
                "登录/退出（只接受启用 ADMIN、登录后换会话 ID、失败统一文案）、控制台（当前管理员 + 三个待接入入口）、"
                "管理端样式与错误页、管理端测试基座与 12 个用例。"
                "验证：mvn -pl cartoon-admin-web -am test → Tests run: 12, Failures: 0；"
                "手工冒烟：/admin/login 200、未登录 /admin/ → 302 登录页、admin 登录后 200 并显示控制台、"
                "顾客与停用账号登录 → 200 同一句失败文案且不建会话、/admin/css/admin.css 免登录 200。"
                "admin.css 属于样式，不逐行对照（按 docs/10 §5 的'配置与样式按配置项说明'），"
                "pom.xml 只新增 spring-boot-webmvc-test 一个测试依赖，也不单独成节。",
        "files": [
            (ADM + "interceptor/AdminAuthInterceptor.java", "AdminAuthInterceptor.java",
             "每请求重读 ADMIN + ENABLED", "java", None),
            (ADM + "config/AdminWebConfig.java", "AdminWebConfig.java",
             "全站拦截 + 登录/静态白名单", "java", None),
            (ADM + "controller/AdminAuthController.java", "AdminAuthController.java",
             "登录、退出与会话 ID 轮换", "java", None),
            (ADM + "controller/AdminHomeController.java", "AdminHomeController.java",
             "控制台：身份与入口", "java", None),
            (ADM + "controller/AdminExceptionHandler.java", "AdminExceptionHandler.java",
             "业务异常转管理端错误页", "java", None),
            (ADM_RES + "admin/fragments.html", "admin/fragments.html",
             "管理端公共片段：head / topbar / header / footer", "html", None),
            (ADM_RES + "admin/login.html", "admin/login.html",
             "登录页：只接受管理员 + 统一失败文案", "html", None),
            (ADM_RES + "admin/main.html", "admin/main.html",
             "控制台：身份卡 + 三个待接入入口", "html", None),
            (ADM_RES + "error/error.html", "error/error.html",
             "管理端错误页", "html", None),
            (ADM_TEST + "AdminLoginTest.java", "AdminLoginTest.java",
             "12 个登录/拦截/控制台用例", "java", None),
            (ADM_TEST + "support/AdminFixture.java", "AdminFixture.java",
             "管理端夹具：账号、会话、商品", "java", None),
            (ADM_TEST + "support/AdminIntegrationTest.java", "AdminIntegrationTest.java",
             "管理端集成测试基座", "java", None),
        ],
    },
    "03": {
        "title": "M07-03 · 分类维护服务",
        "out": "M07-03-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：两级分类树（一次查分类 + 一次分组查商品数）、新增一级/二级（编码格式与后缀、父级层级、"
                "同级同名、编码唯一）、编辑只改名称与排序、删除前三重校验（存在性、子分类、任意状态商品引用）、"
                "表单回显。只做服务层，页面在 M07-04。"
                "验证：mvn -pl cartoon-catalog -am test → Tests run: 36（新增 22）, Failures: 0。"
                "ProductMapper 只对照本次新增的 import 与 countByCategory 方法：其余行属 M02/M05 已验收代码。",
        "files": [
            (CAT + "dto/CategoryForm.java", "CategoryForm.java",
             "新增与编辑共用的表单结构", "java", None),
            (CAT + "dto/CategoryRow.java", "CategoryRow.java",
             "列表行：层级、父名、两个计数与删除提示", "java", None),
            (CAT + "dto/CategoryUsage.java", "CategoryUsage.java",
             "分类商品计数的查询结果", "java", None),
            (CAT + "mapper/ProductMapper.java", "ProductMapper.java",
             "新增按分类分组的商品计数", "java", [(8, 8), (49, 56)]),
            (CAT + "service/CategoryAdminService.java", "CategoryAdminService.java",
             "树、增删改与全部校验", "java", None),
            (CAT_TEST + "service/CategoryAdminServiceTest.java", "CategoryAdminServiceTest.java",
             "22 个用例：校验、编辑、删除与列表", "java", None),
        ],
    },
    "04": {
        "title": "M07-04 · 分类页面",
        "out": "M07-04-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：六条分类路由（列表、新增一级/二级、编辑、删除）、两个模板（列表树 + 共用表单）、"
                "控制台与导航接入、表单校验失败回显并给出 400/409、14 个 MockMvc 用例覆盖 AC-25/AC-26。"
                "验证：mvn -pl cartoon-admin-web -am test → Tests run: 26（新增 14）, Failures: 0；"
                "8081 实机：登录后 /admin/categories 200 且渲染种子分类，/admin/categories/new 200。"
                "admin.css 只追加分类页样式，不逐行对照；CategoryAdminService 只对照新增的 topCategory 方法。",
        "files": [
            (ADM + "controller/AdminCategoryController.java", "AdminCategoryController.java",
             "六条路由与失败回表单", "java", None),
            (ADM_RES + "admin/category-list.html", "admin/category-list.html",
             "列表树、计数与删除提示", "html", None),
            (ADM_RES + "admin/category-form.html", "admin/category-form.html",
             "新增与编辑共用的表单", "html", None),
            (ADM_RES + "admin/fragments.html", "admin/fragments.html",
             "导航加入分类维护", "html", [(32, 35)]),
            (ADM_RES + "admin/main.html", "admin/main.html",
             "控制台分类卡片接入", "html", [(30, 35)]),
            (CAT + "service/CategoryAdminService.java", "CategoryAdminService.java",
             "新增 topCategory 供父名展示", "java", [(185, 196)]),
            (ADM_TEST + "CategoryAdminPageTest.java", "CategoryAdminPageTest.java",
             "14 个页面用例", "java", None),
            (ADM_TEST + "support/AdminFixture.java", "AdminFixture.java",
             "夹具新增 createCategory", "java", [(52, 57)]),
        ],
    },
    "05": {
        "title": "M07-05 · 商品查询与维护服务",
        "out": "M07-05-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：后台商品列表（含下架，按一级/二级分类、名称、状态筛选，id 倒序分页）、新增（默认 DRAFT，"
                "SKU/名称/二级分类/价格/库存/封面/排序校验）、编辑（只改资料与价格，版本冲突 409）、"
                "分类下拉（表单只列二级、筛选列两级）与编辑回显。只做服务层，页面在 M07-06。"
                "验证：mvn -pl cartoon-catalog -am test → Tests run: 54（新增 18）, Failures: 0。"
                "ProductMapper 的 java 只对照新增 import 与方法；XML 只对照 adminWhere 与两个后台查询。",
        "files": [
            (CAT + "dto/AdminProductQuery.java", "AdminProductQuery.java",
             "后台列表查询条件", "java", None),
            (CAT + "dto/AdminProductRow.java", "AdminProductRow.java",
             "列表行：商品字段 + 分类名称", "java", None),
            (CAT + "dto/ProductForm.java", "ProductForm.java",
             "新增与编辑共用的表单", "java", None),
            (CAT + "dto/CategoryOption.java", "CategoryOption.java",
             "分类下拉项", "java", None),
            (CAT + "mapper/ProductMapper.java", "ProductMapper.java",
             "后台列表与条件更新", "java", [(8, 9), (60, 77)]),
            (CAT_XML + "ProductMapper.xml", "ProductMapper.xml",
             "后台列表 SQL 与分组计数后新增的两个查询", "xml", [(63, 93)]),
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java",
             "列表、新增、编辑与全部校验", "java", None),
            (CAT_TEST + "service/ProductAdminServiceTest.java", "ProductAdminServiceTest.java",
             "18 个用例：参数、字段、版本与下拉", "java", None),
        ],
    },
    "06": {
        "title": "M07-06 · 商品页面",
        "out": "M07-06-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：五条商品路由（列表与筛选、新增表单/提交、编辑表单/提交）、两个模板、控制台与导航接入、"
                "校验失败回表单并给出 400/409、11 个 MockMvc 用例覆盖 AC-25/AC-27。"
                "验证：mvn -pl cartoon-admin-web -am test → Tests run: 37（新增 11）, Failures: 0。"
                "admin.css 只追加筛选条/分页/状态徽章样式，不逐行对照。",
        "note": "内容：五条商品路由（列表与筛选、新增表单/提交、编辑表单/提交）、两个模板、控制台与导航接入、"
                "校验失败回表单并给出 400/409、11 个 MockMvc 用例覆盖 AC-25/AC-27。"
                "验证：mvn -pl cartoon-admin-web -am test → Tests run: 37（新增 11）, Failures: 0。"
                "按 docs/10 §5 的产物分类：Java 逐行对照；两个模板（product-list / product-form）与导航、"
                "控制台的少量改动用汇报里的「模板分区说明」手工表说明，不再逐行展开；admin.css 同样不逐行。",
        "files": [
            (ADM + "controller/AdminProductController.java", "AdminProductController.java",
             "五条路由与失败回表单", "java", None),
            (ADM_TEST + "ProductAdminPageTest.java", "ProductAdminPageTest.java",
             "11 个页面用例", "java", None),
            (ADM_TEST + "support/AdminFixture.java", "AdminFixture.java",
             "夹具新增 createProductIn", "java", [(59, 67)]),
        ],
    },
    "07": {
        "title": "M07-07 · 上下架与增量补货",
        "out": "M07-07-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：上下架（只允许 ON_SALE/DRAFT，版本一致才迁移）、增量补货（1–100000，原子累加 + 溢出保护，"
                "返回最新库存）、列表行内两个动作、7 个服务用例 + 4 个页面用例 + AC-28 并发用例。"
                "验证：mvn -pl cartoon-catalog -am test → 62；mvn -pl cartoon-admin-web -am test → 42；"
                "全量 clean verify → 10 模块 SUCCESS、305 个用例。"
                "模板行内动作与样式改动按 docs/10 §5 用汇报里的手工表说明，不逐行展开。",
        "files": [
            (CAT + "service/ProductAdminService.java", "ProductAdminService.java",
             "上下架、补货与两个校验", "java", [(63, 68), (184, 222), (401, 443)]),
            (CAT + "mapper/ProductMapper.java", "ProductMapper.java",
             "条件上下架与原子补货", "java", [(78, 96)]),
            (ADM + "controller/AdminProductController.java", "AdminProductController.java",
             "两个动作的路由", "java", [(101, 121)]),
            (CAT_TEST + "service/ProductAdminServiceTest.java", "ProductAdminServiceTest.java",
             "7 个服务用例", "java", [(271, 363)]),
            (ADM_TEST + "ProductAdminPageTest.java", "ProductAdminPageTest.java",
             "4 个页面用例", "java", [(226, 292)]),
            (ADM_TEST + "RestockConcurrencyTest.java", "RestockConcurrencyTest.java",
             "AC-28 并发用例", "java", None),
        ],
    },
    "08": {
        "title": "M07-08 · 全站订单与发货",
        "out": "M07-08-逐行验收.html",
        "date": "2026-11-17",
        "note": "内容：全站订单列表（状态/完整单号/分页）、订单详情（明细快照、收货、支付凭证、所属用户）、"
                "发货（行锁 + 只允许 PAID + 重复发货幂等）、控制台与导航接入、"
                "5 个查询用例 + 6 个发货用例 + 8 个页面用例。"
                "验证：mvn -pl cartoon-order -am test → 31；mvn -pl cartoon-admin-web -am test → 50；"
                "全量 clean verify → 10 模块 SUCCESS、324 个用例。"
                "两个订单模板与样式按 docs/10 §5 用汇报里的手工表说明；"
                "SalesOrder 与 AccountService 只对照本次新增的状态标签与批量取名方法。",
        "files": [
            (ORD + "service/OrderAdminService.java", "OrderAdminService.java",
             "全站列表、详情与参数归一", "java", None),
            (ORD + "service/ShipService.java", "ShipService.java",
             "发货：行锁、状态迁移与幂等", "java", None),
            (ORD + "domain/ShipResult.java", "ShipResult.java",
             "发货两种结局", "java", None),
            (ORD + "dto/AdminOrderPage.java", "AdminOrderPage.java",
             "后台订单一页", "java", None),
            (ORD + "entity/SalesOrder.java", "SalesOrder.java",
             "新增状态标签", "java", [(72, 76)]),
            (ACC + "service/AccountService.java", "AccountService.java",
             "新增批量取显示名", "java", [(126, 137)]),
            (ADM + "controller/AdminOrderController.java", "AdminOrderController.java",
             "列表、详情与发货路由", "java", None),
            (ORD_TEST + "service/ShipServiceTest.java", "ShipServiceTest.java",
             "6 个发货用例", "java", None),
            (ORD_TEST + "service/OrderAdminServiceTest.java", "OrderAdminServiceTest.java",
             "5 个查询用例", "java", None),
            (ADM_TEST + "OrderAdminPageTest.java", "OrderAdminPageTest.java",
             "8 个页面用例", "java", None),
        ],
    },
}

# 只有"没有行尾注释、规则解释器也认不出"的行才需要登记。
# LINE_NOTES 按 (文件, 行号) 登记：模板行没有注释可抓，用行号最稳。
LINE_NOTES: dict[tuple[str, int], str] = {
    (ADM_RES + "admin/fragments.html", 3): "公共 head：标题后缀与后台样式表。",
    (ADM_RES + "admin/fragments.html", 4): "统一 UTF-8，中文不乱码。",
    (ADM_RES + "admin/fragments.html", 5): "1366 宽下验收，缩放交给浏览器。",
    (ADM_RES + "admin/fragments.html", 6): "标题统一加「 | 漫屿管理端」后缀。",
    (ADM_RES + "admin/fragments.html", 7): "后台只引 admin.css，不引用户端样式。",
    (ADM_RES + "admin/fragments.html", 9): "公共片段集中在一个文件，各页按需 replace。",
    (ADM_RES + "admin/fragments.html", 12): "顶部条：声明这是管理端与演示数据。",
    (ADM_RES + "admin/fragments.html", 13): "内容宽度与页头对齐。",
    (ADM_RES + "admin/fragments.html", 14): "明确写「教学演示站点，商品与订单均为虚构数据」。",
    (ADM_RES + "admin/fragments.html", 16): "没有登录态时不渲染退出按钮。",
    (ADM_RES + "admin/fragments.html", 17): "显示当前管理员，只读。",
    (ADM_RES + "admin/fragments.html", 18): "退出必须走 POST，避免被链接预取。",
    (ADM_RES + "admin/fragments.html", 19): "样式做成链接，行为是提交表单。",
    (ADM_RES + "admin/fragments.html", 26): "页头：品牌 + 后台导航。",
    (ADM_RES + "admin/fragments.html", 27): "页头也用同一内容宽度。",
    (ADM_RES + "admin/fragments.html", 28): "点品牌回控制台。",
    (ADM_RES + "admin/fragments.html", 29): "品牌方框字，与用户端一致。",
    (ADM_RES + "admin/fragments.html", 30): "中文名 + 英文小字。",
    (ADM_RES + "admin/fragments.html", 32): "后台导航；入口随后续任务增加。",
    (ADM_RES + "admin/fragments.html", 33): "只有一个入口时也高亮当前页。",
    (ADM_RES + "admin/fragments.html", 35): "页头右侧显示当前管理员。",
    (ADM_RES + "admin/fragments.html", 36): "显示名来自会话，不额外查库。",
    (ADM_RES + "admin/fragments.html", 41): "页脚：品牌与演示声明。",
    (ADM_RES + "admin/fragments.html", 42): "页脚同一宽度。",
    (ADM_RES + "admin/fragments.html", 43): "页脚品牌名。",
    (ADM_RES + "admin/fragments.html", 44): "写明不涉及真实资金与物流。",
    (ADM_RES + "admin/login.html", 3): "复用公共 head，标题「管理员登录」。",
    (ADM_RES + "admin/login.html", 4): "登录页单独的背景与居中布局。",
    (ADM_RES + "admin/login.html", 7): "整页居中，页脚贴底。",
    (ADM_RES + "admin/login.html", 8): "登录卡片：硬阴影 + 粗描边。",
    (ADM_RES + "admin/login.html", 9): "英文小标题，与用户端同一套视觉。",
    (ADM_RES + "admin/login.html", 10): "页面标题写「管理员登录」，与用户端区分。",
    (ADM_RES + "admin/login.html", 11): "一句话说明这是后台、顾客请去用户端。",
    (ADM_RES + "admin/login.html", 14): "失败提示：文案由 AccountService 统一给出。",
    (ADM_RES + "admin/login.html", 16): "POST 到 /login；页面不传角色，角色由服务端判断。",
    (ADM_RES + "admin/login.html", 17): "用户名标签。",
    (ADM_RES + "admin/login.html", 18): "标签文案用「管理员账号」。",
    (ADM_RES + "admin/login.html", 20): "失败时回显用户名；浏览器可记住。",
    (ADM_RES + "admin/login.html", 22): "密码标签。",
    (ADM_RES + "admin/login.html", 23): "密码不回显。",
    (ADM_RES + "admin/login.html", 24): "密码不回显、不记日志。",
    (ADM_RES + "admin/login.html", 26): "提交登录。",
    (ADM_RES + "admin/login.html", 29): "教学账号写在页面上，方便验收。",
    (ADM_RES + "admin/login.html", 30): "写明两端身份互斥。",
    (ADM_RES + "admin/login.html", 34): "页脚复用公共片段。",
    (ADM_RES + "admin/main.html", 3): "控制台标题。",
    (ADM_RES + "admin/main.html", 4): "控制台用公共页头。",
    (ADM_RES + "admin/main.html", 6): "顶部条：当前管理员与退出。",
    (ADM_RES + "admin/main.html", 7): "页头：品牌与导航。",
    (ADM_RES + "admin/main.html", 9): "主内容区。",
    (ADM_RES + "admin/main.html", 10): "控制台整块。",
    (ADM_RES + "admin/main.html", 11): "内容宽度。",
    (ADM_RES + "admin/main.html", 13): "标题区与身份卡左右排布。",
    (ADM_RES + "admin/main.html", 14): "左侧：标题与说明。",
    (ADM_RES + "admin/main.html", 15): "英文小标题。",
    (ADM_RES + "admin/main.html", 16): "页面标题。",
    (ADM_RES + "admin/main.html", 17): "说明不展示统计数字，数据以列表为准。",
    (ADM_RES + "admin/main.html", 19): "身份卡：只显示当前管理员。",
    (ADM_RES + "admin/main.html", 20): "字段名。",
    (ADM_RES + "admin/main.html", 21): "显示名来自会话。",
    (ADM_RES + "admin/main.html", 22): "身份卡里也给一个退出入口。",
    (ADM_RES + "admin/main.html", 23): "退出后回登录页。",
    (ADM_RES + "admin/main.html", 29): "三个业务入口。",
    (ADM_RES + "admin/main.html", 30): "分类入口：未接入，显示为待接入。",
    (ADM_RES + "admin/main.html", 31): "英文小标题。",
    (ADM_RES + "admin/main.html", 32): "入口名。",
    (ADM_RES + "admin/main.html", 33): "一句话说明这个入口能做什么。",
    (ADM_RES + "admin/main.html", 34): "标明接入任务，避免点了打不开。",
    (ADM_RES + "admin/main.html", 36): "商品入口：未接入。",
    (ADM_RES + "admin/main.html", 37): "英文小标题。",
    (ADM_RES + "admin/main.html", 38): "入口名。",
    (ADM_RES + "admin/main.html", 39): "说明包含版本冲突检测。",
    (ADM_RES + "admin/main.html", 40): "标明接入任务。",
    (ADM_RES + "admin/main.html", 42): "订单入口：未接入。",
    (ADM_RES + "admin/main.html", 43): "英文小标题。",
    (ADM_RES + "admin/main.html", 44): "入口名。",
    (ADM_RES + "admin/main.html", 45): "说明只对已支付订单发货。",
    (ADM_RES + "admin/main.html", 46): "标明接入任务。",
    (ADM_RES + "admin/main.html", 50): "解释为什么是待接入状态。",
    (ADM_RES + "admin/main.html", 55): "页脚复用公共片段。",
    (ADM_RES + "error/error.html", 3): "错误页标题。",
    (ADM_RES + "error/error.html", 4): "错误页也用公共页头。",
    (ADM_RES + "error/error.html", 6): "顶部条。",
    (ADM_RES + "error/error.html", 7): "页头。",
    (ADM_RES + "error/error.html", 9): "主内容区。",
    (ADM_RES + "error/error.html", 11): "错误盒子：状态码 + 文案 + 返回入口。",
    (ADM_RES + "error/error.html", 12): "英文小标题。",
    (ADM_RES + "error/error.html", 13): "显示 HTTP 状态码；没有就写「操作未完成」。",
    (ADM_RES + "error/error.html", 14): "业务异常的可读文案。",
    (ADM_RES + "error/error.html", 15): "错误页永远给一个出口。",
    (ADM_RES + "error/error.html", 19): "页脚复用公共片段。",
    (ADM_RES + "admin/fragments.html", 1): "HTML5 文档声明。",
    (ADM_RES + "admin/fragments.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    (ADM_RES + "admin/login.html", 1): "HTML5 文档声明。",
    (ADM_RES + "admin/login.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    (ADM_RES + "admin/main.html", 1): "HTML5 文档声明。",
    (ADM_RES + "admin/main.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    (ADM_RES + "error/error.html", 1): "HTML5 文档声明。",
    (ADM_RES + "error/error.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    # ---- M07-03：分类服务与用例 ----
    (CAT + "dto/CategoryForm.java", 11): "分类表单：新增与编辑共用；编辑时只有名称与排序生效。",
    (CAT + "dto/CategoryRow.java", 9): "列表行：层级、父名、两个计数与删除提示都在这一个对象里。",
    (CAT + "dto/CategoryRow.java", 39): "构造一行：父名、层级、两个计数都由 Service 一次查询后算好。",
    (CAT + "dto/CategoryRow.java", 40): "续行：其余参数同左列。",
    (CAT + "dto/CategoryUsage.java", 10): "分类商品计数：一次分组查询的结果，列表与删除校验共用。",
    (CAT + "service/CategoryAdminService.java", 28): "分类维护服务：两级树、增删改与引用校验；不改编码与父级。",
    (CAT + "service/CategoryAdminService.java", 63): "一次取全部分类：树与父名都从这份结果算。",
    (CAT + "service/CategoryAdminService.java", 70): "建 id → 名称索引，供二级行显示父名。",
    (CAT + "service/CategoryAdminService.java", 75): "只遍历一级：二级跟随父级一起输出。",
    (CAT + "service/CategoryAdminService.java", 76): "二级跳过，避免重复成行。",
    (CAT + "service/CategoryAdminService.java", 81): "二级紧跟父级，模板按 level 缩进。",
    (CAT + "service/CategoryAdminService.java", 109): "并发下两个请求可能同时通过查重，唯一键兜底。",
    (CAT + "service/CategoryAdminService.java", 144): "先看子分类：有子分类就不能删。",
    (CAT + "service/CategoryAdminService.java", 146): "一级分类通常卡在这里。",
    (CAT + "service/CategoryAdminService.java", 149): "再看商品引用：下架商品也算。",
    (CAT + "service/CategoryAdminService.java", 151): "有引用就拒绝，避免商品挂在空分类上。",
    (CAT + "service/CategoryAdminService.java", 179): "一级分类：新增二级时给父级展示用。",
    (CAT + "service/CategoryAdminService.java", 190): "不存在即 404，编辑与删除共用这一入口。",
    (CAT + "service/CategoryAdminService.java", 199): "编码必填。",
    (CAT + "service/CategoryAdminService.java", 202): "格式不符就 400，不做静默大写化。",
    (CAT + "service/CategoryAdminService.java", 211): "名称必填。",
    (CAT + "service/CategoryAdminService.java", 214): "超长与列宽一致地拒绝。",
    (CAT + "service/CategoryAdminService.java", 225): "负数没有业务含义。",
    (CAT + "service/CategoryAdminService.java", 233): "一级分类不受后缀限制。",
    (CAT + "service/CategoryAdminService.java", 237): "父级必须存在且是一级：同时挡住三级与自引用。",
    (CAT + "service/CategoryAdminService.java", 240): "后缀是商品类型的唯一来源。",
    (CAT + "service/CategoryAdminService.java", 247): "编码查重：库排序规则不区分大小写。",
    (CAT + "service/CategoryAdminService.java", 255): "一级之间比较 parent_id IS NULL。",
    (CAT + "service/CategoryAdminService.java", 261): "编辑时把自己排除。",
    (CAT + "service/CategoryAdminService.java", 271): "把分组结果转成 id → 数量。",
    (CAT + "service/CategoryAdminService.java", 280): "只把二级放进分组。",
    (CAT + "service/CategoryAdminService.java", 289): "组装一行：缺省值在这里补齐。",
    (CAT + "service/CategoryAdminService.java", 290): "续行：子分类数与商品数索引。",
    (CAT + "service/CategoryAdminService.java", 291): "父名与层级由调用方给出。",
    (CAT + "service/CategoryAdminService.java", 292): "续行：排序为空按 0。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 33): "22 个用例：编码/后缀/层级/同名/编辑/删除/树形。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 48): "用例：新增一级去空白、排序缺省 0。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 64): "用例：小写编码被拒，不静默转换。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 74): "用例：下划线不是合法编码字符。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 78): "断言 400。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 83): "用例：空编码被拒。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 91): "用例：空名称与超长名称被拒。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 95): "断言 400（空名称）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 104): "用例：负排序被拒。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 108): "断言 400（负排序）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 113): "用例：编码重复 → 409。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 123): "用例：同级同名 → 409。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 128): "断言可读文案。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 133): "用例：新增二级，父级存在且是一级。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 146): "用例：二级后缀不合法 → 400。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 156): "用例：父级不存在 → 400。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 161): "断言层级错误文案。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 166): "用例：父级本身是二级 → 400。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 172): "断言 400（三级）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 179): "用例：编辑只写名称与排序。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 195): "用例：编辑后同级重名 → 409。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 196): "准备：分类已存在。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 201): "断言重名文案。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 206): "用例：编辑不存在的分类 → 404。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 212): "断言 404（编辑）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 219): "用例：有子分类不能删（AC-26）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 220): "准备：一级分类下有品类。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 231): "用例：有商品引用不能删（AC-26）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 232): "准备：二级分类下 3 件商品。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 244): "用例：无引用可以删。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 245): "准备：无子分类、无商品。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 256): "用例：删除不存在的分类 → 404。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 262): "断言 404（删除）。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 269): "用例：一级在前、二级紧随，计数正确。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 290): "用例：删除按钮的禁用原因。",
    (CAT_TEST + "service/CategoryAdminServiceTest.java", 301): "用例：编辑表单回显当前值。",
    # ---- controller/AdminCategoryController.java ----
    (ADM + "controller/AdminCategoryController.java", 24): "分类维护控制器：六条路由；层级、编码与引用规则都在服务层。",
    (ADM + "controller/AdminCategoryController.java", 33): "列表：服务已排好序，模板直接渲染。",
    (ADM + "controller/AdminCategoryController.java", 41): "新增表单：parentId 决定是一级还是二级。",
    (ADM + "controller/AdminCategoryController.java", 55): "新增提交：失败回表单并带 400/409。",
    (ADM + "controller/AdminCategoryController.java", 56): "参数：表单、模型、一次性提示、响应状态。",
    (ADM + "controller/AdminCategoryController.java", 57): "续行：响应对象用来设置失败状态码。",
    (ADM + "controller/AdminCategoryController.java", 58): "编码格式、层级、同名等业务失败都在服务层抛出。",
    (ADM + "controller/AdminCategoryController.java", 68): "编辑表单：编码与父级只读展示。",
    (ADM + "controller/AdminCategoryController.java", 80): "编辑提交：只改名称与排序。",
    (ADM + "controller/AdminCategoryController.java", 81): "参数同新增，另外带路径里的分类 id。",
    (ADM + "controller/AdminCategoryController.java", 82): "续行：响应对象用来设置失败状态码。",
    (ADM + "controller/AdminCategoryController.java", 83): "同名与参数非法时回表单。",
    (ADM + "controller/AdminCategoryController.java", 93): "删除：冲突交给全局异常处理器转 409 错误页。",
    (ADM + "controller/AdminCategoryController.java", 106): "失败回表单：保留输入并设 400/409。",
    (ADM + "controller/AdminCategoryController.java", 107): "续行：响应与模型，用于状态码与红字。",
    # ---- service/CategoryAdminService.java ----
    (CAT + "service/CategoryAdminService.java", 191): "一级分类没有父级。",
    # ---- CategoryAdminPageTest.java ----
    (ADM_TEST + "CategoryAdminPageTest.java", 26): "分类页面的 14 个用例：AC-25/AC-26。",
    (ADM_TEST + "CategoryAdminPageTest.java", 36): "用例：列表显示两级、父名与计数。",
    (ADM_TEST + "CategoryAdminPageTest.java", 52): "用例：默认新增一级。",
    (ADM_TEST + "CategoryAdminPageTest.java", 61): "用例：带 parentId 时显示所属作品。",
    (ADM_TEST + "CategoryAdminPageTest.java", 72): "用例：父级不是一级 → 404。",
    (ADM_TEST + "CategoryAdminPageTest.java", 82): "用例：新增一级入库（AC-25）。",
    (ADM_TEST + "CategoryAdminPageTest.java", 98): "用例：新增二级挂在作品下（AC-25）。",
    (ADM_TEST + "CategoryAdminPageTest.java", 115): "用例：后缀不合法 → 400 且不写库。",
    (ADM_TEST + "CategoryAdminPageTest.java", 131): "用例：编码重复 → 409。",
    (ADM_TEST + "CategoryAdminPageTest.java", 144): "用例：编辑页带出当前值、编码只读。",
    (ADM_TEST + "CategoryAdminPageTest.java", 156): "用例：只改名称与排序，编码与父级不变。",
    (ADM_TEST + "CategoryAdminPageTest.java", 181): "用例：同级重名 → 409。",
    (ADM_TEST + "CategoryAdminPageTest.java", 197): "用例：有子分类不能删（AC-26）。",
    (ADM_TEST + "CategoryAdminPageTest.java", 209): "用例：有商品引用不能删（AC-26）。",
    (ADM_TEST + "CategoryAdminPageTest.java", 223): "用例：无引用可删。",
    # ---- admin/category-list.html ----
    (ADM_RES + "admin/category-list.html", 1): "HTML5 文档声明。",
    (ADM_RES + "admin/category-list.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    (ADM_RES + "admin/category-list.html", 3): "复用公共 head，标题「分类维护」。",
    (ADM_RES + "admin/category-list.html", 4): "页面主体。",
    (ADM_RES + "admin/category-list.html", 6): "顶部条：当前管理员与退出。",
    (ADM_RES + "admin/category-list.html", 7): "页头：品牌与导航。",
    (ADM_RES + "admin/category-list.html", 9): "主内容区。",
    (ADM_RES + "admin/category-list.html", 10): "分类维护整块。",
    (ADM_RES + "admin/category-list.html", 11): "内容宽度。",
    (ADM_RES + "admin/category-list.html", 13): "标题区与「新增作品」按钮左右排布。",
    (ADM_RES + "admin/category-list.html", 14): "左侧：标题与说明。",
    (ADM_RES + "admin/category-list.html", 15): "英文小标题。",
    (ADM_RES + "admin/category-list.html", 16): "页面标题。",
    (ADM_RES + "admin/category-list.html", 17): "说明两级关系与不可变字段。",
    (ADM_RES + "admin/category-list.html", 18): "续行：有子分类或商品引用不能删。",
    (ADM_RES + "admin/category-list.html", 20): "右侧操作区。",
    (ADM_RES + "admin/category-list.html", 21): "新增一级入口。",
    (ADM_RES + "admin/category-list.html", 26): "新增/编辑/删除成功后的一次性提示。",
    (ADM_RES + "admin/category-list.html", 29): "有分类才渲染表格。",
    (ADM_RES + "admin/category-list.html", 30): "数据表格。",
    (ADM_RES + "admin/category-list.html", 31): "表头。",
    (ADM_RES + "admin/category-list.html", 32): "表头行。",
    (ADM_RES + "admin/category-list.html", 33): "列：层级。",
    (ADM_RES + "admin/category-list.html", 34): "列：名称。",
    (ADM_RES + "admin/category-list.html", 35): "列：编码。",
    (ADM_RES + "admin/category-list.html", 36): "列：父分类。",
    (ADM_RES + "admin/category-list.html", 37): "列：排序。",
    (ADM_RES + "admin/category-list.html", 38): "列：子分类数。",
    (ADM_RES + "admin/category-list.html", 39): "列：商品数。",
    (ADM_RES + "admin/category-list.html", 40): "列：操作。",
    (ADM_RES + "admin/category-list.html", 43): "表体。",
    (ADM_RES + "admin/category-list.html", 44): "每行一个分类；二级行加 child-row 缩进。",
    (ADM_RES + "admin/category-list.html", 46): "层级单元格。",
    (ADM_RES + "admin/category-list.html", 47): "层级标签的样式按层级切换。",
    (ADM_RES + "admin/category-list.html", 48): "续行：一级显示「作品」、二级显示「品类」。",
    (ADM_RES + "admin/category-list.html", 51): "名称。",
    (ADM_RES + "admin/category-list.html", 52): "编码用等宽字体，便于核对。",
    (ADM_RES + "admin/category-list.html", 54): "父分类：一级没有父级。",
    (ADM_RES + "admin/category-list.html", 55): "排序值。",
    (ADM_RES + "admin/category-list.html", 56): "子分类数。",
    (ADM_RES + "admin/category-list.html", 57): "商品数。",
    (ADM_RES + "admin/category-list.html", 58): "操作区。",
    (ADM_RES + "admin/category-list.html", 59): "编辑入口。",
    (ADM_RES + "admin/category-list.html", 61): "一级行才给「新增品类」。",
    (ADM_RES + "admin/category-list.html", 62): "续行：带上父级 id 进入新增表单。",
    (ADM_RES + "admin/category-list.html", 64): "删除表单：POST，避免被链接预取。",
    (ADM_RES + "admin/category-list.html", 65): "续行：删除前二次确认。",
    (ADM_RES + "admin/category-list.html", 66): "删除按钮：有引用时禁用。",
    (ADM_RES + "admin/category-list.html", 67): "续行：禁用条件来自行对象的 deletable。",
    (ADM_RES + "admin/category-list.html", 68): "续行：悬停显示原因。",
    (ADM_RES + "admin/category-list.html", 70): "禁用原因文字。",
    (ADM_RES + "admin/category-list.html", 71): "续行：原因由服务端算好，模板不复述规则。",
    (ADM_RES + "admin/category-list.html", 79): "没有分类时的空状态。",
    (ADM_RES + "admin/category-list.html", 80): "空状态标题。",
    (ADM_RES + "admin/category-list.html", 81): "空状态说明：先建作品再建品类。",
    (ADM_RES + "admin/category-list.html", 82): "空状态入口。",
    (ADM_RES + "admin/category-list.html", 88): "页脚。",
    # ---- admin/category-form.html ----
    (ADM_RES + "admin/category-form.html", 1): "HTML5 文档声明。",
    (ADM_RES + "admin/category-form.html", 2): "lang=zh-CN + Thymeleaf 命名空间。",
    (ADM_RES + "admin/category-form.html", 3): "标题按模式变化：编辑 / 新增分类。",
    (ADM_RES + "admin/category-form.html", 4): "页面主体。",
    (ADM_RES + "admin/category-form.html", 6): "顶部条。",
    (ADM_RES + "admin/category-form.html", 7): "页头。",
    (ADM_RES + "admin/category-form.html", 9): "主内容区。",
    (ADM_RES + "admin/category-form.html", 10): "表单整块。",
    (ADM_RES + "admin/category-form.html", 11): "内容宽度。",
    (ADM_RES + "admin/category-form.html", 13): "面包屑：回列表。",
    (ADM_RES + "admin/category-form.html", 14): "返回分类维护。",
    (ADM_RES + "admin/category-form.html", 15): "分隔符。",
    (ADM_RES + "admin/category-form.html", 16): "当前模式：编辑 / 新增品类 / 新增作品。",
    (ADM_RES + "admin/category-form.html", 20): "校验失败的红字：文案来自服务层。",
    (ADM_RES + "admin/category-form.html", 23): "新建与编辑共用一个表单。",
    (ADM_RES + "admin/category-form.html", 24): "动作按模式切换：编辑带 id，新增不带。",
    (ADM_RES + "admin/category-form.html", 25): "标题按模式切换。",
    (ADM_RES + "admin/category-form.html", 27): "两列表单栅格。",
    (ADM_RES + "admin/category-form.html", 29): "只有二级分类才显示所属作品。",
    (ADM_RES + "admin/category-form.html", 30): "字段名。",
    (ADM_RES + "admin/category-form.html", 31): "父级只读展示。",
    (ADM_RES + "admin/category-form.html", 32): "说明父级不可修改。",
    (ADM_RES + "admin/category-form.html", 36): "编码字段。",
    (ADM_RES + "admin/category-form.html", 37): "字段名。",
    (ADM_RES + "admin/category-form.html", 38): "编码输入：编辑时只读。",
    (ADM_RES + "admin/category-form.html", 39): "续行：长度上限与列宽一致。",
    (ADM_RES + "admin/category-form.html", 40): "续行：占位提示按一级/二级给示例。",
    (ADM_RES + "admin/category-form.html", 41): "新增时的格式说明。",
    (ADM_RES + "admin/category-form.html", 42): "编辑时的只读说明。",
    (ADM_RES + "admin/category-form.html", 46): "名称字段。",
    (ADM_RES + "admin/category-form.html", 47): "字段名。",
    (ADM_RES + "admin/category-form.html", 48): "名称输入：必填、最长 50。",
    (ADM_RES + "admin/category-form.html", 49): "名称规则说明。",
    (ADM_RES + "admin/category-form.html", 53): "排序字段。",
    (ADM_RES + "admin/category-form.html", 54): "字段名。",
    (ADM_RES + "admin/category-form.html", 55): "排序输入：非负整数。",
    (ADM_RES + "admin/category-form.html", 56): "排序说明。",
    (ADM_RES + "admin/category-form.html", 61): "新增二级时把父级 id 一起提交。",
    (ADM_RES + "admin/category-form.html", 63): "操作区。",
    (ADM_RES + "admin/category-form.html", 64): "提交按钮：文案按模式。",
    (ADM_RES + "admin/category-form.html", 65): "返回列表。",
    (ADM_RES + "admin/category-form.html", 72): "页脚。",
    # ---- admin/fragments.html ----
    (ADM_RES + "admin/fragments.html", 34): "导航加入分类维护，并在当前页高亮。",
    # ---- admin/main.html ----
    (ADM_RES + "admin/main.html", 30): "分类卡片接成链接。",
    (ADM_RES + "admin/main.html", 31): "英文小标题。",
    (ADM_RES + "admin/main.html", 32): "入口名。",
    (ADM_RES + "admin/main.html", 33): "一句话说明。",
    (ADM_RES + "admin/main.html", 34): "已接入，显示「进入」。",
    (ADM_RES + "admin/main.html", 35): "卡片闭合：链接结束。",
    # ---- dto/AdminProductQuery.java ----
    (CAT + "dto/AdminProductQuery.java", 13): "后台列表条件：含下架、可筛状态，排序固定 id 倒序。",
    # ---- dto/AdminProductRow.java ----
    (CAT + "dto/AdminProductRow.java", 12): "列表行：JOIN 出分类名，避免页面逐行查库。",
    # ---- dto/ProductForm.java ----
    (CAT + "dto/ProductForm.java", 11): "商品表单：price/stock 用字符串接，便于给具体错误文案。",
    # ---- dto/CategoryOption.java ----
    (CAT + "dto/CategoryOption.java", 8): "分类下拉项：已拼好「作品 / 品类」。",
    # ---- mapper/ProductMapper.java ----
    (CAT + "mapper/ProductMapper.java", 72): "只写资料与价格列：SKU、库存、状态不在这里改。",
    (CAT + "mapper/ProductMapper.java", 73): "续行：简介、描述、封面。",
    (CAT + "mapper/ProductMapper.java", 74): "续行：排序 + 版本自增。",
    (CAT + "mapper/ProductMapper.java", 75): "续行：版本作条件，影响 0 行即冲突。",
    (CAT + "mapper/ProductMapper.java", 77): "返回影响行数：0 表示版本已变。",
    # ---- ProductMapper.xml ----
    (CAT_XML + "ProductMapper.xml", 64): "后台列表公共条件：与 count 共用，避免两处 SQL 走样。",
    (CAT_XML + "ProductMapper.xml", 65): "商品表起头。",
    (CAT_XML + "ProductMapper.xml", 66): "JOIN 分类：既能按一级/二级筛选，也能带出分类名。",
    (CAT_XML + "ProductMapper.xml", 67): "动态条件容器：没有条件时不生成 WHERE。",
    (CAT_XML + "ProductMapper.xml", 68): "分类：给了就筛。",
    (CAT_XML + "ProductMapper.xml", 69): "一级取子树、二级精确。",
    (CAT_XML + "ProductMapper.xml", 71): "状态：ALL 不筛。",
    (CAT_XML + "ProductMapper.xml", 72): "DRAFT / ON_SALE。",
    (CAT_XML + "ProductMapper.xml", 74): "关键字：服务层已转义通配符。",
    (CAT_XML + "ProductMapper.xml", 75): "只搜名称，与前台列表一致。",
    (CAT_XML + "ProductMapper.xml", 81): "后台列表查询：返回行对象。",
    (CAT_XML + "ProductMapper.xml", 82): "显式列清单 + 分类名。",
    (CAT_XML + "ProductMapper.xml", 83): "续行：价格、库存、状态。",
    (CAT_XML + "ProductMapper.xml", 84): "续行：排序与版本。",
    (CAT_XML + "ProductMapper.xml", 85): "复用公共条件。",
    (CAT_XML + "ProductMapper.xml", 86): "最近录入在前，翻页稳定。",
    (CAT_XML + "ProductMapper.xml", 87): "分页。",
    (CAT_XML + "ProductMapper.xml", 90): "总数查询：与列表同条件。",
    (CAT_XML + "ProductMapper.xml", 91): "计数。",
    (CAT_XML + "ProductMapper.xml", 92): "复用公共条件。",
    # ---- service/ProductAdminService.java ----
    (CAT + "service/ProductAdminService.java", 31): "商品后台服务：列表/新增/编辑；上下架与补货留给 M07-07。",
    (CAT + "service/ProductAdminService.java", 77): "列表入口：参数都是原始字符串，在这里归一。",
    (CAT + "service/ProductAdminService.java", 78): "续行：页码与页大小。",
    (CAT + "service/ProductAdminService.java", 99): "不存在即 404。",
    (CAT + "service/ProductAdminService.java", 136): "并发下唯一键兜底。",
    (CAT + "service/ProductAdminService.java", 209): "只处理一级，二级在内层跟随。",
    (CAT + "service/ProductAdminService.java", 226): "筛选项也按一级分组。",
    (CAT + "service/ProductAdminService.java", 227): "一级已被取过，跳过二级行。",
    (CAT + "service/ProductAdminService.java", 256): "SKU 必填。",
    (CAT + "service/ProductAdminService.java", 268): "名称必填。",
    (CAT + "service/ProductAdminService.java", 271): "名称超长与列宽一致。",
    (CAT + "service/ProductAdminService.java", 283): "简介超长。",
    (CAT + "service/ProductAdminService.java", 292): "封面必填。",
    (CAT + "service/ProductAdminService.java", 295): "封面超长。",
    (CAT + "service/ProductAdminService.java", 306): "排序不能为负。",
    (CAT + "service/ProductAdminService.java", 315): "价格必填。",
    (CAT + "service/ProductAdminService.java", 318): "先声明，解析失败在下面处理。",
    (CAT + "service/ProductAdminService.java", 319): "解析失败按 400。",
    (CAT + "service/ProductAdminService.java", 324): "价格必须大于 0。",
    (CAT + "service/ProductAdminService.java", 327): "超过两位小数直接拒绝，不做静默舍入。",
    (CAT + "service/ProductAdminService.java", 330): "超出 DECIMAL(12,2)。",
    (CAT + "service/ProductAdminService.java", 342): "整数字面量解析。",
    (CAT + "service/ProductAdminService.java", 350): "库存上限。",
    (CAT + "service/ProductAdminService.java", 358): "缺版本或负版本都不允许保存。",
    (CAT + "service/ProductAdminService.java", 370): "状态不在白名单。",
    (CAT + "service/ProductAdminService.java", 381): "分类参数解析。",
    (CAT + "service/ProductAdminService.java", 398): "空关键字等价于不搜索。",
    (CAT + "service/ProductAdminService.java", 401): "关键字超长。",
    (CAT + "service/ProductAdminService.java", 409): "SKU 查重。",
    (CAT + "service/ProductAdminService.java", 415): "分类按父级、排序、id 排序。",
    # ---- service/ProductAdminServiceTest.java ----
    (CAT_TEST + "service/ProductAdminServiceTest.java", 35): "18 个用例：列表参数、新增字段、编辑版本、下拉与回显。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 50): "用例：空参数用默认分页与 ALL。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 63): "用例：非法状态 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 72): "用例：非法页码 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 76): "断言 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 81): "用例：超长关键字 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 89): "用例：分类参数非数字 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 93): "断言 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 100): "用例：新增默认 DRAFT、价格两位小数。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 117): "用例：小写 SKU 被拒，不写库。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 126): "用例：SKU 重复 → 409。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 132): "断言重复文案。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 137): "用例：分类是一级 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 142): "断言分类文案。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 147): "用例：价格 0 / 三位小数 / 非数字都拒绝。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 160): "用例：负库存 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 170): "用例：封面为空 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 184): "用例：编辑只写资料列，版本进条件。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 205): "用例：旧版本保存 → 409（AC-27）。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 219): "用例：缺版本号 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 230): "用例：商品不存在 → 404。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 236): "断言 404。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 243): "用例：分类下拉只列二级并带作品前缀。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 244): "准备：一个作品两个品类。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 256): "用例：编辑回显价格与版本。",
    (ADM + "controller/AdminProductController.java", 25): "商品维护控制器：五条路由；校验、解析与版本冲突都在服务层。",
    (ADM + "controller/AdminProductController.java", 34): "列表：筛选参数原样交给服务归一。",
    (ADM + "controller/AdminProductController.java", 52): "新增表单：分类下拉只列二级。",
    (ADM + "controller/AdminProductController.java", 62): "新增提交：失败回表单并带 400/409。",
    (ADM + "controller/AdminProductController.java", 63): "参数：表单、模型、一次性提示、响应状态。",
    (ADM + "controller/AdminProductController.java", 64): "续行：响应对象用来设置失败状态码。",
    (ADM + "controller/AdminProductController.java", 65): "SKU、价格、分类等业务失败都在服务层抛出。",
    (ADM + "controller/AdminProductController.java", 75): "编辑表单：SKU 与库存只读，版本放隐藏域。",
    (ADM + "controller/AdminProductController.java", 88): "编辑提交：只改资料与价格。",
    (ADM + "controller/AdminProductController.java", 89): "参数同新增，另外带路径里的商品 id。",
    (ADM + "controller/AdminProductController.java", 90): "续行：响应对象用来设置失败状态码。",
    (ADM + "controller/AdminProductController.java", 92): "版本冲突与字段非法时回表单。",
    (ADM + "controller/AdminProductController.java", 107): "失败回表单：保留输入并设 400/409。",
    (ADM + "controller/AdminProductController.java", 108): "续行：响应与模型；编辑时还要带只读的库存与状态。",
    # ---- 商品页面用例 ----
    (ADM_TEST + "ProductAdminPageTest.java", 26): "商品页面的 11 个用例：AC-25/AC-27。",
    (ADM_TEST + "ProductAdminPageTest.java", 36): "用例：列表含下架并显示状态徽章。",
    (ADM_TEST + "ProductAdminPageTest.java", 51): "用例：按状态筛选。",
    (ADM_TEST + "ProductAdminPageTest.java", 64): "用例：按名称包含搜索。",
    (ADM_TEST + "ProductAdminPageTest.java", 77): "用例：新增表单的分类下拉只列二级。",
    (ADM_TEST + "ProductAdminPageTest.java", 89): "用例：新增默认 DRAFT（AC-25）。",
    (ADM_TEST + "ProductAdminPageTest.java", 114): "用例：小写 SKU → 400 且不写库。",
    (ADM_TEST + "ProductAdminPageTest.java", 132): "用例：价格三位小数 → 400。",
    (ADM_TEST + "ProductAdminPageTest.java", 148): "用例：分类是一级 → 400。",
    (ADM_TEST + "ProductAdminPageTest.java", 164): "用例：编辑页 SKU 与库存只读、版隐藏在隐藏域。",
    (ADM_TEST + "ProductAdminPageTest.java", 177): "用例：保存成功且版本 +1、SKU 与库存不变（AC-27）。",
    (ADM_TEST + "ProductAdminPageTest.java", 205): "用例：旧版本保存 → 409 且不覆盖（AC-27）。",
    # ---- 商品页夹具 ----
    (ADM_TEST + "support/AdminFixture.java", 60): "在指定二级分类下建商品：状态由用例给。",
    (ADM_TEST + "support/AdminFixture.java", 61): "续行：价格与库存。",
    # ---- M07-07 服务与 Mapper ----
    (CAT + "service/ProductAdminService.java", 214): "商品被并发删掉：按不存在处理。",
    (CAT + "service/ProductAdminService.java", 404): "状态不在白名单。",
    (CAT + "service/ProductAdminService.java", 426): "补货数量必填。",
    (CAT + "service/ProductAdminService.java", 429): "先声明，解析失败在下面处理。",
    (CAT + "service/ProductAdminService.java", 430): "解析失败按 400。",
    (CAT + "service/ProductAdminService.java", 435): "不提供后台扣减入口。",
    (CAT + "service/ProductAdminService.java", 438): "单次上限与文档一致。",
    (CAT + "mapper/ProductMapper.java", 85): "续行：版本作条件。",
    (CAT + "mapper/ProductMapper.java", 86): "返回影响行数：0 表示版本已变。",
    (CAT + "mapper/ProductMapper.java", 95): "续行：溢出时不更新。",
    (CAT + "mapper/ProductMapper.java", 96): "返回影响行数：0 表示不存在或已到上限。",
    (ADM + "controller/AdminProductController.java", 102): "上下架：POST，成功后回列表。",
    (ADM + "controller/AdminProductController.java", 103): "参数：路径 id、目标状态、隐藏域版本。",
    (ADM + "controller/AdminProductController.java", 113): "补货：POST，成功后回列表。",
    (ADM + "controller/AdminProductController.java", 114): "参数：路径 id 与增加数量。",
    # ---- M07-07 用例 ----
    (CAT_TEST + "service/ProductAdminServiceTest.java", 275): "用例：版本一致 → 切换成功。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 286): "用例：非法状态 → 400 且不写库。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 295): "用例：旧版本上下架 → 409。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 301): "断言冲突文案。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 306): "用例：缺版本号 → 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 316): "用例：补货返回累加后的库存。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 330): "用例：数量 0 / 超上限 / 非整数都 400。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 334): "断言 400（数量 0）。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 336): "断言 400（超上限）。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 338): "断言 400（非整数）。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 343): "用例：补货时商品被删 → 404。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 350): "断言 404。",
    (CAT_TEST + "service/ProductAdminServiceTest.java", 355): "用例：库存溢出 → 409。",
    (ADM_TEST + "ProductAdminPageTest.java", 228): "用例：上架成功且版本 +1。",
    (ADM_TEST + "ProductAdminPageTest.java", 247): "用例：旧版本上下架 → 409。",
    (ADM_TEST + "ProductAdminPageTest.java", 264): "用例：补货 +3、版本 +1。",
    (ADM_TEST + "ProductAdminPageTest.java", 281): "用例：补货数量 0 → 400。",
    (ADM_TEST + "RestockConcurrencyTest.java", 30): "AC-28 并发：两个独立事务同时下单与补货。",
    (ADM_TEST + "RestockConcurrencyTest.java", 43): "用例：初始 10，买 2 补 3，最终 11。",
    (ADM_TEST + "RestockConcurrencyTest.java", 54): "快照行：与车行一致（条目、商品、数量、版本、价格）。",
    (ADM_TEST + "RestockConcurrencyTest.java", 62): "两个线程同一起点，异常各自收集。",
    (ADM_TEST + "RestockConcurrencyTest.java", 64): "线程 A：用户下单。",
    (ADM_TEST + "RestockConcurrencyTest.java", 67): "记下异常，主线程断言。",
    (ADM_TEST + "RestockConcurrencyTest.java", 72): "线程 B：后台补货。",
    (ADM_TEST + "RestockConcurrencyTest.java", 75): "记下异常。",
    (ADM_TEST + "RestockConcurrencyTest.java", 82): "无论成败都收线程池。",
    # ---- M07-08 订单服务 ----
    (ORD + "service/OrderAdminService.java", 22): "后台订单查询服务：全站列表与详情；不做发货与状态编辑。",
    (ORD + "service/OrderAdminService.java", 48): "条件：状态可选、单号精确。",
    (ORD + "service/OrderAdminService.java", 56): "只取当前页，排序与分页交给 SQL。",
    (ORD + "service/OrderAdminService.java", 60): "组装一页：含回显用的筛选条件。",
    (ORD + "service/OrderAdminService.java", 67): "不存在即 404（全站可看，但要有这一单）。",
    (ORD + "service/OrderAdminService.java", 75): "明细按下单顺序。",
    (ORD + "service/OrderAdminService.java", 86): "超过列宽直接 400。",
    (ORD + "service/ShipService.java", 19): "发货服务：只把 PAID 迁到 SHIPPED；重复发货走幂等。",
    (ORD + "service/ShipService.java", 47): "订单不存在：404。",
    (ORD + "domain/ShipResult.java", 5): "发货两种结局：本次成功 / 之前已发过。",
    (ORD + "domain/ShipResult.java", 8): "本次真的发货了。",
    (ORD + "domain/ShipResult.java", 11): "之前已经发过，保持首次时间。",
    (ORD + "dto/AdminOrderPage.java", 11): "后台订单一页：订单 + 分页 + 回显的筛选条件。",
    (ORD + "dto/AdminOrderPage.java", 35): "构造一页：",
    (ORD + "dto/AdminOrderPage.java", 36): "续行：总数、总页数与回显条件。",
    (ACC + "service/AccountService.java", 132): "没有 id 就不查库。",
    (ACC + "service/AccountService.java", 135): "一次批量查完本页用户。",
    (ADM + "controller/AdminOrderController.java", 27): "订单控制器：列表、详情、发货；状态机在 order 模块。",
    (ADM + "controller/AdminOrderController.java", 35): "构造器注入：查询、发货、账号三个协作方。",
    (ADM + "controller/AdminOrderController.java", 36): "续行：发货服务。",
    (ADM + "controller/AdminOrderController.java", 44): "列表：全站订单。",
    (ADM + "controller/AdminOrderController.java", 51): "本页涉及的用户 id。",
    (ADM + "controller/AdminOrderController.java", 53): "去重后一次查显示名。",
    (ADM + "controller/AdminOrderController.java", 63): "详情：明细、收货、支付与所属用户。",
    (ADM + "controller/AdminOrderController.java", 75): "发货：仅 PAID；不可发货交给全局处理器转 409。",
    (ADM + "controller/AdminOrderController.java", 78): "区分首次发货与重复提交的提示。",
    (ORD_TEST + "service/ShipServiceTest.java", 31): "6 个发货用例：迁移、幂等、409、404。",
    (ORD_TEST + "service/ShipServiceTest.java", 46): "用例：PAID → SHIPPED 并写时间。",
    (ORD_TEST + "service/ShipServiceTest.java", 55): "断言东八区发货时间。",
    (ORD_TEST + "service/ShipServiceTest.java", 61): "用例：已发货幂等，不重复写。",
    (ORD_TEST + "service/ShipServiceTest.java", 70): "用例：已完成同样幂等。",
    (ORD_TEST + "service/ShipServiceTest.java", 78): "用例：待支付 → 409。",
    (ORD_TEST + "service/ShipServiceTest.java", 84): "断言 409。",
    (ORD_TEST + "service/ShipServiceTest.java", 89): "用例：已取消 → 409。",
    (ORD_TEST + "service/ShipServiceTest.java", 94): "断言文案。",
    (ORD_TEST + "service/ShipServiceTest.java", 99): "用例：订单不存在 → 404。",
    (ORD_TEST + "service/ShipServiceTest.java", 105): "断言 404。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 27): "5 个查询用例：默认分页、状态、超长单号、404。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 40): "用例：空参数用默认分页与 ALL。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 54): "用例：非法状态 → 400。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 58): "断言 400。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 63): "用例：超长订单号 → 400。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 71): "用例：详情不存在 → 404。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 77): "断言 404。",
    (ORD_TEST + "service/OrderAdminServiceTest.java", 82): "用例：详情返回订单。",
    (ADM_TEST + "OrderAdminPageTest.java", 29): "订单页面的 8 个用例：AC-29。",
    (ADM_TEST + "OrderAdminPageTest.java", 39): "用例：跨用户列表与状态标签。",
    (ADM_TEST + "OrderAdminPageTest.java", 57): "用例：按状态筛选。",
    (ADM_TEST + "OrderAdminPageTest.java", 71): "用例：订单号精确匹配。",
    (ADM_TEST + "OrderAdminPageTest.java", 85): "用例：详情快照与支付信息。",
    (ADM_TEST + "OrderAdminPageTest.java", 101): "用例：PAID 发货并写时间（AC-29）。",
    (ADM_TEST + "OrderAdminPageTest.java", 118): "用例：待支付发货 → 409。",
    (ADM_TEST + "OrderAdminPageTest.java", 132): "用例：重复发货保持首次时间（AC-29）。",
    (ADM_TEST + "OrderAdminPageTest.java", 150): "用例：不存在的订单 → 404。",
    (ADM_TEST + "OrderAdminPageTest.java", 168): "夹具：建一张带明细的订单。",
    (ADM_TEST + "OrderAdminPageTest.java", 169): "续行：商品名与总额。",
    (ADM_TEST + "OrderAdminPageTest.java", 176): "续行：时间字段列。",
    (ADM_TEST + "OrderAdminPageTest.java", 177): "续行：占位符与列一一对应。",
    (ADM_TEST + "OrderAdminPageTest.java", 178): "值：单号、用户、状态、金额、收货快照。",
    (ADM_TEST + "OrderAdminPageTest.java", 179): "值：支付三件套按状态成组。",
    (ADM_TEST + "OrderAdminPageTest.java", 180): "值：取消/发货时间按状态成组。",
    (ADM_TEST + "OrderAdminPageTest.java", 185): "明细：快照名与金额。",
    (ADM_TEST + "OrderAdminPageTest.java", 191): "管理员账号先查后建，避免同用例内重复插入。",
    (ADM_TEST + "OrderAdminPageTest.java", 193): "没有才建。",
}


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
    # ---- M07-02：类声明与规则解释器给不出"为什么"的行 ----
    (ADM + "interceptor/AdminAuthInterceptor.java", "public class AdminAuthInterceptor implements HandlerInterceptor {"):
        "管理端拦截器：只放行启用 ADMIN，其余回登录页。",
    (ADM + "interceptor/AdminAuthInterceptor.java", "if (session != null) {"):
        "有会话但身份不合格：作废它。",
    (ADM + "interceptor/AdminAuthInterceptor.java", "if (path.isBlank()) {"):
        "访问 /admin 本身时按根路径处理。",
    (ADM + "config/AdminWebConfig.java", "public class AdminWebConfig implements WebMvcConfigurer {"):
        "管理端拦截器注册：全站受保护，登录与静态资源放行。",
    (ADM + "controller/AdminAuthController.java", "public class AdminAuthController {"):
        "管理端登录与退出；角色判断交给 AccountService。",
    (ADM + "controller/AdminAuthController.java", "StoreUser admin;"):
        "登录成功后的管理员；失败时直接回表单，不会走到这里。",
    (ADM + "controller/AdminAuthController.java", "try {"):
        "登录失败抛 BizException，在这里转成表单提示。",
    (ADM + "controller/AdminAuthController.java", "if (session != null) {"):
        "没有会话时不必作废。",
    (ADM + "controller/AdminHomeController.java", "public class AdminHomeController {"):
        "控制台：只给身份与入口，不显示统计数字。",
    (ADM + "controller/AdminHomeController.java", '@GetMapping({"", "/"})'):
        "/admin 与 /admin/ 都落到控制台。",
    (ADM + "controller/AdminExceptionHandler.java", "public class AdminExceptionHandler {"):
        "管理端业务异常统一转错误页；技术异常不在这里处理。",
    (ADM_TEST + "AdminLoginTest.java", "class AdminLoginTest extends AdminIntegrationTest {"):
        "管理端登录与拦截的集成用例。",
    (ADM_TEST + "AdminLoginTest.java", "void loggedInAdminGoesToConsole() throws Exception {"):
        "用例：已登录管理员访问登录页，直接去控制台。",
    (ADM_TEST + "AdminLoginTest.java", "void customerCannotLogIn() throws Exception {"):
        "用例：顾客登录管理端被拒，且不建会话（AC-05）。",
    (ADM_TEST + "AdminLoginTest.java", "void disabledAdminCannotLogIn() throws Exception {"):
        "用例：停用管理员登录被拒，文案与密码错一致。",
    (ADM_TEST + "AdminLoginTest.java", "void wrongPassword() throws Exception {"):
        "用例：密码错不透露账号是否存在。",
    (ADM_TEST + "AdminLoginTest.java", 'seed("ad_pwd", "Cartoon123!", "ADMIN", "ENABLED");'):
        "准备：启用的管理员 ad_pwd。",
    (ADM_TEST + "AdminLoginTest.java", "void consoleRequiresLogin() throws Exception {"):
        "用例：未登录访问控制台，跳登录页并记住地址。",
    (ADM_TEST + "AdminLoginTest.java", "void consoleRendersForAdmin() throws Exception {"):
        "用例：控制台显示当前管理员与待接入入口。",
    (ADM_TEST + "AdminLoginTest.java", "void customerSessionRejected() throws Exception {"):
        "用例：顾客会话进不了后台，旧会话被作废（AC-05）。",
    (ADM_TEST + "AdminLoginTest.java", "void disabledAfterLoginRejected() throws Exception {"):
        "用例：登录后被停用，下一个请求即被拒（AC-07）。",
    (ADM_TEST + "AdminLoginTest.java", "void cssIsOpen() throws Exception {"):
        "用例：静态资源不需要登录。",
    (ADM_TEST + "AdminLoginTest.java", ".andReturn();"):
        "取回结果，以便检查登录后的会话。",
    (ADM_TEST + "AdminLoginTest.java", "void loginSucceeds() throws Exception {"):
        "用例：管理员登录成功，会话写入 id 与显示名。",
    (ADM_TEST + "AdminLoginTest.java", "void logoutClearsSession() throws Exception {"):
        "用例：退出销毁管理端会话。",
    (ADM_TEST + "AdminLoginTest.java", "void loginPageRenders() throws Exception {"):
        "用例：登录页可匿名访问，并写明这是管理员入口。",
    (ADM_TEST + "AdminLoginTest.java", 'username, passwordEncoder.encode(rawPassword), "测试管理员", role, status);'):
        "续行：真实 BCrypt 散列 + 用例指定的角色与状态。",
    (ADM_TEST + "support/AdminFixture.java", "public final class AdminFixture {"):
        "管理端用例的准备数据：账号、会话、商品、库存。",
    (ADM_TEST + "support/AdminFixture.java", "private AdminFixture() {"):
        "工具类不实例化。",
    (ADM_TEST + "support/AdminFixture.java", 'return createUser(jdbc, username, "CUSTOMER", "ENABLED");'):
        "顾客：启用状态，用于'顾客不能进后台'。",
    (ADM_TEST + "support/AdminFixture.java", "MockHttpSession session = new MockHttpSession();"):
        "准备：手工会话，只放拦截器认的键。",
    (ADM_TEST + "support/AdminFixture.java", "return session;"):
        "交给请求构造器。",
    (ADM_TEST + "support/AdminFixture.java", "return stock == null ? 0 : stock;"):
        "空值按 0，断言时不必再判空。",
    (ADM_TEST + "support/AdminFixture.java", 'workCode, "作品" + workCode);'):
        "一级分类：作品名带编码，便于识别。",
    (ADM_TEST + "support/AdminFixture.java", 'workCode + "-COMIC", workId, "单行本");'):
        "二级分类必须是 -COMIC 结尾，商品只能挂二级。",
    (ADM_TEST + "support/AdminFixture.java", 'workCode + "-COMIC");'):
        "取回二级分类 id。",
    (ADM_TEST + "support/AdminFixture.java",
     '+ "price, stock, status, sort_order) VALUES (?,?,?,?,?,?,?,?,?,1)",'):
        "商品列清单：价格、库存、状态由用例给，排序固定 1。",
    (ADM_TEST + "support/AdminFixture.java", 'sku, categoryId, "商品" + sku, "一句话简介", "商品说明",'):
        "值：SKU、分类、名称与文案。",
    (ADM_TEST + "support/AdminFixture.java",
     '"images/products/" + sku + "/cover.svg", new BigDecimal(price), stock, status);'):
        "封面路径按 SKU 拼；价格用 BigDecimal。",
    (ADM_TEST + "support/AdminIntegrationTest.java", "public abstract class AdminIntegrationTest {"):
        "管理端集成测试基座：真实 MySQL + 每例重建表。",
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
            text = LINE_NOTES.get((rel, i)) or NOTES.get((rel, s)) or NOTES.get((rel, code)) or trailing  # 行号 > 手写 > 行尾注释。
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
