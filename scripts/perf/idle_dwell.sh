#!/usr/bin/env bash
# Frames a scene renders while nobody touches it.
#
#   D=emulator-5554 PKG=os.kei.diag ./idle_dwell.sh <label> [test-tag | x y]...
#
# The one HWUI figure that needs no Developer-options per-frame capture and does not swing
# between runs: reset the counter, leave the scene alone for DWELL seconds, count what it drew
# anyway. A scene that redraws nothing when nothing changes reports 0. Home reports the panel
# rate because its background animates -- see docs/planning/hwui-frame-budget.md.
#
# Arguments after the label navigate to the scene before the counter opens, so the dwell measures
# the scene rather than the journey into it. A bare word is a test tag, resolved through
# uiautomator so it survives a layout change; a pair of numbers is a raw tap.
set -uo pipefail
D=${D:-emulator-5554}
PKG=${PKG:-os.kei.diag}
ACT=${ACT:-os.kei.LauncherAndroidDesigns}
DWELL=${DWELL:-3}
SETTLE=${SETTLE:-1.6}
LABEL="$1"
shift

pause() { perl -e "select(undef,undef,undef,$1)"; }

tag_xy() {
  adb -s "$D" exec-out uiautomator dump /dev/tty 2>/dev/null \
    | tr '<' '\n<' | grep "resource-id=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 \
    | perl -ne 'if (/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/) { printf "%d %d", ($1+$3)/2, ($2+$4)/2 }'
}

adb -s "$D" shell am force-stop "$PKG" >/dev/null 2>&1
adb -s "$D" shell am start -W -n "$PKG/$ACT" >/dev/null 2>&1
pause 6

while [ "$#" -ge 1 ]; do
  if [ "$#" -ge 2 ] && [ -z "${1//[0-9]/}" ] && [ -z "${2//[0-9]/}" ]; then
    adb -s "$D" shell input tap "$1" "$2" >/dev/null 2>&1
    shift 2
  else
    XY=$(tag_xy "$1")
    if [ -z "$XY" ]; then
      echo "$LABEL: test tag '$1' not on screen" >&2
      exit 3
    fi
    adb -s "$D" shell input tap $XY >/dev/null 2>&1
    shift
  fi
  pause "$SETTLE"
done
# Let the arrival animation and any first-frame work finish before the counter opens.
pause 2.5

adb -s "$D" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
pause "$DWELL"

adb -s "$D" shell dumpsys gfxinfo "$PKG" 2>/dev/null | LABEL="$LABEL" DWELL="$DWELL" perl -ne '
  BEGIN { our %v }
  $v{total} = $1 if /^Total frames rendered: (\d+)/;
  $v{p50}   = $1 if /^50th percentile: (\d+)/;
  $v{p90}   = $1 if /^90th percentile: (\d+)/;
  $v{p99}   = $1 if /^99th percentile: (\d+)/;
  $v{missed} = $1 if /^Number Frame deadline missed: (\d+)/;
  END {
    my $fps = $v{total} ? $v{total} / $ENV{DWELL} : 0;
    printf "%-30s idle_frames=%-5s (%5.1f/s)  p50=%-4s p90=%-4s p99=%-5s missed=%s\n",
      $ENV{LABEL}, $v{total}//"-", $fps, $v{p50}//"-", $v{p90}//"-", $v{p99}//"-", $v{missed}//"-";
  }'
