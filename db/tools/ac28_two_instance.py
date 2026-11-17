"""AC-28 两端并发取证：用户买 2 件 与 后台补 3 件 同时发生，初始库存 10 → 终态 11。

前置：
  1. 用户端 :18080、管理端 :18081/admin 各由一个独立 JVM 提供（两个实例，不是单进程两次调用）；
  2. MySQL 可访问，演示库 MyCartoonStore 已初始化。
用法：python db/tools/ac28_two_instance.py [sku]
产物：reports/evidence/m09-ac28-two-instance.txt（控制台同款）与 m09-ac28-before-after.sql
"""
from __future__ import annotations

import http.cookiejar
import pathlib
import re
import subprocess
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

MYSQL = r"C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe"
DB = "MyCartoonStore"
USER_BASE = "http://127.0.0.1:18080"
ADMIN_BASE = "http://127.0.0.1:18081/admin"
CUSTOMER = "ac28"          # 专用账号：不动种子里的 alice / bob。
PASSWORD = "Cartoon123!"
TOKEN = re.compile(r'name="_csrf" value="([^"]+)"')
OUT = pathlib.Path("reports/evidence")


def sql(query: str) -> str:
    """执行一条 SQL，返回批处理格式的纯文本结果。"""
    result = subprocess.run(
        [MYSQL, "--host=127.0.0.1", "--port=3306", "--user=root", "--password=123456",
         "-N", "-B", "-D", DB, "-e", query],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def browser() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()  # 每个"浏览器"一份会话。
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def call(op, base: str, path: str, data: dict | None = None):
    body = None if data is None else urllib.parse.urlencode(data).encode()
    request = urllib.request.Request(base + path, data=body, method="GET" if data is None else "POST")
    request.add_header("Accept", "text/html,*/*;q=0.8")  # 要页面，不要 JSON。
    try:
        with op.open(request, timeout=15) as response:
            return response.status, response.read().decode("utf-8", "replace"), response.geturl()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace"), error.geturl()


def token_of(op, base: str, path: str) -> str:
    _, html, _ = call(op, base, path)  # 读页面拿令牌。
    match = TOKEN.search(html)
    return match.group(1) if match else ""


def main() -> int:
    sku = sys.argv[1] if len(sys.argv) > 1 else "CICADA-C01"
    lines: list[str] = []

    def note(text: str) -> None:
        lines.append(text)
        print(text)

    # 0. 清理上次运行与准备基线库存。
    sql(
        "DELETE c FROM cart_item c JOIN store_user u ON c.user_id=u.id WHERE u.username='ac28';"
        "DELETE i FROM sales_order_item i JOIN sales_order o ON i.order_id=o.id "
        "  JOIN store_user u ON o.user_id=u.id WHERE u.username='ac28';"
        "DELETE o FROM sales_order o JOIN store_user u ON o.user_id=u.id WHERE u.username='ac28';"
        "DELETE FROM store_user WHERE username='ac28';"
        f"UPDATE product SET stock=10 WHERE sku='{sku}';"
    )
    product_id, stock0 = sql(f"SELECT id, stock FROM product WHERE sku='{sku}'").split("\t")
    note(f"[setup]   product {sku} id={product_id} stock={stock0}（基线）")

    # 1. 用户端：注册 → 登录 → 加车 2 件 → 打开结算页。
    user = browser()
    status, _, _ = call(user, USER_BASE, "/register", {
        "_csrf": token_of(user, USER_BASE, "/register"),
        "username": CUSTOMER, "displayName": "AC28 顾客",
        "password": PASSWORD, "confirmPassword": PASSWORD,
    })
    note(f"[user]    注册 {CUSTOMER} → {status}")
    status, _, _ = call(user, USER_BASE, "/login", {
        "_csrf": token_of(user, USER_BASE, "/login"),
        "username": CUSTOMER, "password": PASSWORD,
    })
    note(f"[user]    登录 → {status}")
    status, _, _ = call(user, USER_BASE, "/cart/items", {
        "_csrf": token_of(user, USER_BASE, f"/product/{product_id}"),
        "productId": product_id, "quantity": "2",
    })
    note(f"[user]    加入购物车 2 件 → {status}")
    status, _, _ = call(user, USER_BASE, "/checkout")
    note(f"[user]    结算页 → {status}")

    # 2. 管理端：登录（另一个实例、另一份会话）。
    admin = browser()
    status, _, _ = call(admin, ADMIN_BASE, "/login", {
        "_csrf": token_of(admin, ADMIN_BASE, "/login"),
        "username": "admin", "password": PASSWORD,
    })
    note(f"[admin]   登录 → {status}")

    # 3. 并发起点：用户提交订单 与 后台补货 同时发出。
    barrier = threading.Barrier(2)  # 两个线程在同一时刻放行。
    outcome: dict[str, tuple] = {}

    def place_order() -> None:
        token = token_of(user, USER_BASE, "/checkout")  # 结算页的令牌。
        barrier.wait()
        outcome["order"] = call(user, USER_BASE, "/checkout", {
            "_csrf": token,
            "receiverName": "AC28", "receiverPhone": "13800000000",
            "receiverAddress": "并发取证地址 1 号",
        })

    def restock() -> None:
        token = token_of(admin, ADMIN_BASE, "/products")  # 列表页的令牌。
        barrier.wait()
        outcome["restock"] = call(admin, ADMIN_BASE, f"/products/{product_id}/restock", {
            "_csrf": token, "quantity": "3",
        })

    threads = [threading.Thread(target=place_order), threading.Thread(target=restock)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    order_status, _, order_url = outcome["order"]
    restock_status, _, restock_url = outcome["restock"]
    note(f"[race]    下单 POST → {order_status} {order_url}")
    note(f"[race]    补货 POST → {restock_status} {restock_url}")

    # 4. 终态核对。
    stock1 = sql(f"SELECT stock FROM product WHERE sku='{sku}'")
    order_row = sql(
        "SELECT o.order_no, o.status, o.total_amount, SUM(i.quantity) FROM sales_order o "
        "JOIN store_user u ON o.user_id=u.id JOIN sales_order_item i ON i.order_id=o.id "
        f"WHERE u.username='{CUSTOMER}' GROUP BY o.id ORDER BY o.id DESC LIMIT 1"
    )
    note(f"[after]   库存 stock={stock1}（期望 11 = 10 - 2 + 3）")
    note(f"[after]   订单 {order_row}")

    passed = (stock1 == "11" and "/orders/" in order_url and restock_url.endswith("/products")
              and order_row.split("\t")[-1] == "2")  # urllib 自动跟了 302，最终 URL 才是判据。
    note(f"RESULT: {'PASS' if passed else 'FAIL'}")

    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "m09-ac28-two-instance.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (OUT / "m09-ac28-before-after.sql").write_text(
        f"-- AC-28 前后对照\n-- 基线：UPDATE product SET stock=10 WHERE sku='{sku}';\n"
        f"-- 终态：SELECT stock FROM product WHERE sku='{sku}';  -- {stock1}\n"
        f"-- 订单：{order_row}\n", encoding="utf-8")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
