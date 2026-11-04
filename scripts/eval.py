"""Day6 评测基线：30 条用例，算 recall@1 / recall@3 / MRR / 拒答正确率
只依赖标准库。跑之前先设好数据库密码：
  PowerShell:  $env:DB_PASSWORD="你的密码"
  然后:        python scripts/eval.py
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request

MYSQL = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB = "ai_knowledge_base"
BASE = "http://localhost:8081"
PW = os.environ.get("DB_PASSWORD", "")


def sql(stmt):
    """执行 SQL 并返回逐行拆好的字段列表。

    两个必须注意的点（踩过）：
    ① 从 stdin 喂 UTF-8 语句，避开 Windows 命令行传中文参数的编码问题；
    ② 只按 \\n 切行，且不能整体 strip() ——
       splitlines() 会把 \\x0b/\\u2028 也当换行；
       整体 strip() 会把行尾空字段前的 \\t 一起吃掉，导致列数少 1。
    """
    p = subprocess.run(
        [MYSQL, "-uroot", f"-p{PW}", "--default-character-set=utf8mb4", "-N", "-B", DB],
        input=stmt.encode("utf-8"), capture_output=True,
    )
    if p.returncode != 0:
        print("SQL 失败：", p.stderr.decode("utf-8", "ignore")[:300])
        sys.exit(1)
    out = p.stdout.decode("utf-8", "ignore")
    return [line.rstrip("\r").split("\t") for line in out.split("\n") if line.strip()]


def search(question, topk=3):
    url = BASE + "/api/search?q=" + urllib.parse.quote(question) + f"&topK={topk}"
    with urllib.request.urlopen(url, timeout=30) as r:
        return json.loads(r.read().decode("utf-8")).get("data") or []


def main():
    rows = sql(
        "SELECT question, IFNULL(expected_chunk_id,''), answerable, note "
        "FROM eval_case ORDER BY id;"
    )
    if not rows:
        print("eval_case 是空的，先执行 docs/eval-cases.sql")
        sys.exit(1)

    hit1 = hit3 = 0
    n_ans = n_una = refuse_ok = 0
    rr = 0.0
    for question, exp, ans, note in rows:
        hits = search(question)
        got = [h["chunkId"] for h in hits]
        scores = [round(h["score"], 4) for h in hits]
        if ans == "1":
            n_ans += 1
            exp_id = int(exp) if exp else -1
            if got and got[0] == exp_id:
                hit1 += 1
            if exp_id in got:
                hit3 += 1
                # MRR：正确块排第几就取几分之一；排第 1 = 1.0，第 3 = 0.333
                rr += 1.0 / (got.index(exp_id) + 1)
            else:
                print(f"  MISS {note:20s} exp={exp_id} got={got[:3]} score={scores[:3]}")
        else:
            n_una += 1
            # 拒答正确 = 检索层返回空。Day6 只能做到这个粒度，
            # 真正的拒答交给 Day8 生成层（见开工卡 ③ 节）
            if not got:
                refuse_ok += 1
            else:
                print(f"  LEAK {note:20s} got={got[:3]} score={scores[:3]}")

    print()
    print(f"可答题 {n_ans} 条：recall@1 = {hit1}/{n_ans} ({100*hit1/max(n_ans,1):.1f}%)")
    print(f"              recall@3 = {hit3}/{n_ans} ({100*hit3/max(n_ans,1):.1f}%)")
    print(f"              MRR      = {rr/max(n_ans,1):.3f}")
    print(f"不可答 {n_una} 条：拒答正确 = {refuse_ok}/{n_una}")


if __name__ == "__main__":
    main()
