#!/usr/bin/env python3
"""生成逐行验收 HTML（格式对齐 模块10_逐行验收.html）。

- 行号连续，空行渲染为 gap 行
- 左列完整源码（深色底，注释行绿字）
- 右列该行的简短说明
- 说明来自 EXPLAIN 字典；数据行（IPS/USERS/MERCH/商品 INSERT）按规则解析生成

用法：python db/tools/gen_acceptance.py
"""

from __future__ import annotations

import html
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
PY_FILE = ROOT / "db" / "tools" / "gen_demo_sql.py"
SQL_FILE = ROOT / "db" / "02-schema-with-demo.sql"
OUT = ROOT / "reports" / "M01-02-逐行验收.html"
SCHEMA_FILE = ROOT / "db" / "01-schema.sql"
OUT_SCHEMA = ROOT / "reports" / "M01-01-逐行验收.html"
OUT_M03 = ROOT / "reports" / "M01-03-逐行验收.html"

STYLE = """<style>
*{box-sizing:border-box}body{margin:0;background:#edf1f7;color:#182333;font:15px/1.7 "Microsoft YaHei",system-ui,sans-serif}
header{position:sticky;top:0;z-index:2;background:white;border-bottom:1px solid #cad3df;padding:18px 30px}
h1{font-size:24px;margin:0}header p{color:#536176;margin:6px 0 10px}
nav{display:flex;gap:10px;flex-wrap:wrap}nav a{background:#eaf2ff;border:1px solid #b7cce9;color:#14558c;border-radius:5px;padding:5px 12px;text-decoration:none}
main{max-width:1500px;margin:auto;padding:18px 30px 46px}.note{padding:12px 16px;background:white;border-left:4px solid #2677ba;color:#536176}
section{margin:22px 0 36px}h2{font-size:20px;margin:0 0 3px}small{margin-left:12px;font-size:13px;font-weight:400;color:#536176}
.path{font:13px/1.5 Consolas,monospace;color:#536176;margin:0 0 10px;overflow-wrap:anywhere}
table{border-collapse:collapse;width:100%;table-layout:fixed;border:1px solid #bcc9da}
th{background:#dae3ef;text-align:left;padding:9px 12px;font-size:13px}th:first-child{text-align:center}
td{vertical-align:top;padding:5px 12px}.line{background:#121d30;color:#9badc7;text-align:right;font:13px/1.7 Consolas,monospace}
.code{background:#182337;color:#f3f6fd}pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;font:13.5px/1.6 Consolas,"Microsoft YaHei",monospace;tab-size:4}
pre.cmtline{color:#8fd0a8}
.explain{background:#fff2cf;color:#653d10;border-left:4px solid #e4ae49;font-size:13px;line-height:1.7}
.kw{color:#d5b9ff}.gap td{height:7px;padding:0}
tbody tr{transition:background .08s}
tbody tr:hover .code{background:#24406b}
tbody tr:hover .explain{background:#ffe08a;color:#3d2400}
tbody tr:hover .line{background:#1d3350;color:#d6e4ff}
tbody tr:focus{outline:2px solid #2677ba;outline-offset:-2px}
footer{color:#536176;font-size:13px}
</style>"""

PY_KW = {
    "def", "return", "for", "in", "if", "else", "elif", "import", "from", "as",
    "class", "with", "not", "and", "or", "None", "True", "False", "lambda", "yield",
}
SQL_KW = {
    "CREATE", "TABLE", "DATABASE", "USE", "INSERT", "INTO", "VALUES", "SELECT",
    "FROM", "WHERE", "JOIN", "ON", "AND", "OR", "NOT", "NULL", "PRIMARY", "KEY",
    "UNIQUE", "FOREIGN", "REFERENCES", "CHECK", "CONSTRAINT", "DEFAULT", "COMMENT",
    "ENGINE", "CHARSET", "COLLATE", "SET", "NAMES", "SESSION", "IF", "EXISTS",
    "GENERATED", "ALWAYS", "AS", "STORED", "AUTO_INCREMENT", "NOT", "UPDATE",
}


def esc(text: str) -> str:
    return html.escape(text, quote=False)


def hi_py(line: str) -> str:
    out = []
    for token in re.split(r"(\s+|[(),.[\]{}:=]|![=]|==)", line):
        if token.strip() in PY_KW:
            out.append(f'<span class="kw">{esc(token)}</span>')
        else:
            out.append(esc(token))
    return "".join(out)


