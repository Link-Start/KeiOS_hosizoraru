#!/usr/bin/env bash
# Bring one validation AVD up with the current Debug and BenchRelease builds on it, and prove that
# is what is on it. Driven through scripts/dev/avd_phone.sh and scripts/dev/avd_tablet.sh.
#
#   scripts/dev/avd_up.sh --profile phone|tablet [options]
#
# The daily question this answers is not "is the emulator running" but "is the thing running on it
# the thing I just wrote". Those come apart constantly: an AVD left up from yesterday, a Gradle run
# that failed after the install step, a build made on a branch you have since left. So the script
# does not report what it installed -- it reads the package back off the device afterwards, compares
# the APK's sha256 against the device's own copy, and fails if they disagree.
#
# The digest is what makes the check worth anything, because the version name will not do it. It
# carries a git description, which does not move for uncommitted work, so two different builds of a
# dirty tree wear the same versionName and versionCode. Two builds of a *clean* tree do too.
#
# Install is skipped when the device already holds a byte-identical APK. Measured on this project,
# that fires under --no-build and not otherwise: `:app:generateDebugBuildConfig` re-runs on every
# invocation and drags compile, dex and package along with it, so two consecutive no-op
# `assembleDebug` runs produce two different APKs. Re-running with a build is therefore never free
# -- roughly a minute, and a reinstall -- while `--no-build` against an already-current AVD costs
# two digests and nothing else.
#
# BENCHRELEASE SHARES THE RELEASE APPLICATION ID. `benchmarkRelease` does `initWith(release)` and
# release declares no applicationIdSuffix, so both are `os.kei` -- unlike `debug` (.debug) and
# `releaseDiagnostic` (.diag). Installing it therefore replaces any release build on the target and
# inherits its data directory. That is the point on a validation AVD and is not the point on a
# phone you carry, so a physical device is refused unless --allow-physical is passed.
#
# Exit codes: 0 up and verified, 1 something is not what it claims, 2 bad usage, 3 missing
# prerequisite, 4 the device never finished booting.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

AVD_PHONE="${AVD_PHONE:-KeiOS_API37_Validation}"
AVD_TABLET="${AVD_TABLET:-KeiOS_Pad_API37_Validation}"

PROFILE=""
AVD_NAME=""
DO_BUILD=1
ONLY=""
LAUNCH=0
HEADLESS=0
WIPE=0
REINSTALL=0
ALLOW_PHYSICAL=0
BOOT_TIMEOUT="${BOOT_TIMEOUT:-300}"

usage() {
  cat <<'EOF'
Usage: scripts/dev/avd_up.sh --profile phone|tablet [options]

  --profile <p>      phone or tablet; picks the AVD (override with --avd, AVD_PHONE, AVD_TABLET)
  --avd <name>       use this AVD instead of the profile's default
  --only <variant>   install only one of: debug, bench
  --no-build         skip Gradle; use whatever is already in app/build/outputs
  --launch           start the debug app once it is installed
  --headless         boot the emulator with -no-window
  --wipe             cold boot with -wipe-data; erases everything on that AVD first
  --reinstall        uninstall before installing; use when signing keys stopped matching
  --allow-physical   permit a non-emulator target (see the BenchRelease note in this file)
  --timeout <s>      seconds to wait for boot (default: 300)
  -h, --help         show this help

Exit codes: 0 verified, 1 mismatch, 2 bad usage, 3 missing prerequisite, 4 boot timeout.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      [[ $# -ge 2 ]] || { printf 'Missing phone|tablet after --profile\n' >&2; exit 2; }
      PROFILE="$2"; shift 2 ;;
    --avd)
      [[ $# -ge 2 ]] || { printf 'Missing name after --avd\n' >&2; exit 2; }
      AVD_NAME="$2"; shift 2 ;;
    --only)
      [[ "${2:-}" == "debug" || "${2:-}" == "bench" ]] || {
        printf 'Expected debug or bench after --only\n' >&2; exit 2; }
      ONLY="$2"; shift 2 ;;
    --no-build) DO_BUILD=0; shift ;;
    --launch) LAUNCH=1; shift ;;
    --headless) HEADLESS=1; shift ;;
    --wipe) WIPE=1; shift ;;
    --reinstall) REINSTALL=1; shift ;;
    --allow-physical) ALLOW_PHYSICAL=1; shift ;;
    --timeout)
      [[ "${2:-}" =~ ^[0-9]+$ ]] || { printf 'Missing seconds after --timeout\n' >&2; exit 2; }
      BOOT_TIMEOUT="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$AVD_NAME" ]]; then
  case "$PROFILE" in
    phone) AVD_NAME="$AVD_PHONE" ;;
    tablet) AVD_NAME="$AVD_TABLET" ;;
    "") printf 'One of --profile or --avd is required.\n' >&2; usage >&2; exit 2 ;;
    *) printf 'Unknown profile: %s\n' "$PROFILE" >&2; exit 2 ;;
  esac
