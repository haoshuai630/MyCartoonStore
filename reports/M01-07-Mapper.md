# M01-07 · Mapper 与自定义查询 增量汇报

| 项 | 内容 |
|---|---|
| 任务编号 | M01-07 |
| 所属模块 | M01 数据库与实体 |
| 提交 | 待审阅通过后提交 |
| 日期 | 2026-11-15 |
| 状态 | 🟡 待审阅 |
| 验收命令 | `mvn -DskipTests package` + 启动应用 |

> 逐行验收：**按 10 篇 §5，Mapper 接口用"逐方法说明"**，SQL 逐语句说明（见 §3）。

---

## 1. 这次做了什么

7 个 Mapper 接口 + 2 个 XML：

| 模块 | Mapper | 形态 |
|---|---|---|
| `cartoon-account` | `StoreUserMapper` | 注解 |
| `cartoon-catalog` | `ProductMapper` | **XML**（列表/首页/行锁）+ 注解（扣库存） |
| | `CategoryMapper` | 仅 `BaseMapper` |
| | `ProductImageMapper` | 仅 `BaseMapper` |
| `cartoon-cart` | `CartItemMapper` | **XML**（联表视图）+ 注解 |
| `cartoon-order` | `SalesOrderMapper` | 注解（行锁、查重） |
| | `SalesOrderItemMapper` | 仅 `BaseMapper` |

配套改动：

- 两个启动类加 `@MapperScan`（显式列四个包，不用通配）；
- 两份 `application.yaml` 加 `mybatis-plus.mapper-locations: classpath*:/mapper/**/*.xml`。

## 2. 关键设计

### 2.1 复杂 SQL 放 XML，单条语句用注解

列表查询有 4 个可选条件 + 4 种排序，注解里写 `<script>` 会变成一大坨字符串；XML 的 `<if>` / `<choose>` 可读且能直接对照验收规则。行锁、计数、单条 UPDATE 放进注解，避免为一行 SQL 建一个 XML。

### 2.2 排序不走字符串拼接，用 `<choose>` 映射到固定列

```xml
<choose>
  <when test="q.sort == 'PRICE_ASC'">ORDER BY p.price ASC, p.id ASC</when>
  ...
  <otherwise>ORDER BY p.sort_order ASC, p.id ASC</otherwise>
</choose>
```

即使 Service 漏校验，SQL 里也不存在"把用户输入拼进 ORDER BY"的可能——这是 `04 篇 §4` 那条"禁止接收任意 SQL 排序字段"的底线。

### 2.3 商品锁按 id 升序，所有事务同一顺序

```sql
SELECT * FROM product WHERE id IN (...) ORDER BY id FOR UPDATE
```

下单要锁多个商品，如果 A 事务按 1→2、B 事务按 2→1，就会死锁。统一升序后不可能形成环。

### 2.4 扣库存只有一条 UPDATE，自带条件

```sql
UPDATE product SET stock = stock - #{qty}, version = version + 1
WHERE id = #{id} AND stock >= #{qty}
```

不做"先查库存再更新"：那是两次往返，中间可能被别的事务改掉。影响行数 0 就代表库存不足，Service 据此抛业务异常。

## 3. 逐方法说明

| 方法 | SQL / 实现 | 返回 | 用途 |
|---|---|---|---|
| `ProductMapper.selectPage` | XML：`product JOIN category`，`status='ON_SALE'`，可选的分类/类型/关键字，`<choose>` 排序，`LIMIT/OFFSET` | `List<Product>` | 列表页 |
| `countPage` | 同条件 `COUNT(*)` | `long` | 总数与分页 |
| `selectNewest` | `WHERE status='ON_SALE' ORDER BY created_at DESC, id DESC LIMIT #{limit}` | `List<Product>` | 首页新品 3 |
| `selectFeatured` | `ORDER BY sort_order ASC, id ASC LIMIT #{limit}` | `List<Product>` | 首页精选 4 |
| `lockByIds` | `IN (...) ORDER BY id FOR UPDATE` | `List<Product>` | 下单锁商品 |
| `deductStock` | 注解 `UPDATE ... WHERE id=? AND stock>=?` | `int` | 扣库存，0 行=不足 |
| `StoreUserMapper.findByUsername` | `WHERE username = ?` | `StoreUser` | 登录 |
| `lockById` | `WHERE id = ? FOR UPDATE` | `StoreUser` | 购物车/下单先锁人 |
| `CartItemMapper.selectViewByUser` | XML：`cart_item JOIN product JOIN category`，带当前价格/库存/状态 | `List<CartItemView>` | 购物车页 |
| `findByUserAndProduct` | `WHERE user_id=? AND product_id=?` | `CartItem` | 加车累加前查询 |
| `countByUser` | `COUNT(*) WHERE user_id=?` | `int` | 20 种上限 |
| `SalesOrderMapper.lockByOrderNo` | `WHERE order_no=? FOR UPDATE` | `SalesOrder` | 支付/取消/发货 |
| `findByOrderNo` | `WHERE order_no=?` | `SalesOrder` | 详情 |
| `countByOrderNo` | `COUNT(*)` | `int` | 订单号查重 |
| `countByPaymentNo` | `COUNT(*)` | `int` | 凭证查重 |

## 4. 验证结果

### 4.1 编译

```
$ mvn -DskipTests package
BUILD SUCCESS
```

### 4.2 启动（验证 XML 能被解析、Mapper 能注册）

```
Started CartoonUserApplication in 2.168 seconds
GET /health -> 200
```

MyBatis 在启动时解析全部 XML；namespace 写错、statement id 重复、DTD 不合法都会导致启动失败。能启动说明** XML 结构与 namespace 正确**。

### 4.3 尚未验证

**每个方法的 SQL 能否真正执行（字段映射、参数绑定、`FOR UPDATE` 语义）没有验证**——这需要连库调用，属于 M01-09 的集成测试。本任务不假装跑过。

## 5. 遗留问题

| 问题 | 影响 | 计划 |
|---|---|---|
| 列表关键字未处理 `%` `_` 转义 | 用户输入 `%` 会变成通配符 | M01-09 或 M02 在 Service 层转义 |
| `@Mapper` 与 `@MapperScan` 同时存在 | 可能产生重复注册告警 | 启动未见异常，暂不处理 |
| CartItemView 未含 `altText` 等 | 购物车不需要 | — |

## 6. 下一个任务

**M01-08 测试基座**：`application-test.yaml` + 测试库初始化，让 `cartoon-*` 模块能跑真实数据库测试。
