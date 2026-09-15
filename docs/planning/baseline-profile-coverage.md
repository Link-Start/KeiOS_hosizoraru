# Baseline Profile plan

This document defines what KeiOS compiles ahead of time, the time budget for collecting it, and the
evidence required before a generated profile is accepted.

## Current design

The default generator contains six user journeys with a maximum of 16 replays.

| Journey | Max/stable replays | What it warms |
| --- | ---: | --- |
| startupAndFirstScroll | 5/2 | cold startup, Home first frame, first two list flings, startup dex layout |
| mainPagesAndNavigation | 3/2 | Home, OS, MCP, GitHub and BA destination switches, first scrolls, and one hand swipe both ways |
| commonRoutesAndChrome | 2/2 | Settings, About, WebDAV, Shell, MCP Skill, shared menu presentation |
| gitHubTrackingCore | 2/2 | tracked-card expansion, Actions, add-track, and strategy-Sheet drag/content motion |
| baOfficeAndCatalogCore | 2/2 | office cards, calendar/pool, daily sheet, catalog, selected guide tabs, playback |
| adaptiveLargeScreenCore | 2/2 | recent two-lane pages, independent lane scrolling, sidebar and fold reflow |

The previous generator had 23 journeys with a 96-replay ceiling. Its last capture took 35m24s on the
API 37 AVD and produced 72,507 textual baseline rules plus 24,710 startup rules. That run followed an
earlier 1h21m capture made with library-default replay limits. The new 16-replay ceiling is an 83.3%
reduction in maximum cold starts.

The first accepted six-journey capture ran on `KeiOS_API37_Validation` on 2026-09-02. It completed in
9m22s, with all six generator tests passing in 8m53s of device time. This is 73.5% less wall time than
the previous 35m24s capture. The merged outputs contain 60,896 baseline rules and 23,990 startup rules:
16.0% and 2.9% smaller respectively. The reduction comes from removing low-frequency and fixture-heavy
paths while retaining named rules for Liquid Sheet, the merged Calendar/Pool page, catalog, primary
navigation and adaptive layout.

| Journey | Device time |
| --- | ---: |
| commonRoutesAndChrome | 70.3s |
| baOfficeAndCatalogCore | 92.8s |
| gitHubTrackingCore | 67.8s |
| mainPagesAndNavigation | 102.9s |
| startupAndFirstScroll | 46.1s |
| adaptiveLargeScreenCore | 153.4s |

BaselineProfileTestTagContractTest pins the six-journey and 16-replay limits. Increasing either
requires an explicit update to the test and this plan.

## The switch a tab tap cannot stand in for

Every page switch in the profile used to be a tab tap, and a tap and a swipe do not run the same code.
A tap animates the pager on a timed curve through `animateLoadedPagerPosition`. A finger runs
`draggable`'s drag detection, then `startUserScroll`, `dragBy` once per frame, and `settleAfterDrag`'s
velocity spring -- `animateLoadedPagerSettlePosition`, which has one call site that no tap reaches.

So `mainPagesAndNavigation` now ends by swiping Home -> OS and back, inside its existing cold start and
replay budget. Measured rather than assumed, by capturing that journey twice on the same AVD:

| in the journey's own baseline-prof.txt | tab taps only | with the swipe |
| --- | ---: | ---: |
| `MainLoadedPagerState;->startUserScroll` | 0 | 1 |
| `MainLoadedPagerState;->dragBy` | 0 | 1 |
| `MainLoadedPagerState;->settleAfterDrag` | 0 | 1 |
| `animateLoadedPagerSettlePosition` | 0 | 1 |

What this does **not** claim is new rules in the shipped profile. The merged profile already carried that
path before this change -- from `baOfficeAndCatalogCore`, whose per-journey file shows it and whose
subject is office cards and the catalogue. That is accidental coverage: it survives only as long as some
BA gesture keeps clipping the pager, and nothing would report its loss. The change moves the switch users
actually perform into the journey named for page switching, and `theMainPagerIsAlsoSwitchedByHand` pins
both directions plus the settled-tag proof.

