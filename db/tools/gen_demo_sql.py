#!/usr/bin/env python3
"""生成 db/02-schema-with-demo.sql：表结构 + 教学种子数据。

为什么要脚本生成：
  60 个商品、100 条相册、20 条明细都有强规则（SKU 模板、库存边界、状态分布），
  手写 SQL 极易出现「数量对不上」「边界样例缺失」的问题。脚本保证可复现，
  产物仍是纯 SQL，可以直接用 mysql 客户端执行，不依赖 Python。

用法：
  python db/tools/gen_demo_sql.py
"""

from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "db" / "01-schema.sql"
OUTPUT = ROOT / "db" / "02-schema-with-demo.sql"

# ---------------------------------------------------------------- 基础数据

IPS = [
    {"code": "STARLINE", "cn": "星轨列车", "en": "STARLINE", "sort": 1},
    {"code": "MISTPORT", "cn": "雾港侦探", "en": "MISTPORT", "sort": 2},
    {"code": "ONIGIRI", "cn": "饭团小馆", "en": "ONIGIRI", "sort": 3},
    {"code": "IRONWOOD", "cn": "机械之森", "en": "IRONWOOD", "sort": 4},
    {"code": "CICADA", "cn": "夏夜蝉鸣", "en": "CICADA", "sort": 5},
]

# (用户名, 显示名, 角色, 状态, 邮箱, BCrypt 散列) —— 明文均为 Cartoon123!
USERS = [
    ("alice", "爱丽丝", "CUSTOMER", "ENABLED", "alice@example.com",
     "$2a$10$j/75MLefRUs.J6CnH/rosemt5nzlyPmP8ZqxiTvOHWGQx6yIhAB96"),
    ("bob", "鲍勃", "CUSTOMER", "ENABLED", "bob@example.com",
     "$2a$10$FLGRU.yNTnPKj7C2Sh0.c.p0//xTFpIiXw2j8AwQMTOLgB.HBTLbW"),
    ("carol", "卡萝", "CUSTOMER", "DISABLED", "carol@example.com",
     "$2a$10$55myfBkMkG8w08nnWw4.y.glTUMWdfUNJFBdNFDuLVJK9uM5GWQUK"),
    ("admin", "店长", "ADMIN", "ENABLED", "admin@example.com",
     "$2a$10$hJrmVNne3wWlQhSr/ndtyemGq9r00m4n2R6UhRk1eIews6E4Gr0u2"),
]

VOLUME_PRICE = [28, 28, 30, 30, 32, 34]
VOLUME_SUM = [
    "故事从一列永不到站的列车开始。",
    "车窗外是不断被重写的星空。",
    "乘客名单上出现了自己的名字。",
    "终点站原来根本没有站台。",
    "有人在中途下了车，再也没回来。",
    "最后一卷，列车驶入黎明。",
]

# 周边：按行业通用品类分组（谷子/电商口径），名称里的 {w} 会替换成作品名
MERCH_GROUPS = [
    ("BADGE", "徽章", 2),
    ("STAND", "亚克力立牌", 3),
    ("CHARM", "挂件钥匙扣", 4),
    ("PAPER", "色纸明信片", 5),
    ("TICKET", "镭射票", 6),
    ("PLUSH", "毛绒抱枕", 7),
    ("FIGURE", "手办模型", 8),
    ("ARTBOOK", "画集设定集", 9),
    ("APPAREL", "服装包袋", 10),
    ("STATIONERY", "文具杂货", 11),
]