def hi_sql(line: str) -> str:
    out = []
    for token in re.split(r"(\s+|[(),;=]|!=)", line):
        if token.strip().upper() in SQL_KW and not token.strip().startswith("'"):
            out.append(f'<span class="kw">{esc(token)}</span>')
        else:
            out.append(esc(token))
    return "".join(out)


# ---------------------------------------------------------------- 说明字典

EXPLAIN: dict[str, dict[int, str]] = {"py": {}, "sql": {}}

PY = {
    1: "允许 ./gen_demo_sql.py 直接执行，不依赖调用方式。",
    2: "模块 docstring：产物路径与用途。",
    3: "",
    4: "说明为什么要用脚本生成，避免后来者当成多余文件删掉。",
    5: "列出三条强规则：SKU 模板、库存边界、状态分布。",
    6: "手写 60 行 INSERT 时，数量与边界最容易对不上。",
    7: "强调产物是纯 SQL：执行不需要 Python。",
    8: "",
    9: "用法。",
    10: "唯一入口命令。",
    11: "docstring 结束。",
    13: "延迟注解求值，兼容低版本 Python。",
    15: "只用标准库，不引入第三方依赖。",
    17: "以 __file__ 定位仓库根：从任何工作目录运行都指向同一处。",
    18: "DDL 唯一来源：02 的建表语句从这里摘录，避免两份手抄漂移。",
    19: "输出路径固定，重跑即覆盖。",
    21: "数据区开始。",
    23: "五部作品；列表顺序决定一级分类 id（1–5）与首页顺序。",
    24: "第 1 部：星轨列车，code 用作 SKU 与分类编码前缀。",
    25: "第 2 部：雾港侦探。",
    26: "第 3 部：饭团小馆。",
    27: "第 4 部：机械之森。",
    28: "第 5 部：夏夜蝉鸣。",
    29: "列表结束。",
    30: "",
    31: "账号区开始。",
    32: "6 元组：用户名、显示名、角色、状态、邮箱、BCrypt 散列。",
    33: "",
    34: "",
    35: "",
    36: "",
    37: "",
    38: "",
    39: "",
    40: "",
    41: "账号列表结束。",
    42: "",
    43: "6 卷价格：后两卷略高，覆盖不同价位。",
    44: "每卷一句简介，避免 60 个商品共用同一句。",
    45: "",
    46: "",
    47: "",
    48: "",
    49: "",
    50: "",
    51: "简介列表结束。",
    52: "",
    53: "周边区开始：键为作品 code，值为 (名称, 价格, 库存, 状态)。",
    54: "周边数据。",
    55: "",
    56: "",
    57: "",
    58: "",
    59: "",
    60: "",
    61: "",
    62: "",
    63: "",
    64: "",
    65: "",
    66: "",
    67: "",
    68: "",
    69: "",
    70: "",
    71: "",
    72: "",
    73: "",
    74: "",
    75: "",
    76: "",
    77: "",
    78: "",
    79: "",
    80: "",
    81: "",
    82: "",
    83: "",
    84: "",
    85: "",
    86: "",
    87: "",
    88: "",
    89: "",
    90: "",
    91: "",
    92: "",
    93: "",
    94: "",
    95: "周边数据结束。",
    97: "验收所需的样例分布，集中放在一处，改样例时不用翻 60 行数据。",
    98: "三条最新：CICADA-M04 / ONIGIRI-C04 / MISTPORT-M05，用于首页「新品上架」。",
    99: "四条精选，数值越小越靠前；用于首页「精选推荐」。",
    101: "单行本库存覆盖：制造「库存 1」与「售罄」两个边界。",
    103: "一级分类 id = 1–5，与 IPS 顺序绑定。",
    106: "SQL 字符串转义。",
    107: "",
    108: "先转反斜杠再转单引号，顺序不能反；MySQL 把 '' 当转义单引号。",
    111: "返回 60 条 dict，与 SQL 输出解耦，M01-03 可复用。",
    112: "",
    113: "idx 同时用于分类 id 与时间偏移。",
    114: "单行本二级分类：100/102/104/106/108（偶数）。",
    115: "周边二级分类：101/103/105/107/109（奇数）。",
    116: "每部作品六卷。",
    117: "两位补零，保证字符串排序与卷次一致。",
    118: "第 6 卷统一下架。",
    119: "",
    120: "",
    121: "一定落在二级分类上。",
    122: "书名号 + 卷次。",
    123: "",
    124: "声明演示数据，避免被当成真实出版物。",
    125: "",
    126: "下架库存 0；上架默认 12–17，两件由覆盖表指定。",
    127: "",
    128: "默认 100：精选按升序取前 4，默认值必须大于 4。",
    129: "距今天数，保证有唯一的三条最新。",
    130: "",
    131: "周边数据自带库存与状态，避免再写分支。",
    132: "",
    133: "",
    134: "",
    135: "",
    136: "作品名 + 空格 + 周边名。",
    137: "",
    138: "",
    139: "",
    140: "直接取数据。",
    141: "",
    142: "与单行本共用精选规则。",
    143: "+2 让周边时间与单行本错开。",
    144: "",
    145: "",
    148: "输出入口。",
    149: "显式 UTF-8：Windows 默认 GBK 会把中文读乱。",
    150: "用注释行作分割标记，比按行号截取稳定。",
    151: "从这里截到文件尾，正好是完整 DDL。",
    153: "逐行拼装，避免多处 print 造成顺序错乱。",
    154: "第一行说明这是独立脚本。",
    155: "防止有人直接改 02，下次重跑被覆盖。",
    156: "与 01 的「二选一」口径一致。",
    157: "空库执行、不覆盖的约定。",
    158: "交代教学口令，方便验收。",
    159: "虚构声明。",
    160: "",
    161: "客户端字符集与排序规则。",
    162: "业务时间按东八区解释。",
    163: "",
    164: "建库。",
    165: "字符集与排序规则，与 01 一致。",
    166: "后续 DDL 与数据都落在这个库。",
    167: "",
    168: "rstrip 去掉尾部空白，保证下一行只有一个空行。",
    169: "",
    170: "数据段起点，便于人眼定位。",
    171: "",
    174: "账号段标题。",
    175: "不写 id 交给自增；显式列名，防表结构变化时错位。",
    176: "用「逗号+换行」连接，产出一行一个元组。",
    177: "元组顺序与列名对齐：用户名、散列、显示名、角色、状态、邮箱。",
    178: "末尾补分号。",
    179: "",
    182: "分类段标题。",
    183: "这里写 id：商品要引用，必须固定。",
    184: "",
    185: "一级分类：parent_id 为 NULL。",
    186: "",
    187: "",
    189: "二级分类单独一条 INSERT，便于阅读。",
    190: "",
    191: "",
    192: "偶数 id，code 以 -COMIC 结尾（类型判定依据）。",
    193: "奇数 id，-MERCH 结尾。",
    194: "",
    198: "生成 60 条商品。",
    199: "商品段标题。",
    200: "把验收依赖的分布写进脚本，改数据时不会忘。",
    201: "列名显式，顺序与下面模板一一对应。",
    202: "",
    203: "",
    204: "",
    205: "前 6 列；封面路径按 SKU 拼，一商品一图。",
    206: "价格两位小数；created_at 用相对时间，任何一天跑都合理。",
    207: "文本字段统一过 esc。",
    208: "",
    209: "",
    210: "",
    211: "",
    212: "",
    214: "与第 170 行呼应。",
    217: "写文件，UTF-8 无 BOM。",
    218: "输出路径，便于确认写到了哪。",
    219: "打印数量，跑完立刻能对账。",
    222: "可被 import 而不执行。",
    223: "",
}
EXPLAIN["py"].update(PY)

