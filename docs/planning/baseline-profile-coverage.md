# Baseline Profile plan

This document defines what KeiOS compiles ahead of time, the time budget for collecting it, and the
evidence required before a generated profile is accepted.

## Current design

The default generator contains six user journeys with a maximum of 16 replays.

| Journey | Max/stable replays | What it warms |
| --- | ---: | --- |
| startupAndFirstScroll | 5/2 | cold startup, Home first frame, first two list flings, startup dex layout |
| mainPagesAndNavigation | 3/2 | Home, OS, MCP, GitHub and BA destination switches plus first scrolls |
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
- top navigation, sidebar navigation and a live 775dp to 500dp fold transition.

The journey restores wm size in finally, so a failure cannot leak tablet geometry into later tests.
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
      -e class os.kei.baselineprofile.BaselineProfileGenerator#adaptiveLargeScreenCore \
      os.kei.baselineprofile.test/androidx.test.runner.AndroidJUnitRunner

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
