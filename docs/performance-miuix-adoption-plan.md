# MIUIX Sample Performance Adoption Plan

## Context

- Reference check date: 2026-05-30.
- MIUIX reference: `.tmp/miuix` at `9b86e77` (sample under `example/`, library under `miuix/`).
- This plan tracks **newly identified** performance gaps from a fresh MIUIX sample scan.
  It deliberately does **not** duplicate items already landed in
  `docs/performance-reference-priorities.md` (Squircle SDF pre-bake, nested Backdrop
  safety, MainScreen back coordination, backdrop/blur gates, dynamic background
  `Modifier.Node`, install/share state machines, color producers, popup reveal,
  settings rows). Those remain landed; this doc only covers what is still open.
- Compiler baseline: project is on **Kotlin / Compose compiler 2.3.21**, so
  **strong skipping is ON by default**. This materially lowers the urgency of
  list-stability work (e.g. `ImmutableList`) because unstable lambda/`List` params
  no longer force recomposition the way they did pre-2.x. Priorities below reflect
  that reality.
- Clean-room rule: use MIUIX public behavior and architectural ideas as prompts,
  then write KeiOS-native code against existing project contracts. MIUIX is
  Apache-2.0 (no copyleft contamination), but we still write our own code rather
  than copy structure, names, or implementation details. Do not import InstallerX
  (GPL-3.0) source.

## Baseline scan (KeiOS current state, 2026-05-30)

| Pattern | KeiOS usage today | Verdict |
| --- | --- | --- |
| `@Immutable` / `@Stable` | ~210 / ~62 | Widely adopted, no action |
| `rememberUpdatedState` | ~139 | Widely adopted, no action |
| `snapshotFlow` | ~84 | Widely adopted, no action |
| `graphicsLayer` | ~86 | Widely adopted, no action |
| `contentType` (lazy lists) | ~152 | Widely adopted, no action |
| `WhileSubscribed` / `stateIn` | ~32 | Widely adopted, no action |
| `RuntimeShader` | ~21 | Widely adopted, no action |
| `withContext` (off-main) | ~858 | Widely adopted, no action |
| LRU / bounded caches | ~71 | Widely adopted, no action |
| `() -> Float` deferred-read params | ~106 | Adopted; expand opportunistically (A3) |
| `derivedStateOf` | 4 uses, 2 files | **Gap (A1)** |
| `drawWithCache` | 8 uses, 3 files | **Gap (A2)** |
| `DrawModifierNode` | 1 file (`BgEffectModifier`) | **Gap (A4)** |
| `movableContentOf` | 0 uses | **Gap (A5)** |
| kotlinx persistent / `ImmutableList` | 0 (lone hit is Guava Media3 type) | **Low — strong skipping (A6)** |
| miuix-blur shader internals | consumed via library | **Verify-only (A7)** |

## P1

| ID | Area | MIUIX source idea | KeiOS landing direction | Status |
| --- | --- | --- | --- | --- |
| A1 | `derivedStateOf` for threshold-driven UI | Sample scroll/overscroll chrome derives boolean flags (collapsed, at-top, at-end) from scroll state so a spring/animation fires **once per crossing**, not once per frame | Audit high-frequency reads of `scrollState`/`LazyListState`/pager offset that currently recompute a boolean every frame; wrap each in `derivedStateOf` so recomposition only fires on the flip. Candidate sites: top-bar collapse, bottom-bar elevation/visibility, scroll-to-top button visibility, pager edge state. | Not started |
| A2 | `drawWithCache` expansion | Sample draws brushes, gradients, and shader builders inside `drawWithCache` so allocation happens once per size change, not per draw frame | Sweep animated/draw-heavy composables that still build `Brush`/`Path`/`Shader`/`RuntimeShader` setup inline in `draw{}`. Move size-stable allocation into `onDrawBehind`/`onDrawWithContent` via `drawWithCache`. Current usage is only 3 files; target the glass surfaces, progress bars, and HDR highlight paths. | Not started |

## P2

