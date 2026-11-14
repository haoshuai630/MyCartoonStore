# 数据库脚本

## 1. 两个脚本二选一

| 文件 | 内容 | 什么时候用 |
|---|---|---|
| `01-schema.sql` | 七张表结构，**不含任何数据** | 只想建表、数据自己造 |
| `02-schema-with-demo.sql` | 表结构 + 教学种子数据（4 账号 / 15 分类 / 60 商品 / 100 相册 / 10 购物车 / 10 订单 / 20 明细） | 直接跑起来看完整效果 |

**不要按编号依次执行**。两个文件都自带 `CREATE DATABASE` 与建表语句，在同一个库里跑第二遍会因表已存在而报错。空库执行其中**一个**即可。

## 2. 初始化

```bash
# 方式一：带演示数据（推荐）
mysql -u root -p --default-character-set=utf8mb4 < db/02-schema-with-demo.sql

# 方式二：只要结构
mysql -u root -p --default-character-set=utf8mb4 < db/01-schema.sql
```

说明：

- 库名 `MyCartoonStore`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`；
- 脚本只建库建表，**不创建数据库账号**，也不会被应用启动时自动执行（`spring.sql.init.mode=never`）；
- 已有同名表时脚本会报错，这是有意为之，**不要用 `mysql --force` 跳过**。

## 3. 核对（执行 02 之后）

```sql
USE MyCartoonStore;
SELECT
  (SELECT COUNT(*) FROM store_user)        AS users,     -- 4
  (SELECT COUNT(*) FROM category)          AS categories,-- 15
  (SELECT COUNT(*) FROM product)           AS products,  -- 60
  (SELECT COUNT(*) FROM product_image)     AS images,    -- 100
  (SELECT COUNT(*) FROM cart_item)         AS carts,     -- 10
  (SELECT COUNT(*) FROM sales_order)       AS orders,    -- 10
  (SELECT COUNT(*) FROM sales_order_item)  AS items;     -- 20
```

## 4. 教学账号

| 用户名 | 密码 | 角色 | 状态 |
|---|---|---|---|
| `alice` | `Cartoon123!` | CUSTOMER | ENABLED |
| `bob` | `Cartoon123!` | CUSTOMER | ENABLED |
| `carol` | `Cartoon123!` | CUSTOMER | DISABLED |
| `admin` | `Cartoon123!` | ADMIN | ENABLED |

密码以 BCrypt（cost=10）存储，仅用于本地教学，不能作为任何真实环境凭据。

## 5. 约定

- 表结构、列名、CHECK、索引、外键沿用课程 MyPetStore 基线，**不做修改**；本项目只改库名与注释。
- 商品类型不新增字段，由二级分类 `code` 后缀判定：`-COMIC` 为漫画、`-MERCH` 为周边。
- `category.parent_key` 是生成列（`COALESCE(parent_id,0)` STORED），插入与更新时**不要写它**。
- 应用启动绝不建表、不灌数据；数据只由本目录脚本主动初始化一次。
