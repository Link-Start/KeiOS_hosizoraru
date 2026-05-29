# MIUIX Sample Performance Adoption Plan

## Scope

- Reference date: 2026-05-30.
- MIUIX reference: `.tmp/miuix` at `9b86e77`.
- Scan scope: MIUIX Sample under `example/shared`, plus directly used library
  implementation under `miuix-ui`, `miuix-blur`, `miuix-squircle`, and
  `miuix-navigation3-ui`.
- Goal: absorb reusable performance architecture ideas into KeiOS while
  preserving Liquid Glass visuals, animation richness, page content continuity,
  install/share semantics, notification behavior, Super Island behavior, and HDR
  user-facing behavior.
- Clean-room rule: use MIUIX public behavior and architecture as reference
  material, then write KeiOS-native code against current project contracts.
  MIUIX is Apache-2.0; KeiOS should still keep its own file layout, names, and
  implementation details.

## KeiOS Current Baseline

Static scan from `app/src/main/java/os/kei` and `feature-github/src/main/java/os/kei`
on 2026-05-30:

| Pattern | Current count | Current state |
| --- | ---: | --- |
| `derivedStateOf` | 4 | Used only on a few threshold surfaces. Expand after hotspot evidence. |
| `drawWithCache` | 8 | Present in Home HDR, AppSquircle, and search material. Expand on draw-heavy surfaces. |
| `DrawModifierNode` | 2 | Dynamic background owns the main continuous draw loop. |
| `Modifier.Node` | 1 | Custom node coverage is still narrow. |
| `rememberGraphicsLayer` | 0 | KeiOS uses Kyant Backdrop / layer APIs rather than MIUIX blur internals. |
| `RuntimeShader` | 21 | Dynamic background, interactive highlight, and AppSquircle are shader-backed. |
| `graphicsLayer` | 130 | Phase-deferral is broadly adopted in chrome, sheets, and BGM controls. |
| `snapshotFlow` | 84 | Scroll / pager effects are mostly effect-driven. |
| `contentType` | 161 | Lazy list row typing is broadly adopted. |
| `rememberCombinedBackdrop` | 16 | Nested glass paths already use combined backdrop in major chrome surfaces. |
| `layerBackdrop` | 103 | Backdrop capture topology needs continued audit as new glass surfaces appear. |
| `drawBackdrop` | 36 | Glass drawing is centralized across bottom bar, action bar, sheets, BGM, and cards. |
| `appSquircle*` | 163 | Shader-backed rounded surfaces are widely used. |
| `movableContentOf` | 0 | Adopt only for proven stateful subtree move cases. |

Current strong points:

- `BgEffectBackground` already uses `DrawModifierNode`, `withFrameNanos`,
  active-page gating, alpha gating, 30/60 FPS throttling, render-size downscale,
  and cached shader uniforms.
- `AppSquircle` already has shader-backed fill/clip/surface APIs, baked SDF
  data, path/shader borders, and runtime fallback gates.
- `LiquidGlassBottomBar`, `LiquidActionBar`, and BA BGM dock already follow the
  MIUIX pattern of provider lambdas plus `graphicsLayer` / Backdrop draw-time
  reads.
- Prior reference work in `docs/performance-reference-priorities.md` landed the
  first wave: Squircle pre-bake, nested Backdrop safety, MainScreen back
  coordination, glass gates, dynamic background node ticking, install/share state
  machines, color producers, popup reveal, and settings row specialization.

## Adoptable Patterns

