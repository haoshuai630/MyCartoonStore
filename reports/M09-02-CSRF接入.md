# M09-02 · 两端接入与 403 页面 增量汇报

| 项 | 内容 |
|---|---|
| 任务编号 | M09-02 |
| 所属模块 | M09 验收交付 |
| 提交 | 本任务随 `feat(m09-02)` 提交 |
| 日期 | 2026-11-18 |
| 状态 | 🟡 已实现，待审阅 |
| 验收命令 | 真实容器 `java -jar` + `db/tools/m09_csrf_probe.py`；回归见 M09-03 |

---

## 1. 这次做了什么

把 M09-01 的过滤器真正挂到两端，并让 29 处写表单都带上令牌：读请求签发、模板渲染隐藏域、写请求校验、失败落到 403 页面。
同步把 docs/02 §6.3、docs/04 §1、docs/11 §6 里「CSRF 尚未启用」的口径改掉——从这一刻起，没有令牌的写请求在浏览器里也确实进不去。

**本任务单独提交时 web 测试会全红**：测试夹具还没补令牌。这是清单里写明的「M09-02 与 M09-03 一起验收」，M09-03 补齐后全量 438 用例转绿。

---

## 2. 与项目开发文档的对应

| 文档 | 条款 | 本任务的落实 |
|---|---|---|
| [02 概要设计](../docs/02-概要设计说明书.md) | §6.3 CSRF 三条（过滤器、隐藏域、`@ControllerAdvice`）、§6.5 403 错误页 | 过滤器两端注册；`CsrfAdvice` 注入 `csrfToken`；`error/403.html` 落地；实现状态改写 |
| [04 接口设计](../docs/04-接口设计说明书.md) | §1 统一约定 | CSRF 行改为「已启用」；未登录写请求补「先校验 CSRF：无令牌 403」 |
| [05 页面与交互](../docs/05-页面与交互设计.md) | §3 公共布局、§5 状态设计 | 403 页面与既有错误页同版式（用户端带页头页脚，后台带顶栏） |
| [06 测试与验收](../docs/06-测试与验收方案.md) | AC-31「从外部站点提交写请求 → 无 `_csrf` 被拒 403」 | 真实容器探针：无令牌 POST 得到 403 与 403 页面 |
| [09 模块划分](../docs/09-模块划分与开发流程.md) | §2.2 `common` 管过滤器、web 只做装配 | `FilterRegistrationBean` 与 `CsrfAdvice` 放两端 web 层 |
| [11 本地运行指南](../docs/11-本地运行指南.md) | §6 能测 / 不能测 | CSRF 从「不能测」挪到「能测」 |

---

## 3. 产出文件

| 文件 | 类型 | 行数 | 作用 |
|---|---|---|---|
| `cartoon-user-web/.../web/config/CsrfFilterConfig.java` | 新增 | 27 | 用户端注册过滤器（全站、最高优先级） |
| `cartoon-admin-web/.../admin/config/CsrfFilterConfig.java` | 新增 | 27 | 管理端注册过滤器 |
| `cartoon-user-web/.../web/controller/CsrfAdvice.java` | 新增 | 25 | 用户端把会话令牌注入模型 |
| `cartoon-admin-web/.../admin/controller/CsrfAdvice.java` | 新增 | 25 | 管理端同上 |
| `cartoon-user-web/.../templates/fragments/csrf.html` | 新增 | 9 | 用户端隐藏域片段 |
| `cartoon-admin-web/.../templates/admin/csrf.html` | 新增 | 9 | 后台隐藏域片段 |
| `cartoon-user-web/.../templates/error/403.html` | 新增 | 23 | 用户端 403 页面 |
| `cartoon-admin-web/.../templates/error/403.html` | 新增 | 22 | 后台 403 页面 |
| 21 个模板 | 修改 | +29 行 | 每处写表单插入一行片段引用 |
| `db/tools/m09_csrf_probe.py` | 新增 | 92 | 真实容器取证脚本（两端通用） |
| `docs/02`、`docs/04`、`docs/11` | 修改 | — | 同步「已启用」与 403 口径 |

