# Baseline profile coverage

How the shipped ART profile is generated, what it actually covers, and why the journeys are the
ones they are. Companion to `scripts/qa/baseline_profile_freshness.sh`, which answers the narrower
question of whether the committed capture is still current.

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
- **`github.history`'s tabs — walked, but their cards do not compose.** All four categories are
  selected now and the package collects 549 rules, and yet `GitHubAppInstallHistoryCards`,
  `GitHubRefreshHistoryDiagnostics` and `GitHubTrackChangeHistoryCards` still carry none. Nothing is
  wrong with the journey: a capture installs the app fresh, and those three tabs render history the
  app accumulates over time, so on a clean device they are empty states. Covering the cards means
  seeding history before the journey runs, which is a fixture problem rather than a tagging one.
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
