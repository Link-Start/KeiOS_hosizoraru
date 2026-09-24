#!/usr/bin/env bash
# Cost of switching Home -> <page>, measured per target so the tabs can be ranked.
# Always starts settled on Home so every target sees the same outgoing page.
#
#   TAG=a ./switch_into_page.sh && python3 switch_report.py 'sw_*_*.csv'
#
# A page composes once per process (MainPageActivationState.hasActivated), so a
# single pass measures FIRST entry for every tab. To measure repeat entries,
# bounce Home <-> one tab instead of walking all four.
set -uo pipefail
D=${D:-5eea1f50}
PKG=${PKG:-os.kei.diag}  # never os.kei: that is the real install, with real refreshes
DIR="$(cd "$(dirname "$0")" && pwd)"
TAG="${TAG:-run}"
pause() { perl -e "select(undef,undef,undef,$1)"; }
tab_xy() {
  adb -s "$D" exec-out uiautomator dump /dev/tty 2>/dev/null | tr '<' '\n<' \
    | grep "resource-id=\"$1\"" | grep -o 'bounds="[^"]*"' | head -1 \
    | perl -ne 'if (/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/){printf "%d %d",($1+$3)/2,($2+$4)/2}'
}

adb -s "$D" shell am force-stop $PKG
adb -s "$D" shell am start -W -n "$PKG/os.kei.MainActivity" >/dev/null 2>&1
pause 5

HOME_XY=$(tab_xy main_bottom_tab_home)
for page in github mcp os ba; do
  XY=$(tab_xy "main_bottom_tab_$page")
  [ -z "$XY" ] && { echo "no tab for $page"; continue; }
  # Settle on Home first so the outgoing page is identical for every target.
  [ -n "$HOME_XY" ] && adb -s "$D" shell input tap $HOME_XY
  pause 1.8
  adb -s "$D" shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
  adb -s "$D" shell input tap $XY
  pause 1.5
  adb -s "$D" shell dumpsys gfxinfo $PKG framestats 2>/dev/null > "$DIR/sw_${TAG}_$page.csv"
done