# 逐行补齐：数据续行、空行输出、拼装语句
PY2 = {
    34: "散列续行：$2a$10$ 前缀表示 BCrypt cost=10，盐已内置在串里。",
    36: "散列续行（bob，独立盐）。",
    38: "散列续行（carol，独立盐）。",
    40: "散列续行（admin，独立盐）。",
    45: "第 1 卷的一句话简介。",
    46: "第 2 卷简介。",
    47: "第 3 卷简介。",
    48: "第 4 卷简介。",
    49: "第 5 卷简介。",
    50: "第 6 卷（完结卷）简介。",
    55: "星轨列车：6 件周边。",
    62: "星轨列车组结束。",
    63: "雾港侦探：6 件周边。",
    70: "雾港侦探组结束。",
    71: "饭团小馆：6 件周边。",
    78: "饭团小馆组结束。",
    79: "机械之森：6 件周边。",
    86: "机械之森组结束。",
    87: "夏夜蝉鸣：6 件周边。",
    94: "夏夜蝉鸣组结束。",
    100: "库存边界常量：3 件售罄、2 件只剩 1 件。",
    107: "docstring：单引号转义，防止名称里的引号破坏 SQL。",
    112: "结果容器。",
    119: "单行本记录。",
    120: "SKU。",
    123: "卷简介，按卷次取数组。",
    125: "卷价格。",
    127: "上架 / 下架。",
    130: "记录结束。",
    132: "周边 SKU。",
    133: "周边记录。",
    134: "SKU。",
    135: "周边二级分类。",
    137: "统一简介。",
    138: "描述里带上作品名与周边名。",
    139: "价格来自数据表。",
    141: "状态来自数据表。",
    144: "记录结束。",
    145: "返回 60 条。",
    160: "输出一个空行。",
    163: "输出一个空行。",
    167: "输出一个空行。",
    169: "输出一个空行。",
    171: "输出一个空行。",
    173: "账号段注释。",
    179: "输出一个空行。",
    181: "分类段注释。",
    184: "一级分类文本拼装。",
    186: "join 参数结束。",
    187: "写入并补分号。",
    188: "输出一个空行。",
    190: "二级分类容器。",
    191: "遍历作品。",
    194: "写入二级分类。",
    195: "输出一个空行。",
    197: "商品段注释。",
    202: "商品行容器。",
    203: "遍历 60 条。",
    204: "拼一行。",
    208: "format 参数续行。",
    209: "format 参数续行。",
    210: "format 参数结束。",
    211: "append 结束。",
    212: "写入商品 VALUES。",
    213: "输出一个空行。",
    215: "输出一个空行。",
    223: "入口调用。",
}
EXPLAIN["py"].update(PY2)