**29 处写表单的分布**（按文件）：`login`/`register`/`profile`/`password`/`checkout`/`product`/`cart`×2/`orders`/`order-detail`×2/`payment`×3/`fragments/common`；
后台 `login`/`main`/`fragments`/`category-form`/`category-list`×2/`product-form`/`product-list`×2/`order-detail`/`user-list`/`image-list`×3。
插入规则统一为「`<form ... method="post">` 之后紧跟一行 `<input th:replace="~{…/csrf :: hidden}">`」，缩进跟随原表单。

---

## 4. 关键设计

### 4.1 一处渲染，不逐表单手写

29 处表单都引用同一个片段。手写 29 个 `<input ... th:value="${csrfToken}">` 迟早漏一处，而漏掉的表现是"某个按钮点了 403"；引用片段后，改参数名只改一个文件。

### 4.2 令牌值取会话，模板不参与生成

`CsrfAdvice` 只读不改：令牌由过滤器在请求进入时签发。若让模板"没有就生成"，同一页面并发渲染会出现两个令牌，前一个立刻失效。

### 4.3 403 用独立模板

Spring Boot 的错误视图解析按 `error/403` 找模板；复用 `error/error.html` 会拿到空 `${message}`。新建 `error/403.html` 后，真实容器里 `sendError(403)` 能稳定渲染（见 §6 的探针结果），不再落到 Whitelabel。

### 4.4 静态资源先放行再谈令牌

过滤器在 `/css/`、`/images/`、`/js/`、`/favicon.ico` 上直接 `chain.doFilter`，既不放行任何业务路径，也不为每个样式表建会话。探针断言静态请求没有 `Set-Cookie`。

### 4.5 过滤器排在拦截器之前

无令牌的匿名写请求得到 403，而不是 302 登录页——后者会把"这个地址存在且需要登录"变成可探测信息。这条口径已写进 docs/04 §1。

---

## 5. 验证结果

打包并按临时端口启动两个实例（18080 / 18081，避免占用正在运行的 8080 / 8081），用 `db/tools/m09_csrf_probe.py` 取证：

```
# 用户端 http://127.0.0.1:18080
GET  /login            status=200 token=yes
POST /login            status=403 page=请求未被接受      ← 无令牌
GET  /css/store.css    status=200 setCookie=False        ← 静态资源不建会话
POST /login            status=200 formError=True (valid token)
GET  /login            status=200 tokenStable=True

# 管理端 http://127.0.0.1:18081/admin
GET  /login            status=200 token=yes
POST /login            status=403 page=操作被拒绝
GET  /css/admin.css    status=200 setCookie=False
POST /login            status=200 formError=True (valid token)
GET  /login            status=200 tokenStable=True
```

证据文件：`reports/evidence/m09-ac31-csrf-user-probe.txt`、`m09-ac31-csrf-admin-probe.txt`、
`m09-ac31-user-403.html`、`m09-ac31-admin-403.html`（页面快照）、两端控制台日志。

| 检查点 | 期望 | 实际 |
|---|---|---|
| 登录页含隐藏域 | `name="_csrf"` 且值非空 | ✅ 两端 |
| 无令牌 POST | 403 + 403 页面 | ✅ 两端 |
| 静态资源 | 200 且无 `Set-Cookie` | ✅ 两端 |
| 正确令牌 | 原行为（表单校验照旧） | ✅ 两端 |
| 同会话令牌稳定 | 两次 GET 同值 | ✅ 两端 |

---

## 6. 遗留问题

| 问题 | 影响 | 计划 |
|---|---|---|
| 本任务提交时 web 测试全红（缺测试夹具） | 分支上有一个红提交 | M09-03 补齐后 438 全绿，两任务一起验收 |
| 只有登录页与静态资源做了端到端探针 | 其它 27 处表单靠 M09-03 的页面用例覆盖 | M09-03 的 `CsrfProtectionTest` |
| 403 页面未做浏览器截图 | 有 HTML 快照与文案断言 | 验收记录里以快照与用例为证 |

---

## 7. 下一个任务

- 编号：M09-03 测试夹具、既有用例补令牌与 AC-31 用例
- 内容：`CsrfTestSupport`、20 个测试文件 108 处 POST、两端 `CsrfProtectionTest`、`XssEscapingTest`
- 验收：`mvn test` 全绿（438 用例）
