# Test suite cleanup

## Current state (2026-09-25)

| | 2026-09-25 before | after round 2 |
| --- | ---: | ---: |
| tests run | 3,113 | 2,773 |
| test source lines | 104,555 | 95,202 |
| test files | 621 | 596 |
| test lines per 100 main lines | 33.0 | 30.1 |
| summed test-class time | 120.8s | 94.5s |
| wall time, `testDebugUnitTest --rerun`, two runs | 45s, 38s | 40s, 37s |

Wall time barely moved: modules run in parallel, so it follows :app, whose own time did not fall.
The summed time is where the cut shows (feature-github-engine 21.4s to 2.7s, ui-liquid-glass 19.0s
to 17.7s with 24 moved-in tests).

The suite is audited against the rules below, every module's tests run in CI, and the screenshot
tests compare against tracked goldens there. Round 2 (below) is the latest pass; round 1
(2026-09-23) removed the source-text mirrors.

## Round 2, 2026-09-25: the whole suite

The owner's request: tests had grown to about a third of the codebase, and many were written while a
module was being developed and never looked at again. Cut hard, merge, and improve, the way other
projects were cutting their suites.

### The rules, taken from OpenClaw's audit

openclaw/openclaw#139428 is running the same campaign on a far larger suite. Its acceptance rules
were adopted as they are:

- Count **net** lines: shared fixtures added are deducted from what is deleted, and moving or
  reformatting code is not removal.
- For every removed scenario, name its real regression protection and the stronger test that remains,
  or show it cannot detect a production regression (a constant compared with itself).
- Keep independent API, persistence and migration, lifecycle and concurrency, security, platform and
  release contracts, and the only guard of a documented bug.
- Compare runtime under matched conditions (`testDebugUnitTest --rerun`, twice each side).

### How it was done

Eight read-only audits split the suite by area (ui-liquid-glass; app GitHub; app student; app BA and
sync; feature-github domain; feature-github data and engine; the rest of app; core and small
modules). Each gave every test a verdict: mirror, trivial, duplicate (naming the stronger test),
stale, merge into a table, share a fixture, or keep. A ninth audit looked only across module
boundaries. Each report was then applied in its own worktree by an agent that re-verified every line
against the code before acting and skipped any it disagreed with. The lead merged the worktrees into
master along review boundaries, compiled and tested every intermediate commit on its own, and checked
each merged tree against its worktree with `cmp`.