The arrival is asserted, not optional, for the reason the guide rail is: a horizontal drag a child
consumes leaves the pager where it was, and the destination's page root stays composed as a pager
neighbour either way. Only the *settled* tag separates a real switch from a silent no-op.

## Re-captured 2026-09-15

On `KeiOS_API37_Validation`, 13m04s wall for `:app:generateReleaseBaselineProfile`, all six journeys
passing. Slower than the 9m22s of 2026-09-02 because the app has grown, not because a journey stalled.

| Journey | Device time |
| --- | ---: |
| gitHubTrackingCore | 67.3s |
| commonRoutesAndChrome | 68.7s |
| startupAndFirstScroll | 79.3s |
| baOfficeAndCatalogCore | 98.1s |
| mainPagesAndNavigation | 109.8s |
| adaptiveLargeScreenCore | 160.0s |

Baseline rules 61,226 -> 59,967 (828 added, 2,087 removed); startup rules 24,123 -> 24,145. The total
went **down**, which is the metric this plan already says not to judge a capture by. Named components:

| named component | before | after |
| --- | ---: | ---: |
| `ui/page/main/host` | 1538 | 1538 |
| `MainLoadedPager` | 229 | 229 |
| `ui/page/main/home` | 651 | 650 |
| `kyant/backdrop` | 1520 | 1563 |
| `yukonga/miuix` | 1972 | 1988 |
| `compose/foundation/lazy` | 1361 | 1369 |
| `media3` | 5160 | 5196 |
| `ui/page/main/student` | 5517 | 5744 |
| `ui/page/main/github` | 3153 | **2969** |

Every named path holds or grows except GitHub, and that one is the term this plan already names. What
left is `GitHubRefreshBatchActions$refreshTrackedBatchInternal`, `GitHubPageRefreshNotificationBridge`,
`saveTrackedItems`, `persistCheckCacheNow`, `LatestReleaseCandidate` and `VersionCheckUi` (45 -> 23):
a background refresh no journey drives, which fired during the 2026-09-04 capture and did not fire
during this one. No UI path lost rules.

## Selection rule

A path belongs in the default profile when it satisfies these properties:

- users encounter it during startup or routine navigation;
- the path performs first composition, scrolling, animation, Markdown, media, or adaptive layout work;
- the journey is deterministic on a fresh install;
- the same path can cover several shared components behind one cold start;
- profile value can be checked by named packages/classes or a Macrobenchmark metric.

Feature completeness remains a functional-testing goal. The Profile focuses ART's install-time
compilation budget on high-frequency latency.

## Paths removed from default collection

| Path | Reason |
| --- | --- |
| release-list pagination and page jump | remote content, low interaction frequency, large overlap with tracked-card rendering |
| GitHub refresh-failure injection and all history diagnostics | socket/package-state fixtures, scheduler noise, operational diagnostics |
| F-Droid metadata failure/detail fixture | remote and loopback behavior, narrow feature path |
| component lab and Liquid catalogue | developer-only surface |
| external share and JSON import entry points | separate cold activities, low frequency |
| tile long-press daily editor | shell/component setup; the BA dock reaches the user-facing editor |
| all six student-detail tabs | large low-frequency sweep; Profile keeps Skills and Profile as representative content paths |
| every accordion and every sheet variant | shared animation/presentation code is warmed through representative instances |

These removals also eliminate loopback servers, package hide/unhide, external ACTION_SEND launches
and real-network fixture setup from the default generator.

## Shared Liquid Sheet coverage

Opening a Sheet only compiles its first composition and enter animation. The shared motion path also
needs the grabber, resize layout, nested-scroll arbitration, content fling and settle spring.

gitHubTrackingCore uses the deterministic long Strategy Sheet as the representative shared surface:

1. open the Sheet and wait for both the panel and shared drag-region tags;
2. drag the grabber upward to expand it;
3. scroll the lazy content up and back down;
4. drag the grabber downward to resize it;
5. dismiss through the normal back path.

