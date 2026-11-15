"""生成站内 SVG 素材：60 个商品封面 + 相册图 + logo。

产物直接写进 cartoon-user-web 的 static 目录，不依赖外部图片，也没有版权问题。
命名与数据库里的路径严格一致：
    cover_image        = images/products/{sku}/cover.svg
    product_image.path = images/products/{sku}/{n}.svg
"""

from __future__ import annotations

import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

from gen_demo_sql import IPS, build_products  # noqa: E402

ROOT = pathlib.Path(__file__).resolve().parents[2]
STATIC = ROOT / "cartoon-user-web" / "src" / "main" / "resources" / "static"
IMAGES = STATIC / "images"

PALETTE = {
    "STARLINE": ("#1B3B6F", "#4FC3F7"),
    "MISTPORT": ("#2F4858", "#A7C4BC"),
    "ONIGIRI": ("#B85C24", "#F6D9A8"),
    "IRONWOOD": ("#2E5E4E", "#D6B25E"),
    "CICADA": ("#4B3F72", "#F28FB1"),
}


def cover_svg(ip_code: str, title: str, subtitle: str, seed: int) -> str:
    bg, fg = PALETTE[ip_code]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="600" height="800" viewBox="0 0 600 800">
<rect width="600" height="800" fill="{bg}"/>
<circle cx="{430 + seed % 40}" cy="{150 + seed % 60}" r="140" fill="{fg}" opacity="0.20"/>
<path d="M0 560 L600 430 L600 520 L0 650 Z" fill="#D93A2B" opacity="0.9"/>
<text x="46" y="96" font-family="Arial" font-size="20" fill="{fg}" letter-spacing="6">{ip_code}</text>
<text x="44" y="345" font-family="Microsoft YaHei, sans-serif" font-size="82" font-weight="900" fill="#ffffff">{title}</text>
<text x="44" y="420" font-family="Microsoft YaHei, sans-serif" font-size="44" font-weight="700" fill="{fg}">{subtitle}</text>
<text x="48" y="612" font-family="Microsoft YaHei, sans-serif" font-size="26" font-weight="700" fill="#ffffff">{subtitle}</text>
<rect width="600" height="800" fill="none" stroke="#16130F" stroke-width="14"/>
<circle cx="512" cy="700" r="46" fill="#F2C14E" stroke="#16130F" stroke-width="6"/>
<text x="512" y="708" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="16" font-weight="900" fill="#16130F">正版</text>
</svg>
"""


def gallery_svg(ip_code: str, label: str, index: int) -> str:
    bg, fg = PALETTE[ip_code]
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="600" height="600" viewBox="0 0 600 600">
<rect width="600" height="600" fill="{bg}"/>
<rect x="30" y="30" width="540" height="540" fill="none" stroke="{fg}" stroke-width="4" opacity="0.6"/>
<text x="300" y="250" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="54" font-weight="900" fill="#ffffff">{label}</text>
<text x="300" y="330" text-anchor="middle" font-family="Arial" font-size="22" fill="{fg}" letter-spacing="4">VIEW {index}</text>
<rect width="600" height="600" fill="none" stroke="#16130F" stroke-width="10"/>
</svg>
"""


def logo_svg() -> str:
    return """<svg xmlns="http://www.w3.org/2000/svg" width="120" height="120" viewBox="0 0 120 120">
<rect width="120" height="120" fill="#D93A2B"/>
<rect x="8" y="8" width="104" height="104" fill="none" stroke="#16130F" stroke-width="6"/>
<text x="60" y="80" text-anchor="middle" font-family="Microsoft YaHei, sans-serif" font-size="56" font-weight="900" fill="#ffffff">漫</text>
</svg>
"""


def main() -> None:
    products = build_products()
    counts = {"cover": 0, "gallery": 0}
    for idx, p in enumerate(products):
        ip_code = p["sku"].split("-")[0]
        folder = IMAGES / "products" / p["sku"]
        folder.mkdir(parents=True, exist_ok=True)
        m = re.match(r"《(.+?)》(.*)", p["name"])
        title = m.group(1) if m else p["name"]
        subtitle = m.group(2) if m else ""

        (folder / "cover.svg").write_text(
            cover_svg(ip_code, title, subtitle, idx), encoding="utf-8")
        counts["cover"] += 1
        pos = idx % 12
        gallery = 3 if pos < 4 else (2 if pos < 8 else 0)
        for n in range(1, gallery + 1):
            (folder / f"{n}.svg").write_text(gallery_svg(ip_code, subtitle, n), encoding="utf-8")
            counts["gallery"] += 1

    (IMAGES / "logo.svg").write_text(logo_svg(), encoding="utf-8")
    print(f"covers={counts['cover']} gallery={counts['gallery']} total={sum(counts.values()) + 1}")


if __name__ == "__main__":
    main()