# M01-03 追加：行号在插入相册段后整体后移，这里按新行号覆盖
PY3 = {
    212: "写入商品 VALUES。",
    213: "输出一个空行。",
    214: "追加相册段（100 条）。",
    215: "输出一个空行。",
    216: "演示数据结束标记。",
    217: "输出一个空行。",
    219: "写文件，UTF-8 无 BOM。",
    220: "输出路径，便于确认写到了哪。",
    221: "打印数量，跑完立刻能对账。",
    224: "返回一段完整的 INSERT 文本，供 main 追加。",
    225: "docstring：说明 3/2/0 张的分布。",
    226: "",
    227: "用 SKU 子查询取 product_id，不依赖自增顺序。",
    228: "脚本只用于空库初始化；重复执行会重复插入相册。",
    229: "docstring 结束。",
    230: "行容器。",
    231: "复用 build_products()，保证与商品插入顺序一致。",
    232: "每部作品 12 件里的位置。",
    233: "前 4 件 3 张、中 4 件 2 张、后 4 件 0 张。",
    234: "逐张生成。",
    235: "拼一行。",
    236: "product_id 用子查询；路径按 SKU 与序号拼。",
    237: "alt_text 用「商品名 图N」，sort_order 即序号。",
    238: "append 结束。",
    239: "段标题 + 列清单；注释里写明 100 条的构成。",
    240: "第二条拼接行：INSERT 头部。",
    241: "逗号换行连接，末尾补分号。",
    244: "可被 import 而不执行。",
    245: "入口调用。",
}
EXPLAIN["py"].update(PY3)

SQL = {
    1: "与 01 的「仅表结构」区分：这份带演示数据。",
    2: "生成物声明：改数据请改生成器。",
    3: "二选一，不依次执行。",
    4: "空库执行、不覆盖。",
    5: "教学口令说明。",
    6: "虚构声明。",
    8: "客户端字符集与排序规则。",
    9: "会话时区。",
    11: "建库。",
    12: "字符集与排序规则。",
    13: "后续对象都落在这个库。",
    14: "",
    15: "-- ===== 七张表的统一结构开始 =====",
    224: "账号段：不写 id 交给自增。",
    225: "",
    226: "",
    227: "",
    228: "末行带分号；carol 为 DISABLED。",
    231: "一级分类：parent_id 为 NULL，id 1–5。",
    232: "",
    233: "",
    234: "",
    235: "",
    236: "末行带分号。",
    238: "二级分类：每部作品一对，父 id 指向上表。",
    239: "偶数 id，-COMIC 结尾。",
    240: "奇数 id，-MERCH 结尾。",
    241: "",
    242: "",
    243: "",
    244: "",
    245: "",
    246: "",
    247: "",
    248: "末行带分号。",
    252: "商品段：11 列显式列出，顺序与每行一一对应。",
    253: "",
    314: "与第 170 行呼应；M01-03 会在此之前插入相册段。",
}
EXPLAIN["sql"].update(SQL)