fi

# ---------------------------------------------------------------- the SDK

sdk_dir() {
  local from_properties
  from_properties="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*\(.*\)$/\1/p' local.properties 2>/dev/null | head -1)"
  if [[ -n "$from_properties" && -d "$from_properties" ]]; then
    printf '%s' "$from_properties"
    return 0
  fi
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk"; do
    [[ -n "$candidate" && -d "$candidate" ]] && { printf '%s' "$candidate"; return 0; }
  done
  return 1
}

SDK="$(sdk_dir)" || { printf 'No Android SDK found (local.properties sdk.dir, ANDROID_HOME).\n' >&2; exit 3; }
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
[[ -x "$ADB" ]] || { printf 'No adb at %s\n' "$ADB" >&2; exit 3; }
[[ -x "$EMULATOR" ]] || { printf 'No emulator at %s\n' "$EMULATOR" >&2; exit 3; }
command -v python3 >/dev/null 2>&1 || { printf 'python3 is required.\n' >&2; exit 3; }

# Always the SDK's own adb, never whatever is first on PATH. Two adb binaries with different
# versions fight over the server and the loser reports "no compatible devices connected", which
# reads as a broken AVD rather than as a tooling collision.
OTHER_ADB="$(command -v adb 2>/dev/null || true)"
if [[ -n "$OTHER_ADB" && "$OTHER_ADB" != "$ADB" ]]; then
  printf 'note: using %s, not the adb on PATH (%s)\n' "$ADB" "$OTHER_ADB"
fi

# This machine's AVD list is shared with other projects -- `KeiMi_API37_Validation` belongs to one
# of them. Two projects driving one emulator fight over it: each installs its own packages, each
# assumes the state it left behind, and whichever ran second gets blamed. So the script will only
# touch an AVD named for this project, and that applies to --avd and to the AVD_PHONE / AVD_TABLET
# overrides alike. There is deliberately no flag to bypass it; retargeting at another project's
# emulator should take editing this line, not a hurried argument.
AVD_OWNED_PREFIX="KeiOS"
if [[ "$AVD_NAME" != "$AVD_OWNED_PREFIX"* ]]; then
  printf '%s is not a KeiOS AVD, so this will not touch it.\n' "$AVD_NAME" >&2
  printf 'AVDs on this machine are shared with other projects; driving one from two is how both\n' >&2
  printf 'end up with the other'"'"'s packages on it.\n' >&2
  exit 2
fi

if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$AVD_NAME"; then
  printf 'No such AVD: %s\n\nAvailable:\n' "$AVD_NAME" >&2
  "$EMULATOR" -list-avds 2>/dev/null | sed 's/^/  /' >&2
  exit 3
fi

# ------------------------------------------------------------- boot the AVD

serial_of_avd() {
  local serial name
  for serial in $("$ADB" devices | awk '/^emulator-/ {print $1}'); do
    name="$("$ADB" -s "$serial" emu avd name 2>/dev/null | head -1 | tr -d '\r' || true)"
    [[ "$name" == "$AVD_NAME" ]] && { printf '%s' "$serial"; return 0; }
  done
  return 1
}

SERIAL="$(serial_of_avd || true)"

if [[ -n "$SERIAL" && "$WIPE" -eq 1 ]]; then
  printf 'AVD %s is already running; --wipe needs it closed first.\n' "$AVD_NAME" >&2
  exit 2
fi

