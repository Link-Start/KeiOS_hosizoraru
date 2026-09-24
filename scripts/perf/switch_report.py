#!/usr/bin/env python3
"""Rank switch-into-page cost, pooling repeats of the same target."""
import glob
import statistics as st
import sys
from collections import defaultdict


def load(path):
    header, rows = None, []
    for line in open(path, encoding="utf-8", errors="ignore"):
        parts = [p for p in line.strip().split(",") if p != ""]
        if not parts:
            continue
        if parts[0] == "Flags":
            header = {n: i for i, n in enumerate(parts)}
            continue
        if header is None or not parts[0].isdigit():
            continue
        try:
            v = [int(p) for p in parts]
        except ValueError:
            continue
        if len(v) < len(header):
            continue
        g = lambda k: v[header[k]]  # noqa: E731
        total = (g("FrameCompleted") - g("IntendedVsync")) / 1e6
        # Unfinished frames have a negative total; a long one is a real hitch and is kept.
        if total <= 0:
            continue
        rows.append(dict(
            total=total,
            start_delay=(g("HandleInputStart") - g("IntendedVsync")) / 1e6,
            ui_work=(g("SyncQueued") - g("HandleInputStart")) / 1e6,
            sync_wait=(g("SyncStart") - g("SyncQueued")) / 1e6,
            rt=(g("CommandSubmissionCompleted") - g("SyncStart")) / 1e6,
            gpu=(g("GpuCompleted") - g("CommandSubmissionCompleted")) / 1e6,
        ))
    return rows


def q(rows, k, p):
    xs = sorted(r[k] for r in rows)
    return xs[min(int(len(xs) * p), len(xs) - 1)]


pooled = defaultdict(list)
for pattern in sys.argv[1:]:
    for path in sorted(glob.glob(pattern)):
        page = path.split("_")[-1].replace(".csv", "")
        pooled[page].extend(load(path))

print(f"{'switch to':<10}{'n':>5}{'total p50':>11}{'p90':>8}{'p99':>8}"
      f"{'ui_work':>9}{'sync_wait':>11}{'rt':>7}{'gpu':>7}{'worst':>8}")
for page in ("github", "mcp", "os", "ba"):
    r = pooled.get(page)
    if not r:
        continue
    print(f"{page:<10}{len(r):>5}{q(r,'total',.5):>11.2f}{q(r,'total',.9):>8.2f}"
          f"{q(r,'total',.99):>8.2f}{q(r,'ui_work',.5):>9.2f}{q(r,'sync_wait',.5):>11.2f}"
          f"{q(r,'rt',.5):>7.2f}{q(r,'gpu',.5):>7.2f}{max(x['total'] for x in r):>8.1f}")
print()
print(f"{'switch to':<10}{'ui p90':>8}{'ui p99':>8}{'rt p90':>8}{'rt p99':>8}"
      f"{'gpu p90':>9}{'gpu p99':>9}   frames over 16.7ms")
for page in ("github", "mcp", "os", "ba"):
    r = pooled.get(page)
    if not r:
        continue
    over = sum(1 for x in r if x["total"] > 16.7)
    print(f"{page:<10}{q(r,'ui_work',.9):>8.2f}{q(r,'ui_work',.99):>8.2f}"
          f"{q(r,'rt',.9):>8.2f}{q(r,'rt',.99):>8.2f}{q(r,'gpu',.9):>9.2f}"
          f"{q(r,'gpu',.99):>9.2f}   {over:>4} / {len(r)} ({100*over//len(r)}%)")
