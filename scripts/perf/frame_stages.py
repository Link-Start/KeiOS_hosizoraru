#!/usr/bin/env python3
"""Per-stage breakdown of a gfxinfo framestats dump, keyed by the CSV header."""
import statistics as st
import sys

STAGES = [
    ("input", "HandleInputStart", "AnimationStart"),
    ("animation", "AnimationStart", "PerformTraversalsStart"),
    ("measure+layout", "PerformTraversalsStart", "DrawStart"),
    ("record draw", "DrawStart", "SyncQueued"),
    ("sync", "SyncStart", "IssueDrawCommandsStart"),
    ("RT issue->swap", "IssueDrawCommandsStart", "SwapBuffers"),
    ("swap->completed", "SwapBuffers", "FrameCompleted"),
]


def load(path):
    header, rows = None, []
    for line in open(path, encoding="utf-8", errors="ignore"):
        parts = [p for p in line.strip().split(",") if p != ""]
        if not parts:
            continue
        if parts[0] == "Flags":
            header = {name: i for i, name in enumerate(parts)}
            continue
        if header is None or not parts[0].isdigit():
            continue
        try:
            v = [int(p) for p in parts]
        except ValueError:
            continue
        if len(v) < len(header):
            continue
        get = lambda k: v[header[k]]  # noqa: E731
        total = (get("FrameCompleted") - get("IntendedVsync")) / 1e6
        # A frame that never completed (skipped, or still in flight) has FrameCompleted 0 and a
        # negative total. A long one is a real hitch and stays in: dropping frames over some
        # ceiling hid exactly the frames a switch or scroll report exists to find.
        if total <= 0:
            continue
        row = {"total": total, "interval": get("FrameInterval") / 1e6}
        ok = True
        for name, a, b in STAGES:
            d = (get(b) - get(a)) / 1e6
            if d < -1:
                ok = False
                break
            row[name] = max(d, 0.0)
        if ok:
            rows.append(row)
    return rows


def q(rows, key, pct):
    xs = sorted(r[key] for r in rows)
    return xs[min(int(len(xs) * pct), len(xs) - 1)]


def report(label, rows):
    if not rows:
        print(f"{label}: no frames")
        return
    mean_total = st.mean(r["total"] for r in rows)
    interval = st.median(r["interval"] for r in rows)
    over = sum(1 for r in rows if r["total"] > interval)
    print(f"\n{label}: {len(rows)} frames, vsync interval {interval:.2f}ms "
          f"({100 * over // len(rows)}% over interval)")
    long = sum(1 for r in rows if r["total"] >= 500)
    if long:
        print(f"  {long} frame(s) of 500ms or more are included")
    print(f"  total            p50={q(rows,'total',.5):6.2f}  p90={q(rows,'total',.9):6.2f}  "
          f"p99={q(rows,'total',.99):6.2f}")
    print(f"  {'stage':<17}{'p50':>7}{'p90':>7}{'p99':>7}{'mean':>7}   share")
    for name, _, _ in STAGES:
        m = st.mean(r[name] for r in rows)
        bar = "#" * round(34 * m / mean_total)
        print(f"  {name:<17}{q(rows,name,.5):>7.2f}{q(rows,name,.9):>7.2f}"
              f"{q(rows,name,.99):>7.2f}{m:>7.2f}   {100 * m / mean_total:4.1f}% {bar}")


for path in sys.argv[1:]:
    report(path.split("/")[-1].replace("fs_", "").replace(".csv", ""), load(path))