# 每个品类 1-2 件：(名称模板, 价格, 库存, 状态)
MERCH = {
    "BADGE": [("{w}主角吧唧", 25, 30, "ON_SALE"), ("{w}金属徽章套装", 45, 18, "ON_SALE")],
    "STAND": [("{w}主角亚克力立牌", 79, 15, "ON_SALE"), ("{w}场景立牌", 89, 9, "ON_SALE")],
    "CHARM": [("{w}亚克力挂件", 39, 25, "ON_SALE"), ("{w}金属钥匙扣", 45, 12, "ON_SALE")],
    "PAPER": [("{w}明信片组", 25, 20, "ON_SALE")],
    "TICKET": [("{w}镭射票", 35, 0, "ON_SALE")],
    "PLUSH": [("{w}角色抱枕", 99, 6, "ON_SALE")],
    "FIGURE": [("{w}拼装模型", 169, 4, "ON_SALE")],
    "ARTBOOK": [("{w}官方画集", 118, 5, "ON_SALE")],
    "APPAREL": [("{w}主题T恤", 89, 0, "ON_SALE")],
    "STATIONERY": [("{w}线圈笔记本", 29, 22, "ON_SALE")],
}

# 二级分类：品类代码 → (id 偏移, 名称, 排序)

# 让「新品上架」与「精选推荐」各有确定的样例
NEWEST_AGE = {"CICADA-M04": 0, "ONIGIRI-C04": 1, "MISTPORT-M05": 2}
FEATURED_SORT = {"STARLINE-C01": 1, "MISTPORT-C01": 2, "IRONWOOD-M01": 3, "CICADA-C05": 4}
# 库存边界：上架商品里 3 件售罄、2 件只剩 1 件
VOLUME_STOCK = {"STARLINE-C02": 1, "MISTPORT-C03": 0}

CATEGORY_IDS = {ip["code"]: (i + 1) * 1 for i, ip in enumerate(IPS)}  # 1..5


def esc(text: str) -> str:
    """单引号转义，避免名称里的引号破坏 SQL。"""
    return text.replace("\\", "\\\\").replace("'", "''")


def build_products():
    rows = []
    for idx, ip in enumerate(IPS):
        comic_id = 100 + idx * 11
        for n in range(1, 7):
            sku = f"{ip['code']}-C{n:02d}"
            status = "DRAFT" if n == 6 else "ON_SALE"
            rows.append({
                "sku": sku,
                "cat": comic_id,
                "name": f"《{ip['cn']}》第{n}卷",
                "summary": VOLUME_SUM[n - 1],
                "desc": f"《{ip['cn']}》第{n}卷正文，官方单行本。教学演示数据，非真实出版物。",
                "price": VOLUME_PRICE[n - 1],
                "stock": 0 if status == "DRAFT" else VOLUME_STOCK.get(sku, 11 + n),
                "status": status,
                "sort": FEATURED_SORT.get(sku, 100),
                "age": NEWEST_AGE.get(sku, 5 + idx * 6 + n),
            })
        j = 0
        for group, _cname, order in MERCH_GROUPS:
            for tmpl, price, stock, status in MERCH[group]:
                j += 1
                sku = f"{ip['code']}-M{j:02d}"
                name = tmpl.format(w=ip["cn"])
                rows.append({
                    "sku": sku,
                    "cat": 100 + idx * 11 + order,
                    "name": f"《{ip['cn']}》{name}",
                    "summary": "官方授权周边，随书附赠收藏卡。",
                    "desc": f"{ip['cn']}「{name}」，官方授权周边。材质与规格以实物为准，教学演示数据。",
                    "price": price,
                    "stock": stock,
                    "status": status,
                    "sort": FEATURED_SORT.get(sku, 100),
                    "age": NEWEST_AGE.get(sku, 5 + idx * 6 + j + 2),
                })
    return rows


