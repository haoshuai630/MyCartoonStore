"""M09-02 真实容器取证：页面隐藏域、无令牌 403 页面、静态资源不建会话。

用法：python db/tools/m09_csrf_probe.py user|admin
  user  → http://127.0.0.1:18080（登录页 /login，静态 /css/store.css）
  admin → http://127.0.0.1:18081/admin（登录页 /admin/login，静态 /admin/css/admin.css）

产物：reports/evidence/m09-ac31-csrf-<端>-probe.txt 与 403 页面快照。
"""
from __future__ import annotations

import http.cookiejar
import pathlib
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

TOKEN = re.compile(r'name="_csrf" value="([^"]+)"')

SIDES = {
    "user": {
        "base": "http://127.0.0.1:18080",
        "login": "/login",
        "css": "/css/store.css",
        "forbidden": "请求未被接受",
        "username": "alice",
    },
    "admin": {
        "base": "http://127.0.0.1:18081/admin",
        "login": "/login",
        "css": "/css/admin.css",
        "forbidden": "操作被拒绝",
        "username": "admin",
    },
}


def opener() -> urllib.request.OpenerDirector:
    jar = http.cookiejar.CookieJar()  # 保持会话 Cookie，才能验证令牌来自同一个会话。
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def call(op, url: str, data: dict | None = None):
    body = None if data is None else urllib.parse.urlencode(data).encode()
    request = urllib.request.Request(url, data=body, method="GET" if data is None else "POST")
    request.add_header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")  # 要页面，错误控制器才走 HTML 分支。
    try:
        with op.open(request, timeout=10) as response:
            return response.status, response.read().decode("utf-8", "replace"), response.headers
    except urllib.error.HTTPError as error:  # 4xx 也抛，正好拿状态码与页面。
        return error.code, error.read().decode("utf-8", "replace"), error.headers


def main() -> int:
    side = sys.argv[1] if len(sys.argv) > 1 else "user"
    spec = SIDES[side]
    out = pathlib.Path("reports/evidence")
    out.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    op = opener()

    status, html, _ = call(op, spec["base"] + spec["login"])  # 打开登录页。
    match = TOKEN.search(html)
    token = match.group(1) if match else ""
    lines.append(f"GET  {spec['login']:<12} status={status} token={'yes' if match else 'NO'}")
    (out / f"m09-ac31-{side}-login.html").write_text(html, encoding="utf-8")  # 留一份带令牌的表单页。

    status, html, _ = call(op, spec["base"] + spec["login"],
                           {"username": spec["username"], "password": "Cartoon123!"})  # 故意不带令牌。
    lines.append(f"POST {spec['login']:<12} status={status} page={spec['forbidden'] if spec['forbidden'] in html else 'NO-403-PAGE'}")
    (out / f"m09-ac31-{side}-403.html").write_text(html, encoding="utf-8")  # 403 页面快照。

    status, _, headers = call(op, spec["base"] + spec["css"])  # 静态资源：不参与令牌流程。
    lines.append(f"GET  {spec['css']:<17} status={status} setCookie={headers.get('Set-Cookie') is not None}")

    status, html, _ = call(op, spec["base"] + spec["login"],
                           {"username": spec["username"], "password": "wrong", "_csrf": token})  # 令牌合法、密码错。
    lines.append(f"POST {spec['login']:<12} status={status} formError={'账号或密码错误' in html} (valid token)")

    status, html, _ = call(op, spec["base"] + spec["login"])  # 同一会话再打开。
    again = TOKEN.search(html)
    lines.append(f"GET  {spec['login']:<12} status={status} tokenStable={bool(again) and again.group(1) == token}")

    text = "\n".join(lines)
    (out / f"m09-ac31-csrf-{side}-probe.txt").write_text(text + "\n", encoding="utf-8")
    print(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