| ID | Area | MIUIX source idea | KeiOS landing direction | Status |
| --- | --- | --- | --- | --- |
| A3 | Deferred-read lambda params (`() -> Float`) | Sample passes animated scalars as lambdas so the read happens in layout/draw, skipping recomposition of the caller | Already ~106 uses. Extend to remaining composables that take a resolved animated value as a parameter and recompose on every tick (look for `animateFloatAsState`/`Animatable.value` read at call site then passed down by value). | Not started |
| A4 | `DrawModifierNode` for per-frame effects | Sample routes continuous draw effects through `Modifier.Node` + `invalidateDraw()` instead of recomposition | Only `BgEffectModifier` uses this today. Identify other continuously-animating draw effects (glass shimmer, progress shimmer, focus glow) that currently drive frames through recomposition/`graphicsLayer` state and evaluate moving them to a draw node. Opportunistic — only where a measurable per-frame recomposition cost exists. | Not started |
| A5 | `movableContentOf` for state-preserving moves | Sample preserves subtree state when a node moves between layout slots (orientation / pager rearrange) | Zero uses today. Low-frequency need in KeiOS; adopt only if a concrete case appears where a stateful subtree is currently torn down and rebuilt on a layout move. Document the candidate when found rather than forcing adoption. | Deferred (no concrete site yet) |

## P3 / Verify-only

| ID | Area | MIUIX source idea | KeiOS landing direction | Status |
| --- | --- | --- | --- | --- |
| A6 | Immutable list params | Sample uses immutable collections to keep list params stable across recomposition | **Low priority under Kotlin 2.3.21 strong skipping** — unstable `List`/lambda params no longer force recomposition by default. The single `ImmutableList` reference in KeiOS is Guava's (a Media3 `CommandButton` type), unrelated to Compose stability. No kotlinx persistent-collections dependency is declared. Action: do **not** add the dependency speculatively; revisit only if profiling shows a specific list param defeating strong skipping. | Closed (no action under strong skipping) |
| A7 | miuix-blur shader internals (cascade downsampling, noise dithering, GraphicsLayer pooling) | Library-internal RuntimeShader optimizations in `miuix-blur` | KeiOS consumes the blur via the library, not reimplemented. Action is **verify**, not reimplement: confirm the consumed library version performs cascade downsampling / dithering / layer pooling, and that KeiOS's capability gates (`appGlassRuntimeEffectsEnabled()`, `activeGlassBackdrop()`) correctly short-circuit when unsupported. | Not started (verify) |

## Adoption order

1. A1 (`derivedStateOf` thresholds) — highest signal-to-effort, directly cuts per-frame recomposition on scroll.
2. A2 (`drawWithCache` expansion) — cuts per-frame allocation; pairs naturally with A1 on the same chrome surfaces.
3. A3 (deferred lambda reads) — incremental, low-risk extension of an existing pattern.
4. A7 (verify blur internals) — confirmation pass, no code unless a gap is found.
5. A4 (`DrawModifierNode`) — only where profiling justifies it.
6. A5 (`movableContentOf`) — deferred until a concrete site appears.
7. A6 — closed; revisit only on profiling evidence.

## Guardrails

- Preserve current Liquid Glass visuals, animation timing, and all page behavior. This is a
  performance pass: move work to draw/layout, cache size-stable inputs, and reduce per-frame
  recomposition — never trade away UI richness.
- MIUIX is Apache-2.0; still write KeiOS-native code rather than copy structure/names.
- Prefer release/benchmark evidence for final performance claims; debug HWUI bars are triage only.
- Each landed item must pass: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`,
  `git diff --check`, and the `collectAsState(` misuse scan, then update the Status column here.

## Verification Log

- 2026-05-30 plan authored:
  - Re-scanned MIUIX sample at `9b86e77` (`example/`, `miuix/`).
  - Verified KeiOS gap counts: `derivedStateOf` 4/2 files, `drawWithCache` 8/3 files,
    `DrawModifierNode` 1 file, `movableContentOf` 0, kotlinx immutable collections 0
    (lone `ImmutableList` is Guava Media3).
  - Confirmed Kotlin/Compose compiler 2.3.21 (strong skipping ON) from `build.gradle.kts`.
  - Confirmed widely-adopted patterns require no action.