SQL2 = {
    230: "分类段标题：5 个一级 + 10 个二级。",
    250: "商品段标题：60 件 = 5 部作品 ×（6 卷 + 6 周边）。",
    251: "把验收依赖的分布写在注释里：54 上架 / 6 下架 / 3 售罄 / 2 件库存 1。",
    314: "相册段标题：100 条 = 20 件×3 张 + 20 件×2 张，另 20 件无相册。",
    315: "相册段：product_id 用 SKU 子查询，不依赖自增顺序。",
    417: "演示数据结束标记。",
}
EXPLAIN["sql"].update(SQL2)


def explain_py(n: int, line: str) -> str:
    fixed = EXPLAIN["py"].get(n)
    if fixed:
        return fixed
    s = line.strip()
    m = re.match(r'\{"code": "([A-Z]+)", "cn": "([^"]+)", "en": "([A-Z]+)", "sort": (\d+)\}', s)
    if m:
        return "第 {0} 部作品：{1}，code={2}，分类排序 {3}".format(m.group(4), m.group(2), m.group(1), m.group(4))
    m = re.match(r'\("([a-z]+)", "([^"]+)", "(CUSTOMER|ADMIN)", "(ENABLED|DISABLED)", "([^"]+)",', s)
    if m:
        note = "禁用样例" if m.group(4) == "DISABLED" else "口令 Cartoon123! 的 BCrypt 散列"
        return f"{m.group(1)}：{m.group(3)}/{m.group(4)}，{note}"
    m = re.match(r'\("([^"]+)", (\d+), (\d+), "(ON_SALE|DRAFT)"\),?', s)
    if m:
        price, stock, status = int(m.group(2)), int(m.group(3)), m.group(4)
        extra = "（售罄样例）" if stock == 0 and status == "ON_SALE" else ("（下架样例）" if status == "DRAFT" else "")
        return f"{m.group(1)}：¥{price}，库存 {stock}{extra}"
    return ""


def explain_sql(n: int, line: str) -> str:
    fixed = EXPLAIN["sql"].get(n)
    if fixed:
        return fixed
    s = line.strip().rstrip(",").rstrip(";")
    m = re.match(r"\('([a-z]+)', '\$2a\$10\$[^']+', '([^']+)', '([A-Z]+)', '(ENABLED|DISABLED)', '([^']+)'\)", s)
    if m:
        note = "禁用样例" if m.group(4) == "DISABLED" else "口令 Cartoon123! 的 BCrypt 散列"
        return f"{m.group(1)}：{m.group(3)}/{m.group(4)}，{note}"
    m = re.match(r"\((\d+), '([A-Z]+)', NULL, '([^']+)', (\d+)\)", s)
    if m:
        return f"一级分类 id={m.group(1)}：{m.group(3)}（code={m.group(2)}，排序 {m.group(4)}）"
    m = re.match(r"\((\d+), '([A-Z]+)-(COMIC|MERCH)', (\d+), '([^']+)', (\d+)\)", s)
    if m:
        kind = "漫画" if m.group(3) == "COMIC" else "周边"
        return f"二级分类 id={m.group(1)}：{m.group(5)}（{kind}，父 id={m.group(4)}）"
    m = re.match(r"\(\(SELECT id FROM product WHERE sku='([^']+)'\), '([^']+)', '([^']+)', (\d+)\)", s)
    if m:
        return f"{m.group(1)} 第 {m.group(4)} 张：{m.group(2)}（alt「{m.group(3)}」）"
    m = re.match(
        r"\('([A-Z]+-[CM]\d{2})', (\d+), '([^']*)', '([^']*)', '([^']*)', 'images/products/[^']+', "
        r"(\d+\.\d{2}), (\d+), '(ON_SALE|DRAFT)', (\d+), DATE_SUB\(NOW\(\), INTERVAL (\d+) DAY\)\)",
        s,
    )
    if m:
        sku, cat, name, price, stock, status, sort, age = (
            m.group(1), m.group(2), m.group(3), m.group(6), m.group(7), m.group(8), m.group(9), m.group(10),
        )
        extra = ""
        if status == "DRAFT":
            extra = "，下架"
        elif int(stock) == 0:
            extra = "，售罄"
        elif int(stock) == 1:
            extra = "，仅剩 1 件"
        return f"{name}｜分类 {cat}，¥{price}，库存 {stock}{extra}，sort={sort}，{age} 天前上架"
    return ""


