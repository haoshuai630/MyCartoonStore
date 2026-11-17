"""AC-32 空库复现取证：备份 → 用交付脚本重建 → 核对行数 → 恢复。

用法：
  python db/tools/ac32_fresh_db.py backup     # 备份演示库到 _scratch/（不提交）
  python db/tools/ac32_fresh_db.py init       # 执行 db/02-schema-with-demo.sql（脚本自身会 DROP + CREATE）
  python db/tools/ac32_fresh_db.py verify     # 打印七表行数；--baseline 校验种子口径
  python db/tools/ac32_fresh_db.py restore    # 用备份恢复演示库

产物：reports/evidence/m09-ac32-{1-backup,2-init,3-verify,5-restore}.txt
"""
from __future__ import annotations

import pathlib
import subprocess
import sys

BIN = pathlib.Path(r"C:/Program Files/MySQL/MySQL Server 8.4/bin")
MYSQL = BIN / "mysql.exe"
DUMP = BIN / "mysqldump.exe"
ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "db" / "02-schema-with-demo.sql"
BACKUP = ROOT / "_scratch" / "MyCartoonStore-before-ac32.sql"
OUT = ROOT / "reports" / "evidence"
DB = "MyCartoonStore"

# 交付脚本的种子口径（db/README.md §3.1）。
EXPECTED = {"store_user": 4, "category": 60, "product": 95, "product_image": 160,
            "cart_item": 10, "sales_order": 10, "sales_order_item": 20}

COMMON = ["--host=127.0.0.1", "--port=3306", "--user=root", "--password=123456",
          "--default-character-set=utf8mb4"]


def run_mysql(args: list[str], stdin_file: pathlib.Path | None = None) -> subprocess.CompletedProcess:
    stdin = open(stdin_file, "rb") if stdin_file else None
    try:
        return subprocess.run([str(MYSQL), *COMMON, *args], stdin=stdin,
                              stderr=subprocess.PIPE, text=False)
    finally:
        if stdin:
            stdin.close()


def rows() -> dict[str, int]:
    sql = " UNION ALL ".join(
        f"SELECT '{t}', COUNT(*) FROM {DB}.{t}" for t in EXPECTED)
    result = subprocess.run([str(MYSQL), *COMMON, "-N", "-B", "-e", sql],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip())
    counts = {}
    for line in result.stdout.strip().splitlines():
        name, value = line.split("\t")
        counts[name] = int(value)
    return counts


def tables() -> int:
    result = subprocess.run(
        [str(MYSQL), *COMMON, "-N", "-B", "-e",
         f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{DB}'"],
        capture_output=True, text=True)
    return int(result.stdout.strip())


def report(step: str, lines: list[str]) -> None:
    """按步骤落一个证据文件：备份、重建、核对、恢复各一份，互不覆盖。"""
    text = "\n".join(lines)
    print(text)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / f"m09-ac32-{step}.txt").write_text(f"== {step} ==\n{text}\n", encoding="utf-8")


def main() -> int:
    action = sys.argv[1] if len(sys.argv) > 1 else "verify"
    baseline = "--baseline" in sys.argv

    if action == "backup":
        BACKUP.parent.mkdir(parents=True, exist_ok=True)
        result = subprocess.run([str(DUMP), *COMMON, "--single-transaction", "--databases", DB],
                                stdout=open(BACKUP, "wb"), stderr=subprocess.PIPE, text=False)
        if result.returncode != 0:
            raise RuntimeError(result.stderr.decode("utf-8", "replace"))
        report("1-backup", [f"mysqldump → {BACKUP.relative_to(ROOT)}（{BACKUP.stat().st_size} 字节）",
                            f"备份前行数：{rows()}"])
        return 0

    if action == "init":
        result = run_mysql([], stdin_file=SCRIPT)  # 在服务器上直接执行交付脚本。
        log = result.stderr.decode("utf-8", "replace").strip()
        report("2-init", [
            f"脚本：{SCRIPT.relative_to(ROOT)}",
            f"退出码：{result.returncode}",
            f"stderr：{log if log else '（无）'}",
            f"表数量：{tables()}",
        ])
        return result.returncode

    if action == "verify":
        counts = rows()
        lines = [f"表数量：{tables()}"]
        ok = True
        for name, expected in EXPECTED.items():
            actual = counts.get(name, -1)
            mark = "OK" if not baseline or actual == expected else "MISMATCH"
            ok = ok and mark == "OK"
            lines.append(f"{name:<18} {actual:>5}   期望 {expected:>3}   {mark}")
        lines.append(f"RESULT: {'PASS' if ok else 'FAIL'}")
        report("3-verify", lines)
        return 0 if ok else 1

    if action == "restore":
        result = run_mysql([], stdin_file=BACKUP)  # 用备份覆盖回去。
        log = result.stderr.decode("utf-8", "replace").strip()
        report("5-restore", [f"导入：{BACKUP.relative_to(ROOT)}",
                             f"退出码：{result.returncode}",
                             f"stderr：{log if log else '（无）'}",
                             f"恢复后行数：{rows()}"])
        return result.returncode

    print(__doc__)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
