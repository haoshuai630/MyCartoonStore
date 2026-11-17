# 漫屿 MANGA ISLE · MyCartoonStore

原创漫画 + 官方周边商店，SSM（Spring Boot + Spring MVC + MyBatis-Plus + Thymeleaf）课程实验一项目。

一个仓库、Maven 多模块、两个可独立启动的应用：用户端 `:8080` 与管理端 `:8081/admin`，共享同一个 MySQL 库的七张业务表。

---

## 状态

| 模块 | 内容 | 状态 |
|---|---|---|
| M00 | 工程基座（parent、common、测试基座、依赖边界检查） | ✅ 完成 |
| M01 | 数据库与实体（SQL 改写、七表实体/Mapper、种子数据） | ✅ 完成（v0.3-m01） |
| M02 | 商品浏览（首页 / 作品墙 / 作品页 / 列表 / 详情） | ✅ 完成（v0.4-m02） |
| M03 | 账号与权限（注册 / 登录 / 拦截） | ✅ 完成（v0.5-m03） |
| M04 | 购物车（加入 / 改量 / 移除 / 徽标 / 失效条目） | ✅ 已验收（v0.6-m04，2026-11-16） |
| M05 | 订单（结算 / 下单 / 列表 / 取消） | ✅ 完成（`v0.7-m05`） |
| M06 | 支付 | ✅ 完成（`v0.8-m06`） |
| M07 | 管理端（身份口径 / 登录 / 分类 / 商品 / 补货 / 查单 / 发货） | 🟡 进行中（5/9，登录/控制台/分类/商品服务已提交） |
| M08 | P1 扩展 | ⬜ |
| M09 | 验收交付 | ⬜ |

> 数据库已是 `MyCartoonStore` + 漫画种子数据（4 用户 / 5 作品 / 55 品类 / 95 商品 / 10 订单）。最近一次更新：2026-11-17（M06 支付完成）。

---

## 环境

| 项 | 版本 |
|---|---|
| JDK | 17+（当前开发机 27） |
| Maven | 仓库自带 `mvnw`（3.10.0），也可用本机 Maven 3.9+ |
| MySQL | 8.0.16+（`utf8mb4_0900_ai_ci`） |

数据库连接在 `cartoon-user-web/src/main/resources/application.yaml` 与 `cartoon-admin-web/.../application.yaml`，默认 `root` / `${DB_PASSWORD:123456}`。

---

## 构建与测试

> 完整步骤（含数据库初始化、IDEA 启动、常见报错）见 [docs/11-本地运行指南](docs/11-本地运行指南.md)。

```bash
# 全量打包（跳过测试）
./mvnw -DskipTests package          # Windows: mvnw.cmd -DskipTests package

# 单个模块 + 它的上游依赖
./mvnw -pl cartoon-common -am test
./mvnw -pl cartoon-cart -am verify

# 启动两端（需要先初始化数据库）
./mvnw -pl cartoon-user-web  spring-boot:run
./mvnw -pl cartoon-admin-web spring-boot:run
```

已实现的页面与探活：

| 地址 | 说明 |
|---|---|
| <http://localhost:8080/main> | 首页：作品入口 + 新品 3 + 精选 4 |
| <http://localhost:8080/login> | 登录（种子账号 alice / Cartoon123!） |
| <http://localhost:8080/register> | 注册 |
| <http://localhost:8080/works> | 作品墙：5 部作品 |
| <http://localhost:8080/works/1> | 作品页：品类胶囊 + 商品 |
| <http://localhost:8080/category> | 全部商品：作品下拉 / 类型 / 排序 / 关键字 |
| <http://localhost:8080/product/1> | 详情页：面包屑、相册、加入购物车 |
| <http://localhost:8080/cart> | 购物车：改数量 / 移除 / 合计 / 失效条目（需登录） |
| <http://localhost:8080/checkout> | 结算确认：收货信息 + 后端快照（需登录） |
| <http://localhost:8080/orders> | 我的订单：五状态页签 / 完整订单号 / 分页（需登录） |
| `/orders/{orderNo}` | 订单详情：快照、时间线、支付/取消入口（订单号从列表进入，需登录） |
| `/payment/{orderNo}` | 模拟支付：大字金额 + 成功/失败两个动作（需登录） |
| <http://localhost:8080/health> | 用户端探活 |
| <http://localhost:8081/admin/health> | 管理端探活（管理页面在 M07 做） |

---

## 模块

```
mycartoonstore/           parent：依赖管理 + 依赖边界检查（enforcer）
├── cartoon-common/       异常、分页参数、金额工具（无 Web / 无 MyBatis）
├── cartoon-catalog/      分类、商品、相册
├── cartoon-account/      账号、登录校验、资料
├── cartoon-cart/         购物车
├── cartoon-order/        结算确认、订单、取消
├── cartoon-payment/      模拟支付
├── cartoon-test-support/ 测试库常量与测试工具
├── cartoon-user-web/     用户端应用，8080
└── cartoon-admin-web/    管理端应用，8081，context-path=/admin
```

依赖方向单向：`web → 业务模块 → common`。业务模块禁止依赖 Web starter，由 `maven-enforcer-plugin` 在构建期检查（web 模块跳过该检查）。

---

## 文档

完整的开发文档在 [`docs/`](docs/README.md)：需求规格、概要设计、数据库设计、接口设计、页面设计、测试验收、开发计划、PRD 平移对照、模块划分与流程。

每个模块的开发汇报在 [`reports/`](reports/README.md)。

---

## 目录

| 目录 | 说明 |
|---|---|
| `db/` | 数据库脚本（结构版与含演示数据版，二选一执行） |
| `docs/` | 开发文档 |
| `reports/` | 模块汇报与验收证据 |
| `demo/` | 分类结构对比静态站点（信息架构选型时使用） |
