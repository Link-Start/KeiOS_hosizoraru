#!/usr/bin/env bash
# Is the committed baseline profile still describing the code that ships?
#
#   scripts/qa/baseline_profile_freshness.sh [--ref <git-ref>]
#
# Exits 0 when fresh, 1 when a profiled runtime source moved after the last
# capture, 2 when the profile is missing entirely. Meant as a release gate:
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
# "is it current" is a date comparison, and the fix is a 30-minute run:
#
#   ANDROID_SERIAL=<emulator> ./gradlew :app:generateBaselineProfile
set -uo pipefail

REF="HEAD"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done

PROFILE_DIR="app/src/release/generated/baselineProfiles"
BASELINE="$PROFILE_DIR/baseline-prof.txt"
STARTUP="$PROFILE_DIR/startup-prof.txt"

for file in "$BASELINE" "$STARTUP"; do
  if [[ ! -f "$file" ]]; then
    echo "MISSING  $file — the release would ship with no profile at all"
    exit 2
  fi
done

# Sources whose compiled method set the profile describes. Everything under a
# module's main sources qualifies: the journeys walk whole pages, and an inlined
# helper three modules away still lands in the recorded signatures.
CAPTURE=$(git log -1 --format=%H -- "$PROFILE_DIR")
if [[ -z "$CAPTURE" ]]; then
  echo "UNKNOWN  no commit touches $PROFILE_DIR; cannot date the capture"
  exit 2
fi

CAPTURE_WHEN=$(git log -1 --format=%cs "$CAPTURE")
CAPTURE_SUBJECT=$(git log -1 --format=%s "$CAPTURE")
DRIFT=$(git diff --name-only "$CAPTURE..$REF" -- '*/src/main/*' | sort)
RULES=$(grep -cv '^#' "$BASELINE")
STARTUP_RULES=$(grep -cv '^#' "$STARTUP")

echo "profile   $RULES baseline rules, $STARTUP_RULES startup rules"
echo "captured  ${CAPTURE:0:9} ($CAPTURE_WHEN) $CAPTURE_SUBJECT"

if [[ -z "$DRIFT" ]]; then
  echo "STATUS    fresh — no runtime source has moved since the capture"
  exit 0
fi

echo "STATUS    STALE — runtime source moved after the capture:"
echo "$DRIFT" | sed 's/^/            /'
echo
echo "Regenerate before shipping:"
echo "  ANDROID_SERIAL=<emulator> ./gradlew :app:generateBaselineProfile"
exit 1
