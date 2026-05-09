#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

WITH_AUDIT=1
FETCH_AUDIT=1
STAMP="${COMPOSE_AUDIT_STAMP:-$(date +%Y%m%d-%H%M%S)}"
ARTIFACT_DIR="${COMPOSE_AUDIT_ARTIFACT_DIR:-$ROOT_DIR/artifacts/compose-audit-$STAMP}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --quick)
      WITH_AUDIT=0
      shift
      ;;
    --no-fetch)
      FETCH_AUDIT=0
      shift
      ;;
    --artifact-dir)
      ARTIFACT_DIR="$2"
      shift 2
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

mkdir -p "$ARTIFACT_DIR"
LOG_FILE="$ARTIFACT_DIR/compose-qa.log"
exec > >(tee "$LOG_FILE") 2>&1

run_step() {
  local title="$1"
  shift
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$title"
  "$@"
}

resolve_audit_init_script() {
  if [[ -n "${COMPOSE_AUDIT_INIT_SCRIPT:-}" && -f "${COMPOSE_AUDIT_INIT_SCRIPT:-}" ]]; then
    printf '%s\n' "$COMPOSE_AUDIT_INIT_SCRIPT"
    return 0
  fi

  local default_root="/tmp/keios-compose-skill-audit"
  local default_init="$default_root/jetpack-compose-audit/scripts/compose-reports.init.gradle"
  if [[ -f "$default_init" ]]; then
    printf '%s\n' "$default_init"
    return 0
  fi

  local probe_init="/tmp/keios-compose-skill-probe/jetpack-compose-audit/scripts/compose-reports.init.gradle"
  if [[ -f "$probe_init" ]]; then
    printf '%s\n' "$probe_init"
    return 0
  fi

  if [[ "$FETCH_AUDIT" -eq 1 ]]; then
    rm -rf "$default_root"
    git clone --depth 1 https://github.com/hamen/compose_skill "$default_root" >/dev/null
    if [[ -f "$default_init" ]]; then
      printf '%s\n' "$default_init"
      return 0
    fi
  fi

  return 1
}

copy_audit_outputs() {
  local audit_out="$ROOT_DIR/app/build/compose_audit"
  [[ -d "$audit_out" ]] || return 0
  cp -f "$audit_out/app-classes.txt" "$ARTIFACT_DIR/app-classes.txt" 2>/dev/null || true
  cp -f "$audit_out/app-composables.csv" "$ARTIFACT_DIR/app-composables.csv" 2>/dev/null || true
  cp -f "$audit_out/app-composables.txt" "$ARTIFACT_DIR/app-composables.txt" 2>/dev/null || true
  cp -f "$audit_out/release/app-module.json" "$ARTIFACT_DIR/app-module.json" 2>/dev/null || true
}

write_metric_summary() {
  local module_json="$ARTIFACT_DIR/app-module.json"
  local summary="$ARTIFACT_DIR/compose-metrics-summary.txt"
  [[ -f "$module_json" ]] || return 0
  if command -v jq >/dev/null 2>&1; then
    jq -r '
      "totalComposables=\(.totalComposables)",
      "skippableComposables=\(.skippableComposables)",
      "knownUnstableArguments=\(.knownUnstableArguments)",
      "inferredUnstableClasses=\(.inferredUnstableClasses)",
      "markedStableClasses=\(.markedStableClasses)",
      "effectivelyStableClasses=\(.effectivelyStableClasses)"
    ' "$module_json" > "$summary"
  else
    sed -n \
      -e 's/[", ]//g' \
      -e '/^\(totalComposables\|skippableComposables\|knownUnstableArguments\|inferredUnstableClasses\|markedStableClasses\|effectivelyStableClasses\):/ { s/:/=/; p; }' \
      "$module_json" > "$summary"
  fi
  printf '\nCompose metrics summary:\n'
  cat "$summary"
}

printf 'Compose QA artifacts: %s\n' "$ARTIFACT_DIR"

run_step "Debug Kotlin compile" ./gradlew :app:compileDebugKotlin
run_step "Markdown parser unit test" ./gradlew :app:testDebugUnitTest --tests os.kei.ui.page.main.widget.markdown.AppMarkdownParserTest

if [[ "$WITH_AUDIT" -eq 1 ]]; then
  if AUDIT_INIT_SCRIPT="$(resolve_audit_init_script)"; then
    run_step "Release Compose audit" ./gradlew clean :app:compileReleaseKotlin --init-script "$AUDIT_INIT_SCRIPT" --no-daemon --quiet --max-workers=2
    copy_audit_outputs
    write_metric_summary
  else
    printf '\nCompose audit init script unavailable. Set COMPOSE_AUDIT_INIT_SCRIPT or rerun with network fetch enabled.\n'
    exit 3
  fi
fi

run_step "Diff whitespace check" git diff --check

printf '\nCompose QA gate passed. Log: %s\n' "$LOG_FILE"