This stays inside the existing GitHub cold start and its 2/2 replay budget. It covers shared Sheet
implementation rather than adding one journey per business Sheet.

MainNavigationFrameBenchmarks contains matching `liquidSheetMotionBaselineProfile` and
`liquidSheetMotionCompilationNone` tests. Each runs five iterations and reports FrameTimingMetric for
the same expand, content-scroll and collapse trace sections, plus RenderThread/UI slice metrics. Their
A/B delta establishes the part ART compilation can recover; the slice split keeps RenderThread
glass-layer cost visible as a separate runtime term.

The 2026-09-02 A17 API 37 AVD diagnostic run used the R8-enabled `benchmarkRelease` variant. The
Profile reduced median Compose recomposition total from 4.07ms to 2.05ms and median maximum
`Choreographer#doFrame` from 81.43ms to 76.44ms. End-to-end frame CPU P95 stayed effectively flat
(99.82ms Profile, 99.13ms None), as did frame-overrun P95 (128.25ms Profile, 129.01ms None). The
Profile successfully warms the Kotlin/Compose path; Draw/RenderThread work remains the Sheet's
dominant smoothness limit. Emulator suppression was supplied only on the command line, so committed
benchmarks continue to require suitable performance hardware.

## Recent adaptive UI coverage

adaptiveLargeScreenCore forces a 1000x800dp window, then exercises both horizontal lane coordinates.
It covers the recent branches in:

- Settings and About two-column lists;
- OS cards and Shell command/output panes;
- MCP cards and the Skill page's two lanes;
- BA office lanes and the merged Calendar/Pool route;
- catalog Students, Lobby, Music and Play layouts, including the album/queue split when data exists;
- GitHub main content and History lanes when records exist;
- top navigation, sidebar navigation and a live 775dp to 500dp fold transition;
- the student guide's rail, converted to and back from inside the journey.

The journey restores wm size in finally, so a failure cannot leak tablet geometry into later tests.

### The guide rail is the one journey step that writes a preference

The guide's rail is off by default and stored, so nothing else in the profile ever composes it -- and
reaching it means *changing* a setting on the capture device rather than only reading one. So that step
converts back in a `finally` of its own, and the restore is verified rather than assumed: after a run,
force a tablet window on the capture device and open a guide page. It must show the bottom bar with the
toggle offered, and no `..._sidebar_row` tags. Checked that way at ab30aaefb.

Two things about the step are load-bearing. It runs **on arrival at the catalog**, before the lane
flings and the tab tour, because doing it afterwards silently found nothing -- the first entry was no
longer where `scrollTestTagIntoReach` could reach it, and every wait in that block is optional, so the
journey passed while compiling none of the rail. And the conversion is confirmed by the *row* tag, not
the toggle: the toggle keeps one tag in both shapes, so waiting on it again passes either way. When
changing this step, read the journey's own `BaselineProfileGenerator_adaptiveLargeScreenCore-baseline-prof.txt`
for `BaStudentGuideSidebar` instead of trusting a green run.
Content-dependent cards remain optional; the wide page and container branches still execute on a fresh
install.

Calendar and Pool have one dock entry and one page root. Phone collection selects the Pool category
inside that page. Wide collection follows the current UI contract: both lists are visible in independent
lanes and the category bar is absent, so the journey scrolls both lanes directly.

The Star List Import page enters its two-lane state only after a remote preview exists. Its empty state
uses one lane by design. The default Profile opens shared GitHub chrome and edit surfaces; Star preview
lane behavior stays covered by source and screenshot tests until a deterministic local preview fixture
exists.

## Startup and measurement contract

startupAndFirstScroll is the only journey with includeInStartupProfile enabled. It reaches Home and
performs two flings, so startup-prof.txt covers dex layout for startup and the first user gesture.

MainPagerPageHost calls ReportDrawn when the real Home branch is composed. StartupTimingMetric can
therefore report timeToFullDisplay for the first meaningful app surface.

