#!/usr/bin/env bash
# Capture a repository's GitHub releases response as a test fixture, trimmed to what the parser
# actually reads.
#
#   scripts/qa/capture_release_fixture.sh <owner/repo> [--limit <n>] [--name <slug>] [--stdout]
#
# Every release-check report so far has been diagnosed the same way: fetch the real response, cut it
# down to the fields `GitHubApiTokenReleaseStrategy.parseReleaseEntry` looks at, and drive that
# through the strategy's own parser in a test. The cutting was done by hand -- 324KB to 19KB for
# stratumauth, 260KB to 68KB for NekoBox -- which is slow, and worse, is a judgement call about
# which fields matter made separately each time. Get it wrong by dropping a field the parser reads
# and the fixture quietly stops reproducing the bug.
#
# So the field list lives here instead, and is the one the parser reads:
#
#   draft  tag_name  name  html_url  id  node_id  body  prerelease
#   author{login,avatar_url}  published_at  created_at  assets[{updated_at}]
#
# `body` is kept whole because the channel heuristics read it for pre-releases, and it is most of
# the weight. Asset objects are kept as objects with only `updated_at`, because the parser counts
# them -- an empty list is the claim "nothing to install here", and flattening them to a number
# would lose the shape the parser is given.
#
# Needs `gh` authenticated (`gh auth status`) and `jq`. Guest access works for public repositories
# but is rate limited; the token is used when there is one.
#
# Exit codes: 0 captured, 2 bad usage, 3 missing tool or auth, 4 request failed.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

FIXTURE_DIR="feature-github-engine/src/test/resources"
LIMIT=30
NAME=""
TO_STDOUT=0
SLUG=""

usage() {
  cat <<'EOF'
Usage: scripts/qa/capture_release_fixture.sh <owner/repo> [options]

  --limit <n>   releases to request, 1-100 (default: 30, matching the app's own page size)
  --name <slug> fixture basename; default is the repository name lowercased
  --stdout      write to stdout instead of the test resources directory
  -h, --help    show this help

Writes feature-github-engine/src/test/resources/<slug>-releases.json.

Exit codes: 0 captured, 2 bad usage, 3 missing tool or auth, 4 request failed.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    --limit) LIMIT="${2:-}"; shift 2 ;;
    --name) NAME="${2:-}"; shift 2 ;;
    --stdout) TO_STDOUT=1; shift ;;
    -*) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
    *)
      if [ -n "$SLUG" ]; then echo "only one repository at a time" >&2; exit 2; fi
      SLUG="$1"; shift ;;
  esac
done

if [ -z "$SLUG" ]; then usage >&2; exit 2; fi
case "$SLUG" in */*) ;; *) echo "expected owner/repo, got: $SLUG" >&2; exit 2 ;; esac
case "$LIMIT" in ''|*[!0-9]*) echo "--limit takes a number" >&2; exit 2 ;; esac
if [ "$LIMIT" -lt 1 ] || [ "$LIMIT" -gt 100 ]; then
  echo "--limit must be 1-100 (GitHub's own ceiling for per_page)" >&2
  exit 2
fi

for tool in gh jq; do
  command -v "$tool" >/dev/null 2>&1 || { echo "$tool is required but not installed" >&2; exit 3; }
done
gh auth status >/dev/null 2>&1 || echo "warning: gh is not authenticated; guest rate limits apply" >&2

OWNER="${SLUG%%/*}"
REPO="${SLUG##*/}"
if [ -z "$NAME" ]; then
  NAME="$(printf '%s' "$REPO" | tr '[:upper:]' '[:lower:]')"
fi

RAW="$(mktemp)"
trap 'rm -f "$RAW"' EXIT

if ! gh api "repos/$OWNER/$REPO/releases?per_page=$LIMIT" > "$RAW" 2>/dev/null; then
  echo "request failed for $SLUG -- check the name, and \`gh auth status\`" >&2
  exit 4
fi

# The projection, and the only place this field list is written down.
TRIMMED="$(jq '[ .[] | {
  draft,
  tag_name,
  name,
  html_url,
  id,
  node_id,
  body,
  prerelease,
  author: (if .author then { login: .author.login, avatar_url: .author.avatar_url } else null end),
  published_at,
  created_at,
  assets: [ .assets[]? | { updated_at } ]
} ]' "$RAW")"

RAW_BYTES="$(wc -c < "$RAW" | tr -d ' ')"
TRIM_BYTES="$(printf '%s' "$TRIMMED" | wc -c | tr -d ' ')"
COUNT="$(printf '%s' "$TRIMMED" | jq 'length')"
PRE_COUNT="$(printf '%s' "$TRIMMED" | jq '[ .[] | select(.prerelease) ] | length')"
EMPTY_ASSETS="$(printf '%s' "$TRIMMED" | jq '[ .[] | select((.assets | length) == 0) ] | length')"

if [ "$TO_STDOUT" -eq 1 ]; then
  printf '%s\n' "$TRIMMED"
else
  OUT="$FIXTURE_DIR/$NAME-releases.json"
  mkdir -p "$FIXTURE_DIR"
  printf '%s\n' "$TRIMMED" > "$OUT"
  echo "wrote $OUT" >&2
fi

# All of this goes to stderr, so `--stdout` stays a clean JSON pipe.
{
  echo "  $SLUG: $COUNT releases ($PRE_COUNT pre-release, $EMPTY_ASSETS with no assets)"
  echo "  $RAW_BYTES bytes -> $TRIM_BYTES bytes"
  if [ "$COUNT" -ge "$LIMIT" ]; then
    # The app records this as windowWasFull; a fixture captured at the limit is a slice of the
    # history, and any assertion about the shape of the list has to say so.
    echo "  the page came back full -- this repository has more releases than the fixture holds"
  fi
} >&2
