#!/usr/bin/env bash
# Read what the last Gradle test run actually produced: per-module counts, and when something
# failed, the failures grouped by cause instead of listed one by one.
#
#   scripts/qa/test_report.sh [--variant <name>] [--all] [--slowest <n>] [--module <name>]
#
# Gradle prints failing test names and a path to an HTML report. Across seventeen modules that is
# the wrong shape twice over. It does not tell you the totals, and a systemic breakage arrives as
# hundreds of separate lines with the same single cause buried in each -- bumping Robolectric to
# 4.17 produced 241 failures in :app that were one missing JDK export, and the way to see that was
# to normalise the first line of every stack and count. This does that.
#
# It reads the XML Gradle already wrote, so it is free and can be run repeatedly after one test
# run. That is also its one trap: the results stay on disk after a green run, so a module whose
# tests did not run this time still reports its previous numbers. Anything older than --stale
# minutes is called out rather than quietly counted.
#
# Exit codes: 0 all green, 1 failures or errors present, 2 bad usage, 3 no results to read.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

VARIANT="testDebugUnitTest"
SHOW_ALL=0
SLOWEST=0
STALE_MINUTES=30
STACK_LINES=12
ONLY_MODULE=""

usage() {
  cat <<'EOF'
Usage: scripts/qa/test_report.sh [options]

  --variant <name>   test task directory to read (default: testDebugUnitTest)
  --module <name>    restrict to one module, e.g. --module app
  --all              print every failure cluster, not just the largest five
  --lines <n>        stack lines to show per cluster example (default: 12)
  --slowest <n>      also list the n slowest test classes
  --stale <minutes>  age at which a module's results are called stale (default: 30)
  -h, --help         show this help

Exit codes: 0 green, 1 failures, 2 bad usage, 3 no results.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) SHOW_ALL=1; shift ;;
    --variant)
      [[ $# -ge 2 ]] || { printf 'Missing name after --variant\n' >&2; exit 2; }
      VARIANT="$2"; shift 2 ;;
    --module)
      [[ $# -ge 2 ]] || { printf 'Missing name after --module\n' >&2; exit 2; }
      ONLY_MODULE="$2"; shift 2 ;;
    --lines)
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { printf 'Missing number after --lines\n' >&2; exit 2; }
      STACK_LINES="$2"; shift 2 ;;
    --slowest)
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { printf 'Missing number after --slowest\n' >&2; exit 2; }
      SLOWEST="$2"; shift 2 ;;
    --stale)
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { printf 'Missing number after --stale\n' >&2; exit 2; }
      STALE_MINUTES="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

command -v python3 >/dev/null 2>&1 || { printf 'python3 is required.\n' >&2; exit 3; }

VARIANT="$VARIANT" SHOW_ALL="$SHOW_ALL" SLOWEST="$SLOWEST" STALE_MINUTES="$STALE_MINUTES" \
  STACK_LINES="$STACK_LINES" ONLY_MODULE="$ONLY_MODULE" python3 - <<'PY'
import collections
import glob
import os
import re
import time
import xml.etree.ElementTree as ET

VARIANT = os.environ["VARIANT"]
SHOW_ALL = os.environ["SHOW_ALL"] == "1"
SLOWEST = int(os.environ["SLOWEST"])
STALE_SECONDS = int(os.environ["STALE_MINUTES"]) * 60
STACK_LINES = int(os.environ["STACK_LINES"])
ONLY_MODULE = os.environ["ONLY_MODULE"]

suites = sorted(glob.glob(f"*/build/test-results/{VARIANT}/TEST-*.xml"))
if ONLY_MODULE:
    suites = [path for path in suites if path.split("/")[0] == ONLY_MODULE]

if not suites:
    where = f"*/build/test-results/{VARIANT}/"
    print(f"No test results under {where}")
    print("Run the tests first, or pass --variant for a different task.")
    raise SystemExit(3)

Module = collections.namedtuple("Module", "tests failures errors skipped classes newest seconds")
modules = collections.defaultdict(lambda: [0, 0, 0, 0, 0, 0.0, 0.0])
clusters = collections.Counter()
examples = {}
durations = []

# Two stacks are the same failure when their first line matches once the numbers are taken out:
# addresses, object hashes, port numbers and line numbers differ per test and say nothing about
# the cause. Assertion messages that differ only in an expected value collapse too, which is the
# point -- one broken contract should read as one problem.
VARIABLE = re.compile(r"0x[0-9a-f]+|\d+")

for path in suites:
    module = path.split("/")[0]
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        print(f"  unreadable: {path}")
        continue
    entry = modules[module]
    entry[0] += int(root.get("tests", 0))
    entry[1] += int(root.get("failures", 0))
    entry[2] += int(root.get("errors", 0))
    entry[3] += int(root.get("skipped", 0))
    entry[4] += 1
    entry[5] = max(entry[5], os.path.getmtime(path))
    entry[6] += float(root.get("time", 0) or 0)
    durations.append((float(root.get("time", 0) or 0), module, root.get("name", path)))

    for case in root.iter("testcase"):
        for problem in list(case.findall("failure")) + list(case.findall("error")):
            text = problem.text or ""
            headline = text.strip().split("\n")[0].strip()[:180]
            key = VARIABLE.sub("#", headline)
            clusters[key] += 1
            examples.setdefault(key, (module, case.get("classname", ""), case.get("name", ""), text))

now = time.time()
total = failed = errored = skipped = 0
width = max(len(name) for name in modules) + 2

print(f"{VARIANT}\n")
for module in sorted(modules):
    tests, failures, errors, skips, classes, newest, seconds = modules[module]
    total += tests
    failed += failures
    errored += errors
    skipped += skips
    age = now - newest
    marks = []
    if failures or errors:
        marks.append(f"{failures + errors} FAILED")
    if skips:
        marks.append(f"{skips} skipped")
    if age > STALE_SECONDS:
        marks.append(f"stale, {age / 60:.0f} min old")
    line = f"  {module:<{width}}{tests:>5} tests {classes:>4} class{'es' if classes != 1 else '  '} {seconds:>7.1f}s"
    print(f"{line}  {', '.join(marks)}" if marks else line)

print(f"\n  {'TOTAL':<{width}}{total:>5} tests, {failed} failures, {errored} errors, {skipped} skipped")

stale = [name for name in modules if now - modules[name][5] > STALE_SECONDS]
if stale:
    print(
        f"\n{len(stale)} module(s) carry results older than {STALE_SECONDS // 60} minutes, counted"
        " above as they stand.\nUsually that is Gradle's up-to-date check and the numbers are still"
        " true. It is not, when the\nlast invocation named fewer modules than this report covers."
    )

if SLOWEST:
    print(f"\nslowest {SLOWEST} test classes")
    for seconds, module, name in sorted(durations, reverse=True)[:SLOWEST]:
        print(f"  {seconds:>7.1f}s  {module:<{width}}{name}")

if not clusters:
    raise SystemExit(0)

shown = clusters.most_common() if SHOW_ALL else clusters.most_common(5)
print(f"\n{sum(clusters.values())} failure(s) in {len(clusters)} distinct cause(s)")
for key, count in shown:
    module, classname, name, text = examples[key]
    print(f"\n=== {count}x  [{module}] {classname.rsplit('.', 1)[-1]} > {name}")
    for line in text.strip().split("\n")[:STACK_LINES]:
        print(f"    {line.rstrip()}")
if not SHOW_ALL and len(clusters) > len(shown):
    print(f"\n  ... {len(clusters) - len(shown)} more cause(s); --all to see them")

raise SystemExit(1)
PY