if [[ -z "$SERIAL" ]]; then
  BOOT_LOG="${TMPDIR:-/tmp}/keios-avd-$AVD_NAME.log"
  printf 'booting %s (log: %s)\n' "$AVD_NAME" "$BOOT_LOG"
  EMULATOR_ARGS=(-avd "$AVD_NAME")
  [[ "$HEADLESS" -eq 1 ]] && EMULATOR_ARGS+=(-no-window)
  [[ "$WIPE" -eq 1 ]] && EMULATOR_ARGS+=(-wipe-data)
  nohup "$EMULATOR" "${EMULATOR_ARGS[@]}" >"$BOOT_LOG" 2>&1 &

  DEADLINE=$(( $(date +%s) + BOOT_TIMEOUT ))
  while :; do
    SERIAL="$(serial_of_avd || true)"
    if [[ -n "$SERIAL" ]]; then
      BOOTED="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || true)"
      [[ "$BOOTED" == "1" ]] && break
    fi
    if (( $(date +%s) >= DEADLINE )); then
      printf '%s did not finish booting within %ss. Tail of %s:\n' "$AVD_NAME" "$BOOT_TIMEOUT" "$BOOT_LOG" >&2
      tail -20 "$BOOT_LOG" >&2 || true
      exit 4
    fi
    sleep 3
  done
  "$ADB" -s "$SERIAL" wait-for-device >/dev/null 2>&1 || true
  printf 'booted as %s\n' "$SERIAL"
else
  printf 'already running: %s is %s\n' "$AVD_NAME" "$SERIAL"
fi

if [[ "$SERIAL" != emulator-* && "$ALLOW_PHYSICAL" -ne 1 ]]; then
  printf 'Target %s is not an emulator, and BenchRelease installs over the release app.\n' "$SERIAL" >&2
  printf 'Pass --allow-physical if that is really what you want.\n' >&2
  exit 2
fi

# Unlock, so a freshly booted AVD is usable rather than sitting on the keyguard.
"$ADB" -s "$SERIAL" shell input keyevent 82 >/dev/null 2>&1 || true

# ---------------------------------------------------------------- the builds

VARIANTS=()
[[ "$ONLY" == "bench" ]] || VARIANTS+=("debug:assembleDebug")
[[ "$ONLY" == "debug" ]] || VARIANTS+=("benchmarkRelease:assembleBenchmarkRelease")

if [[ "$DO_BUILD" -eq 1 ]]; then
  TASKS=()
  for entry in "${VARIANTS[@]}"; do TASKS+=(":app:${entry#*:}"); done
  printf '\nbuilding %s\n' "${TASKS[*]}"
  ./gradlew "${TASKS[@]}"
else
  printf '\nskipping the build; reading app/build/outputs as it stands\n'
fi

# AGP writes applicationId, versionCode, versionName and the apk name per variant, so none of that
# has to be guessed from a filename or re-derived from the Gradle files.
metadata() {
  python3 - "$1" "$2" <<'PY'
import json, os, sys
variant, field = sys.argv[1], sys.argv[2]
path = f"app/build/outputs/apk/{variant}/output-metadata.json"
if not os.path.isfile(path):
    print("")
    raise SystemExit
data = json.load(open(path))
element = data["elements"][0]
print({
    "applicationId": data["applicationId"],
    "versionCode": element["versionCode"],
    "versionName": element["versionName"],
    "outputFile": element["outputFile"],
}[field])
PY
}

# ------------------------------------------------------------ install and prove

FAILURES=0

