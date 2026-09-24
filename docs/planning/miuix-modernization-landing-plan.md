# MIUIX Modernization Landing Plan

> Reference: `.tmp/miuix` at `b09d5deb`.
> Scope: migrate KeiOS temporary MIUIX-era adapters to current MIUIX behavior cores while preserving KeiOS Liquid Glass visuals, custom sheet guard rules, user-facing motion richness, and existing app architecture.

## Goals

- Keep public KeiOS UI helper interfaces stable where possible.
- Move shared behavior cores to current MIUIX implementations or MIUIX-shaped thin adapters.
- Preserve custom Liquid Glass rendering, Backdrop visual depth, adaptive sheet sizing, and unsaved-dismiss checks.
- Reduce long-term maintenance load in popup, sheet, squircle, slider, switch, chrome, and predictive-back code.
- Verify each phase with focused unit/compile checks before broader AVD validation.

## Priority Matrix

| Priority | Area | Landing Target | Status |
| --- | --- | --- | --- |
| P0 | Nav residual cleanup | Keep route-level `NavDisplay` as the owner of route predictive back; narrow custom runtime to pager/local/fullscreen/activity paths. | Done |
| P0 | Temporary source residue | Remove the old ignored `.tmp/miuix-nav-v1` reference cache after migrating to the latest `.tmp/miuix` navigation source. | Done |
| P1 | Squircle | Convert `AppSquircle` into a thin wrapper over `top.yukonga.miuix.kmp.squircle` while retaining existing `appSquircle*` call sites. | Done |
| P1 | Popup/Menu | Replace custom popup back-dismiss behavior with a MIUIX `NavigationBackHandler` adapter, keeping KeiOS glass row visuals and adaptive width policy. | Done |
| P1 | Bottom sheet | Rebase Liquid Glass sheet behavior onto current MIUIX bottom-sheet interaction patterns: `NavigationBackHandler`, single transition collector, drag snap channel, and nested-scroll dismissal semantics. | Done |
| P2 | Slider/Switch | Adopt MIUIX interaction semantics for drag, press, haptic, hover, and snap, while keeping Backdrop glass track/thumb visuals. | Done |
| P2 | Chrome primitives | Audit MIUIX badge/tooltip/press/indication overlap. Keep existing KeiOS chrome primitives where they already wrap Miuix badge/tooltip and custom glass press visuals. | Done |
| P2 | Back runtime | Keep OEM policy and local fullscreen/activity handling; route-level predictive back is owned by Miuix `NavDisplay`. | Done |

## Implementation Order

1. Document and baseline scan. Done.
2. P0 route-level back cleanup and stale nav reference handling. Done.
3. P1 squircle wrapper migration. Done.
4. P1 popup/menu adapter migration. Done.
5. P1 bottom-sheet behavior alignment. Done.
6. P2 slider/switch interaction alignment. Done.
7. P2 chrome/back runtime cleanup. Done.
8. Compile, focused unit test, R8, and release art-profile pass. Done.

## Guardrails

- Preserve visual quality and interaction richness.
- Keep data and UI flow architecture unchanged unless a touched module requires a local cleanup.
- Keep public function names such as `appSquircleBackground`, `SnapshotWindowListPopup`, and `SnapshotWindowBottomSheet` during the first pass to avoid broad call-site churn.
- Prefer layout/draw-phase reads for hot drag/animation state.
- Keep sheet dismiss guard behavior intact: blocked dismiss should surface the existing prompt path.
- Keep AVD validation for a later visual QA pass after compile/test stability.

## Verification Checklist

