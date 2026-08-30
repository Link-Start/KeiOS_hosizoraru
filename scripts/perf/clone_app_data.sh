#!/usr/bin/env bash
# Copy one installed build's app data over another's, so an A/B pair renders the same content.
#
#   scripts/perf/clone_app_data.sh [source-pkg] [target-pkg]
#
# Frame-time A/B means two builds differing in one thing. Installing the second beside the first
# (see the `releaseDiagnostic` build type) keeps both on the device, but a fresh install starts with
# empty storage — and on a data-driven page that is not the same screen, so the numbers are not
# comparable. Measured on the BA office page: 38.2ms against 45.7ms purely because the second build
# had no account configured and therefore a different card list.
#
# Needs root, so this is an emulator tool. Both apps are stopped first: copying storage under a
# running process leaves it reading files that changed beneath it.
set -uo pipefail
D=${ANDROID_SERIAL:-emulator-5554}
SRC=${1:-os.kei}
DST=${2:-os.kei.diag}
A() { adb -s "$D" "$@"; }

A root >/dev/null 2>&1
sleep 2
[ "$(A shell id -u | tr -d '\r')" = "0" ] || { echo "needs root; adb root failed on $D"; exit 1; }

for pkg in "$SRC" "$DST"; do
  A shell pm list packages | grep -qx "package:$pkg" || { echo "$pkg is not installed"; exit 2; }
  A shell am force-stop "$pkg"
done

# `appId` on current platforms, `userId` on older ones — accept either.
DST_UID=$(A shell dumpsys package "$DST" \
  | grep -m1 -oE '(appId|userId)=[0-9]+' | grep -oE '[0-9]+' | tr -d '\r')
[ -n "$DST_UID" ] || { echo "could not read $DST uid"; exit 3; }

A shell "rm -rf /data/data/$DST/* 2>/dev/null"
A shell "cp -a /data/data/$SRC/. /data/data/$DST/ 2>/dev/null"
# The copy carries the source's ownership and SELinux labels, which the target cannot read.
A shell "chown -R $DST_UID:$DST_UID /data/data/$DST"
A shell "restorecon -R /data/data/$DST"
echo "cloned $SRC -> $DST (uid $DST_UID)"
