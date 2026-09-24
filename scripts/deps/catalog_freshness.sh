#!/usr/bin/env bash
# Report which version catalog pins are behind, using the rule this project already bumps by:
# the newest release candidate or stable. Sibling of miuix_snapshot_check.sh, which owns the one
# pin this cannot reason about.
#
#   scripts/deps/catalog_freshness.sh [--all] [--only <ref>] [--update] [--self-test]
#
# The rule, and why it is not "highest number wins":
#
#   1.6.0-rc01  beats  1.5.0       -- a newer release line wins even as a candidate
#   1.5.0       beats  1.5.0-rc01  -- inside one release line, the final wins
#   alpha, beta, dev, eap, M1, snapshot                  -- excluded outright
#
# Getting that wrong is not hypothetical: a previous pass ranked 1.13.0-rc01 above 1.13.0 because
# "rc01"'s digits leaked into the numeric key. `--self-test` runs the comparator against both
# directions of that case and exits without touching the network, which is worth doing after any
# edit to the scoring below.
#
# Two things this deliberately refuses to call an upgrade, because both have happened here:
#
#   pin ahead     the repository's newest is older than what is pinned (`kxml2` 2.3.0 against
#                 Central's 2.2.2). Following it would be a downgrade.
#   unrankable    the pin is not a plain release at all (`xmlpull` 1.1.3.4d_b4_min, or a JitPack
#                 commit hash). Nothing here can order it; compare by hand.
#
# Exit codes: 0 everything current, 1 something is behind, 2 bad usage, 3 missing prerequisite.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

CATALOG="gradle/libs.versions.toml"

SHOW_ALL=0
DO_UPDATE=0
SELF_TEST=0
ONLY_REF=""

