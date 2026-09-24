#!/usr/bin/env bash
# Is the committed baseline profile still describing the code that ships?
#
#   scripts/qa/baseline_profile_freshness.sh [--ref <git-ref>]
#
# Exits 0 when fresh, 1 when a profiled runtime source or a dependency version
# moved after the last capture, 2 when the profile is missing entirely, 64 on bad
# usage (including a --ref git cannot resolve). Meant as a release gate:
#
#   scripts/qa/baseline_profile_freshness.sh || echo "regenerate before shipping"
#
# Why this exists: a stale profile fails silently. Rules for methods that no
# longer exist are dropped without a word, and methods added since the capture
# simply have none — so the paths a release most wants pre-compiled are the ones
# a refactor quietly removes from the profile. It has already shipped twice: once
# from a capture taken before the BA card rewrite, once from one taken two
# commits before the office cards changed which draw branch they run.
#
# It deliberately does not try to judge *how much* drift matters. Any runtime
# change on a path a journey walks can move method signatures, so the answer to
# "is it current" is a date comparison, and the fix is a run of about 11 minutes (2026-09-24):
#
#   ANDROID_SERIAL=<emulator> ./gradlew :app:generateReleaseBaselineProfile
set -uo pipefail

REF="HEAD"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)
      [[ $# -ge 2 ]] || { echo "missing value for --ref" >&2; exit 64; }
      REF="$2"; shift 2 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done

# Every path below is relative to the repository root, wherever this was started from.
cd "$(git -C "$(dirname "$0")" rev-parse --show-toplevel)" || exit 64
# A mistyped ref must not read as "fresh": git diff would fail, leave DRIFT empty, and pass.
git rev-parse --verify -q "$REF^{commit}" >/dev/null || { echo "bad ref: $REF" >&2; exit 64; }

PROFILE_DIR="app/src/release/generated/baselineProfiles"
BASELINE="$PROFILE_DIR/baseline-prof.txt"
STARTUP="$PROFILE_DIR/startup-prof.txt"

for file in "$BASELINE" "$STARTUP"; do
  if [[ ! -f "$file" ]]; then
    echo "MISSING  $file — the release would ship with no profile at all"
    exit 2
  fi
done

# Compiled runtime sources whose method set the profile describes. The journeys
# walk whole pages, and an inlined helper three modules away still lands in the
# recorded signatures. Resources and manifests can change runtime data, while
# leaving the ART profile's class and method rules valid, so they stay outside
# this method-signature freshness check.
CAPTURE=$(git log -1 --format=%H -- "$PROFILE_DIR")
if [[ -z "$CAPTURE" ]]; then
  echo "UNKNOWN  no commit touches $PROFILE_DIR; cannot date the capture"
  exit 2
fi

CAPTURE_WHEN=$(git log -1 --format=%cs "$CAPTURE")
CAPTURE_SUBJECT=$(git log -1 --format=%s "$CAPTURE")
DRIFT=$(
  git diff --name-only "$CAPTURE..$REF" -- \
    ':(glob)*/src/main/**/*.kt' \
    ':(glob)*/src/main/**/*.java' \
    ':(glob)*/src/main/**/*.aidl' \
    | sort
)
# Library code is in the profile too (androidx.compose and Miuix alone are tens of thousands of
# rules), so a dependency bump can leave it as stale as a source edit. Compared by value, so a
# comment or an unrelated property in the same files does not count.
versions_at() {
  {
    git show "$1:gradle/libs.versions.toml" 2>/dev/null \
      | awk '/^\[versions\]/{on=1; next} /^\[/{on=0} on && /^[A-Za-z0-9_.-]+[[:space:]]*=/' \
      | sed -E 's/[[:space:]]*#.*$//; s/[[:space:]]*=[[:space:]]*/=/'
    git show "$1:gradle.properties" 2>/dev/null | grep -E '^[[:space:]]*miuix\.version[[:space:]]*=' \
      | sed -E 's/[[:space:]]*=[[:space:]]*/=/; s/^[[:space:]]+//'
  } | sort
}
DEP_DRIFT=$(comm -13 <(versions_at "$CAPTURE") <(versions_at "$REF"))

RULES=$(grep -cv '^#' "$BASELINE")
STARTUP_RULES=$(grep -cv '^#' "$STARTUP")

echo "profile   $RULES baseline rules, $STARTUP_RULES startup rules"
echo "captured  ${CAPTURE:0:9} ($CAPTURE_WHEN) $CAPTURE_SUBJECT"

if [[ -z "$DRIFT" && -z "$DEP_DRIFT" ]]; then
  echo "STATUS    fresh — no runtime source or dependency version has moved since the capture"
  exit 0
fi

if [[ -n "$DRIFT" ]]; then
  echo "STATUS    STALE — runtime source moved after the capture:"
  while IFS= read -r path; do
    echo "            $path"
  done <<< "$DRIFT"
fi
if [[ -n "$DEP_DRIFT" ]]; then
  echo "STATUS    STALE — dependency versions moved after the capture (values now; build and test tools such as agp are listed too and do not stale it on their own):"
  while IFS= read -r line; do
    echo "            $line"
  done <<< "$DEP_DRIFT"
fi
echo
echo "Regenerate before shipping:"
echo "  ANDROID_SERIAL=<emulator> ./gradlew :app:generateReleaseBaselineProfile"
exit 1
