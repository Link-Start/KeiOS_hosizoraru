# Baseline profile coverage

How the shipped ART profile is generated, what it actually covers, and why the journeys are the
ones they are. Companion to `scripts/qa/baseline_profile_freshness.sh`, which answers the narrower
question of whether the committed capture is still current.

## Where this stands, as of 2026-09-01

Everything below is in the order it was learned, corrections included, which is useful when you are
about to repeat one of the mistakes and unhelpful when you just want the current picture. This is the
current picture.

| | |
| --- | --- |
| Journeys | 23, plus 14 macrobenchmarks the capture skips |
| Iteration budget | startup 8/3, every other journey 4/2 |
| A capture costs | ~35 minutes, 96 cold starts, on the API 37 AVD |
| Last capture | 23/23, 72,507 baseline rules, 24,710 startup rules |

**Read the named components, not the total.** Three captures moved the total while every driven
component stayed identical. The sum is dominated by things no journey drives -- the *scheduled*
background refresh is collected only if its job happens to fire inside the capture's 35 minutes -- and
by journeys that fetch real content over the network. A change worth believing shows up in the
component, as `+1,135` did when the F-Droid journey landed.

**Reproduce failures with `am instrument`, not with a capture.** Driving eight journeys in one
instrumentation pass puts a late journey in a realistic state in about fifteen minutes instead of
forty. Two real failures in this document were found that way after captures had only pointed at them
vaguely, and the ordering is JUnit's `MethodSorters.DEFAULT` -- a hash over method names, so a subset's
order is stable but is not the full run's.

**Known gaps, all deliberate:** `FdroidAppSearchService` and `GitHubTrackEditFdroidDiscoverySection`
(the F-Droid *search* path, which needs a dropdown and a query field); `GitHubTrackChangeHistoryCards`
and `GitHubAppInstallHistoryCards` cover one action arm each rather than all of them; and the
`feedback` window, for the reason `sharedIntentWindowInteractions` records.

## What the two reference implementations do

### Miuix's example app

Miuix ships baseline profiles for its *libraries*, so its shape is not ours, but three of its
decisions are worth knowing about.

- **The generator is deliberately tiny.** `BaselineProfileGenerator` there has two tests: a startup
  one, and an `interactions` one that flings the home tab three times and taps "Settings". That is
  the whole journey set. It works because the library's surface is exercised incidentally — the
  example app is a component catalogue, so scrolling it composes most of miuix.
- **It generates on a Gradle Managed Device, not a connected one.** `pixel6Api34`, `aosp-atd`
  (`useConnectedDevices = false`). An ATD image has no Play services and no background apps, so
  captures are reproducible across machines and CI. KeiOS uses `useConnectedDevices = true` because
  its journeys depend on real app state — tracked repositories, BA accounts — which a fresh GMD
  does not have.
- **Library profiles are post-processed into wildcards.** `ConvertBaselineProfileTask` rewrites
  precise signatures like `HSPLtop/…/Button;->invoke(…)V` into `HSPLtop/…/Button;->**(**)**`, drops
  everything outside `top.yukonga.miuix.kmp.`, and splits the result per module. Precise signatures
  are fragile for a library because the consumer recompiles with a different R8; a wildcard survives
  that. **This is exactly wrong for an application**, which is why KeiOS does not do it: our profile
  is captured against the same build that ships, AGP rewrites it through the R8 mapping, and
  wildcards would over-compile — a bigger profile, more dex pages touched, slower installs, for
  rules ART would never have used.

Miuix's example app also sets `android.experimental.r8.dex-startup-optimization = true`. KeiOS sets
it to `false` — see the open item at the bottom.

### Google's guidance

- **Cover critical user journeys, not the app.** Startup, scrolling, navigation between screens, and
  animations. Profiling everything makes the profile larger, lengthens install-time compilation, and
  buys nothing for paths a user does not take.
- **One `@Test` per journey**, each its own `rule.collect { }`, so a failure names the journey.
- **`includeInStartupProfile = true` only for startup flows.** The startup profile is what R8 uses to
  decide which classes land in the primary dex; padding it dilutes that.
- **`collect` re-runs until the output stabilises** — `stableIterations = 3`, `maxIterations = 15` by
  default. `strictStability = true` turns an unstable profile into a failure rather than a warning.
- **Wait on conditions, never on sleeps**, and keep journeys deterministic — a step that depends on
  a network round trip should not be able to fail the capture.
- **Regenerate per release**, and verify with macrobenchmarks comparing `CompilationMode.None()`
  against `CompilationMode.Partial(BaselineProfileMode.Require)`.
- Generation needs API 33+, or a rooted API 28+.

## What KeiOS's profile actually covered

Measured against the capture at the head of this work: 53,427 baseline rules and 23,874 startup
rules, containing 3,035 distinct `os/kei` classes.

Mapping every `@Composable`-carrying source file to its file class and asking whether the profile
names it:

| | files |
|---|---|
| covered | 299 |
| **uncovered** | **229** (~2.0 MB of source) |

The uncovered set was not evenly spread. It was whole features that no journey could reach:

| package | uncovered files | uncovered source |
|---|---|---|
| `student.*` (guide catalog + student guide) | 113 | ~950 KB |
| `github.sheet` (track editor, app picker, APK info, check logic) | 11 | 175 KB |
| `debug` (component lab) | 13 | 178 KB |
| `github.importer` (star import) | 6 | 86 KB |
| `github.section` (asset panel, version sections — expanded card only) | 10 | 82 KB |
| `github.actions` (workflow runs sheet) | 11 | 74 KB |
| `github.share` (share-sheet import window) | 18 | 66 KB |
| `github.release` (the release list page) | 1 | 38 KB |
| `feedback`, `jsonimport` | 9 | 54 KB |

