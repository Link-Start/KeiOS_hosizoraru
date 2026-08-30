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
