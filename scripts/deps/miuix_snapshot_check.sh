#!/usr/bin/env bash
# Report whether the pinned Miuix snapshot is the newest one published, and what landed
# upstream in between. Miuix iterates fast, so "is this already fixed upstream?" is worth
# asking before writing a local workaround.
#
# Exit codes: 0 up to date, 1 behind, 2 bad usage, 3 missing prerequisite.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

OWNER="compose-miuix-ui"
REPO="$OWNER/miuix"
PACKAGE="top.yukonga.miuix.kmp.miuix-ui-android"

SHOW_FILES=0
SHOW_ALL=0
DO_UPDATE=0
DIFF_PATTERN=""

usage() {
  cat <<'EOF'
Usage: scripts/deps/miuix_snapshot_check.sh [options]

  --files             list every library file that changed in the range
  --diff <pattern>    print the patch for changed files matching <pattern>, e.g. --diff PullToRefresh
  --all               include example/CI/tooling commits in the listing
  --update            move the pin to the newest: gradle.properties, the version catalog and the
                      build-doc examples, whatever value each of them currently holds
  -h, --help          show this help

Exit codes: 0 up to date, 1 behind, 2 bad usage, 3 missing prerequisite.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --files) SHOW_FILES=1; shift ;;
    --all) SHOW_ALL=1; shift ;;
    --update) DO_UPDATE=1; shift ;;
    --diff)
      [[ $# -ge 2 ]] || { printf 'Missing pattern after --diff\n' >&2; exit 2; }
      DIFF_PATTERN="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

for tool in gh python3; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf '%s is required.\n' "$tool" >&2
    exit 3
  }
done

property_in() {
  # First assignment wins, matching how Gradle reads a properties file.
  [[ -f "$1" ]] || return 1
  local value
  value="$(sed -n "s/^[[:space:]]*$2[[:space:]]*=[[:space:]]*\(.*\)$/\1/p" "$1" | head -1)"
  [[ -n "$value" ]] || return 1
  value="${value%\"}"
  printf '%s' "${value#\"}"
}

# Same order the build resolves in: a Gradle property first (GRADLE_USER_HOME wins over the
# project file), then local.properties, then the version catalog. Anything found later is
# reported as shadowed when it disagrees — a stale entry there is a classic "why is my pin ignored" trap.
PINNED=""
PINNED_FROM=""
for candidate in \
  "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties:miuix.version" \
  "gradle.properties:miuix.version" \
  "local.properties:miuix.version" \
  "gradle/libs.versions.toml:miuix"; do
  file="${candidate%:*}"
  key="${candidate##*:}"
  if value="$(property_in "$file" "$key")"; then
    if [[ -z "$PINNED" ]]; then
      PINNED="$value"
      PINNED_FROM="$file"
    elif [[ "$value" != "$PINNED" ]]; then
      printf 'note: %s sets %s=%s, shadowed by %s\n' "$file" "$key" "$value" "$PINNED_FROM"
    fi
  fi
done

[[ -n "$PINNED" ]] || { printf 'No Miuix version found in any of the usual places.\n' >&2; exit 3; }

VERSIONS="$(gh api "/orgs/$OWNER/packages/maven/$PACKAGE/versions?per_page=100")" || {
  printf 'Could not read published versions. The token needs read:packages.\n' >&2
  exit 3
}

read -r LATEST LATEST_AT BEHIND <<<"$(printf '%s' "$VERSIONS" | PINNED="$PINNED" python3 -c '
import json, os, sys
versions = sorted(json.load(sys.stdin), key=lambda v: v["created_at"], reverse=True)
names = [v["name"] for v in versions]
pinned = os.environ["PINNED"]
# Only the newest 100 are fetched; a pin older than that (or never published) has no index.
behind = names.index(pinned) if pinned in names else "100+"
print(names[0], versions[0]["created_at"][:16], behind)
')"

printf 'pinned  %s  (%s)\n' "$PINNED" "$PINNED_FROM"
printf 'latest  %s  (%s)\n' "$LATEST" "$LATEST_AT"

if [[ "$PINNED" == "$LATEST" ]]; then
  printf '\nUp to date. A Miuix-side fix for your problem would have to be unpublished.\n'
  exit 0
fi

if [[ "$BEHIND" == "100+" ]]; then
  printf '\nThe pin is not among the newest 100 published snapshots (older, or never published).\n'
else
  printf '\n%s snapshot(s) behind.\n' "$BEHIND"
fi

# Snapshots are named <version>-<sha>-SNAPSHOT; a plain version maps to its release tag.
sha_of() {
  if [[ "$1" =~ -([0-9a-f]{7,40})-SNAPSHOT$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  else
    printf 'v%s' "$1"
  fi
}

COMPARE_FILE="$(mktemp)"
trap 'rm -f "$COMPARE_FILE"' EXIT
gh api "repos/$REPO/compare/$(sha_of "$PINNED")...$(sha_of "$LATEST")" > "$COMPARE_FILE" || {
  printf 'Could not compare %s...%s upstream.\n' "$(sha_of "$PINNED")" "$(sha_of "$LATEST")" >&2
  exit 3
}

printf '\ncommits\n'
SHOW_ALL="$SHOW_ALL" python3 -c '
import json, os, re, sys
# Upstream prefixes every subject; everything outside these buckets touches the library.
skip = re.compile(r"^(example|ci|build|docs|chore)[:(]")
# A dependency bump is never skipped, whatever its prefix: it changes what the published artifacts
# pull onto our classpath (e.g. a JetBrains Compose bump moves every androidx.compose module).
deps = re.compile(r"\(deps\)")
show_all = os.environ["SHOW_ALL"] == "1"
kept = dropped = 0
for c in json.load(open(sys.argv[1]))["commits"]:
    subject = c["commit"]["message"].split("\n")[0]
    is_deps = bool(deps.search(subject))
    if not show_all and not is_deps and skip.search(subject):
        dropped += 1
        continue
    kept += 1
    tag = "  [deps: check the classpath]" if is_deps else ""
    print("  %s %s %s%s" % (c["sha"][:8], c["commit"]["author"]["date"][:10], subject, tag))
if not kept:
    print("  (none touch the library)")
if dropped:
    print("  ... plus %d example/CI/tooling commit(s); --all to see them" % dropped)
' "$COMPARE_FILE"

if [[ "$SHOW_FILES" == 1 || -n "$DIFF_PATTERN" ]]; then
  printf '\nlibrary files changed\n'
  python3 -c '
import json, sys
files = [f for f in json.load(open(sys.argv[1]))["files"]
         if f["filename"].startswith("miuix-") and "/example/" not in f["filename"]]
for f in files:
    print("  +%-4d -%-4d %s" % (f["additions"], f["deletions"], f["filename"]))
if not files:
    print("  (none)")
' "$COMPARE_FILE"
fi

if [[ -n "$DIFF_PATTERN" ]]; then
  printf '\npatch for files matching %s\n' "$DIFF_PATTERN"
  PATTERN="$DIFF_PATTERN" python3 -c '
import json, os, sys
pattern = os.environ["PATTERN"].lower()
hit = False
for f in json.load(open(sys.argv[1]))["files"]:
    if pattern in f["filename"].lower() and f.get("patch"):
        hit = True
        print("\n--- %s" % f["filename"])
        print(f["patch"])
if not hit:
    print("  (no changed file matches, or GitHub omitted the patch as too large)")
' "$COMPARE_FILE"
fi

if [[ "$DO_UPDATE" == 1 ]]; then
  printf '\nupdating pin\n'
  # Rewrite each place by its key, not by searching for the effective pin: a file holding an older
  # value (the catalog, shadowed by gradle.properties, is the usual one) would otherwise be skipped
  # and keep drifting.
  LATEST="$LATEST" python3 - <<'PY'
import os, re
latest = os.environ["LATEST"]
targets = [
    ("gradle.properties", r"^(\s*miuix\.version\s*=\s*)(\S+)(\s*)$"),
    ("gradle/libs.versions.toml", r'^(\s*miuix\s*=\s*")([^"]+)(".*)$'),
    ("readme/BUILD.md", r"^(\s*miuix\.version\s*=\s*)(\S+)(\s*)$"),
    ("readme/BUILD_CN.md", r"^(\s*miuix\.version\s*=\s*)(\S+)(\s*)$"),
]
for path, pattern in targets:
    if not os.path.exists(path):
        continue
    lines = open(path, encoding="utf-8").read().split("\n")
    old = []
    for i, line in enumerate(lines):
        m = re.match(pattern, line)
        if m and m.group(2) != latest:
            old.append(m.group(2))
            lines[i] = m.group(1) + latest + m.group(3)
    if old:
        open(path, "w", encoding="utf-8").write("\n".join(lines))
        print("  %s  (was %s)" % (path, ", ".join(sorted(set(old)))))
    elif any(re.match(pattern, l) for l in lines):
        print("  %s  already %s" % (path, latest))
    else:
        print("  %s  has no miuix pin line; left alone" % path)
PY
  case "$PINNED_FROM" in
    gradle.properties|gradle/libs.versions.toml) ;;
    *)
      printf '\nwarning: the effective pin comes from %s, which shadows the repo files and was not\n' "$PINNED_FROM"
      printf 'changed. Remove or update miuix.version there, or the build keeps using %s.\n' "$PINNED"
      ;;
  esac
  printf '\nBuild and check on device before committing: a snapshot can carry behaviour changes.\n'
else
  printf '\n  --diff <pattern>  patch for one component, e.g. --diff PullToRefresh\n'
  printf '  --update          move the pin to %s\n' "$LATEST"
fi

exit 1