In every case the cause was the same and mechanical: **the entry point carried no test tag**, so no
journey could have reached it even if one had wanted to. The guide catalog is the clearest example —
about a hundred files and two nav routes behind a floating-dock button that had a
`contentDescription` and nothing else.

`github.release` is different: it has a journey, added after the last capture. It is the
freshness script's case, not this one's.

## What was added

Nine new test tags, four new journeys.

- **`baGuideCatalogInteractions`** — the dock's catalog action, the catalog page, the student guide
  detail and every one of the guide's six tabs. The detail step is conditional: catalog entries come
  from a synced dataset, and an unsynced device showing an empty state is a state worth profiling,
  not a reason to fail a half-hour capture. Then the catalog's own dock — memory lobby, student BGM,
  favourite BGM — and a track played from it, for `androidx.media3`.
- **`gitHubTrackedCardInteractions`** — a tracked card *opened*. Every earlier GitHub journey
  scrolled collapsed cards, and a collapsed card composes its header and nothing else. Both
  directions of the accordion, then the Actions sheet from the same card's overflow, conditionally
  because that row only exists for GitHub repository tracks.
- **`gitHubChromeSheetInteractions`** — the track editor, the strategy sheet and the check-logic
  sheet, plus the star importer. The importer is a separate activity, so it is proven by the GitHub
  page going away rather than by a page-root tag that would exist only to be waited on.
- **`sharedIntentWindowInteractions`** — the share-import window driven with the intent a share sheet
  sends, and the JSON import window. Both start from a dead process into their own activity, which is
  the worst case an ART profile can fix.

## What a capture cost, and what reading it taught

Three captures, because the first two passed every journey and still collected almost nothing from
some of them. **The merged rule count does not tell you this.** The artifact to read is
`baselineprofile/build/intermediates/baselineprofiles/<variant>/BaselineProfileGenerator_<journey>-baseline-prof-*.txt`,
one per journey, which is where the three silent failures showed up:

1. **The catalog journey scrolled before it clicked.** The entry card is a lazy item, so the flings
   disposed it and the conditional detail step found nothing and skipped. `student/page` came out at
   57 rules and `student/section` at zero. Fixed by opening the detail first.
2. **The share window closed before composing.** The URL was KeiOS's own repository — picked so a
   confirmed import would be a no-op, which is exactly why the flow had no decision to make and
   finished silently. The capture had the coordinators and the result writer and not one sheet.
3. **Two tab bars are not pagers.** Neither the guide's tabs nor the catalog's dock respond to a
   horizontal swipe, and neither publishes a resource id or a content description, so a journey could
   reach either page and then only ever see the tab it landed on. Both are tagged now.
4. **A collapsing bottom bar is four bugs, not one.** The catalog dock took four captures, each
   failure only visible once the one before it was fixed: the tabs had no ids at all, then the bar
   collapsed on scroll, then the collapsed bar could not be scrolled back on a tab whose content did
   not fill the screen, then the pill lookup raced its own click across a cross-fade. `clickBottomBarTab`
   is the accumulated answer. Note too that the guide's bar does *not* hide on every tab — its profile
   tab leaves it alone and its skills tab does not — so a check on one tab is no evidence about the
   others.
5. **A tap that lands and does nothing is the recurring failure on this device.** A card under the
   floating bottom bar, or a list still settling under the tap. It has now cost four separate
   journeys, so route pushes and window opens both go through `openWindowFrom`, which takes two
   identical bounds readings as proof the layout settled, retries, and — the part that ended four
   rounds of guessing — reports the trigger's bounds, which known roots are on screen, and which
   activity holds the window when it gives up. "It did not open" is not a diagnosis.
6. **A covered interface is not a covered feature.** The BGM mini player sits on every catalog tab,
   so its composables land in the profile whether or not anything plays — while `androidx.media3`,
   the ExoPlayer/MediaSession/extractor stack behind it, loads only when a track starts. A capture
   can look like it covers playback and carry none of it. The journey now taps a student-BGM row,
   which is the whole card; verified on the AVD by `dumpsys audio` reporting a started AudioTrack at
   44.1kHz under the app's uid.

`connectedNonMinifiedReleaseAndroidTest` with a `--tests` filter is a good way to prove a journey
does not time out, and proves nothing else: it does not run the profile-collection step, so no
per-journey file is produced. Only a `generate…BaselineProfile` task tells you what was actually
collected — use `:app:generateReleaseBaselineProfile`, not the bare `generateBaselineProfile`.
The bare one is an aggregate over every profileable variant, and since `releaseDiagnostic` was
added it walks all 21 journeys a second time against the diagnostic APK for an identical result.

## Deliberately not covered

- **`debug` — the component lab — covered, but intermittently.** It contributes 463 rules, the
  Liquid catalogue included, so both windows opened and the whole sample list composed. That is one
  capture. The capture before it collected zero from the same journey, so re-selecting About's lab
  tab on each attempt improved the odds rather than fixing a cause: at the failure the About page was
  up, its tab bar was up, and the lab row was not composed, meaning the pager's tab state is lost
  between finding the row and tapping it, and the same steps by hand on the same build open it every
  time. Five attempts, four wrong theories — a collapsing bar, tap position, pager settling, tab
  state. The journey is best-effort by design and cannot cost a capture, so an occasional miss shows
  up as these rules going absent rather than as a failure. Check for it before trusting a release
  profile to carry them.