| Pattern | MIUIX reference | Why it matters | KeiOS current fit | Decision |
| --- | --- | --- | --- | --- |
| RuntimeShader dynamic background in `DrawModifierNode` | `example/shared/.../effect/BgEffectModifier.kt`, `BgEffectPainter.kt` | Continuous animation stays in Draw; shader uniforms update only when inputs change. | KeiOS has this core path. Home still feels visually static in some states because page-active/data-active/pager-scroll gates can suppress motion aggressively. | P0: tune Home activation, visual energy, and verification evidence. |
| Scroll threshold derivers | `miuix-ui/.../basic/TopAppBar.kt`, `PullToRefresh.kt`, `SearchBar.kt` | Boolean transitions fire once per threshold crossing while alpha/offset reads stay in layout/draw. | KeiOS has `snapshotFlow` on many pages and a few `derivedStateOf` uses. Some chrome/search/far-jump decisions still need a targeted audit after recent refactors. | P0: audit MainScreen children and high-frequency chrome thresholds. |
| Search overlay layout-phase reads | `example/shared/.../component/SuperSearchBar.kt` | Search expansion keeps page content mounted and moves top padding reads into layout. | KeiOS search docks already use `graphicsLayer` on some fields. Page-level search routes need a continuity audit to prevent blank-page style load shedding. | P1: unify search overlay rules for Settings, GitHub, MCP, OS, and BA. |
| Liquid navigation provider chain | `example/shared/.../liquid/LiquidGlassNavigationBar.kt` | Drag, press, selection, highlight, and backdrop layer transforms read hot values in draw/layout phases. | Bottom bar and action bar already mirror this. Residual risk lives in new BGM chrome and action surfaces added after the first pass. | P1: residual `Animatable.value` / resolved progress scan. |
| Shader-backed Squircle fill/clip split | `miuix-squircle/.../SquircleBackground.kt`, `SquircleBorder.kt` | Fill-only background avoids offscreen layers; clip/surface are reserved for real clipping. | KeiOS has equivalent APIs. Widespread `appSquircle*` use needs surface-by-surface cost classification. | P0: audit high-density cards and list rows. |
| Backdrop capture node and stable callbacks | `miuix-blur/.../LayerBackdropModifier.kt`, `RuntimeShaderCache.kt` | Capture work lives in a node; callbacks stay current without rebuilding capture topology. | KeiOS uses Kyant Backdrop and combined backdrop. Count is high enough that topology drift can hurt HWUI during page switching. | P1: Backdrop topology audit with Backdrop MCP guidance. |
| Debug FPS / 1 percent low overlay | `example/shared/.../utils/FPSMonitor.kt` | HWUI bars show pressure; rolling FPS plus 1 percent low exposes stutter spikes during interactive QA. | KeiOS currently relies on adb/HWUI/gfxinfo and benchmark runs. | P2: optional debug/benchmark-only overlay. |
| Navigation subtree preservation | `miuix-navigation3-ui/.../SceneSetupNavEntryDecorator.kt` | `movableContentOf` keeps stateful entries alive when scene layout moves. | KeiOS already uses Navigation 3 and custom MainScreen host. A concrete move-state bug is needed before adoption. | P3: observe. |

## Priority Backlog

### P0

| ID | Area | KeiOS target | Work |
| --- | --- | --- | --- |
| M0 | Home dynamic background parity | `core/ui/effect/background/*`, `ui/page/main/home/*` | Compare KeiOS `BgEffect*` against MIUIX OS3 sample and tune activation rules so Home remains alive during static viewing without producing excessive HWUI bars. Keep render downscale and shader uniform caches. Measure with AVD HWUI bars and benchmark/gfxinfo. |
| M1 | Chrome threshold state | `MainScreen`, Home, Settings, GitHub, OS, MCP, BA page chrome | Audit scroll/pager/topbar/search/far-jump threshold reads. Use `derivedStateOf` for threshold booleans, `snapshotFlow + distinctUntilChanged` for effects, and provider lambdas for layout/draw values. |
| M2 | Squircle/offscreen cost classification | `widget/shape/AppSquircle.kt`, `widget/core`, GitHub rows/cards, BA rows/cards, Settings rows | Replace clip/surface usage with fill-only background where descendants never need clipping. Keep `appSquircleClip` and `appSquircleSurface` for content that truly needs masking. Record each touched surface in this file. |

### P1