def main() -> None:
    schema_text = SCHEMA.read_text(encoding="utf-8")
    marker = "-- ===== 七张表的统一结构开始 ====="
    ddl = schema_text[schema_text.index(marker):]

    out = []
    out.append("-- MyCartoonStore 实验一：表结构与演示数据（独立完整脚本）")
    out.append("-- 由 db/tools/gen_demo_sql.py 生成，请勿手工修改；改数据请改生成器后重跑。")
    out.append("-- 与 01-schema.sql 二选一，不按编号依次执行；此文件已包含完整表结构。")
    out.append("-- 在空的 MyCartoonStore 库中首次执行；不 DROP、不清空、不覆盖已有表。")
    out.append("-- 教学账号统一明文 Cartoon123!，BCrypt cost=10，每个账号独立盐。")
    out.append("-- 演示数据仅用于教学，商品、订单、金额均为虚构。")
    out.append("")
    out.append("SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    out.append("SET SESSION time_zone = '+08:00';")
    out.append("")
    out.append("CREATE DATABASE IF NOT EXISTS MyCartoonStore")
    out.append("    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;")
    out.append("USE MyCartoonStore;")
    out.append("")
    out.append(ddl.rstrip())
    out.append("")
    out.append("-- ===== 演示数据开始 =====")
    out.append("")

    # ---- 账号
    out.append("-- 1. 教学账号：4 个，其中 carol 为禁用样例")
    out.append("INSERT INTO store_user (username, password_hash, display_name, role, status, email) VALUES")
    out.append(",\n".join(
        f"('{u[0]}', '{u[5]}', '{u[1]}', '{u[2]}', '{u[3]}', '{u[4]}')" for u in USERS
    ) + ";")
    out.append("")

    # ---- 分类
    out.append("-- 2. 分类：5 个一级（作品）+ 10 个二级（单行本 / 官方周边）")
    out.append("INSERT INTO category (id, code, parent_id, name, sort_order) VALUES")
    primary = ",\n".join(
        f"({CATEGORY_IDS[ip['code']]}, '{ip['code']}', NULL, '{ip['cn']}', {ip['sort']})" for ip in IPS
    )
    out.append(primary + ";")
    out.append("")
    out.append("INSERT INTO category (id, code, parent_id, name, sort_order) VALUES")
    secondary = []
    for idx, ip in enumerate(IPS):
        secondary.append(f"({100 + idx * 11}, '{ip['code']}-COMIC', {CATEGORY_IDS[ip['code']]}, '单行本', 1)")
        for group, cname, order in MERCH_GROUPS:
            secondary.append(f"({100 + idx * 11 + order}, '{ip['code']}-{group}', {CATEGORY_IDS[ip['code']]}, '{cname}', {order})")
    out.append(",\n".join(secondary) + ";")
    out.append("")

    # ---- 商品
    products = build_products()
    out.append("-- 3. 商品：60 件 = 5 部作品 ×（6 卷单行本 + 6 件周边）")
    out.append("--    54 上架 / 6 下架；其中 3 件上架售罄、2 件库存为 1（边界样例）")
    out.append("INSERT INTO product (sku, category_id, name, summary, description, cover_image, price, stock, status, sort_order, created_at) VALUES")
    value_rows = []
    for p in products:
        value_rows.append(
            "('{sku}', {cat}, '{name}', '{summary}', '{desc}', 'images/products/{sku}/cover.svg', "
            "{price:.2f}, {stock}, '{status}', {sort}, DATE_SUB(NOW(), INTERVAL {age} DAY))".format(
                sku=p["sku"], cat=p["cat"], name=esc(p["name"]), summary=esc(p["summary"]),
                desc=esc(p["desc"]), price=p["price"], stock=p["stock"], status=p["status"],
                sort=p["sort"], age=p["age"],
            )
        )
    out.append(",\n".join(value_rows) + ";")
    out.append("")
    out.append(render_images_sql())
    out.append("")
    out.append(render_trade_sql())
    out.append("")
    out.append("-- ===== 演示数据结束 =====")
    out.append("")

    OUTPUT.write_text("\n".join(out), encoding="utf-8")
    print(f"written: {OUTPUT}")
    print(f"products: {len(products)}, categories: {55}, users: {len(USERS)}")


def render_images_sql() -> str:
    """相册：每部作品 12 件中，前 4 件 3 张、中 4 件 2 张、后 4 件无相册（共 100 条）。

    product_id 用 SKU 子查询取得，不依赖自增顺序；重复执行会因唯一性无关而重复插入，
    因此脚本只用于空库初始化。
    """
    rows = []
    for idx, p in enumerate(build_products()):
        pos = idx % 12
        count = 3 if pos < 4 else (2 if pos < 8 else 0)
        for n in range(1, count + 1):
            rows.append(
                "((SELECT id FROM product WHERE sku='{sku}'), 'images/products/{sku}/{n}.svg', "
                "'{name} 图{n}', {n})".format(sku=p["sku"], name=p["name"], n=n)
            )
    header = ("-- 4. 相册：100 条 = 20 件×3 张 + 20 件×2 张；另有 20 件无相册（用于回退封面）\n"
              "INSERT INTO product_image (product_id, image_path, alt_text, sort_order) VALUES\n")
    return header + ",\n".join(rows) + ";"


CART = [
    ("alice", "STARLINE-C01", 1),
    ("alice", "MISTPORT-C02", 2),
    ("alice", "ONIGIRI-M03", 1),
    ("alice", "STARLINE-M02", 3),
    ("alice", "STARLINE-C06", 2),   # 已下架，失效条目
    ("alice", "MISTPORT-C03", 5),   # 库存 0，失效条目
    ("bob", "CICADA-C01", 1),
    ("bob", "IRONWOOD-M01", 2),
    ("bob", "ONIGIRI-C02", 1),
    ("bob", "STARLINE-M03", 1),
]

RECEIVER = {
    "alice": ("爱丽丝", "13800000001", "湖南省长沙市岳麓区麓山南路 932 号 3 栋 502"),
    "bob": ("鲍勃", "13900000002", "湖南省长沙市开福区芙蓉中路 88 号 12 楼"),
}

# (订单号, 用户, 状态, 距今天数, [(SKU, 数量)], 是否已支付)
ORDERS = [
    ("DEMO-202611-0001", "alice", "PENDING_PAYMENT", 1, [("STARLINE-C01", 2), ("MISTPORT-M02", 1)]),
    ("DEMO-202611-0002", "alice", "PAID", 2, [("ONIGIRI-C02", 1), ("IRONWOOD-M03", 2)]),
    ("DEMO-202611-0003", "alice", "SHIPPED", 4, [("CICADA-C01", 1), ("STARLINE-M03", 1)]),
    ("DEMO-202611-0004", "alice", "COMPLETED", 7, [("MISTPORT-C01", 2), ("ONIGIRI-M04", 1)]),
    ("DEMO-202611-0005", "alice", "CANCELLED", 3, [("IRONWOOD-C02", 1), ("CICADA-M05", 2)]),
    ("DEMO-202611-0006", "bob", "PENDING_PAYMENT", 1, [("IRONWOOD-C03", 1), ("STARLINE-M04", 2)]),
    ("DEMO-202611-0007", "bob", "PAID", 3, [("CICADA-C02", 2), ("ONIGIRI-C03", 1)]),
    ("DEMO-202611-0008", "bob", "SHIPPED", 5, [("MISTPORT-C04", 1), ("STARLINE-M05", 1)]),
    ("DEMO-202611-0009", "bob", "COMPLETED", 8, [("STARLINE-C03", 1), ("IRONWOOD-M02", 3)]),
    ("DEMO-202611-0010", "bob", "CANCELLED", 2, [("ONIGIRI-C05", 2), ("CICADA-C03", 1)]),
]


def render_trade_sql() -> str:
    """购物车 10 条 + 订单 10 张 + 明细 20 条，并对未取消订单净扣库存。"""
    products = {p["sku"]: p for p in build_products()}
    out = []

    # ---- 购物车
    out.append("-- 5. 购物车：10 条（alice 6 条含 2 条失效，bob 4 条）")
    out.append("INSERT INTO cart_item (user_id, product_id, quantity) VALUES")
    cart_rows = []
    for user, sku, qty in CART:
        cart_rows.append(
            f"((SELECT id FROM store_user WHERE username='{user}'), "
            f"(SELECT id FROM product WHERE sku='{sku}'), {qty})"
        )
    out.append(",\n".join(cart_rows) + ";")
    out.append("")

    # ---- 订单
    out.append("-- 6. 订单：10 张（alice/bob 各 5 张，五种状态各 2 张；6 张带 MOCK 凭证）")
    out.append("--    金额仅商品合计，无运费/优惠；时间满足既有 CHECK 的先后顺序")
    out.append("INSERT INTO sales_order (order_no, user_id, status, total_amount, receiver_name, "
               "receiver_phone, receiver_address, created_at, payment_method, payment_no, "
               "paid_at, cancelled_at, shipped_at, completed_at) VALUES")
    order_rows = []
    for idx, (no, user, status, age, items) in enumerate(ORDERS, start=1):
        name, phone, addr = RECEIVER[user]
        total = sum(products[sku]["price"] * qty for sku, qty in items)
        created = f"DATE_SUB(NOW(), INTERVAL {age} DAY)"
        paid = f"({created} + INTERVAL 1 HOUR)"
        shipped = f"({paid} + INTERVAL 2 DAY)"
        completed = f"({shipped} + INTERVAL 1 DAY)"
        cancelled = f"({created} + INTERVAL 1 HOUR)"
        method = "NULL"
        pno = "NULL"
        paid_at = "NULL"
        cancel_at = "NULL"
        ship_at = "NULL"
        complete_at = "NULL"
        if status in ("PAID", "SHIPPED", "COMPLETED"):
            method = "'MOCK'"
            pno = f"'PAY20261115{idx:04d}'"
            paid_at = paid
        if status == "SHIPPED":
            ship_at = shipped
        if status == "COMPLETED":
            ship_at = shipped
            complete_at = completed
        if status == "CANCELLED":
            cancel_at = cancelled
        order_rows.append(
            f"('{no}', (SELECT id FROM store_user WHERE username='{user}'), '{status}', {total:.2f}, "
            f"'{name}', '{phone}', '{addr}', {created}, {method}, {pno}, {paid_at}, {cancel_at}, "
            f"{ship_at}, {complete_at})"
        )
    out.append(",\n".join(order_rows) + ";")
    out.append("")

    # ---- 明细
    out.append("-- 7. 明细：20 条，保存成交快照；line_amount = unit_price × quantity")
    out.append("INSERT INTO sales_order_item (order_id, product_id, sku_snapshot, product_name_snapshot, "
               "cover_image_snapshot, unit_price, quantity, line_amount) VALUES")
    item_rows = []
    for no, _user, _status, _age, items in ORDERS:
        for sku, qty in items:
            p = products[sku]
            item_rows.append(
                f"((SELECT id FROM sales_order WHERE order_no='{no}'), "
                f"(SELECT id FROM product WHERE sku='{sku}'), '{sku}', '{p['name']}', "
                f"'images/products/{sku}/cover.svg', {p['price']:.2f}, {qty}, {p['price'] * qty:.2f})"
            )
    out.append(",\n".join(item_rows) + ";")
    out.append("")

    # ---- 净扣库存
    deduct = {}
    for _no, _user, status, _age, items in ORDERS:
        if status == "CANCELLED":
            continue
        for sku, qty in items:
            deduct[sku] = deduct.get(sku, 0) + qty
    out.append("-- 8. 净扣库存：只对未取消订单扣减，取消订单视为已恢复；版本号同步 +1")
    for sku, qty in sorted(deduct.items()):
        out.append(f"UPDATE product SET stock = stock - {qty}, version = version + 1 WHERE sku = '{sku}';")
    return "\n".join(out)


if __name__ == "__main__":
    main()