usage() {
  cat <<'EOF'
Usage: scripts/deps/catalog_freshness.sh [options]

  --all            list every ref, including the ones that are current or cannot be ranked
  --only <ref>     check a single version ref, e.g. --only kotlin
  --update         rewrite the behind pins in gradle/libs.versions.toml
  --self-test      check the version comparator and exit; no network
  -h, --help       show this help

Exit codes: 0 current, 1 behind, 2 bad usage, 3 missing prerequisite.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all) SHOW_ALL=1; shift ;;
    --update) DO_UPDATE=1; shift ;;
    --self-test) SELF_TEST=1; shift ;;
    --only)
      [[ $# -ge 2 ]] || { printf 'Missing ref after --only\n' >&2; exit 2; }
      ONLY_REF="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'Unknown option: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

command -v python3 >/dev/null 2>&1 || { printf 'python3 is required.\n' >&2; exit 3; }
python3 -c 'import tomllib' 2>/dev/null || {
  printf 'python3 with tomllib is required (3.11+).\n' >&2
  exit 3
}
[[ -f "$CATALOG" ]] || { printf 'No %s here.\n' "$CATALOG" >&2; exit 3; }

CATALOG="$CATALOG" SHOW_ALL="$SHOW_ALL" DO_UPDATE="$DO_UPDATE" SELF_TEST="$SELF_TEST" \
  ONLY_REF="$ONLY_REF" python3 - <<'PY'
import concurrent.futures as cf
import io
import json
import os
import re
import subprocess
import sys
import tomllib
import urllib.request

CATALOG = os.environ["CATALOG"]
SHOW_ALL = os.environ["SHOW_ALL"] == "1"
DO_UPDATE = os.environ["DO_UPDATE"] == "1"
SELF_TEST = os.environ["SELF_TEST"] == "1"
ONLY_REF = os.environ["ONLY_REF"]

# ---------------------------------------------------------------- the rule

PRERELEASE = re.compile(r"(alpha|beta|-dev|dev\d|snapshot|[-.]M\d|eap|pre)", re.I)
RELEASE = re.compile(r"^(?P<rel>\d+(?:\.\d+)*)$")
CANDIDATE = re.compile(r"^(?P<rel>\d+(?:\.\d+)*)[-.]?(?:rc|RC)[-.]?(?P<n>\d*)$")


def rank(version):
    """Sort key for a version, or None when it is not a stable/rc release.

    The release numbers are compared first and the rc/final flag only after, which is the half a
    naive key gets wrong: it must never let "rc01"'s digits participate in the release comparison.
    """
    version = version.strip()
    if PRERELEASE.search(version):
        return None
    matched = RELEASE.match(version)
    if matched:
        return (tuple(int(part) for part in matched.group("rel").split(".")), 1, 0)
    matched = CANDIDATE.match(version)
    if matched:
        return (
            tuple(int(part) for part in matched.group("rel").split(".")),
            0,
            int(matched.group("n") or 0),
        )
    return None


def newest(versions):
    scored = [(rank(v), v) for v in versions]
    scored = [pair for pair in scored if pair[0] is not None]
    return max(scored)[1] if scored else None


if SELF_TEST:
    cases = [
        (["1.13.0", "1.13.0-rc01"], "1.13.0"),
        (["1.13.0-rc01", "1.13.0"], "1.13.0"),
        (["1.5.0", "1.6.0-rc01"], "1.6.0-rc01"),
        (["1.6.0-rc01", "1.5.0"], "1.6.0-rc01"),
        (["2.4.10", "2.4.20-RC2", "2.4.20"], "2.4.20"),
        (["2.4.10", "2.4.20-RC2"], "2.4.20-RC2"),
        (["1.5.0-rc01", "1.5.0-rc02"], "1.5.0-rc02"),
        (["9.4.0-alpha01", "9.3.2"], "9.3.2"),
        (["1.11.0", "1.12.0-beta01"], "1.11.0"),
        (["1.9.0", "1.10.0"], "1.10.0"),
        (["2.0.0", "10.0.0"], "10.0.0"),
        (["1.1.3.4d_b4_min"], None),
    ]
    failed = 0
    for versions, want in cases:
        got = newest(versions)
        if got != want:
            failed += 1
        print(f"  {'ok ' if got == want else 'BAD'} {versions} -> {got} (want {want})")
    print("comparator", "PASS" if failed == 0 else f"FAIL ({failed})")
    raise SystemExit(0 if failed == 0 else 1)

# ------------------------------------------------------- catalog -> coordinates

catalog = tomllib.load(open(CATALOG, "rb"))
versions = catalog.get("versions", {})

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
PORTAL = "https://plugins.gradle.org/m2"
JITPACK = "https://jitpack.io"

# A plugin id is not an artifact. These three resolve from somewhere other than the marker, and
# the marker is the fallback for anything else.
PLUGIN_ARTIFACTS = {
    "com.android.": "com.android.tools.build:gradle",
    "org.jetbrains.kotlin.": "org.jetbrains.kotlin:kotlin-gradle-plugin",
    "androidx.baselineprofile": "androidx.benchmark:benchmark-baseline-profile-gradle-plugin",
}

# Refs this cannot answer for, and the reason, so the skip is never silent.
NOT_A_DEPENDENCY = "not a dependency version"
EXCLUDED = {
    "miuix": "owned by scripts/deps/miuix_snapshot_check.sh",
}


def plugin_coordinate(plugin_id):
    for prefix, artifact in PLUGIN_ARTIFACTS.items():
        if plugin_id.startswith(prefix):
            return artifact
    return f"{plugin_id}:{plugin_id}.gradle.plugin"


# A library entry wins over a plugin entry, because it names a real artifact rather than a marker,
# and the first library entry wins over later ones so the reported coordinate is the one a reader
# would have guessed. Any artifact of the same train answers the question either way.
coordinate = {}
for entry in catalog.get("plugins", {}).values():
    ref = (entry.get("version") or {}).get("ref") if isinstance(entry.get("version"), dict) else None
    if ref and "id" in entry:
        coordinate[ref] = plugin_coordinate(entry["id"])
from_library = set()
for entry in catalog.get("libraries", {}).values():
    ref = (entry.get("version") or {}).get("ref") if isinstance(entry.get("version"), dict) else None
    if ref and "module" in entry and ref not in from_library:
        coordinate[ref] = entry["module"]
        from_library.add(ref)


def repositories_for(group):
    """Ask the repository most likely to have it first, then the others."""
    if group.startswith(("androidx.", "com.android.", "com.google.android.")):
        order = [GOOGLE, CENTRAL, PORTAL, JITPACK]
    elif group.startswith("com.github."):
        order = [JITPACK, CENTRAL, GOOGLE, PORTAL]
    elif group.endswith(".gradle.plugin"):
        order = [PORTAL, CENTRAL, GOOGLE, JITPACK]
    else:
        order = [CENTRAL, GOOGLE, PORTAL, JITPACK]
    return order


def published(module):
    group, artifact = module.split(":", 1)
    for repository in repositories_for(group):
        url = f"{repository}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
        try:
            with urllib.request.urlopen(url, timeout=40) as response:
                body = response.read().decode("utf-8", "replace")
        except Exception:
            continue
        found = re.findall(r"<version>([^<]+)</version>", body)
        if found:
            return found, repository
    return None, None


def verdict(ref):
    if ref in EXCLUDED:
        return ref, None, None, "skipped", EXCLUDED[ref]
    module = coordinate.get(ref)
    if module is None:
        return ref, None, None, "skipped", NOT_A_DEPENDENCY
    found, repository = published(module)
    pinned = versions[ref]
    if not found:
        return ref, module, None, "unreachable", "no maven-metadata.xml in any known repository"
    best = newest(found)
    if best is None:
        return ref, module, None, "unrankable", "the repository publishes no plain release"
    if rank(pinned) is None:
        return ref, module, best, "unrankable", f"pin {pinned!r} is not a plain release"
    if best == pinned:
        return ref, module, best, "current", ""
    if rank(best) < rank(pinned):
        return ref, module, best, "pin ahead", f"newest published is {best}"
    return ref, module, best, "behind", ""


# Pins that live outside the catalog. They are checked by the same rule, reported under a
# "file:" label, and never rewritten by --update, which only knows the catalog.
OUTSIDE = {}
if os.path.exists("settings.gradle.kts"):
    for plugin_id, version in re.findall(
        r'id\("([^"]+)"\)\s+version\s+"([^"]+)"', io.open("settings.gradle.kts", encoding="utf-8").read()
    ):
        label = f"settings:{plugin_id.rsplit('.', 1)[-1]}"
        versions[label] = version
        coordinate[label] = plugin_coordinate(plugin_id)
        OUTSIDE[label] = "settings.gradle.kts"


def gradle_wrapper_row():
    path = "gradle/wrapper/gradle-wrapper.properties"
    if not os.path.exists(path):
        return None
    match = re.search(r"gradle-([0-9][^-/]*?(?:-rc-\d+)?)-(?:bin|all)\.zip", io.open(path, encoding="utf-8").read())
    if not match:
        return None
    label = "gradle-wrapper"
    versions[label] = match.group(1)
    OUTSIDE[label] = path
    found = []
    for channel in ("current", "release-candidate"):
        try:
            with urllib.request.urlopen(f"https://services.gradle.org/versions/{channel}", timeout=40) as response:
                data = json.loads(response.read().decode("utf-8"))
        except Exception:
            continue
        if data.get("version") and not data.get("broken"):
            found.append(data["version"])
    if not found:
        return label, "services.gradle.org", None, "unreachable", "could not read the version service"
    # Gradle spells candidates "9.8-rc-1"; the comparator knows "rc".
    best = newest(found) or found[0]
    pinned = versions[label]
    if best == pinned:
        return label, "services.gradle.org", best, "current", ""
    if rank(pinned) is not None and rank(best) is not None and rank(best) < rank(pinned):
        return label, "services.gradle.org", best, "pin ahead", f"newest published is {best}"
    return label, "services.gradle.org", best, "behind", ""


targets = [ONLY_REF] if ONLY_REF else sorted(versions)
if ONLY_REF and ONLY_REF not in versions and ONLY_REF != "gradle-wrapper":
    print(f"No such version ref: {ONLY_REF}", file=sys.stderr)
    raise SystemExit(2)
targets = [t for t in targets if t != "gradle-wrapper"]

with cf.ThreadPoolExecutor(16) as pool:
    rows = list(pool.map(verdict, targets))
if not ONLY_REF or ONLY_REF == "gradle-wrapper":
    wrapper = gradle_wrapper_row()
    if wrapper:
        rows.append(wrapper)

# ------------------------------------------------------------------- report

behind = [row for row in rows if row[3] == "behind"]
attention = [row for row in rows if row[3] in ("unreachable", "unrankable", "pin ahead")]
current = [row for row in rows if row[3] == "current"]
skipped = [row for row in rows if row[3] == "skipped"]

# A snapshot pin is three times the width of a plain one, so the columns are sized to the rows
# actually being printed rather than to a guess.
shown = behind + attention + (current + skipped if SHOW_ALL else [])
ref_width = max([len(row[0]) for row in shown] + [10]) + 2
pin_width = max([len(versions[row[0]]) for row in shown] + [10]) + 2

if behind:
    print("behind")
    for ref, module, best, _, _ in behind:
        where = f"  (edit {OUTSIDE[ref]} by hand)" if ref in OUTSIDE else ""
        print(f"  {ref:<{ref_width}}{versions[ref]:<{pin_width}}-> {best:<{pin_width}}{module}{where}")
else:
    print("Every rankable pin is the newest stable-or-rc published.")

if attention:
    print("\nnot an upgrade, and not silence either")
    for ref, module, best, state, note in attention:
        print(f"  {ref:<{ref_width}}{versions[ref]:<{pin_width}}{state:<12}{note}")

if SHOW_ALL:
    if current:
        print("\ncurrent")
        for ref, module, best, _, _ in current:
            print(f"  {ref:<{ref_width}}{versions[ref]:<{pin_width}}{module}")
    if skipped:
        print("\nskipped")
        for ref, _, _, _, note in skipped:
            print(f"  {ref:<{ref_width}}{versions[ref]:<{pin_width}}{note}")
else:
    print(
        f"\n{len(current)} current, {len(skipped)} skipped"
        f"{' (--all to list them)' if current or skipped else ''}"
    )

if not behind:
    raise SystemExit(0)

if DO_UPDATE:
    source = io.open(CATALOG, encoding="utf-8").read()
    for ref, _, best, _, _ in behind:
        if ref in OUTSIDE:
            print(f"\nnot rewritten: {ref} lives in {OUTSIDE[ref]}")
            continue
        old = f'{ref} = "{versions[ref]}"'
        if source.count(old) != 1:
            print(f"\nRefusing to rewrite {ref}: {source.count(old)} matches for {old!r}")
            raise SystemExit(1)
        source = source.replace(old, f'{ref} = "{best}"')
    io.open(CATALOG, "w", encoding="utf-8").write(source)
    print(f"\nupdated {CATALOG}")

    # The versions are quoted in docs too, and a doc that still names the old one is worse than no
    # doc. Reported rather than rewritten: matching a bare version string across prose is how you
    # corrupt an unrelated line.
    #
    # Only docs that state what the build uses *now*. `docs/planning/` keeps a chronology and
    # `readme/RELEASE_*.md` describes a shipped version, so an old number there is the record doing
    # its job, not a staleness to fix.
    HISTORICAL = ("docs/planning", "readme/RELEASE_")
    # Tracked files only. Walking the tree reached build output and a foreign checkout under .tmp,
    # and a substring test on the directory ("build", ".git") skipped .github and anything named
    # like a build folder.
    stale = []
    tracked = subprocess.run(["git", "ls-files", "-z", "--", "*.md"], capture_output=True, check=True)
    for path in filter(None, tracked.stdout.decode("utf-8").split("\0")):
        if path.startswith(HISTORICAL):
            continue
        try:
            text = io.open(path, encoding="utf-8").read()
        except Exception:
            continue
        for ref, _, _, _, _ in behind:
            if f"`{versions[ref]}`" in text:
                stale.append((path, ref, versions[ref]))
    if stale:
        print("\ndocs still quoting a version that moved")
        for path, ref, old in sorted(set(stale)):
            print(f"  {path:<46}{ref} {old}")

    print("\nBuild and test before committing: a pin is not a no-op until it is.")
else:
    print("\n  --update   move the behind pins to the newest stable-or-rc")

raise SystemExit(1)
PY
