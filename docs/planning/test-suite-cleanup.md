# Test suite cleanup, 2026-09-23

The owner's request: the test count had kept growing, some of the tests had probably gone stale, and
the suite should be tidied, merged and cleaned up on a regular basis rather than left to grow.

## What was wrong

147 test files, holding 507 tests, read Kotlin source text and asserted on it. Most of them pin one
file's implementation: that a particular argument is passed, a modifier comes before another, a call
appears three times, or a local variable has a certain name. `AGENTS.md` already asks for the opposite
("avoid tests that merely mirror implementation text"). Tests of that kind break on every refactor and
cannot catch a behaviour bug, and many had gone stale: they were checking for strings that are gone
from the code, or for migrations finished long ago.

## How it was decided

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

## What changed

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

## The rewrite backlog: cleared 2026-09-23

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

## Keeping it this way

The policy is now in `CLAUDE.md` under *Tests*: behaviour first, and source scans only for repo-wide
bans with an allow-list, cross-file contracts, or a documented bug that cannot be rendered. Repeat this
audit when the count of source-reading test files climbs well past today's 82 (147 before):

```bash
git grep -l "readText()\|sourceFile(\|File(.*src/main" -- '*/src/test/*.kt' | wc -l
```