def render_table(lines: list[str], explain_fn) -> tuple[str, int]:
    rows = []
    explained = 0
    for i, line in enumerate(lines, start=1):
        stripped = line.rstrip("\n")
        if not stripped.strip():
            rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
            continue
        exp = explain_fn(i, stripped)
        if exp:
            explained += 1
        cls = ' class="cmtline"' if stripped.lstrip().startswith(("#", "--")) else ""
        code = hi_py(stripped) if explain_fn is explain_py else hi_sql(stripped)
        rows.append(
            f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
            f'<td class="code"><pre{cls}>{code}</pre></td>'
            f'<td class="explain">{esc(exp)}</td></tr>'
        )
    return "\n".join(rows), explained


def main() -> None:
    py_lines = PY_FILE.read_text(encoding="utf-8").splitlines()
    py_nonblank = sum(1 for line in py_lines if line.strip())
    sql_lines = SQL_FILE.read_text(encoding="utf-8").splitlines()

    py_rows, py_exp = render_table(py_lines, explain_py)

    # SQL 只对照种子段（224 行起）：1–220 是常量头与 01 的 DDL 复制
    sql_start = 224
    seed_lines = sql_lines[sql_start - 1:]
    seed_nonblank = sum(1 for line in seed_lines if line.strip())
    seed_rows = []
    seed_exp = 0
    for offset, line in enumerate(seed_lines):
        n = sql_start + offset
        if not line.strip():
            seed_rows.append(f'<tr class="gap"><td class="line">{n}</td><td></td><td></td></tr>')
            continue
        exp = explain_sql(n, line)
        if exp:
            seed_exp += 1
        cls = ' class="cmtline"' if line.lstrip().startswith("--") else ""
        seed_rows.append(
            f'<tr tabindex="0" data-line="{n}"><td class="line">{n}</td>'
            f'<td class="code"><pre{cls}>{hi_sql(line)}</pre></td>'
            f'<td class="explain">{esc(exp)}</td></tr>'
        )

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>M01-02 · 分类与商品种子 逐行验收</title>
{STYLE}</head>
<body>
<header>
<h1>M01-02 · 分类与商品种子（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码（注释行原位绿字），右列是该行的说明。</p>
<nav>
<a href="#gen">gen_demo_sql.py</a>
<a href="#sql">02-schema-with-demo.sql（种子段）</a>
</nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-02。依据 docs/03 §5 种子数据规格、docs/08 §4 改动清单。
验证：空库执行后 4 账号 / 15 分类 / 60 商品，54 上架 + 6 下架，3 件售罄、2 件库存 1；4 个账号 BCrypt 校验全部 matches=true。
SQL 的建表段（第 15–220 行）来自 01-schema.sql，见 M01-01 逐行验收，此处不重复。</p>

<section id="gen">
<h2>gen_demo_sql.py<small>种子生成器 · {len(py_lines)} 行</small></h2>
<p class="path">db/tools/gen_demo_sql.py</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{py_rows}
</tbody></table>
</section>

