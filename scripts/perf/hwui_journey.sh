#!/usr/bin/env bash
# Reset HWUI stats, drive one fixed journey, print the aggregate percentiles.
#
#   D=<serial> PKG=<app id> ./hwui_journey.sh <name> <journey>
#
# Defaults to os.kei.diag, like idle_dwell.sh: the diagnostic variant is release with R8, built
# to be measured, and measuring os.kei instead drives the user's real install and its real data.
#   journeys: home_scroll | section_switch | route_push
#
# Requires Developer options -> Profile HWUI rendering -> "In adb shell dumpsys
# gfxinfo"; without it the percentile block is absent and every field prints "-".
#
# Read the CPU/GPU percentiles and ignore "Janky frames" and "gpu p99": those two
# swing 6x across identical runs on 5eea1f50 (jank 9.6% / 11.8% / 59.0% on three
# back-to-back runs of the same build). The percentiles hold to +/-1-2ms.
#
# Journeys are deliberately identical across builds so two runs can be diffed.
set -uo pipefail
D=${D:-5eea1f50}
PKG=${PKG:-os.kei.diag}
LABEL="$1"
JOURNEY="$2"

W=$(adb -s "$D" shell wm size | tail -1 | tr -d '\r' | sed 's/.*: //' | cut -dx -f1)
H=$(adb -s "$D" shell wm size | tail -1 | tr -d '\r' | sed 's/.*: //' | cut -dx -f2)
CX=$((W / 2))
LO=$((H * 74 / 100))
HI=$((H * 34 / 100))

tap() { adb -s "$D" shell input tap "$1" "$2"; }
fling_up() { adb -s "$D" shell input swipe $CX $LO $CX $HI 90; }
fling_down() { adb -s "$D" shell input swipe $CX $HI $CX $LO 90; }
pause() { perl -e "select(undef,undef,undef,$1)"; }

# Bottom tab centres, resolved from the published test tags so the script
# survives a layout change.
tab_xy() {
  adb -s "$D" exec-out uiautomator dump /dev/tty 2>/dev/null \
    | tr '<' '\n<' | grep "resource-id=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 \
    | perl -ne 'if (/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/) { printf "%d %d", ($1+$3)/2, ($2+$4)/2 }'
}

goto_home() {
  adb -s "$D" shell am force-stop $PKG
  adb -s "$D" shell am start -W -n "$PKG/os.kei.MainActivity" >/dev/null 2>&1
  pause 4
}

journey_home_scroll() {
  for _ in 1 2 3; do fling_up; pause 0.4; done
  for _ in 1 2 3; do fling_down; pause 0.4; done
}

journey_section_switch() {
  for tag in main_bottom_tab_github main_bottom_tab_mcp main_bottom_tab_ba \
             main_bottom_tab_os main_bottom_tab_home; do
    XY=$(tab_xy "$tag"); [ -z "$XY" ] && continue
    tap $XY; pause 1.1
  done
}

journey_route_push() {
  for _ in 1 2 3; do
    XY=$(tab_xy home_settings_button); [ -z "$XY" ] && break
    tap $XY; pause 1.6
    adb -s "$D" shell input keyevent KEYCODE_BACK; pause 1.6
  done
}

goto_home
adb -s "$D" shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
"journey_$JOURNEY"
pause 1.0

adb -s "$D" shell dumpsys gfxinfo $PKG 2>/dev/null | perl -ne '
  BEGIN { our %v }
  chomp;
  $v{total}  = $1 if /^Total frames rendered: (\d+)/;
  $v{jank}   = $1 if /^Janky frames \(legacy\): (\d+)/;
  $v{jankpc} = $1 if /^Janky frames \(legacy\): \d+ \(([\d.]+)%\)/;
  $v{p50}    = $1 if /^50th percentile: (\d+)/;
  $v{p90}    = $1 if /^90th percentile: (\d+)/;
  $v{p95}    = $1 if /^95th percentile: (\d+)/;
  $v{p99}    = $1 if /^99th percentile: (\d+)/;
  $v{g50}    = $1 if /^50th gpu percentile: (\d+)/;
  $v{g90}    = $1 if /^90th gpu percentile: (\d+)/;
  $v{g99}    = $1 if /^99th gpu percentile: (\d+)/;
  $v{slowui} = $1 if /^Number Slow UI thread: (\d+)/;
  $v{slowdraw} = $1 if /^Number Slow issue draw commands: (\d+)/;
  $v{missed} = $1 if /^Number Frame deadline missed \(legacy\): (\d+)/;
  END {
    printf "%-22s frames=%-5s jank=%-5s (%5s%%)  cpu p50=%-3s p90=%-3s p95=%-3s p99=%-4s  gpu p50=%-3s p90=%-3s p99=%-3s  slowUI=%-4s slowDraw=%-4s missed=%s\n",
      $ENV{LABEL}."/".$ENV{JOURNEY}, $v{total}//"-", $v{jank}//"-", $v{jankpc}//"-",
      $v{p50}//"-", $v{p90}//"-", $v{p95}//"-", $v{p99}//"-",
      $v{g50}//"-", $v{g90}//"-", $v{g99}//"-",
      $v{slowui}//"-", $v{slowdraw}//"-", $v{missed}//"-";
  }'