Skipped lines were skipped for evidence, not for caution: several "duplicates" turned out to be the
only test of some path (a manual refresh that bypasses a fresh cache, the ID accessor's server index,
the export job's success rule), and several benchmark or fixture files held one assertion nothing else
made. Those assertions were moved into the behaviour tests before the files went.

### What changed

31 commits after 7edbae916, before this document. Net test lines removed: 9,353 (14,240 deleted,
4,691 added, the added lines being the tables and shared fixtures that replaced copies). Production
Kotlin lost 112 lines of dead code.

- **Mirrors and trivial checks**: constants, dp values, colours and copy asserted against their own
  definitions; defaults and data-class getters read back; a test that fed in -1 and expected -1.
- **Duplicates**: each removed case names the stronger test that asserts the same behaviour, in the
  same module or the one that owns the code. Where the "stronger" test missed one assertion, that
  assertion moved into it first.
- **Merges**: near-identical cases became labelled tables; every original input and expected value is
  a row, and a failing row names its case.
- **Shared fixtures**: about twenty per-module helpers (a ranged MockWebServer, an Atom feed server,
  release-corpus loaders, F-Droid index and tracked-app builders, account and page-state builders, a
  backdrop scene, sheet row fixtures, a repo-root reader) replace copies in several files each.
- **Extraction leftovers**: tests that stayed in :app or feature-github after their code moved went to
  the owning module (ui-liquid-glass, feature-github-engine); 132 empty `TestApp : Application()`
  classes became `Application::class`; test dependencies and Robolectric settings no module used
  were removed. feature-os has no tests left.
- **Behaviour instead of text**: one flattening source check became a rendered test; the frame-rate
  vote bans became one repo-wide scan (mutation-checked); the MCP documentation check reads the tools'
  own schema.

### Found along the way

- **CI ran only `:app`.** The unit-test job ran `:app:testDebugUnitTest`, so the ~1,500 tests of every
  module extracted from `:app` since May never ran in CI; `GitHubWorkflowContractTest` pinned that
  command and so protected the gap. CI now runs the unqualified `testDebugUnitTest`, and the contract
  test fails on any module-qualified unit-test task (e30521b41).
- **Screenshots were never compared in CI.** Without `roborazzi.test.verify` the screenshot tests only
  compose; with a golden replaced by another image the old command still passed. CI now verifies.
  Three GitHub goldens had been gitignored since a4300aa45 (2026-05-06) as "optional"; their inputs
  are fixed, so they are tracked now (b58588c37).
- **Benchmarks in the unit suite.** Two engine benchmarks and a fixture performance chain timed loops
  that never fail on slowness. Their unique assertions moved into the ranker, selection-engine and
  corpus tests; the engine module's test time fell from 21.4s to 1.1s (2.7s after six tests moved in).
- **Dead production code**, each with no caller in any module: an always-false APK cache policy and its
  branch (off since 662c36121), the Dialog and Popup window wrappers menus stopped using in
  2bcc2063a, two sheet-height helpers, a badge formatter, a manifest reader method and a test-only
  wrapper. `dropdown-menu-inventory.md` had named the dead Popup wrapper as the menus' window boundary
  since August; it is corrected.
- **A real documentation gap.** The MCP SKILL guides (en, zh-CN, ja) never mentioned the
  `fdroid_repository` source and filter mode the tracking tools offer. The two tests meant to catch
  drift compared hard-coded substrings, so neither could; McpGitHubTrackingOptionsDocumentationTest
  now reads the options from the tools' argument enums and checks every description and guide.
- **A live test nobody could switch on the documented way.** feature-github did not forward `-D`
  properties to the test JVM; it does now, except the API token, which the configuration cache would
  store on disk.

### Deliberately left alone

- Everything MiFocus (`core-notification/.../focus`, `MiFocusOfficialTemplateCatalog.kt` and its
  tests): the template layer is research material used with the `xiaomi-super-island-research` skill
  and shared with other projects, even though nothing in KeiOS calls the catalog.
- The pane API in `AppWindowPanes.kt` and its test: it has no caller, but be33d6d78 is "step 1 of the
  two-pane work", so whether it goes is a product decision.
- `shareImportLinkageEnabled` and its "off" paths, which the MCP output still reports.

### Next

A deeper round is expected. Where to look first:

- :app sets the wall time, and its Robolectric Compose tests are the bulk of it. Tests that render a
  fixed component and only assert a tag exists are the next category to question.
- feature-mcp, core-notification and feature-keepalive spend 6-12s each on a handful of Robolectric
  tests; measure which cases cost that before cutting.
- The source-scan count is 34 files by the round 1 command below, 41 counting the shared
  `repoSource(` reader. Each should still be one of the three kinds CLAUDE.md allows.
- Not a test, noticed in passing: `feature-mcp/.../McpDevTools.kt` still names
  `app/src/main/assets/mcp/SKILL.md`, which moved to feature-mcp.

## Round 1, 2026-09-23: source-text mirrors

The owner's request: the test count had kept growing, some of the tests had probably gone stale, and
the suite should be tidied, merged and cleaned up on a regular basis rather than left to grow.

### What was wrong

147 test files, holding 507 tests, read Kotlin source text and asserted on it. Most of them pin one
file's implementation: that a particular argument is passed, a modifier comes before another, a call
appears three times, or a local variable has a certain name. `AGENTS.md` already asks for the opposite
("avoid tests that merely mirror implementation text"). Tests of that kind break on every refactor and
cannot catch a behaviour bug, and many had gone stale: they were checking for strings that are gone
from the code, or for migrations finished long ago.

### How it was decided

Four read-only audits gave every one of the 504 source-reading tests a verdict: widget, student,
GitHub + BA, and the rest. The categories were:

| verdict | meaning |
| --- | --- |
| KEEP | a repo-wide ban, a cross-file contract, or the only guard for a documented bug that cannot be rendered in a unit test |
| mirror | restates one file's implementation text |
| duplicate | the same behaviour is already covered by another named test |
| stale | the thing it guards no longer exists, or the assertion has become vacuous |
| merge | several tests make near-identical checks |

Rendering and value tests that happened to sit in the same files were kept.

One rule overrode the verdicts. A mirror that is the only guard for a documented bug was **kept**
rather than deleted, even where a behavioural test would be better. That way no known regression lost
its guard in this pass. Those are listed below as the rewrite backlog.

### What changed

| | before | after |
| --- | ---: | ---: |
| `:app` unit tests | 1777 | 1564 |
| `:ui-liquid-glass` unit tests | 450 | 426 |
| `:feature-mcp` unit tests | 59 | 58 |

- **56 files deleted whole.** Each held only mirrors, duplicates or stale checks: the per-screen
  theme scans (`AppThemeSourceContractTest` already bans `isSystemInDarkTheme()` everywhere), the lane
  wiring files for five pages (the lane rules have their own pure tests), the backdrop-order and
  argument-pinning files, and the dual-action-row "reuse" counts, among others.
- **89 tests removed from mixed files.** The rendering tests alongside them stayed. Private
  source-reading helpers and imports left unused were pruned with them.
- **`MainPagerTabJumpControllerTest` deleted.** It pinned a tween duration formula that the Miuix
  spring replaced the same day.
- **One merge, `PlatformWindowSourceContractTest`.** It replaces five per-dialog tests that each
  asserted one dialog used the shared host and did not call `WindowDialog(`. `WindowDialog(` no
  longer exists anywhere, so those checks had become vacuous. The real invariant, that nothing opens a
  platform `Dialog` or `Popup` window where Liquid Glass cannot sample the page, is now one repo-wide
  scan with two allowed hosts and a non-vacuity check.
- **Two documents corrected.** A KDoc in `BaselineProfileTestTagContractTest` named a test that did
  not exist, and `dropdown-menu-inventory.md` listed a deleted test as current coverage.

### The rewrite backlog: cleared 2026-09-23

Each of these read source text as the only guard for a real bug. Each is now a behavioural test that
fails when its bug is put back (mutation-checked), and the source check went in the same change:

- Done: `BaGuideBgmFavoriteUndoTest` is now a Compose test of the favourites list, and the removal,
  restore and expiry run in `BaGuideBgmFavoriteUndoControllerTest` on virtual time.
- Done: `BaGuideCatalogFavoritesSynchronizationTest` now writes through the store's real save and
  toggle, using a key-value seam, and checks that the follower behind `catalogFavoriteEntries` reloads.
- Done: `BaCalendarPoolStackedLayoutSourceTest` is now `BaCalendarPoolTwoColumnLayoutTest`, a bounds test at w1280dp.
- Done: `BaCalendarPoolBottomChromeSourceTest` is now `BaCalendarPoolBottomChromeScrollTest`, which swipes the real calendar list.
- Done: `GitHubTrackAppPickerSynchronizationTest` now tracks an app through `GitHubPageState` and checks the picker drops it.
- Done: `GitHubHistoryUnreadSynchronizationTest` now publishes the store's watermark signal and checks `GitHubHistoryUnreadBadge`, which the page ViewModel holds, follows it.
- Done: the delete-sheet source check in `GitHubTrackDialogsTest` is now two rendering tests, one for back and one for a scrim tap, each refused mid-delete.
- Done: `BaLiquidSurfacesBackdropTest.surfaceMaterialsFollowTheAppTheme` is now `aCardInTheEdgeStackKeepsItsGlassAndExportsItToDescendants` (`482f0cfb3`).
- Done: `AppTopBarChromeGutterSourceTest` is now `AppTopBarChromeGutterTest`, a 1280dp landscape bounds test.
- Done: the `ItgsaFairMemoryRateLimitTest` kill-path check is now a pure test of `shouldRunItgsaRelease(kill, ...)`.

The audits also found merge candidates, left for later because they are guards worth keeping, not
dead weight:

- the three edge-stack host checks in `AppEdgeStackHostSourceTest`;
- the ancestor-transform bans in `AppSurfaceCardTransformContractTest`, together with the toast;
- the presentation blur and lens duplication bans;
- the two frame-rate-vote bans in `BgEffectFramePacingTest` and `MainPagerFrameRateTest`;
- a repo-wide "no standalone `rememberLayerBackdrop` producer" scan, to replace the per-directory
  ones.

### Keeping it this way

The policy is now in `CLAUDE.md` under *Tests*: behaviour first, and source scans only for repo-wide
bans with an allow-list, cross-file contracts, or a documented bug that cannot be rendered. Repeat this
audit when the count of source-reading test files climbs well past 34 (2026-09-25; 82 after round
1, 147 before it):

```bash
git grep -l "readText()\|sourceFile(\|File(.*src/main" -- '*/src/test/*.kt' | wc -l
```