for entry in "${VARIANTS[@]}"; do
  VARIANT="${entry%%:*}"
  APPLICATION_ID="$(metadata "$VARIANT" applicationId)"
  OUTPUT_FILE="$(metadata "$VARIANT" outputFile)"
  if [[ -z "$APPLICATION_ID" || -z "$OUTPUT_FILE" ]]; then
    printf '\n%s: no output metadata. Build it, or drop --no-build.\n' "$VARIANT" >&2
    FAILURES=$((FAILURES + 1))
    continue
  fi
  APK="app/build/outputs/apk/$VARIANT/$OUTPUT_FILE"
  [[ -f "$APK" ]] || { printf '\n%s: missing %s\n' "$VARIANT" "$APK" >&2; FAILURES=$((FAILURES + 1)); continue; }

  LOCAL_SHA="$(shasum -a 256 "$APK" | cut -d' ' -f1)"
  printf '\n%s -> %s\n' "$VARIANT" "$APPLICATION_ID"
  printf '  built    %s  %s\n' "${LOCAL_SHA:0:16}" "$APK"

  # What the device holds now, compared by content rather than by version string. A version name
  # carries the git description, which does not move for uncommitted work -- so two different
  # builds of a dirty tree share one, and only the digest can tell them apart.
  DEVICE_PATH="$("$ADB" -s "$SERIAL" shell pm path "$APPLICATION_ID" 2>/dev/null | sed -n 's/^package://p' | head -1 | tr -d '\r' || true)"
  DEVICE_SHA=""
  if [[ -n "$DEVICE_PATH" ]]; then
    DEVICE_SHA="$("$ADB" -s "$SERIAL" shell sha256sum "$DEVICE_PATH" 2>/dev/null | awk '{print $1}' | tr -d '\r' || true)"
  fi

  if [[ -n "$DEVICE_SHA" && "$DEVICE_SHA" == "$LOCAL_SHA" ]]; then
    printf '  on %s  %s  already the current build, install skipped\n' "$SERIAL" "${DEVICE_SHA:0:16}"
  else
    if [[ "$REINSTALL" -eq 1 && -n "$DEVICE_PATH" ]]; then
      printf '  uninstalling first (--reinstall); its data goes with it\n'
      "$ADB" -s "$SERIAL" uninstall "$APPLICATION_ID" >/dev/null 2>&1 || true
    fi
    printf '  installing\n'
    if ! INSTALL_OUTPUT="$("$ADB" -s "$SERIAL" install -r -d "$APK" 2>&1)"; then
      printf '%s\n' "$INSTALL_OUTPUT" | sed 's/^/    /' >&2
      if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE\|signatures do not match" <<<"$INSTALL_OUTPUT"; then
        printf '    The installed copy was signed with a different key. --reinstall replaces it,\n' >&2
        printf '    and erases that package'"'"'s data on this device.\n' >&2
      fi
      FAILURES=$((FAILURES + 1))
      continue
    fi
  fi

  # Read it back rather than trusting the install. This is the whole point of the script.
  DEVICE_PATH="$("$ADB" -s "$SERIAL" shell pm path "$APPLICATION_ID" 2>/dev/null | sed -n 's/^package://p' | head -1 | tr -d '\r' || true)"
  VERIFY_SHA="$("$ADB" -s "$SERIAL" shell sha256sum "$DEVICE_PATH" 2>/dev/null | awk '{print $1}' | tr -d '\r' || true)"
  DEVICE_VERSION="$("$ADB" -s "$SERIAL" shell dumpsys package "$APPLICATION_ID" 2>/dev/null | awk -F= '/versionCode=/ {print $2; exit}' | awk '{print $1}' | tr -d '\r' || true)"

  if [[ -z "$VERIFY_SHA" ]]; then
    # Not every image ships sha256sum; fall back to the version code, and say which check ran.
    EXPECTED_CODE="$(metadata "$VARIANT" versionCode)"
    if [[ "$DEVICE_VERSION" == "$EXPECTED_CODE" ]]; then
      printf '  verified versionCode %s (no sha256sum on this image, digest not compared)\n' "$DEVICE_VERSION"
    else
      printf '  MISMATCH versionCode: device %s, built %s\n' "$DEVICE_VERSION" "$EXPECTED_CODE" >&2
      FAILURES=$((FAILURES + 1))
    fi
  elif [[ "$VERIFY_SHA" == "$LOCAL_SHA" ]]; then
    printf '  verified %s  versionCode %s  %s\n' "${VERIFY_SHA:0:16}" "$DEVICE_VERSION" \
      "$(metadata "$VARIANT" versionName)"
  else
    printf '  MISMATCH: device has %s, built %s\n' "${VERIFY_SHA:0:16}" "${LOCAL_SHA:0:16}" >&2
    FAILURES=$((FAILURES + 1))
  fi
done

if [[ "$LAUNCH" -eq 1 && "$ONLY" != "bench" ]]; then
  DEBUG_ID="$(metadata debug applicationId)"
  if [[ -n "$DEBUG_ID" ]]; then
    printf '\nlaunching %s\n' "$DEBUG_ID"
    "$ADB" -s "$SERIAL" shell monkey -p "$DEBUG_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
  fi
fi

printf '\n%s  %s\n' "$AVD_NAME" "$SERIAL"
if [[ "$FAILURES" -eq 0 ]]; then
  printf 'Everything on it is the build in this tree.\n'
  exit 0
fi
printf '%d variant(s) are not what this tree built.\n' "$FAILURES" >&2
exit 1