| ID | Area | KeiOS target | Work |
| --- | --- | --- | --- |
| M3 | Backdrop capture topology | Bottom bar, action bar, sheets, BA BGM, Home cards, GitHub cards | Audit nested `layerBackdrop` / `drawBackdrop` chains with Backdrop MCP guidance. Prefer one parent capture plus combined/exported backdrop for nested glass. Keep callbacks stable and avoid capture surface churn during scroll. |
| M4 | Bottom chrome hot-value residuals | `LiquidGlassBottomBar`, `LiquidActionBar`, BA BGM dock/chrome | Scan for resolved animated values passed through composition. Keep drag/press/selection/highlight values as providers read from `graphicsLayer`, `drawBackdrop` layer blocks, or gesture callbacks. |
| M5 | Search overlay continuity | Settings, GitHub, MCP, OS, BA Catalog | Keep page content mounted during search expansion. Move top padding, alpha, dim, and blocker reads into layout/draw. Keep search query/data filtering in ViewModel/repository derivers. |
| M6 | Draw allocation cache pass | `widget/glass`, Home HDR, BA BGM hero, progress/status components | Expand `drawWithCache` only where brushes, paths, masks, shader wrappers, or repeated size-stable objects are still created in draw lambdas. |

### P2

| ID | Area | KeiOS target | Work |
| --- | --- | --- | --- |
| M7 | Internal frame overlay | Debug / benchmark builds | Add a gated overlay inspired by MIUIX FPSMonitor: average FPS, 1 percent low FPS, draggable position, low refresh overhead. Keep release builds free of overlay work. |
| M8 | Page utility convergence | Shared page scaffold/chrome helpers | Align page padding, scroll haptic, overscroll, nested scroll, and blur gates through shared helpers where pages still duplicate logic. |
| M9 | Backdrop / blur dependency verification | Gradle dependency graph, Backdrop MCP docs, MIUIX blur docs | Verify consumed library versions keep runtime shader caches and capture-node behavior. Update rules only when current dependency behavior diverges from the expected topology. |

### P3

| ID | Area | KeiOS target | Work |
| --- | --- | --- | --- |
| M10 | `movableContentOf` adoption | Navigation 3 host / movable page subtrees | Adopt for a proven stateful subtree relocation bug or transition case. Keep it out of ordinary static layouts. |
| M11 | Immutable collections | ViewModel UI models crossing very hot composables | Kotlin/Compose strong skipping reduces urgency. Add persistent collections only when compiler reports or runtime traces identify a specific list parameter as a blocker. |

## Guardrails

- Preserve visual richness, glass depth, rounded-corner quality, animations, and
  mounted page content. Performance work should move reads to draw/layout,
  cache stable inputs, and reduce repeated capture work.
- Keep data loading asynchronous. Repository/ViewModel work can precompute
  display snapshots; Composables should render immutable UI state and emit
  callbacks.
- Treat debug HWUI bars as triage evidence. Use benchmark/release builds,
  `dumpsys gfxinfo`, Macrobenchmark, Perfetto, or Compose compiler reports for
  final claims.
- Keep Backdrop changes aligned with Backdrop MCP documentation, especially
  nested glass and exported/combined backdrop paths.
- Use AVD for mutable install/smoke tests unless a real-device run is explicitly
  approved. Protect `os.kei` release package operations.
- Update the status table and verification log whenever an item lands.