- **`github.history`'s tabs — the Refresh tab is covered now; the other two are not.**
  *(Corrected twice below: the pull described here covered the records and not the diagnostics, and
  the diagnostics are covered now by a refresh made to fail. Read "Measured: 33m 31s" and "The failing
  refresh" before acting on this entry.)* All four
  categories are selected and the package collects 549 rules. A capture installs the app fresh, and
  these tabs render history the app accumulates over time, so on a clean device they were all empty
  states and the three card components collected nothing.
  `gitHubActionsHistoryRouteInteractions` now calls `seedGitHubHistory()` — one pull-to-refresh on the
  GitHub page before entering the route. Verified on the API 37 AVD from a `pm clear` state:
  `github_refresh_history` does not exist beforehand, the pull creates it, and the Refresh tab goes
  from its empty state to "2 of 2 records shown" with a real finished timestamp. It does not need the
  network to succeed, because `GitHubRefreshHistoryService` records the run's `outcome` whatever it is
  and `failedCount` is a field on the record, so a failed refresh still writes one.
  **The other two stay uncovered, and the distinction is worth keeping.** The same pull also creates
  the `github_track_change_history` *file*, but the store stays empty — the Tracking tab still reads
  "0 of 0 records shown / No tracking records" and the Apps tab "No app records". A file existing is
  not a record existing, and it would have been easy to claim the fix covered all three on the strength
  of `ls`. *(Both are covered now -- see "The other two history tabs" at the end.)*
  `GitHubTrackChangeHistoryCards` needs a real tracked-repo change and
  `GitHubAppInstallHistoryCards` needs a real app install; the second is not something a capture can
  reasonably do.
- **The BA account sheet**, which does not open under a synthetic tap on its toolbar action. Noted
  on `presentationChromeInteractions` and still true.
- **The feedback window** (`feedback`, 28 KB). It is `exported="false"`, and `am start` runs as the
  shell uid, which on this platform does not hold `START_ANY_ACTIVITY` — starting it by name comes
  back as `SecurityException: … not exported from uid …`. Verified on the API 37 AVD, and the same
  applies to `GitHubStarImportActivity`, which is why the star importer is reached through its menu
  row instead. The only other way into feedback is a button inside an expandable card most of the way
  down the settings page: a three-tag chain for 28 KB, left for whoever wants it.

## Open item: dex startup optimization

`app/build.gradle.kts` sets `android.experimental.r8.dex-startup-optimization = false` for every
variant, with the recorded reason that generated startup profiles carry D8/R8 synthetic lambda rules
R8 reports as missing before minification. That flag is what turns `startup-prof.txt` into a dex
*layout* — startup classes packed into the primary dex — and it is a cold-start win separate from
AOT compilation. Miuix's example app enables it. Worth re-testing against a fresh capture rather
than left as a permanent no.

## Capture on the 1.5.0-rc02 toolchain (2026-08-31)

Taken after the baseline profile plugin moved `1.5.0-alpha07` → `1.5.0-rc02` and the
`androidx.benchmark` runtime was aligned to match. 36 tests, **0 failures**, 1h 21m on
`KeiOS_API37_Validation`.

| profile | added | removed | unmodified |
| --- | --- | --- | --- |
| `baseline-prof.txt` | 540 (0.76%) | 2063 (2.91%) | 68284 (96.33%) |
| `startup-prof.txt` | 168 (0.70%) | 202 (0.84%) | 23709 (98.46%) |

96–98% unmodified is the useful number: the new plugin and runtime produce the same profile from the
same journeys, so the drift below is code drift, not toolchain noise.

**It also closed a real gap.** The committed profile was stale on the Liquid-Glass cull work, and the
cost was not abstract — it carried **zero** rules for `CullWhenFullyClipped`, so the cull that every
`AppSurfaceCard` now runs on each draw was shipping uncompiled. The new capture has 13. That is
exactly the silent failure `scripts/qa/baseline_profile_freshness.sh` was written to catch, and the
first time the gate's value has been demonstrated rather than argued.

### The adb trap this ran into first

The first attempt failed with `No compatible devices connected`, which reads like a plugin
incompatibility and is not one. The line above it is the real one:

```
Skipping device 'KeiOS_API37_Validation(AVD)': Unknown API Level
Caused by: com.android.ddmlib.ShellCommandUnresponsiveException
```

Two adb binaries — Homebrew's `/opt/homebrew/bin/adb` and the SDK's `platform-tools/adb` — were both
trying to own the server (`ADB server didn't ACK`), so ddmlib's property fetch timed out and AGP could
not read the API level. Killing both and starting one from `platform-tools` fixed it: property reads
went to 28ms and the run reported `Starting 36 tests on KeiOS_API37_Validation(AVD) - 17`. Put
`platform-tools` first on `PATH` for a capture so Gradle and the shell share one server.

Worth pairing with a second habit: that failed run's task summary still said *exit code 0*, because a
`| tail` pipeline reports the tail's status and not Gradle's. Capture `GRADLE_EXIT=$?` before piping,
or a failed capture reads as a successful one.

## Why a capture took 1h21m, and the budget that replaces it

The 2026-08-31 run reported 36 tests. Fourteen of those are macrobenchmarks
(`MainNavigationFrameBenchmarks`, `StartupBenchmarks`) and the log shows every one **SKIPPED**, so
they cost nothing. The remaining 22 are the generator journeys, and they own all 4860 seconds.

`BaselineProfileRule.collect` defaults to `maxIterations = 15, stableIterations = 3` — replay until
three consecutive captures are identical, give up at fifteen. The generator passed neither, so those
defaults applied to all 22. Only one reading of the arithmetic fits:

| iterations/journey | cold starts | implied seconds each |
| --- | --- | --- |
| 3 | 66 | 73.6 |
| 5 | 110 | 44.2 |
| 8 | 176 | 27.6 |
| **15** | **330** | **14.7** |

A cold start plus a deep navigation costs somewhere around 12–20s, which is the 15 row. The others
would require a single pass to take 44s or 74s, and nothing in these journeys does. So essentially
every journey was running the full fifteen — a UI-driven journey on a Compose app rarely produces
three byte-identical captures in a row, and `strictStability` is false, so failing to stabilise is
silent and simply costs the remaining replays.

The budget is now explicit: startup keeps 8/3 because it is one short journey feeding
`startup-prof.txt`, everything else takes 4/2. That bounds a capture at `1x8 + 22x4 = 96` cold starts
(92 when this was written, against 22 journeys)
against 330, so roughly 22 minutes rather than 81.

**A hypothesis worth recording as dead.** The first suspect was `waitForIdle()`: the generator calls
it 49 times, configures no `Configurator` timeout so the UiAutomator default of 10s applies, and this
app has continuously-animating Liquid Glass surfaces — an app that never idles turns each call into a
10-second no-op. Measured instead of assumed, and it is false: `os.kei` foreground on Home renders
**0 frames in 4 seconds**, so the window does reach idle and `waitForIdle` returns immediately. Do not
re-open this one without re-measuring first.

**The trade being made.** `collect` accumulates the *union* of methods across iterations, so fewer
replays can mean fewer rules rather than just less confirmation. It is a good trade here because the
journeys are deterministic scripts over the same code, and the 2026-08-31 recapture came back 96.33%
baseline and 98.46% startup rules unmodified against its predecessor — the marginal yield per extra
replay is close to nothing. Compare rule counts after the next capture anyway;
`scripts/qa/baseline_profile_freshness.sh` prints both.

## The startup profile now covers the first scroll

`includeInStartupProfile` is what feeds `startup-prof.txt`, and the `startup` journey was the only one
setting it — while doing nothing but reaching Home. Dex layout therefore covered launch and nothing
the user does next, leaving the first fling to the interpreter on a fresh install, which is exactly
where first-run jank is felt. The journey now flings twice after launch through the same
`flingVisibleScrollable` helper the Home journey already uses.

## Measured: 33m 31s, and two corrections to the analysis above

The first capture on the new budget: **33m 31s (2011s) against 81m 5s (4865s), 2.42x**, exit 0, on the
API 37 AVD.

**The time model was wrong in its numbers, right in its direction.** It predicted ~22 minutes from
92 cold starts at 14.7s. The real per-start cost is 2011/92 = **21.9s**, not 14.7s. Running that
backwards through the original 4865s gives 222 starts, so the old run averaged about **10 iterations
per journey, not the full 15** — some journeys did stabilise. Iterations were the cost, which is what
mattered, but "essentially every journey ran fifteen" overstated it.

**Fewer replays did not cost rules; they gained.** This was the trade flagged as the risk, so it is
worth stating plainly that it did not materialise:

| profile | before | after | delta |
| --- | ---: | ---: | ---: |
| `baseline-prof.txt` | 68,824 | 70,608 | **+1,784** |
| `startup-prof.txt` | 23,877 | 24,615 | **+738** |

The startup gain is the first-scroll change landing: `ScrollableState` goes 12 -> 33 rules and
`LazyListState` 98 -> 106 in `startup-prof.txt`, which is exactly the dex-layout coverage that was
missing.

**The history seeding did not work, and the reason corrects the gap entry above.** `seedGitHubHistory`
does run and the Refresh tab does render — but `GitHubRefreshHistoryDiagnostics` still carries 0 rules,
same as before. Reading the file explains it: it declares `GitHubRefreshHistoryDiagnosticPills` and
`GitHubRefreshFailureSummaryBlock`, both gated behind `hasRefreshTraceDiagnostics()` and failure state.
A clean successful refresh produces a record with nothing to diagnose, so those composables never enter
composition. The premise was not "the tab has no history" — `GitHubRefreshHistoryCards` was already
carrying 41 rules before any of this — it is "the tab has no *failing* history". Covering them needs a
refresh that fails, which a capture could arrange by pointing at an unreachable host, not merely one
that runs.

## The failing refresh, and the four things in the way

Written after doing it. `gitHubActionsHistoryRouteInteractions` now imports two tracked projects that
cannot succeed, waits for the refresh to finish, and expands the record it produced.
`GitHubRefreshHistoryDiagnostics` goes from **0 rules to 16**, covering both composables the previous
entry named — `GitHubRefreshHistoryDiagnosticPills`, `GitHubRefreshFailureSummaryBlock` — plus
`hasRefreshTraceDiagnostics` and `rememberFailureCategoryLabel`.

The fixture is a JSON export pushed through the app's own import window: one repository that does not
exist, and one direct-APK subscription pointing at the discard port on loopback. Two rather than one
because `rememberFailureCategoryLabel` branches on the category and each fixture compiles a different
arm — measured, `http_error` (HTTP 404, 81ms) and `network_error` (connection refused, 474ms). Neither
needs the network to work, only to decide what the failure is *called*.

Four things had to be fixed on the way, none of which announced itself:

1. **The import window published no tags at all.** `testTagsAsResourceId` is set by `pageRootTestTag`,
   and this window had never needed a page root, so its confirm button was composed and invisible to
   UiAutomator. It now goes through `pageRootTestTag` like every other route, and is in the contract
   test's `PAGE_ROOT_SOURCES` so it stays that way.
2. **`UiDevice.executeShellCommand` is not a shell.** It splits on whitespace and hands the pieces to
   `Runtime.exec`, so quotes arrive as characters and a space ends the argument. The payload was first
   quoted (the extra arrived as `'{...}'`, which the router files as an unknown file) and then, once
   unquoted, still carried spaces inside its labels (the extra arrived cut off at the first one). The
   payload now has no spaces anywhere, labels included, and no quotes around it.
3. **`scrollTestTagIntoReach` cannot reach a control that *is* the bottom of the list.** It insists the
   target sit above 80% of the screen height, which is right on a page with a floating dock over its
   lower fifth and impossible on one whose action row settles at 93% with nothing after it. That is a
   loop that swipes its whole budget at a list already at its end. `scrollTestTagIntoViewAndClick` is
   the variant without the band check.
4. **Navigating away cancels the refresh.** This is the one worth remembering. The batch is scoped to
   the GitHub page, so pushing the history route a second after pulling kills it — and it still writes
   a record, just an empty one: measured, `0/3 done, updates 0, failed 0, Interrupted` where waiting
   gives `3/3 done, failed 2, Partial failed`. An interrupted batch has no failures, no slow items and
   no stop reason, so `hasRefreshTraceDiagnostics` is false and the record draws what a clean one draws.
   The journey now waits on `GitHubOverviewRefreshing`, a tag applied to the overview's progress ring
   only while the state is `Refreshing`, rather than on a delay.

**Two guards, because this path has already failed silently once.** Every refresh record card carries
`GitHubRefreshHistoryCard` — the same tag, not a "first" one, since which record has failures depends on
what the device did last — and the journey walks the visible cards until the pills appear, closing the
ones that turn out to be clean. If none of them has diagnostics the journey *fails*, loudly, rather than
collecting a collapsed card's worth of rules and reporting success. That is precisely how this stayed at
zero through the last capture.

**Still uncovered, and worth naming.** `rememberFailureLimitDetail` and `formatDiagnosticBytes` want a
`response_too_large` failure — a `limitBytes >= 0` diagnostic, which only
`BoundedContentTextReadTooLargeException` produces. Neither fixture reaches it, and arranging one means
serving an oversized body rather than refusing a connection.

**A side effect worth having.** The import window's real work was never compiled before: the existing
`{}` payload settles on the unknown-file branch, so the planner, the applier, the preview stats and the
sample list were all interpreted on first use. With a real payload the `jsonimport` package collects
**327 rules**.

### Two more, found by the capture rather than by the single-journey run

The journey passed on its own and the full capture still came back 20/22. Both failures were things a
one-test run cannot show, and both are now fixed.

**The fixture is in the tracked list for every journey after it, and that changed which card is first.**
`gitHubReleaseListInteractions` opens the overflow of whichever tracked card `findObject` returns first,
which on a capture had always been the app's own self-tracked entry. The tracked list defaults to
`GitHubSortMode.Update` ascending, which falls through to the display title once nothing is updatable,
and a labelled track's display title is its label — so two fixtures named for what they are sorted above
"KeiOS", took first place, and sent that journey looking for releases on a repository that does not
exist. It waited its full 45 seconds and failed, 32 minutes into the run. The labels now start with
`zz-`; verified on the AVD that the first overflow button belongs to the KeiOS card again.

Leaving the fixtures in place rather than deleting them afterwards is deliberate. They are two failing
tracked cards, an overview that reports failures, and the "Show failed"/"Retry failed" row — none of
which a capture had ever rendered, because a capture's app tracks only itself and its own releases
resolve.

**`UiObject2` handles go stale, and `visibleBounds` throws rather than returning null.** The same
staleness `clickBottomBarTab` documents, one call later: `findObjects` answered, the list recomposed,
and reading the bounds raised `StaleObjectException`. The read is wrapped now, and a stale handle is
treated as what it is — a card that moved, worth another pass — rather than as a card that is not there.

### And a third, which needed a cheaper way to reproduce

The second capture came back 21/22 — the release journey fixed, this one failing again, differently.
Rather than spend a third half-hour, the run was reproduced with `am instrument` driving four journeys
in one instrumentation pass, which puts the history journey third with the app already carrying state.
That reproduces it in five minutes. Note the ordering is JUnit's `MethodSorters.DEFAULT`, a hash over the
method names, so the subset's order is stable but is not the full run's — what matters is only that
something runs before it.

The failure message, enriched for exactly this, said `across 3 passes over 1 visible cards`. One card,
and it had nothing to diagnose — which rules out both earlier suspects. It was not staleness and it was
not the card being scrolled off the top: it was the wrong record.

**A pull-to-refresh on a page whose tracked list has not loaded yet targets nothing.** It finishes
instantly and still writes a record — `0/0 done, failed 0` — and that record is the newest, so it is
the one the journey expands. `clickAndWaitForPage` proves the page arrived; it says nothing about its
store. The journey now waits for a tracked card to be on screen before pulling. Verified as an A/B:
the same four journeys in the same order, failing before the wait and passing after it.

Both of the earlier reads were also wrong in an instructive way. The first capture's
`StaleObjectException` and the second's "no diagnostics" were the same underlying miss, because the
`runCatching` added after the first one turned a throw into a skipped index — the fix for the symptom
hid the cause. The stale read is real and is still handled, in place rather than by skipping, but it was
never why this failed.

**The failure message now carries the screen.** On failure the journey dumps the window hierarchy and
quotes the visible text into the exception. A card count says the walk happened; the record's own
summary line is what distinguishes "the refresh failed as intended and the pills are missing" from "the
refresh targeted nothing". Two captures were spent inferring that from timings.

## Measured: 22/22 in 33m 27s, and the import-window claim corrected

The third capture came back clean — 22 tests, 0 failures, `gitHubActionsHistoryRouteInteractions`
passing in 3m 16s — in 33m 27s, the same wall clock as the run before this work started.

| profile | before | after | delta |
| --- | ---: | ---: | ---: |
| `baseline-prof.txt` | 70,607 | 71,606 | **+999** |
| `startup-prof.txt` | 24,614 | 24,712 | **+98** |

What the failing refresh bought, counted in the real profile rather than the single-journey one:

| package | before | after |
| --- | ---: | ---: |
| `GitHubRefreshHistoryDiagnostics` | **0** | **16** |
| `GitHubRefreshHistoryCards` | 41 | 61 |
| `GitHubRefreshHistoryRetryTargets` | 0 | 9 |

**The import-window figure above was wrong, and worth correcting rather than quietly restating.** The
"327 rules" came from the single-journey capture, where it was the *whole* `jsonimport` package count in
a profile containing almost nothing else — and it was reported as though the package had been at zero.
It had not: the existing `{}` journey already collected **296** rules there, because the window, its
theme, its scaffold and the preview it draws for an unknown file are the same code either way. The real
payload adds **29**, and they are the ones that only a file the app can act on reaches:
`KeiOSJsonImportGitHubPlanner.buildPlan` and `buildPreview` with their continuations,
`ImportableKeiOSJsonPlan`, `JsonImportResultAction`, and the content lambdas for the importable and done
branches.

`GitHubTrackedItemsImportApplier` is still at **0** rules, before and after, even though the import
demonstrably applies — the record it produces is what the whole journey then reads. Worth knowing before
assuming a profile covers everything a journey executes.

## The other two history tabs

The last two components at zero rules. Measured in a single-journey capture, which is why the numbers
below are larger than the same components will show in a full one:

| component | before | after |
| --- | ---: | ---: |
| `GitHubAppInstallHistoryCards` | **0** | **27** |
| `GitHubAppInstallHistoryUiRecord` | **0** | **10** |
| `GitHubTrackChangeHistoryCards` | 14 | 20 |

**Tracking was already half done, by accident.** `GitHubTrackedItemsImportApplier` calls
`recordChangesBlocking`, so the failing-refresh fixture's JSON import had *already* written real
track-change records and taken that component from 0 to 14 in the previous capture. What was still
missing is the same thing the refresh card was missing before it: these cards are collapsible, so
selecting the tab composed a header and stopped. Expanding one adds the content lambda and its rows.

**Apps needed a real package event, and nothing the app does to itself is one.**
`recordPackageChangedBlocking` returns early unless a tracked item names the package the broadcast is
about, and the only writer is the runtime receiver in `KeiOSApp`. So a third fixture tracks a package
that is already installed, and the journey makes that package come and go with `pm hide` / `pm unhide`.
Hiding is what `setApplicationHiddenSettingAsUser` does: the platform broadcasts `PACKAGE_REMOVED` and
then `PACKAGE_ADDED`, the APK is never touched, and no data is lost. The pair matters —
`buildPackageChangeResult` maps a removal to `Uninstalled` and an add with no previous snapshot to
`Installed`, so two events give two records with two different actions and two version strings.
`PACKAGE_CHANGED` was the obvious cheaper trigger and is useless: that branch writes no records at all.

**The package has to be one the app is willing to look at.** `com.android.cts.ctsshim` was the first
choice — present on every build, no data, no behaviour — and it produced exactly half the records:
hiding wrote `Uninstalled`, unhiding wrote nothing. `shouldIgnoreInstalledApp` ignores anything with
`FLAG_HAS_CODE == 0` and the CTS shims are code-less stub APKs, so `querySnapshot` returned null and the
add branch bails on a null snapshot; the removal branch does not, because it synthesises the previous
snapshot it needs. Checked with `dumpsys package` on both the AVD and the physical device: the shims have
no `HAS_CODE`, `com.android.egg` does. With the easter egg both records appear, carrying
`Current 1.0 (12)` and `Previous 1.0 (12)`.

Unhiding runs in a `finally` and is verified against `dumpsys package` afterwards, so a run that fails to
restore the package fails loudly instead of leaving it hidden on somebody's device.

**One shared helper now.** `expandHistoryRecord(cardTag, bodyTag)` serves all three tabs: `bodyTag` is a
handle that exists only inside an expanded card, so waiting for it separates a header tap that worked
from one that landed and did nothing. On the refresh tab that tag is the diagnostic pill row, which
proves the record has failures as well — the same check, doing more work.

### The scroll-to-top was the bug, and it was mine

The capture after that came back 21/22 again, with the same journey failing — and the screen dump
added for exactly this moment showed `Failed 3 | Details 3 | Slow 4 | Slowest 8s` on screen. Those are
the diagnostic pills. The record had failures, the card was open, and the wait for its tag still
reported nothing.

Measured by hand on the device, in the failing state, tapping the very coordinates the helper computes:

| sequence | card opens |
| --- | --- |
| tap the header | **yes** |
| three backward nudges, then tap the header | **no** |

`scrollVisibleScrollableToTop()` was three backward nudges, and on a list already at its top a backward
nudge is a pull-to-refresh. The tap that followed landed while the list was still reloading and was
swallowed — every pass, which is why three passes found nothing.

That helper was added a round earlier on the theory that the tab loop's fling left the newest record off
screen. The theory was inferred from timings and never measured; the real cause that round was the
empty-target refresh, fixed separately. So the speculative fix contributed nothing and became the next
failure. It is removed, not repaired: walking the visible cards already covers position, because every
record this journey creates has failures and whichever card the fling leaves on screen will do.

The reload between passes now runs *after* a failed pass rather than before a tap, and waits for the
cards to come back. Verified as an A/B with the eight journeys that precede this one in a real run:
failing before the change, 8/8 with no exceptions after it.

**Twice now a fix has become the next failure.** The `runCatching` added for a `StaleObjectException`
turned a throw into a skipped index and hid its own cause; this one broke a tap that worked. Both times
the way out was the same: reproduce on the device and measure the step, rather than reason from a stack
trace. The `am instrument` reproduction and the screen dump in the failure message are what made that
affordable — fifteen minutes against forty-four.

## Measured: 22/22 in 37m 24s, both tabs covered

The fifth capture is clean — 22 tests, 0 failures — with the two components this set out to cover:

| component | before | after |
| --- | ---: | ---: |
| `GitHubAppInstallHistoryCards` | **0** | **27** |
| `GitHubAppInstallHistoryUiRecord` | **0** | **10** |
| `GitHubTrackChangeHistoryCards` | 14 | 20 |

**The totals moved the other way, and it is worth not glossing over.** `baseline-prof.txt` went
71,606 -> 71,602 and `startup-prof.txt` 24,712 -> 24,711. Two things are in there. Removing the
scroll-to-top removed its accidental coverage of the history route's *own* pull-to-refresh --
`refreshStarted` and friends go 29 -> 16, which is exactly the 16 that stood before any of this work, so
that is a return to baseline rather than a loss. The rest, about thirty rules, is capture-to-capture
variance: the union across iterations is not identical run to run, and at this scale a few dozen rules
either way is noise. The three components above are what moved deterministically.

Recovering the accidental 13 would take one deliberate `pullToRefresh()` at the end of the route's work,
where no tap follows it and a swallowed one cannot matter. Left undone because it costs a capture, and
the path it covers was uncovered before this work started.

## The route's own pull-to-refresh, on purpose this time

22/22 again, 33m 59s. `refreshStarted` goes 16 -> **29**, which is exactly what the accidental version
reached, and the three history components are unchanged at 20 / 27 / 10.

The pull is placed after every tap the journey needs, which is the whole point: a pull leaves the list
reloading long enough to swallow the next tap, so the only safe place for one is where nothing follows.
It repeats six times because a pull only arms at the top and an expanded record leaves the list about
1.2 screens down against roughly 0.38 per drag -- the early passes scroll, the last ones pull.

**Do not read the totals as a quality signal at this granularity.** Three captures with identical
targeted components came back 71,606, 71,602 and 71,536 baseline rules. The last of those is this one,
and its drop is mostly `github/share/`, which went 237 -> 224 -- still well above the 191 that predate
this work. That journey resolves a real repository over the network, so what it collects moves run to
run. What is worth checking after a capture is the named components, not the sum.

## response_too_large: unreachable, then fixed at the source

The last two functions in `GitHubRefreshHistoryDiagnostics` -- `rememberFailureLimitDetail` and
`formatDiagnosticBytes`, which draw the limit/declared/observed/stage row -- needed a
`response_too_large` failure. Chasing one turned up an app bug rather than a fixture problem.

**The detection worked; the classification threw it away.** A loopback server declaring
`Content-Length: 99999999` made `stringLimitedBlocking` throw exactly as designed --
`content text exceeds 8388608 bytes (stage=DeclaredLength, observed=99999999, declared=99999999)`
appeared verbatim in the failure message -- while the category pill read **"Unknown failure"** and no
size row was drawn. `FdroidBatchPackageSnapshotProvider` reports a combined message when both its
halves fail and can keep only one of them as the cause: it kept `repositoryError ?: apiError`, and that
branch is reached only when the repository fallback also failed, so the typed exception was never the
cause. `FdroidRepositoryIndexClient` does no bounded read at all -- it stream-parses -- so
`repositoryError` could never be the typed one either. The category was unreachable by construction,
and so was every row it drives.

The other three source modes cannot reach it either, checked before concluding: `github_repository` is
pinned to github.com; `git_repository` builds `https://${host}/api/v1`, forcing TLS and dropping the
port, so a loopback fixture never gets a request (its bounded read *would* classify correctly --
`failedGitRepositoryCheck` passes the raw error); and on `direct_apk` the APK step's error is what
reaches `failedCheck`, measured as `network_error` while the `.json` and directory-index bounded reads
never surface.

**The fix is in the classifier, not the fixture.** `GitHubRefreshFailureClassifier` now walks causes
*and* suppressed exceptions, breadth-first, capped at 16 nodes -- an aggregating wrapper can only carry
one cause and the one it keeps is not always the one worth classifying. The F-Droid aggregation now
attaches the API error with `addSuppressed` instead of dropping it. Four unit tests pin it, including a
suppressed *cycle*: the walk is a graph now, so it can contain a loop, and unbounded that hangs a
refresh thread.

On the device the pill reads **Response too large** and the row reads
`Limit 8 MiB · Declared 95 MiB · Read 95 MiB · Stage Before read`. In a single-journey capture the two
functions collect a rule each, taking the component 16 -> 18.

**The fixture is a server, not a payload.** `startOversizedResponseServer` binds 127.0.0.1 inside the
instrumentation process and answers every request with headers only -- no body is ever sent, because
`stringLimitedBlocking` compares the declared length before reading a byte. The test APK already merges
`INTERNET`. It is started once per test rather than inside the `collect` block: the block is replayed
per iteration, and the second replay met its own still-bound socket with `EADDRINUSE`. `reuseAddress`
does not help there -- it covers a socket in TIME_WAIT from a previous run, not one still open.

## Measured: 22/22 in 35m 53s, and where the totals actually come from

Clean capture, and the two functions this set out to reach each collect a rule:

| component | before | after |
| --- | ---: | ---: |
| `GitHubRefreshHistoryDiagnostics` | 16 | **18** |
| `GitHubTrackChangeHistoryCards` | 20 | 21 |
| `GitHubAppInstallHistoryCards` | 27 | 27 |
| `refreshStarted` (route pull) | 29 | 29 |

The F-Droid fixture is not only a diagnostics fixture: it is the first time a capture has driven the
F-Droid client at all. `feature/github/data/remote/fdroid` goes 16 -> 86, `domain/fdroid` 26 -> 97 and
`data/local/fdroid` 30 -> 53, which is **+164** rules of a subsystem that shipped uncompiled.

Totals still read 71,536 -> 71,372, and it is worth writing down what that number is made of, because
three captures have now moved it while every driven component stayed put. The largest single loss this
time is `GitHubBackgroundRefreshService`, `GitHubBackgroundRefreshJobService`,
`AppForegroundInfoHandler` and `GitHubBackgroundRefreshCheckpointWriter` -- the *scheduled* background
refresh. No journey drives those. They are collected when the job service happens to fire inside the
capture's 35 minutes, and not otherwise. The rest of the movement is the BA and share-import journeys,
which fetch real content over the network.

So the sum is mostly a measure of what the device did while the capture ran. Read the named components.

## The F-Droid subsystem, which had been shipping uncompiled

The oversized-response fixture was the first time any capture had spoken to an F-Droid repository, and
it only ever spoke to a broken one. Everything the client does after a *successful* response was still
untouched: `GitHubFdroidDetailSheet` is 713 lines and collected **2 rules**, `FdroidCandidateSelector`
and `FdroidAppSearchService` collected none, `FdroidMetadataSidecar` 8. Nearly six thousand lines, for
one reason -- a capture tracks only what it can reach, and there was no F-Droid repository on the other
end of a capture's network.

There is one now. The fixture server answers two ways by path: the oversized headers for the
diagnostics track, and a real package API response for a second, *succeeding* track. Two versions in
it, with `suggestedVersionCode` pointing at the older one, because that is the shape a real repository
uses to hold back a release and it gives `FdroidCandidateSelector` a choice to make rather than a
single answer to return. Confirmed on the device: the sheet reads `Version 1.0.0 / 12` while the feed
also offers 13, so the suggestion is being honoured rather than the maximum taken.

Measured from `gitHubFdroidTrackInteractions` alone:

| component | before | after |
| --- | ---: | ---: |
| `GitHubFdroidDetailSheet` | 2 | **75** |
| `FdroidMetadataSidecar` | 8 | **87** |
| `FdroidReleaseCheckSource` | 26 | 44 |
| `FdroidPackageApiClient` | 35 | 39 |
| `FdroidCandidateSelector` | **0** | 8 |
| all `fdroid` rules | ~164 | **725** |

The sheet is reached **by capability rather than position**: the detail action composes only for an
F-Droid track, so the journey opens overflows until the tagged item appears and dismisses the ones that
turn out to be other cards. Position would be a bet on the sort order, which is the fixture's own
labels -- the same bet that broke `gitHubReleaseListInteractions` earlier in this document.

Its own `@Test`, too. The fixture import is idempotent so it costs one cold start, and a journey that
depends on another journey having run first is the ordering assumption that has already cost two
captures here.

**What is still uncovered, and why it was left.** `FdroidAppSearchService` (514 lines) and
`GitHubTrackEditFdroidDiscoverySection` (680) are both on the *search* path: adding an F-Droid app
through the track-edit sheet, which means driving a source-mode dropdown and then a query field. This
file already records why that is a different class of risk -- "driving a text field from a journey
depends on focus and the IME in a way a tab tap does not" -- so it is named here rather than attempted
at the end of a long change.

## Measured: 23/23 in 35m 24s, and the first capture whose total went up

| | before | after |
| --- | ---: | ---: |
| `baseline-prof.txt` | 71,372 | **72,507** (+1,135) |
| `startup-prof.txt` | 24,711 | 24,710 |
| all `fdroid` rules | 446 | **727** |
| `GitHubFdroidDetailSheet` | 2 | **75** |
| `FdroidMetadataSidecar` | 8 | **87** |
| `FdroidReleaseCheckSource` | 21 | 45 |
| `FdroidCandidateSelector` | **0** | 8 |

The three history components held at 18 / 21 / 27. This is also the first capture in this run of work
whose *total* moved up rather than down, which is what a new journey looks like against the noise the
section above describes: +1,135 is well outside the +-150 the undriven background refresh and the
network-dependent journeys have been swinging by.

**The capture before this one failed, and not on anything here.** `baGuideCatalogInteractions` could
not bring `ba_guide_catalog_dock_favorite_bgm` back into view after its eight retries, having run 265s
against its usual 231s. Two readings were available and the flattering one is not automatically right,
so the suspicion worth testing was mine: four permanently-failing tracked fixtures mean every app start
now runs a refresh that fails them, one of which reaches api.github.com, and that is real contention for
a journey which loads remote content. Tested directly -- seed the fixtures with the F-Droid journey,
then run the BA journey alone against that state in a second `am instrument` invocation so the data
persists -- and it passed. Combined with that journey passing every other capture, including this one,
the balance is a flake in a network-dependent journey rather than interference. One data point is not
proof of absence; if it recurs there is now a way to reproduce it in eight minutes.