- `./gradlew :ui-liquid-glass:compileDebugKotlin`
- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:testDebugUnitTest`
- `git diff --check`
- `./gradlew :app:compileReleaseArtProfile`
- Optional visual pass: AVD sheet, dropdown, action bar, bottom bar, slider, switch, and route back smoke.

## Progress Log

| Date | Phase | Notes |
| --- | --- | --- |
| 2026-06-30 | Plan | Added plan for P0/P1/P2 MIUIX modernization landing work. |
| 2026-06-30 | P0 | Renamed custom route source to `StandaloneRoute`; route-level predictive back remains owned by Miuix `NavDisplay`. Removed ignored `.tmp/miuix-nav-v1` cache. |
| 2026-06-30 | P1 | `AppSquircle` now delegates to `miuix-squircle` public APIs while keeping non-composable draw-path helpers for hot-path callers. Popup and Liquid Glass sheet now use `NavigationBackHandler` predictive-back progress instead of framework back-only dismissal. |
| 2026-06-30 | P2 | Liquid sliders gained MIUIX-style edge/key-point haptics; Liquid switch gained toggle haptics on drag and tap. Existing action bar/bottom bar/floating dock chrome already uses shared KeiOS glass primitives plus Miuix badge/tooltip pieces, so this pass preserved those visuals. |
| 2026-06-30 | Verification | Passed `:ui-liquid-glass:compileDebugKotlin`, focused app back tests, `:ui-liquid-glass:testDebugUnitTest`, `:app:compileReleaseArtProfile`, and `git diff --check`. Removed stale Squircle SDF entries from release baseline profiles. |
| 2026-08-16 | Upgrade | `0.9.3-c6d7d6dd-SNAPSHOT` → `0.9.4-4a6b750b-SNAPSHOT`. See [Snapshot upgrade: 0.9.4](#snapshot-upgrade-094). |
| 2026-08-24 | Upgrade and adoption | `0.9.4-4a6b750b-SNAPSHOT` → `0.9.4-4f86de92-SNAPSHOT`; adopted MIUIX floating-toolbar ownership for the phone main navigation. See [Snapshot follow-up: 4f86de92](#snapshot-follow-up-4f86de92). |
| 2026-08-31 | Upgrade | `0.9.4-4f86de92-SNAPSHOT` → `0.9.4-7cc339c2-SNAPSHOT`; no library source moved, so nothing to adapt. See [Snapshot follow-up: 7cc339c2](#snapshot-follow-up-7cc339c2). |
| 2026-09-14 | Upgrade | `0.9.4-7cc339c2-SNAPSHOT` → `0.9.4-5157b503-SNAPSHOT`; two library commits, neither in a component KeiOS uses. See [Snapshot follow-up: 5157b503](#snapshot-follow-up-5157b503). |
| 2026-09-23 | Upgrade and adoption | `0.9.4-5157b503-SNAPSHOT` → `0.9.4-39c40f99-SNAPSHOT`; the MCP page gets a real `SavedStateHandle` now that nav entries carry creation extras, and tab switches and pager snaps take Miuix's page-navigation spring. See [Snapshot follow-up: 39c40f99](#snapshot-follow-up-39c40f99). |

<a id="snapshot-upgrade-094"></a>

## Snapshot upgrade: 0.9.4-4a6b750b-SNAPSHOT

Latest snapshot on the GitHub Packages feed, published 2026-08-13. `gradle.properties` carries the
pin; `libs.versions.toml` holds the fallback and had drifted to a stale `0.9.2` — both now agree.

Snapshot artifacts on that feed are addressed by their timestamped filename
(`…-0.9.4-4a6b750b-20260813.145107-1-sources.jar`), not by the `-SNAPSHOT` version string, so a plain
`curl` of the version-named path 404s. Read the per-version `maven-metadata.xml` for the
`<snapshotVersion>` value first.

### The source delta is four files

Diffed from the published sources jars of both versions across all eight artifacts:

| file | module | reaches KeiOS? |
| --- | --- | --- |
| `nav/gesture/NavSwipeArbitrator.kt` (new) | nav | only if `swipeDismiss` is enabled — it is not |
| `nav/gesture/NavSwipeDismiss.kt` | nav | same |
| `nav/core/NavDisplay.kt` | nav | comment only |
| `blur/DrawBackdropModifier.kt` | blur | **no** — see below |
| `basic/Scaffold.kt` | ui | **no** — see below |

**`Scaffold.kt`** hoists the floating-toolbar measurement above the snackbar offset so a bottom-docked
toolbar can no longer cover a snackbar. KeiOS has zero references to `floatingToolbar`,
`ToolbarPosition` or `snackbar` in `:app`, `:ui-liquid-glass` or any feature module, so both slots
measure 0×0: `isFloatingToolbarEmpty` short-circuits the new offset to `null` and `snackbarHeight == 0`
short-circuits the new `coerceAtLeast`. Layout is byte-identical.

**`DrawBackdropModifier.kt`** removes `DrawBackdropNode.captureLayerScale` and the display-space
inverse it fed — the path that inverted a pure-scale `layerBlock` at full resolution instead of inside
a downscaled recording, to stop fractional resampling flicker. That path only runs for a **non-null
`layerBlock`**, and KeiOS never passes one: it reaches miuix blur solely through `textureBlur`,
`textureEffect` and `progressiveTextureBlur` (`TextureEffect.kt`, unchanged), none of which expose the
parameter, and no miuix-ui or miuix-nav component passes it either. KeiOS's own `layerBlock`-driven
reveals — the toast and the menu — run on `com.kyant.backdrop`, a different library. Unreachable, so
the revert cannot regress us; if a future KeiOS surface does pass a `layerBlock` over a downscaled
miuix blur, expect scale-animation flicker and check this file first.

**Nav** closes the issue #21 gap. Recorded in
[`miuix-nav-swipe-dismiss-gap.md`](miuix-nav-swipe-dismiss-gap.md), including why the gesture stays
disabled here anyway.

### The upgrade is also a Compose upgrade

Not optional, and easy to miss. 0.9.4 moved its Compose dependency from
`org.jetbrains.compose.foundation:foundation` **1.11.1** to `foundation-android` **1.12.0-rc01**, and
`material3-window-size-class` 1.9.0 → 1.12.0-alpha03. JetBrains' Android variants *are* the androidx
artifacts, so every `androidx.compose.*` module — foundation, ui, runtime, saveable, graphics — now
resolves to **1.12.0-rc01** regardless of what the catalog declares. Holding androidx at 1.11.4 would
run miuix against APIs older than it was compiled on, so the catalog follows the resolution instead of
fighting it; `BuildConfig.COMPOSE_VERSION` feeds the diagnostics page and must not lie.

One new deprecation surfaced from the Compose side, not from miuix:
`androidx.compose.ui.test.junit4.createComposeRule` now points at the `v2` package, whose rules use
`StandardTestDispatcher` instead of `UnconfinedTestDispatcher`. It is a warning only —
`AppTopBarSearchShellScreenshotTest` still passes — but the migration queues work, so tests relying on
immediate execution will need explicit synchronisation when it happens.

### Verified

- `:app` and `:ui-liquid-glass` compile with no new source incompatibility.
- 1793 unit tests across `:app`, `:ui-liquid-glass`, `:core-prefs` — 0 failures, same count as before.
- API 37 AVD: Home, OS, MCP, GitHub and BA all render; Home's `textureBlur` chip rows, the BA floating
  dock glass and the bottom bar are intact; logcat clean of `FATAL`, `AndroidRuntime`,
  `NoSuchMethod` and `NoClassDefFound` across the sweep.

<a id="snapshot-follow-up-4f86de92"></a>

## Snapshot follow-up: 0.9.4-4f86de92-SNAPSHOT

The 2026-08-22 snapshot is pinned in both `gradle.properties` and `libs.versions.toml`. Its Android
runtime source delta from `4a6b750b` is the nullable smart-cast cleanup in `Scaffold.kt`; the published
artifacts keep the floating-toolbar and snackbar placement contract introduced earlier on the 0.9.4 line.

KeiOS now routes the compact-width main navigation through `Scaffold.floatingToolbar` at
`ToolbarPosition.BottomCenter`. `AppScaffold` and `AppPageScaffold` expose the upstream
`floatingToolbar`, `floatingToolbarPosition`, and `snackbarHost` slots so app-level chrome can use the
same placement owner. The regular-width top navigation, sidebar, and route-local fixed action bars keep
their existing adaptive layout roles.

The Liquid Glass surface, collapse animation, pager selection interpolation, test tags, grip-aware
touch handling, and Backdrop ownership stay in KeiOS. MIUIX contributes bottom safe-inset placement and
keeps snackbars above the dock. The content-side padding accounts for MIUIX's 4dp host spacing, preserving
the established visible baseline of 8dp above a reported navigation inset and the 36dp zero-inset fallback.

### Follow-up verification

- Compose layout contract: the floating toolbar remains an overlay and leaves content padding unchanged.
- Snackbar layout contract: a bottom snackbar is measured above the floating toolbar.
- Main pager geometry contract: reported and zero navigation-inset baselines retain their previous values.
- `:app:testDebugUnitTest`: 1565 tests, 0 failures and 0 errors.
- `:app:assembleRelease`: R8, resource optimization, Lint Vital, and ART Profile compilation passed.
- API 37 visual acceptance: 1280×2856 phone expanded navigation, OS selection, and scroll collapse passed;
  2560×1600 tablet top-tab and sidebar modes passed. The target app log stayed clear of fatal runtime and
  linkage errors through the sweep.
<a id="snapshot-follow-up-7cc339c2"></a>

## Snapshot follow-up: 0.9.4-7cc339c2-SNAPSHOT

**Nothing to adapt.** All eight upstream commits between `4f86de92` (2026-08-22) and `7cc339c2`
(2026-08-29) are Renovate dependency bumps and CI workflow edits. The compare touches six files —
four `.github/workflows/*.yml`, `gradle/libs.versions.toml`, and `gradle/wrapper/*.properties` —
and not one library source file.

Verified against the published artifacts rather than only the compare, because the tag-to-artifact
mapping is the part that could be wrong: the sources jars for all six modules KeiOS consumes are
byte-identical across the two snapshots.

| module | .kt files | old vs new |
| --- | --- | --- |
| `miuix-ui` | 86 | identical |
| `miuix-icons` | 156 | identical |
| `miuix-nav` | 29 | identical |
| `miuix-blur` | 24 | identical |
| `miuix-preference` | 19 | identical |
| `miuix-squircle` | 7 | identical |

### What actually reaches KeiOS

Two transitive versions moved in the Gradle module metadata, and neither changes behaviour.

`org.jetbrains.compose.foundation:foundation` **1.12.0-rc01 → 1.12.0**. Its Android variant *is* the
androidx artifact, so this is what the catalog comment above is about, and `compose` is now declared
at `1.12.0` to match what resolves. The two androidx AARs were unpacked and hashed file by file:
**1984 entries each, exactly one differs** — `META-INF/androidx.compose.foundation_foundation.version`,
the version stamp. The bytecode is identical, so rc01 to final is a re-stamp and carries no runtime
change. This is also why `:ui-liquid-glass:compileDebugKotlin` stayed `UP-TO-DATE` through the bump:
Gradle's compile-classpath normalisation saw an unchanged ABI, correctly.

`com.materialkolor:material-color-utilities` **5.0.0 → 5.0.1**. The release headline — "stop
overriding consumer Material3 version on Android" — does not apply here twice over: the utilities
module never declared a Material3 dependency, and KeiOS has no androidx Material3 on its classpath at
all. Its only metadata change is `kotlin-stdlib` 2.4.0 → 2.4.10, which the app already pins at 2.4.10.

So the entire eight-commit delta lands in the APK as one changed string, `BuildConfig.COMPOSE_VERSION`,
which the About page reads. `:app:compileDebugKotlin` recompiling while every library module stayed
up-to-date is exactly that and nothing more.

### Verification

- `:app:compileDebugKotlin` and `:ui-liquid-glass:compileDebugKotlin` pass; no new warnings.
- `:app:testDebugUnitTest` 1567 tests and `:ui-liquid-glass:testDebugUnitTest` 438 tests —
  2005 total, 0 failures and 0 errors.
- `:app:assembleRelease`: R8, resource optimization, Lint Vital, and ART Profile compilation passed.
- Baseline profile freshness: this bump moves it no further. `baseline_profile_freshness.sh` does report
  STALE, but on `GitHubStrategySheet.kt`, `AppFeatureCards.kt` and `CullWhenFullyClipped.kt` — the
  Liquid-Glass cull work, already stale before this. No build-file change can affect that gate, which
  compares only `src/main` runtime sources.
- API 37 AVD visual acceptance (release APK, 1280×2856): Home, OS, MCP, GitHub and BA all render and
  scroll. Home's `textureBlur` chip rows, the BA office cards and floating dock, the GitHub tracked card,
  the MIUIX floating toolbar and the bottom-bar dock glass are all intact, including the dock's blur over
  scrolled content. The log carried no `FATAL`, `NoSuchMethod`, `NoClassDefFound` or `AbstractMethodError`
  through the sweep — the only `AndroidRuntime` lines are `uiautomator`'s own, from the dumps that drove it.
- The one user-visible change, confirmed in the shipped artifact: `BuildConfig.COMPOSE_VERSION` is
  `"1.12.0"`, and no `1.12.0-rc01` string survives anywhere in the release dex.
<a id="snapshot-follow-up-5157b503"></a>

## Snapshot follow-up: 0.9.4-5157b503-SNAPSHOT

**Nothing to adapt.** Nine upstream commits between `7cc339c2` (2026-08-29) and `5157b503`
(2026-09-11), of which exactly two touch library source — and both land in components KeiOS does
not compose. A grep for `TabRow`, `TabRowWithContour`, `NavigationBarItem`, `FloatingNavigationBarItem`
and `NavigationBarDefaults` across every module returns nothing, against 226 files that import
`miuix.kmp.basic` for something else. The app's own bottom chrome is KeiOS glass, not
`miuix.kmp.basic.NavigationBar`.

| commit | subject | what it is |
| --- | --- | --- |
| `86cce57f` | library: prevent TabRow horizontal nested scrolling | `TabRow` and `TabRowWithContour` now swallow leftover horizontal scroll and fling (`onPostScroll`/`onPostFling` returning the available x) and carry `overScrollHorizontal`, so dragging a full tab row no longer pages the `HorizontalPager` under it. |
| `e2cca731` | library: Add colors parameter to NavigationBarItem & FloatNavigationBarItem (#414) | New `NavigationBarItemColors` value class and `NavigationBarDefaults.navigationBarItemColors(…)`; both item composables gained a `colors` parameter defaulting to it. Source-compatible, ABI-breaking. |

One behaviour nuance in `e2cca731` worth recording in case KeiOS ever adopts these items: the
pressed/unselected tints moved from **replacing** the base colour's alpha
(`color.copy(alpha = UnselectedAlpha)`) to **multiplying** it
(`color.copy(alpha = color.alpha * UnselectedAlpha)`). For the default opaque
`onSurfaceContainer` the two are the same value; they diverge only for a translucent colour passed in.

Verified against the published artifacts and not only the compare, the same way as last time. The
sources jars for all six modules KeiOS consumes were unpacked and compared file by file:

| module | .kt files | old vs new |
| --- | --- | --- |
| `miuix-ui` | 86 | `NavigationBar.kt` and `TabRow.kt` differ; the other 84 identical |
| `miuix-icons` | 156 | identical |
| `miuix-nav` | 29 | identical |
| `miuix-blur` | 24 | identical |
| `miuix-preference` | 19 | identical |
| `miuix-squircle` | 7 | identical |

So the artifact agrees with the compare exactly — no third file moved quietly.

### What actually reaches KeiOS

One transitive version moved in the Gradle module metadata, and it is not a Miuix one:
`org.jetbrains.kotlin:kotlin-stdlib` **2.4.10 → 2.4.20**, from upstream's Renovate bump of the Kotlin
monorepo (`91301e89`). That is enough to change what resolves here, because KeiOS pins the Kotlin
plugin at `2.4.20-RC2` and Gradle orders a release above its own release candidate. Checked both ways
on `:ui-liquid-glass:debugRuntimeClasspath`:

```
-Pmiuix.version=0.9.4-7cc339c2-SNAPSHOT  ->  kotlin-stdlib 2.4.20-RC2
-Pmiuix.version=0.9.4-5157b503-SNAPSHOT  ->  kotlin-stdlib 2.4.20
```

A final stdlib under an RC compiler of the same version is the supported direction and the build
raises no version-skew warning, so nothing was owed here. It did mean the RC pin bought nothing but the
compiler itself, and the catalog-wide bump that followed the same day closed it: `kotlin` is `2.4.20`,
so the compiler and the stdlib now agree without depending on Gradle's ordering to get there.

Compose did **not** move: `org.jetbrains.compose.*` stays at `1.12.0` in both module files, so
`BuildConfig.COMPOSE_VERSION` and the About page are untouched.

### Verification

- `:ui-liquid-glass:compileDebugKotlin` and `:app:compileDebugKotlin` pass. Both recompiled — the
  `NavigationBarItem` signature change is an ABI change — and neither module's own ABI moved, so
  every downstream `bundleLibCompileToJarDebug` stayed `UP-TO-DATE`. No new warnings.
- `:app:testDebugUnitTest` 1741 tests and `:ui-liquid-glass:testDebugUnitTest` 450 tests —
  2191 total, 0 failures and 0 errors. `:app:verifyRoborazziDebug` passed, so no screenshot moved.
- `:app:assembleRelease`: R8, Lint Vital, resource optimization and ART Profile compilation all passed,
  which is the check that would catch a `NoSuchMethod` from the `NavigationBarItem` signature change if
  anything here did reference it.
- Not run, and not owed: no device pass. Nothing KeiOS draws changed, and Roborazzi already holds the
  pixels.

<a id="snapshot-follow-up-39c40f99"></a>

## Snapshot follow-up: 0.9.4-39c40f99-SNAPSHOT

Five upstream commits between `5157b503` (2026-09-11) and `39c40f99` (2026-09-20). Four touch library
source. Of those, one fixes a bug KeiOS had worked around, one brings pager APIs KeiOS partly
takes, one adds a navigation API nothing here needs yet, and one fixes a component KeiOS does not use.

| commit | subject | in KeiOS |
| --- | --- | --- |
| `5de9d0f5` | library: add LocalNavTransitionScope to miuix-nav (#430) | available, no consumer; see below |
| `63529058` | library: fix NavigationRail selection highlight (#434) | not used: no `NavigationRail` anywhere in the tree |
| `26b37993` | AGP 9.4.1 (#436) | upstream's own build only |
| `29d6deb3` | library: fix missing SavedState CreationExtras in nav entries (#438) | **adopted**: the MCP page's workaround is gone |
| `39c40f99` | library: add pager gesture conflict resolution utilities (#437) | **partly adopted**: the page-navigation spring; the gesture interceptor is not |

### SavedStateHandle in nav entries (#438): adopted

Until this snapshot, a `NavDisplay` entry's `ViewModelStoreOwner` was a bare wrapper around the entry's
store. It carried no creation extras, so `createSavedStateHandle()` inside an entry threw
`CreationExtras must have a value by SAVED_STATE_REGISTRY_OWNER_KEY` (upstream #407). KeiOS hit this on
2026-06-30 (`fc73a383d`). The MCP page's fix was a factory that handed `McpPageViewModel` a bare
`SavedStateHandle()`. That handle is registered with nothing, so the cards a reader opened (onboarding,
control, the tool groups, logs) closed again after process death.

The entry owner is now built inside the entry's saveable-state scope, from lifecycle's
`ViewModelStoreOwner(store, savedStateRegistryOwner, defaultCreationExtras, defaultFactory)`. Both
halves were checked in the bytecode KeiOS actually ships rather than taken from the PR:

- **Compose 1.12.0.** `SaveableStateHolderImpl.SaveableStateProvider` wraps each key's registry in a
  `SaveableStateRegistryWrapper`, which is a `SavedStateRegistryOwner`, and provides it as
  `LocalSavedStateRegistryOwner`. So each entry has its own saved-state owner, and its handles are saved
  with the entry.
- **lifecycle 2.11.0.** The saved-state variant of that owner sets `SAVED_STATE_REGISTRY_OWNER_KEY`,
  `VIEW_MODEL_STORE_OWNER_KEY` and `DEFAULT_ARGS_KEY`, and calls `enableSavedStateHandles`.

So `McpPage` now calls `viewModel { McpPageViewModel(createSavedStateHandle()) }`, and the factory and
the test that pinned it are deleted. `McpPageViewModelNavEntryTest` creates the ViewModel inside a real
`NavDisplay` entry, opens two cards, and restores the saved state. For the restore it swaps in a fresh
parent `ViewModelStoreOwner`: the entry stores live in the parent's store, so a plain restore would hand
back the same ViewModel. The fresh owner is what process death leaves behind. The test passes on
`39c40f99`, and with `-Pmiuix.version=0.9.4-5157b503-SNAPSHOT` it fails with #407's exact exception.

The same change gives every entry the activity's default factory and extras. So `viewModel()` of an
`AndroidViewModel`, or of a `(SavedStateHandle)` constructor, now works inside a route without a
hand-written factory. `applicationViewModel` was left alone. The student guide passes a real argument
through it (`warmStartId`), and the call sites that pass only the `Application` work either way.
Moving them to plain `viewModel()` is optional tidying, not a fix.

### Pager gesture utilities (#437): the spring, not the interceptor

`PagerNavigationSpringSpec` and `PagerState.springAnimateToPage` are what upstream's own tab rows and
snap fling now share. Upstream's example replaced a tween lasting `100 * distance + 100` ms with them.
`host/pager/PagerSwitchAnimation.animateTabSwitch` is KeiOS's copy of that same tween, with far-jump
dim hooks added. It now springs, with the hooks kept, and `PagerSwitchAnimationTest` pins what its
callers rely on: a jump of more than one page is bracketed by the dim hooks, an adjacent one is not, and
the pager ends exactly on the target page. The two `HorizontalPager`s take the spring for their snap
fling too, so a page lands the same way whether a tab or a finger moved it:

- the student guide (`BaStudentGuidePagerContent`);
- the BA account card (`BaAccountPagerCard`), whose jump when the active account changes elsewhere
  also moved from `animateScrollToPage` to `springAnimateToPage`.

`pagerGestureOverride` was not adopted. Its default Cross-Axis mode makes horizontal swipes win while a
page's list is flinging or bouncing. It does that by claiming every horizontal drag past touch slop in
`PointerEventPass.Initial`, which runs parent-first, before any page content sees the drag. The
guide's gallery section has `LiquidMusicProgressSlider`, a `dragOrientation = Orientation.Horizontal`
control, which would lose every drag to a page swipe. The same mode also opens a `PagerState.scroll`
mutation on every touch-down, so `isScrollInProgress` goes true for taps and vertical scrolls. The guide
reads that flag for its tab selection and its pager performance report. `TapToHalt` engages only while a
child is flinging (the first horizontal swipe stops the list, the next pages), so it avoids the slider
conflict. It is a behaviour change, though, and left to the owner. The Overscroll half of the same
commit applies everywhere with no adoption: a nested-scroll event with a zero delta on an axis no
longer cancels that axis' spring, so a horizontal move no longer stops a vertical bounce.

The main pager is not a `HorizontalPager`. `MainLoadedPager` is its own `draggable`, so the
`PagerState` utilities do not attach to it. Its motion is tuned: an `EaseInOut` tab jump, and a
no-bounce spring (stiffness 1200) on drag release that keeps the release velocity (`5212bbc37`). It is
unchanged. `MainMiuixPager` and `MainFoundationPager` carried the example's old tween too, but the
coordinator only ever builds `MainLoadedPagerState`, so they were unreachable. They have since been
deleted, together with `PreloadPolicy.mainPagerBeyondViewportPageCount`, which only they read.

### LocalNavTransitionScope (#430): available, no consumer

An entry can now read the live transition, including a composition-safe `isRunning`. The one place
KeiOS needs to know whether an entry is covered already has its answer: `rememberNavEntryAtTop`, which
reads the entry lifecycle. `b0d18428d` verified that it closes only behind a settled, opaque page.
`NavTransitionScope.isRunning` has a default (`gesture != null || settle != null`), and KeiOS's
transitions are `navGraphicsTransition` blocks, not implementations, so nothing had to change.

### Artifact check

As before, the sources jars were fetched for both versions of every module that resolves. A scratch
Gradle build reading the same GitHub Packages credentials did this, and the files were compared one by
one:

| module | files | old vs new |
| --- | --- | --- |
| `miuix-ui` | 88 | `NavigationRail.kt`, `Overscroll.kt`, `OverscrollFactory.kt` differ; `PagerGestureUtils.kt` new |
| `miuix-nav` | 31 | `LiveNavTransitionScope.kt`, `NavDisplay.kt`, `NavEntryViewModel.kt`, `NavTransitionScope.kt` differ; `LocalNavTransitionScope.kt` new |
| `miuix-icons` | 157 | identical |
| `miuix-blur` | 25 | identical |
| `miuix-preference` | 20 | identical |
| `miuix-squircle` | 8 | identical |
| `miuix-shader`, `miuix-core` | 5, 4 | identical |

The artifact agrees with the compare exactly.

### What actually reaches KeiOS

Nothing outside Miuix moved. Diffing `:app:debugRuntimeClasspath` before and after changes only
`top.yukonga.miuix.kmp` lines: zero others. Compose stays at 1.12.0, so `COMPOSE_VERSION` and the About
page are untouched.

The generated release profiles name the class upstream removed. `baseline-prof.txt` and
`startup-prof.txt` each have nine lines for `NavEntryViewModelStoreOwner` and the `NavEntryHost` lambdas
that took it. The miuix-nav AAR ships its own wildcard rules for `NavDisplayKt**`,
`NavEntryViewModelKt**` and `LiveNavTransitionScope`, so the moved code stays covered. The stale lines
go at the next capture, and generated profiles are not edited by hand.

### Verification

- `:app:compileDebugKotlin` passes. `:app:verifyRoborazziDebug` ran all 1,777 app tests, 0 failures, and
  no screenshot moved. `:ui-liquid-glass:testDebugUnitTest` ran 450, 0 failures.
- `McpPageViewModelNavEntryTest` passes here and fails on `5157b503` with #407's exception (above).
  `PagerSwitchAnimationTest` pins the tab switch's hooks and its exact landing.
- `:app:assembleRelease` passes: R8, Lint Vital, resource optimization and ART Profile compilation,
  with no warnings.
- On the Android 17 AVD `KeiOS_API37_Validation`, this build installed over existing data:
  - **MCP page.** It opens without the #407 crash. With two cards opened, the app was sent home and
    killed with `am kill`. Relaunched cold on a new pid from the same task, it came back on the MCP tab
    with both cards still open. Before this change both would have closed.
  - **Student guide.** Tapping a tab four pages away springs there and ends on the page, undimmed. An
    adjacent tap lands on its page. A fast swipe snaps to the neighbouring page, and a slow drag released
    at about 37% springs back. No fatal exception across the guide, the roster, the BA page and a
    Settings round trip.
  - **Not covered on the device:** the BA account pager. The AVD has one account, so its pager has a
    single page and neither the swipe nor the programmatic jump can run.
- Midway through, the emulator itself aborted at 09:33 (`qemu-system-aarch64`, SIGABRT from
  `std::__throw_bad_function_call` in its gRPC callback path). That is an emulator bug with nothing of
  KeiOS in the stack. A cold boot brought it back in 12 s with DNS resolving.

### Follow-up the same day: TapToHalt and the main pager spring

The owner asked for both open choices to be tried, and adopted if they worked.

**TapToHalt on the student guide pager: adopted.** `pagerGestureOverride(mode = TapToHalt)` stays on
native gestures, so the gallery's audio slider keeps its drags, and acts only while a page's list is
coasting. On the AVD the test was a fling on Voice Lines and, 150 ms later, a horizontal swipe, with
both issued in one `adb shell`:

| build | swipe during the fling |
| --- | --- |
| TapToHalt | 5 of 6 only stopped the list; the next swipe paged |
| master (control) | 5 of 6 paged |

The one exception each way is a swipe that arrived after the momentum had already ended.

**Miuix's page-navigation spring on the main pager: adopted.** `MainLoadedPager` is not a `PagerState`,
so it takes `PagerNavigationSpringSpec`'s stiffness and damping in page units, with a 0.0005-page
threshold, about the half pixel Miuix settles to. It replaces an `EaseInOut` tween whose length was
`100 * max(distance, 2) + 100` ms, the formula Miuix's example dropped. The duration plumbing went with
it:

- the `durationMillis` parameter on `MainPagerStateContract.animateToPage`;
- the `Timed` motion, now `Navigation`;
- the four per-page duration helpers and two orphaned constants;
- the test that pinned the formula.

About, Settings, the BA calendar and pool, the catalog, and WebDAV sync switch tabs through the same
pager, so they now land the same way.

Frames drawn per jump on the AVD (`dumpsys gfxinfo`; only jumps *from* Home are clean, because Home
keeps drawing its background):

| jump | tween | spring |
| --- | --- | --- |
| Home -> OS (adjacent) | 17, 21, 19 | 28, 22, 30 |
| Home -> BA (four tabs) | 33, 34, 32 | 35, 38, 37 |

The spring covers 95% of the distance sooner than the tween did (about 0.19 s against 0.24 s for an
adjacent tab), then spends its extra frames on a tail that moves under 1% of a page. That is the cost
of Miuix's feel, and the one knob is the threshold. Frame *time* was not measured: the AVD cannot
measure it, and the phone is where it would have to be checked.

## Snapshot follow-up: 0.9.4-2afdbb39-SNAPSHOT

Five upstream commits between `39c40f99` (2026-09-20) and `2afdbb39` (2026-09-23), all on 2026-09-23. Four
touch library source; three of them rework the pager gesture utilities, and one changes miuix-nav.

| commit | subject | in KeiOS |
| --- | --- | --- |
| `73ba6073` | update jetbrains.compose.multiplatform to v1.12.1 (#442) | the JetBrains wrappers move to 1.12.1; androidx Compose was already 1.12.1 here |
| `a575835c` | preserve child gestures in cross-axis pager (#440) | Cross-Axis only; KeiOS uses TapToHalt |
| `70528417` | clear focus when navigation top changes (#441) | **applies everywhere**; checked below |
| `00e2f193` | fix cross-axis child gestures after pager takeover | Cross-Axis only |
| `2afdbb39` | keep pager settling during child gestures | Cross-Axis only |

### pagerGestureOverride takes the pager's fling behaviour: migrated

Every `pagerGestureOverride` overload now takes a `FlingBehavior`, and the call on the student guide's pager
(`BaStudentGuidePagerContent`) stopped compiling. The guide passes the same `PagerDefaults.flingBehavior(
snapAnimationSpec = PagerNavigationSpringSpec)` instance to the pager and to the modifier; it is now hoisted
into one `val`.

The parameter matters only to Cross-Axis. There the pager runs with `userScrollEnabled = false`, and the new
`PagerNonTouchScroll` restores wheel, Shift+wheel and trackpad input by driving that fling behaviour. The
TapToHalt branch still reduces to `iosStyleMomentumHalt`. `iosStyleMomentumHalt`,
`PagerFlingTrackerConnection` and `PagerGestureNestedScrollConnection` are identical in the two snapshots,
compared function by function in the sources. So the guide's gesture behaviour should not change, and the
phone agrees:

| swipe on Voice Lines (phone `5eea1f50`) | result |
| --- | --- |
| 150 ms after a list fling | 6 of 6 only stopped the list |
| with the list at rest (control) | 4 of 4 paged |

Cross-Axis is still not adopted, for the reason given under `39c40f99`: the gallery's audio slider is a
horizontal drag. #440 and `00e2f193` narrow that conflict (the recognizer is now a Foundation `DragGestureNode`
whose angle arbitration lets an aligned horizontal child win). That makes Cross-Axis worth another look, but
it is a behaviour change and was not measured here.

### Focus cleared when the top entry changes (#441): checked, nothing to adapt

`NavDisplayLayout` now calls `LocalFocusManager.clearFocus()` from a `DisposableEffect` whenever the back
stack's top key changes, because covered entries stay composed and could keep focus. Production code that
requests focus: the BGM search panel and bottom chrome (after a tap in the catalog), and the shell runner's
command input, which takes focus *on route entry* when its startup behaviour is "Focus input on entry". Only
that last one could lose to a clear on the same frame. Its request comes from a `LaunchedEffect` that bumps a
token, and the input requests focus from a second `LaunchedEffect` on that token. Both run after the push
frame's effects apply, so the clear lands first. On the phone, with the setting switched to "Focus input on
entry" and back to Silent afterwards: the keyboard came up on the first entry and on three re-entries, and
leaving the route took it down.

### Artifact check

Sources jars for both versions of every module that resolves, fetched by a scratch Gradle build with the
GitHub Packages credentials and compared file by file:

| module | files | old vs new |
| --- | --- | --- |
| `miuix-ui` | 90 | `BreadcrumbBar.kt`, `TabRow.kt`, `PagerGestureUtils.kt` differ; `PagerNonTouchScroll.kt` and `PagerNonTouchScroll.android.kt` new |
| `miuix-nav` | 31 | `NavDisplay.kt` differs |
| `miuix-icons`, `miuix-blur`, `miuix-preference`, `miuix-squircle`, `miuix-shader`, `miuix-core` | 157, 25, 20, 8, 5, 4 | identical |

The artifact agrees with the compare exactly. KeiOS uses neither `BreadcrumbBar` nor `TabRow`.

`miuix_snapshot_check.sh --update` moved `gradle.properties` and the two build readmes but not the catalog,
which it only reports as shadowed; `libs.versions.toml` was moved by hand, as the earlier bumps did.

### What actually reaches KeiOS

Diffing `:app:debugRuntimeClasspath` before and after, the resolved versions change only for
`top.yukonga.miuix.kmp` and for the `org.jetbrains.compose.*` wrappers (1.12.0 -> 1.12.1, from #442). Every
`androidx.compose` module was already on 1.12.1, the catalog's floor, so `COMPOSE_VERSION` is unchanged.

### Verification

- `verifyRoborazziDebug` over every module: 3,112 tests, 0 failures, no screenshot moved.
  `:app:assembleRelease`, `:app:assembleReleaseDiagnostic` and `:baselineprofile:assemble` pass.
- Phone `5eea1f50`, `os.kei.diag`: the TapToHalt table and the shell focus check above; an edge back gesture
  leaves a route; no fatal exception in the session's log.
- Not covered on the device: the gallery's audio slider. Its only link to this change is the TapToHalt path,
  which is byte-identical.