<section id="sql">
<h2>02-schema-with-demo.sql<small>种子段（第 224–{len(sql_lines)} 行）</small></h2>
<p class="path">db/02-schema-with-demo.sql</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(seed_rows)}
</tbody></table>
</section>
</main>
<footer>2026-11-15 · M01-02 · 生成器 {len(py_lines)} 行（非空 {py_nonblank}）、种子段 {len(seed_lines)} 行（非空 {seed_nonblank}）；逐行说明覆盖 {py_exp}/{py_nonblank} 与 {seed_exp}/{seed_nonblank}。
完整汇报见同目录 M01-02-分类商品种子.md。</footer>
</body></html>
"""
    # M01-02 的逐行验收已随该任务提交并冻结，不再重生成
    print(f"M01-02 (frozen): py={py_exp}, sql={seed_exp}")
    write_m01_01()
    write_m01_03()


def schema_explanations(lines: list[str]) -> dict[int, str]:
    """01-schema.sql 的逐行说明：列说明直接取 DDL 里的 COMMENT。"""
    exp = {
        1: "标题：仅表结构，不含数据；库名已改为 MyCartoonStore。",
        2: "新增行：声明 DDL 与课程基线一致，只改库名与注释。",
        3: "适用版本；本机 8.4.9 实测可执行。",
        4: "两个脚本二选一，不能依次执行。",
        5: "空库首次执行；脚本不 DROP 任何数据。",
        6: "已有同名表会报错，不要用 --force 跳过。",
        7: "脚本不建账号、不配密码、不被应用自动执行。",
        9: "客户端字符集与排序规则。",
        10: "会话时区，业务时间按东八区解释。",
        11: "",
        12: "建库：IF NOT EXISTS 避免重复执行中断。",
        13: "库字符集与排序规则。",
        14: "后续 DDL 落在这个库。",
        15: "",
        16: "分段标记：七张表结构开始。",
    }
    in_check = False
    for i, raw in enumerate(lines, 1):
        if i in exp:
            continue
        s = raw.strip()
        if not s:
            continue
        if in_check:
            if re.match(r"^\),?$", s):
                exp[i] = "约束结束"
                in_check = False
            else:
                exp[i] = "CHECK 条件续行"
            continue
        if s.startswith("ON DELETE"):
            exp[i] = "外键策略：删除与更新均 RESTRICT（有引用则拒绝）"
            continue
        if s.startswith("--"):
            m = re.match(r"-- (\d)\. (.+)", s)
            if m:
                exp[i] = f"表 {m.group(1)}：{m.group(2).rstrip('。')}"
            elif "=====" in s:
                exp[i] = "分段标记"
            else:
                exp[i] = "补充说明"
            continue
        m = re.match(r"CREATE TABLE (\w+) \(", s)
        if m:
            exp[i] = f"建表 {m.group(1)}"
            continue
        if s.startswith(") ENGINE"):
            cm = re.search(r"COMMENT='([^']*)'", s)
            exp[i] = f"表选项（InnoDB/utf8mb4）；表注释「{cm.group(1)}」" if cm else "表选项"
            continue
        m = re.match(r"CONSTRAINT (\w+) CHECK \(", s)
        if m:
            exp[i] = f"CHECK 约束 {m.group(1)}"
            if not s.rstrip().endswith("),"):
                in_check = True
            continue
        m = re.match(r"CONSTRAINT (\w+) FOREIGN KEY \(([^)]+)\) REFERENCES (\w+) \(([^)]+)\)", s)
        if m:
            exp[i] = f"外键 {m.group(1)}：{m.group(2)} → {m.group(3)}.{m.group(4)}"
            continue
        m = re.match(r"UNIQUE KEY (\w+) \(([^)]*)\)", s)
        if m:
            exp[i] = f"唯一键 {m.group(1)}：{m.group(2)}"
            continue
        m = re.match(r"KEY (\w+) \(([^)]*)\)", s)
        if m:
            exp[i] = f"普通索引 {m.group(1)}：{m.group(2)}"
            continue
        if s.startswith("PRIMARY KEY"):
            exp[i] = "主键"
            continue
        if "GENERATED ALWAYS AS" in s:
            exp[i] = "生成列 COALESCE(parent_id,0) STORED；实体层不要写它"
            continue
        m = re.match(r"(\w+)\s+\w+.*?COMMENT '([^']*)'", s)
        if m:
            exp[i] = f"{m.group(1)}：{m.group(2)}"
            continue
        if re.match(r"\w+\s+(BIGINT|VARCHAR|INT|DECIMAL|TEXT|DATETIME)", s):
            exp[i] = "列定义"
            continue
    return exp


def write_m01_01() -> None:
    lines = SCHEMA_FILE.read_text(encoding="utf-8").splitlines()
    exp = schema_explanations(lines)
    rows = []
    explained = 0
    nonblank = 0
    for i, line in enumerate(lines, 1):
        if not line.strip():
            rows.append(f'<tr class="gap"><td class="line">{i}</td><td></td><td></td></tr>')
            continue
        nonblank += 1
        e = exp.get(i, "")
        if e:
            explained += 1
        cls = ' class="cmtline"' if line.lstrip().startswith("--") else ""
        rows.append(
            f'<tr tabindex="0" data-line="{i}"><td class="line">{i}</td>'
            f'<td class="code"><pre{cls}>{hi_sql(line)}</pre></td>'
            f'<td class="explain">{esc(e)}</td></tr>'
        )

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>M01-01 · 结构脚本本地化 逐行验收</title>
{STYLE}</head>
<body>
<header>
<h1>M01-01 · 结构脚本本地化（逐行验收）</h1>
<p>一行源码一行记录，行号连续。左列完整源码（注释行原位绿字），右列是该行的说明。</p>
<nav><a href="#schema">01-schema.sql</a></nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-01。依据 docs/03 七表结构与 docs/08 D-06。
验证：与课程基线 diff 为 7 增 6 删，全部落在头部注释与第 12/14 行；空库执行 exit=0，建成 7 张表、7 主键、8 唯一、8 外键、35 CHECK，parent_key 为 STORED GENERATED。
列说明直接取自 DDL 内的 COMMENT，保证"文档即表结构"。</p>
<section id="schema">
<h2>01-schema.sql<small>七表结构 · {len(lines)} 行</small></h2>
<p class="path">db/01-schema.sql</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{chr(10).join(rows)}
</tbody></table>
</section>
</main>
<footer>2026-11-15 · M01-01 · 共 {len(lines)} 行（非空 {nonblank}），逐行说明覆盖 {explained}/{nonblank}。
完整汇报见同目录 M01-01-结构脚本.md。</footer>
</body></html>
"""
    OUT_SCHEMA.write_text(doc, encoding="utf-8")
    print(f"written: {OUT_SCHEMA}")
    print(f"schema explained: {explained}/{nonblank}")


