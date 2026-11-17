# M09-01 · CSRF 令牌公共件 增量汇报

| 项 | 内容 |
|---|---|
| 任务编号 | M09-01 |
| 所属模块 | M09 验收交付 |
| 提交 | 本任务随 `feat(m09-01)` 提交 |
| 日期 | 2026-11-18 |
| 状态 | 🟡 已实现，待审阅 |
| 验收命令 | `mvn -pl cartoon-common test` |

---

## 1. 这次做了什么

把「所有写请求必须带会话令牌」从文档口径变成可复用的公共件：令牌怎么签发、存在哪、怎么比对、失败怎么拒绝，全部收在 `cartoon-common` 的两个类里。
两端只需注册过滤器、把令牌渲染进表单（M09-02），不再各写一套判断。

本任务**不改变现有行为**：过滤器还没有注册到任何应用，页面也还没有隐藏域，所以现有 405 个用例与两端运行不受影响。

---

## 2. 与项目开发文档的对应

| 文档 | 条款 | 本任务的落实 |
|---|---|---|
| [02 概要设计](../docs/02-概要设计说明书.md) | §6.3 CSRF（同步令牌）、§6.5 错误处理（CSRF 失败 403） | `CsrfToken` + `CsrfFilter`；失败走 `sendError(403)`，由 M09-02 的 `error/403.html` 呈现 |
| [04 接口设计](../docs/04-接口设计说明书.md) | §1 统一约定：所有 `POST` 必须带 `_csrf`，否则 403 | 参数名与状态码都取同一常量 `CsrfToken.PARAM_NAME` / 403 |
| [06 测试与验收](../docs/06-测试与验收方案.md) | AC-31「无 `_csrf` 的写请求被拒 403」 | 过滤器单测 10 例覆盖缺令牌、错令牌、正确令牌；页面级用例在 M09-03 |
| [09 模块划分](../docs/09-模块划分与开发流程.md) | §2.2 `common` 负责 CSRF 过滤器；§2.3 依赖方向 | 过滤器放 `common`，只依赖 `spring-web` 与 `jakarta.servlet-api`；pom 新增 `spring-context`（provided） |
| [10 汇报规范](../docs/10-任务清单与增量汇报规范.md) | §5.2 行尾注释与逐行口径 | 新增 340 行全部带"为什么"行尾注释；逐行验收无兜底、无「—」 |

> 不对应具体条款：`cartoon-common/pom.xml` 新增 `spring-context`（provided）纯属编译需要——`OncePerRequestFilter` 的父类实现了 `EnvironmentAware`，而 common 原来只有 `spring-web`。

---

## 3. 产出文件

| 文件 | 类型 | 行数 | 作用 |
|---|---|---|---|
| `cartoon-common/src/main/java/org/csu/mycartoonstore/common/security/CsrfToken.java` | 新增 | 40 | 会话键、参数名、令牌生成、恒定时间比对 |
| `cartoon-common/src/main/java/org/csu/mycartoonstore/common/security/CsrfFilter.java` | 新增 | 89 | 读请求签发令牌；写请求校验；静态资源放行；失败 403 |
| `cartoon-common/src/test/java/org/csu/mycartoonstore/common/security/CsrfTokenTest.java` | 新增 | 56 | 5 个令牌用例 |
| `cartoon-common/src/test/java/org/csu/mycartoonstore/common/security/CsrfFilterTest.java` | 新增 | 155 | 10 个过滤器用例 |
| `cartoon-common/pom.xml` | 修改 | +6 | `spring-context`（provided） |
| `db/tools/gen_m09_acceptance.py` | 新增 | 106 | M09 逐行验收 HTML 生成器（复用 M08 渲染器） |
| `reports/M09-01-逐行验收.html` | 新增 | 340 行代码 | 逐行对照，兜底 0 |

---

## 4. 关键设计

### 4.1 令牌只存会话，不落库、不写 Cookie

两端 Cookie 名不同、会话各自独立，令牌跟着会话走最省事。放弃「双提交 Cookie」：那要多一个可被 JS 读取的 Cookie，且两端同名会互相覆盖，收益只是省一次会话读取。

### 4.2 缺令牌等于拒绝，不做「没带就放行」

最容易写错的地方是 `if (token == null) return true`——那等于没做 CSRF。`CsrfToken.matches` 对 `null`、空串、长度不同一律返回 `false`，用例里专门钉住这四条。

### 4.3 恒定时间比较

用 `MessageDigest.isEqual` 而不是 `equals`：后者按字节短路返回，理论上可被计时侧信道利用。长度不同的输入也走完整循环。

### 4.4 静态资源放行且不建会话

若所有请求都 `getSession(true)`，每个 CSS/图片请求都会建会话，浏览器会话数暴涨。过滤器只放行 `/css/`、`/images/`、`/js/`、`/webjars/` 与 `/favicon.ico`，且**先判断静态再决定是否建会话**；用例断言「静态请求后 `getSession(false)` 为 null」。

### 4.5 过滤器在拦截器之前，403 先于 302

漏掉令牌的匿名写请求应当 403（来源不可信），而不是 302 去登录页——后者等于告诉攻击者"这个地址存在且需要登录"。这条口径要同步进 docs/04 §1（M09-02 做）。

### 4.6 方法名统一大写后再判断

Servlet 规范给的是大写方法名，但这里不依赖调用方守规矩：`toUpperCase(Locale.ROOT)` 后判断，`post` 也会被当作写请求校验；`getMethod()` 为 null 时按写请求处理（更严）。

---

## 5. 验证结果

```
$ mvn -pl cartoon-common test
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

（原有 10 例 + 本任务新增 15 例：`CsrfTokenTest` 5、`CsrfFilterTest` 10）

| 检查点 | 期望 | 实际 |
|---|---|---|
| 读请求签发令牌 | 会话里有非空 `csrfToken`，链路放行 | ✅ |
| 同一会话不换令牌 | 两次 GET 取到同一个值 | ✅ |
| 静态资源 | 放行且不建会话（含 `/admin` context-path） | ✅ |
| 无会话 POST | 403，且不进下游 | ✅ |
| 有会话无参数 / 参数不匹配 | 403 | ✅ |
| 令牌一致 | 放行 | ✅ |
| 小写 `post`、`PUT`、`DELETE` | 同样校验 | ✅ |
| 逐行验收 | 兜底 0、无「—」 | ✅ 340 行 / 兜底 0 |

---

## 6. 遗留问题

| 问题 | 影响 | 计划 |
|---|---|---|
| 过滤器未注册到两端，页面没有隐藏域 | 本任务不改变任何现有行为，也不提供任何防护 | M09-02 注册 + 29 处表单 + 403 页面，同任务内完成，避免"注册了但表单没令牌"的中间态 |
| `templates/error/403.html` 未建 | `sendError(403)` 现在会落到 Boot 的白板页 | M09-02 建页并断言文案 |
| docs/02 §6.3 仍写「CSRF 尚未启用」 | 文档与代码不一致 | M09-02 改写实现状态 |
| 页面级用例（表单含令牌、跨请求令牌、XSS 转义）未写 | AC-31 只有单测层证据 | M09-03 |

---

## 7. 下一个任务

- 编号：M09-02 两端接入与 403 页面
- 内容：两端 `FilterRegistrationBean`、`CsrfAdvice`、`fragments/csrf.html`、29 处写表单隐藏域、两端 `error/403.html`
- 验收：启动后页面源码含 `_csrf`；无令牌 POST → 403 文案；带令牌原行为不变；两端全量回归
