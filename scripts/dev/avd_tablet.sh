#!/usr/bin/env bash
# Bring the Android 17 Tablet validation AVD up with this tree's Debug and BenchRelease on it.
#
#   scripts/dev/avd_tablet.sh [options]
#
# Thin wrapper. Everything -- the boot wait, the build, the install, and the read-back that proves
# the device holds what this tree built -- lives in avd_up.sh, including its --help and its exit
# codes. Two entry points rather than two copies: the profiles differ by one AVD name, and a second
# copy of two hundred lines is a second place for that logic to drift.
#
# Override the AVD with AVD_TABLET=<name> or --avd <name>.
set -euo pipefail
exec "$(cd "$(dirname "$0")" && pwd)/avd_up.sh" --profile tablet "$@"
