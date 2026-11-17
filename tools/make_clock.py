#!/usr/bin/env python3
"""把语料排成 assets/clock.json（一天 1440 分钟的静态表）。

输入：
  tools/classic.json        —— 句库，每条 {hours:[时…], s, h, b}；hours 是这句适用的钟点
  assets/local-corpus.json  —— 可选，自备语料，每条 {time:"HH:MM", s, h, b}，精确到分钟

输出：
  assets/clock.json         —— App 直接读的表，每条 {time, s, h, b}

规则：
  * 有精确语料的分钟，用精确语料；
  * 没有的，从「同一小时」的句库里挑候选（默认每个分钟 3 条）；
  * 单句最多排进 PER_MINUTE_MAX_SLOTS 个分钟位，防止某句被反复征用；
  * 同一句在同一天里出现次数 ≈ 分钟位数 / 候选数，App 端按 day % 候选数 轮换。
"""
import json
import os
import sys
import collections

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POOL = os.path.join(BASE, "tools", "classic.json")
LOCAL = os.path.join(BASE, "assets", "local-corpus.json")
OUT = os.path.join(BASE, "assets", "clock.json")

PER_MINUTE = 3          # 每个缺口语料分钟配几个候选
PER_MINUTE_MAX_SLOTS = 6  # 单句最多排进几个分钟位


def load(path):
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def main():
    pool = load(POOL)
    local = load(LOCAL)

    by_min = collections.defaultdict(list)
    for r in local:
        t = r.get("time", "")
        if len(t) == 5 and t[2] == ":":
            by_min[t].append({"time": t, "s": r["s"].strip(), "h": r.get("h", ""), "b": r["b"].strip()})
    print("自备语料 %d 条，覆盖 %d 分钟" % (len(local), len(by_min)))

    uses = collections.defaultdict(int)
    out = []
    for m in range(1440):
        key = "%02d:%02d" % (m // 60, m % 60)
        if key in by_min:
            out.extend(by_min[key])
            continue
        hour = m // 60
        cands = [i for i, r in enumerate(pool) if hour in r.get("hours", [])]
        if not cands:
            continue
        cands.sort(key=lambda i: uses[i])
        picked = []
        for i in cands:
            if uses[i] >= PER_MINUTE_MAX_SLOTS:
                continue
            picked.append(i)
            if len(picked) >= PER_MINUTE:
                break
        if not picked:
            # 这一小时的句子都排满了：宁可留空（App 会退化为「最近一段」），
            # 也不硬塞，否则同一句一天会出现十几次
            continue
        for i in picked:
            uses[i] += 1
            r = pool[i]
            out.append({"time": key, "s": r["s"], "h": r.get("h", ""), "b": r["b"]})

    out.sort(key=lambda r: (r["time"], r["b"]))
    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    covered = {r["time"] for r in out}
    print("写出 %s：%d 条，覆盖 %d/1440 分钟" % (os.path.relpath(OUT, BASE), len(out), len(covered)))
    missing = [m for m in range(1440) if "%02d:%02d" % (m // 60, m % 60) not in covered]
    if missing:
        print("未覆盖的分钟数：%d（这些分钟会退化为「最近一段」）" % len(missing))
    worst = sorted(uses.items(), key=lambda kv: -kv[1])[:5]
    if worst:
        print("排位最多的句子：")
        for i, n in worst:
            print("  %d 位  %s" % (n, pool[i]["s"][:18]))


if __name__ == "__main__":
    main()
