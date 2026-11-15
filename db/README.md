# 数据库脚本

## 1. 两个脚本二选一

| 文件 | 内容 | 什么时候用 |
|---|---|---|
| `01-schema.sql` | 七张表结构，**不含任何数据** | 只想建表、数据自己造 |
| `02-schema-with-demo.sql` | 表结构 + 教学种子数据（4 账号 / 15 分类 / 60 商品 / 100 相册 / 10 购物车 / 10 订单 / 20 明细） | 直接跑起来看完整效果 |

**不要按编号依次执行**。两个文件都自带 `CREATE DATABASE` 与建表语句，在同一个库里跑第二遍会因表已存在而报错。空库执行其中**一个**即可。

`02` 由 `tools/gen_demo_sql.py` 生成，产物是纯 SQL，执行时不需要 Python。

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
- 已有同名表时脚本会报错，这是有意为之，**不要用 `mysql --force` 跳过**；
- `--default-character-set=utf8mb4` 不能省，否则 Windows 控制台可能按 GBK 发送中文。

## 3. 核对

### 3.1 执行 `02` 之后（带数据）

```sql
USE MyCartoonStore;
SELECT
  (SELECT COUNT(*) FROM store_user)        AS users,      -- 4
  (SELECT COUNT(*) FROM category)          AS categories, -- 15
  (SELECT COUNT(*) FROM product)           AS products,   -- 60
  (SELECT COUNT(*) FROM product_image)     AS images,     -- 100
  (SELECT COUNT(*) FROM cart_item)         AS carts,      -- 10
  (SELECT COUNT(*) FROM sales_order)       AS orders,     -- 10
  (SELECT COUNT(*) FROM sales_order_item)  AS items;      -- 20
```

期望输出：

```
users  categories  products  images  carts  orders  items
4      15          60        100     10     10      20
```

### 3.2 执行 `01` 之后（仅结构）

```sql
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = 'MyCartoonStore';   -- 7
```

七张表都是 0 行——说明只建了结构。

### 3.3 关键样例核对（执行 `02` 之后）

```sql
-- 上架 54 / 下架 6
SELECT status, COUNT(*) FROM product GROUP BY status;

-- 上架但售罄：3 件；库存只剩 1：2 件
SELECT sku, stock FROM product WHERE status='ON_SALE' AND stock <= 1 ORDER BY sku;

-- 订单五状态各 2 张
SELECT status, COUNT(*) FROM sales_order GROUP BY status;

-- 已支付（带 MOCK 凭证）6 张
SELECT COUNT(*) FROM sales_order WHERE payment_no IS NOT NULL;

-- 订单总额必须等于明细合计，应为 0 行
SELECT o.order_no FROM sales_order o
JOIN (SELECT order_id, SUM(line_amount) s FROM sales_order_item GROUP BY order_id) i
  ON i.order_id = o.id
WHERE o.total_amount <> i.s;

-- 无相册商品 20 个（详情页回退封面）
SELECT COUNT(*) FROM product p
WHERE NOT EXISTS (SELECT 1 FROM product_image i WHERE i.product_id = p.id);
```

## 4. 重置（重新初始化）

```sql
DROP DATABASE IF EXISTS MyCartoonStore;
CREATE DATABASE MyCartoonStore
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

然后重新执行 `02`。**只在本地教学库上做**，不要对有任何真实数据的库执行。

## 5. 教学账号

| 用户名 | 密码 | 角色 | 状态 |
|---|---|---|---|
| `alice` | `Cartoon123!` | CUSTOMER | ENABLED |
| `bob` | `Cartoon123!` | CUSTOMER | ENABLED |
| `carol` | `Cartoon123!` | CUSTOMER | DISABLED |
| `admin` | `Cartoon123!` | ADMIN | ENABLED |

密码以 BCrypt（cost=10）存储，仅用于本地教学，不能作为任何真实环境凭据。

## 6. 约定

- 表结构、列名、CHECK、索引、外键沿用课程 MyPetStore 基线，**不做修改**；本项目只改库名与注释。
- 商品类型不新增字段，由二级分类 `code` 后缀判定：`-COMIC` 为漫画、`-MERCH` 为周边。
- `category.parent_key` 是生成列（`COALESCE(parent_id,0)` STORED），插入与更新时**不要写它**。
- 应用启动绝不建表、不灌数据；数据只由本目录脚本主动初始化一次。
- 订单时间用相对 `NOW()` 生成，每次初始化时间会变，不影响数据关系。