## Verification Commands

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
git diff --check
rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'
```

Recommended performance evidence per landed item:

```bash
adb -s <emulator-serial> shell dumpsys gfxinfo os.kei.benchmark framestats
adb -s <emulator-serial> shell setprop debug.hwui.profile visual_bars
```

For install/smoke validation, target emulator packages:

- Debug: `os.kei.debug`
- Benchmark: `os.kei.benchmark`

## Status Table

| ID | Status | Notes |
| --- | --- | --- |
| M0 | Planned | Highest value remaining MIUIX Sample idea for Home perceived smoothness. |
| M1 | Audited (no scroll/pager gap) | Scroll/pager/far-jump threshold reads are all already deferred (snapshotFlow + distinctUntilChanged, derivedStateOf, or provider lambdas invoked only from effects/callbacks). No composition-phase per-frame read to convert. TopAppBar collapse + search-expansion thresholds remain for a later targeted pass. |
| M2 | First pass landed | Full inventory of `appSquircleClip`/`appSquircleSurface` sites (31 active). Two safe wins landed: `BaGuideCatalogEntryAvatarFallback` surface→background (centered Icon at 50% size, no content reaches corners) and `LiquidActionBar` clip-only Box removal (centered Icon, no riple, no background — clip masks nothing). 15 mask-needed sites kept. 10+ uncertain sites require visual inspection on AVD before conversion. |
| M3 | Planned | Backdrop count is high; topology drift can affect page switching. |
| M4 | Planned | Bottom chrome already strong; audit residuals after BGM changes. |
| M5 | Planned | Search expansion should keep continuity and mounted content. |
| M6 | First target landed | Swept draw lambdas for size-stable per-frame allocations. Landed: `InteractiveHighlight` re-wrapped `ShaderBrush(shader)` every press-animation frame; hoisted to a stable member field (fixed-shader wrapper is size-independent, uniforms still mutate in place). Other draw-heavy sites already memoize brushes via `remember` / `.background()` / top-level helpers. Continue on Home HDR / BA BGM hero if new draw allocations appear. |
| M7 | Planned | QA-only tool to supplement HWUI bars and benchmark numbers. |
| M8 | Planned | Shared page helper convergence after hotspot fixes. |
| M9 | Verified | Inspected consumed `miuix-blur` (`0.9.1-f7c90d71-SNAPSHOT`): adaptive cascade downsampling (1/2/4/8/16 via `computeDownScaleParams`) + separable H/V Gaussian (`BlurEffect.kt`); compile-once shader pooling (`RuntimeShaderCacheImpl.obtainRuntimeShader` getOrPut, GC'd on dispose); GraphicsLayer pooling (`createGraphicsLayer`/`releaseGraphicsLayer` on detach, separate `noiseLayer` for dithering in `DrawBackdropModifier.kt`); zero-alloc scratch buffers. KeiOS gate `appGlassRuntimeEffectsEnabled()` delegates to `isRuntimeShaderSupported()`; `activeGlassBackdrop()` nulls the backdrop when unsupported. No KeiOS change needed; re-verify on dependency bumps. |
| M10 | Observe | Requires a concrete stateful subtree relocation case. |
| M11 | Observe | Requires compiler/runtime evidence under strong skipping. |

## Verification Log

- 2026-05-30 refresh:
  - Confirmed `.tmp/miuix` at `9b86e77`.
  - Scanned MIUIX Sample dynamic background, liquid navigation bar, search bar,
    FPS monitor, page utilities, TopAppBar, blur capture node, Squircle
    background/border, and Navigation 3 scene decorator.
  - Re-scanned KeiOS counts for derived state, draw caches, modifier nodes,
    shader paths, graphics layers, snapshot flows, lazy content types, Backdrop
    usage, Squircle usage, and movable content.
  - Reconciled this plan with `docs/performance-reference-priorities.md` so
    already-landed work remains historical reference and this file tracks the
    next actionable backlog.
- 2026-05-30 M1 / M6 / M9 first implementation pass:
  - M1: audited scroll/pager/far-jump threshold reads across MainScreen children,
    Home, Settings, GitHub, OS, MCP, BA. All already deferred via
    `snapshotFlow + distinctUntilChanged`, `derivedStateOf`, or provider lambdas
    invoked only from effects/callbacks. No composition-phase per-frame read to
    convert. No code change.
  - M6: hoisted the per-frame `ShaderBrush(shader)` wrapper in
    `InteractiveHighlight` to a stable member field; no other draw lambda
    re-allocates a size-stable object.
  - M9: verified `miuix-blur` internals (cascade downsampling, separable Gaussian,
    shader pooling, GraphicsLayer pooling, noise dithering layer, zero-alloc
    scratch) and confirmed KeiOS gates short-circuit correctly. No code change.
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
- 2026-05-30 M2 Squircle cost first pass:
  - Full inventory of `appSquircleClip` / `appSquircleSurface` sites across
    KeiOS: 31 active call sites (28 clip, 3 surface).
  - Classification: 15 mask-needed (images, gradients, video, search fields),
    2 safe-to-convert, 10+ uncertain (need AVD visual inspection).
  - Landed (safe wins):
    - `BaGuideCatalogEntryAvatarFallback`: `appSquircleSurface` →
      `appSquircleBackground` (centered Icon at 50% size, no content reaches
      corners; eliminates one offscreen layer).
    - `LiquidActionBar`: removed `appSquircleClip(999.dp)` from icon-only Box
      (centered Icon, no riple indication, no background; clip masks nothing
      visible; eliminates one offscreen layer).
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
