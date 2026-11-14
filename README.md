# 漫屿 MANGA ISLE · MyCartoonStore

原创漫画 + 官方周边商店，SSM（Spring Boot + Spring MVC + MyBatis-Plus + Thymeleaf）课程实验一项目。

一个仓库、Maven 多模块、两个可独立启动的应用：用户端 `:8080` 与管理端 `:8081/admin`，共享同一个 MySQL 库的七张业务表。

---

## 状态

| 模块 | 内容 | 状态 |
|---|---|---|
| M00 | 工程基座（parent、common、测试基座、依赖边界检查） | ✅ 完成 |
| M01 | 数据库与实体（SQL 改写、七表实体/Mapper、种子数据） | ⬜ 待开始 |
| M02 | 商品浏览 | ⬜ |
| M03 | 账号与权限 | ⬜ |
| M04 | 购物车 | ⬜ |
| M05 | 订单 | ⬜ |
| M06 | 支付 | ⬜ |
| M07 | 管理端 | ⬜ |
| M08 | P1 扩展 | ⬜ |
| M09 | 验收交付 | ⬜ |

> `db/` 里的两份 SQL 目前仍是课程原始的 `MyPetStore` 版本，M01 会改写为 `MyCartoonStore` 与漫画种子数据。

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

探活：

- 用户端 <http://localhost:8080/health>
- 管理端 <http://localhost:8081/admin/health>

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