def render_range(lines: list[str], start: int, explain_fn) -> tuple[str, int, int]:
    rows, explained, nonblank = [], 0, 0
    for n in range(start, len(lines) + 1):
        line = lines[n - 1]
        if not line.strip():
            rows.append(f'<tr class="gap"><td class="line">{n}</td><td></td><td></td></tr>')
            continue
        nonblank += 1
        e = explain_fn(n, line)
        if e:
            explained += 1
        cls = ' class="cmtline"' if line.lstrip().startswith(("#", "--")) else ""
        hi = hi_py(line) if explain_fn is explain_py else hi_sql(line)
        rows.append(
            f'<tr tabindex="0" data-line="{n}"><td class="line">{n}</td>'
            f'<td class="code"><pre{cls}>{hi}</pre></td>'
            f'<td class="explain">{esc(e)}</td></tr>'
        )
    return "\n".join(rows), explained, nonblank


def write_m01_03() -> None:
    py_lines = PY_FILE.read_text(encoding="utf-8").splitlines()
    sql_lines = SQL_FILE.read_text(encoding="utf-8").splitlines()
    py_rows, py_exp, py_nb = render_range(py_lines, 210, explain_py)
    sql_rows, sql_exp, sql_nb = render_range(sql_lines, 313, explain_sql)

    doc = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8">
<title>M01-03 · 相册种子 逐行验收</title>
{STYLE}</head>
<body>
<header>
<h1>M01-03 · 相册 100 条（逐行验收）</h1>
<p>只覆盖本任务新增/修改的行；行号是文件中的真实行号。</p>
<nav><a href="#gen">gen_demo_sql.py（第 210 行起）</a><a href="#sql">02-schema-with-demo.sql（第 313 行起）</a></nav>
</header>
<main>
<p class="note">所属模块：M01 数据库与实体 / 任务 M01-03。依据 docs/03 §5.4：20 件×3 张 + 20 件×2 张 + 20 件无相册 = 100 条。
验证：product_image 100 行；无相册商品 20 个；张数分布 0:20 / 2:20 / 3:20。</p>
<section id="gen">
<h2>gen_demo_sql.py<small>新增 render_images_sql() 与调用</small></h2>
<p class="path">db/tools/gen_demo_sql.py</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{py_rows}
</tbody></table>
</section>
<section id="sql">
<h2>02-schema-with-demo.sql<small>相册段</small></h2>
<p class="path">db/02-schema-with-demo.sql</p>
<table>
<colgroup><col style="width:64px"><col style="width:58%"><col></colgroup>
<thead><tr><th>行号</th><th>实际源码（注释行保持原位）</th><th>该行的说明</th></tr></thead>
<tbody>
{sql_rows}
</tbody></table>
</section>
</main>
<footer>2026-11-15 · M01-03 · 生成器第 210 行起（非空 {py_nb}，覆盖 {py_exp}/{py_nb}）、相册段（非空 {sql_nb}，覆盖 {sql_exp}/{sql_nb}）。
完整汇报见同目录 M01-03-相册种子.md。</footer>
</body></html>
"""
    OUT_M03.write_text(doc, encoding="utf-8")
    print(f"written: {OUT_M03}  py={py_exp}/{py_nb}  sql={sql_exp}/{sql_nb}")


if __name__ == "__main__":
    main()