On the same R8-enabled AVD diagnostic run, ten cold starts measured a 569.23ms median with the Profile
and 610.38ms with `CompilationMode.None`, a 41.15ms or 6.74% improvement. The AVD was userdebug and
CPU frequency was unlocked; this result validates direction and Profile wiring rather than a device
shipping target.

Macrobenchmarks keep these rules:

- profiled measurements use CompilationMode.Partial with BaselineProfileMode.Require;
- the A/B sibling uses CompilationMode.None;
- startup uses at least 10 iterations;
- frame timing uses at least 5 iterations;
- reported comparisons use medians, with P95 frameOverrunMs for scrolling/navigation;
- performance conclusions come from a release/benchmark build on suitable physical or Cuttlefish
  hardware.

## Fast validation before a full capture

Use source/build gates while editing journeys:

    ./gradlew :app:testDebugUnitTest --tests os.kei.ui.testing.BaselineProfileTestTagContractTest
    ./gradlew :baselineprofile:compileNonMinifiedReleaseKotlin

A connected smoke run can target one journey through instrumentation. It validates tags, navigation
and timeouts without producing the complete merged release Profile:

    adb shell am instrument -w \
      -e targetAppId os.kei \
      -e class os.kei.baselineprofile.BaselineProfileGenerator#adaptiveLargeScreenCore \
      os.kei.baselineprofile/androidx.test.runner.AndroidJUnitRunner

Two arguments the Gradle task supplies and a hand-run does not:

- The component is `os.kei.baselineprofile`, **not** `os.kei.baselineprofile.test`. A Macrobenchmark
  module self-instruments -- `pm list instrumentation` reports `target=os.kei.baselineprofile` -- so the
  `.test` suffix an ordinary androidTest APK carries does not exist here. Without it the command fails in
  a second with `Unable to find instrumentation info`.
- `-e targetAppId os.kei`, or every journey dies on the first line with `targetAppId not passed as
  instrumentation runner arg`.

Check that the run actually started rather than assuming: both failures exit 0 through a pipe, and read
like a stalled journey rather than a malformed command.

Install both APKs first, or the run instruments a stale build:

    ANDROID_SERIAL=<avd> ./gradlew :app:installNonMinifiedRelease :baselineprofile:installNonMinifiedRelease

This smoke run proves the UI script. Complete Profile collection proof comes from the generation task,
its per-journey outputs and the merged generated artifacts.

## Full capture acceptance

Run the complete capture only after the source/build gates pass:

    ./gradlew :app:generateReleaseBaselineProfile

Accept the result after all of these checks:

1. Gradle exits successfully; preserve the Gradle exit code when output is piped.
2. All six generator journeys complete.
3. Per-journey files exist under
   baselineprofile/build/outputs/connected_android_test_additional_output/nonMinifiedRelease/.
4. The generated baseline-prof.txt and startup-prof.txt changed from this commit.
5. scripts/qa/baseline_profile_freshness.sh reports the expected generated artifacts.
6. The release APK contains assets/dexopt/baseline.prof and assets/dexopt/baseline.profm.
7. Named critical components retain rules: startup/Home, main navigation, Compose scrolling,
   Liquid presentation, Markdown, Media3 when catalog data is available, and adaptive layout helpers.
8. Macrobenchmark A/B medians show startup or frame-timing value with BaselineProfileMode.Require.

Rule count alone is diagnostic metadata. A larger total can come from background jobs, network timing
or unrelated library paths. Acceptance follows named-path coverage, shipped assets and measured
startup/frame behavior.

## Device hygiene

Use one SDK platform-tools/adb server for connected capture. Competing Homebrew and SDK ADB servers
previously caused Unknown API Level, ShellCommandUnresponsiveException and false device discovery
failures.

The generated profiles in the working tree represent the accepted six-journey capture. The freshness
script dates a capture from the latest commit touching the generated Profile directory, so it continues
to report the previous capture until these generated artifacts are committed.
