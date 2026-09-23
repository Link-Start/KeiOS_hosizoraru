# Main navigation performance audit — 2026-07-30

## Scope and acceptance boundary

This audit investigates the HWUI regression observed after the 1.11.0 release,
with emphasis on Liquid Glass, Backdrop, the retained main pager, miuix-nav route
transitions, and edge-stacked cards.

The physical Xiaomi device supplies the problem baseline. Initial iteration ran
on the single visible Android 17 AVD `KeiOS_API37_Validation` at
`emulator-5554`. The final v1.11.8 comparison and page-switch refinement use the
same persistent AVD profile at `emulator-5556`: 6 CPU cores, 6 GB RAM, host GPU,
1280 × 2856, and 480 dpi. A final physical-device `benchRelease` pass remains
the release acceptance gate.

Visual fidelity is a hard constraint. The fixes preserve blur, refraction,
highlight, shadow, animation duration, page-switch semantics, sheet detents, and
Toast entry/exit behavior.

## Physical-device problem baseline

Device: Xiaomi `25098PN5AC`, Android 16 / API 36, 1220 × 2656, density 520.

Measured interaction:

1. Home → GitHub → MCP → Home.
2. Repeat five times.

Observed result:

| Metric | Baseline |
| --- | ---: |
| Total frames | 742 |
| Modern jank | 0 |
| Legacy jank | 736 / 99.19% |
| CPU P50 / P90 / P95 / P99 | 48 / 85 / 97 / 105 ms |
| GPU P50 / P90 / P95 | 8 / 16 / 19 ms |
| Scratch `GrVkTextureRenderTarget` | 508.31 MB / 745 entries |
| Total GPU memory | 610.07 MB |

The low GPU frame percentiles and very large RenderTarget cache point to
RenderNode submission, Backdrop captures, and offscreen raster work rather than
shader execution alone.

## Android 17 AVD benchmark

The benchmark module now contains four `benchmarkRelease` Macrobenchmarks:

- `homeRestingDynamicBackground`: three seconds of the complete animated Home
  background and HDR sweep.
- `homeScrollWithFullEffects`: repeated Home scrolling with every visual effect
  enabled.
- `homeGitHubMcpHome`: Home → GitHub → MCP → Home.
- `mcpStackedCardsScroll`: three upward and three downward MCP list swipes.

All four use:

- `FrameTimingMetric`
- `CompilationMode.Partial(BaselineProfileMode.Require)`
- warm startup
- five iterations
- R8-enabled `benchmarkRelease`

The AVD is not CPU-locked and remains a relative signal. Emulator suppression is
passed only on the command line; the project configuration still rejects
emulator performance results by default.

After the code-level comparisons were complete, the persistent AVD profile was
raised from 4 CPU cores / 2 GB / automatic graphics to 6 CPU cores / 6 GB /
explicit host graphics. A cold boot confirmed gfxstream, Metal-backed GLES,
MoltenVK on Apple M2 Pro, and a 512 MB configured VM heap (resolved to 576 MB by
the Pixel 10 Pro device profile). Measurements across that configuration
boundary are treated as separate diagnostic baselines.

## Perfetto diagnosis

The initial navigation trace recorded 106 app frames, including 77 missed app
frames and 74 `App Deadline Missed` frames.

| Work | Initial trace |
| --- | ---: |
| Main thread `Running` | 265.18 ms |
| RenderThread `Running` | 2817.83 ms |
| RenderThread `Drawing full screen` | 2887.13 ms total / 28.87 ms average |
| Main thread `postAndWait` | 2723.59 ms total / 27.24 ms average |
| RenderThread `flush commands` | 1269.34 ms total |
| RenderThread `flush layers` | 979.55 ms total / 10.00 ms average |
| Ganesh `FillRectOp` | 82,522 |

Compose recomposition accounted for a much smaller share:
`Recomposer:recompose` was approximately 83.56 ms and `Compose:recompose`
approximately 68.72 ms.

The main thread is mostly waiting for RenderThread. Native raster, layer flush,
and full-screen Backdrop capture are the primary optimization targets.

## Implemented changes

### 1. Sheet Backdrop producers are demand-driven

GitHub and MCP previously retained separate full-page content and Sheet
producers while every Sheet was closed. The content and Sheet identities now
collapse while no Sheet is visible, then separate in the same composition that
opens a Sheet.

The GitHub visibility gate includes all overview, strategy, check-logic,
Droid-source, debug, Actions, add/edit, artifact, APK information, install
confirmation, F-Droid, and decision-assist Sheet requests.

### 2. Liquid Toast producer follows the live Toast stack

The NavDisplay previously lived inside a permanent full-screen Toast Backdrop
producer. `LiquidToastState.isVisible` now tracks live Toast slots.

The producer is attached before a visible Toast consumer and remains attached
through its exit animation. It detaches after the final Toast slot leaves.

### 3. Retained inactive pages release full-page producers

The custom main pager intentionally keeps Home, OS, BA, MCP, and GitHub
compositions alive to preserve state. OS, BA, MCP, and GitHub also created
full-size content and Sheet producers inside those retained compositions.

`MainPageContentBackdropScene` now accepts `producerActive`. Each page connects
it to `MainPageRuntime.isPageActive`, which is true for the settled and target
pages during a transition. Inactive retained pages keep their state and data
contracts while detaching their full-page producers.

The target page restores its producer in the same runtime-state update that
starts the transition. Outgoing and incoming glass therefore remain available
during page motion.

### 4. Resting edge-stacked cards avoid a dedicated transform layer

The previous resting fast path reset a permanently allocated `graphicsLayer` to
identity for every visible card. The card modifier now uses normal placement
below the stack line and `placeRelativeWithLayer` only for cards actively
entering the top pile.

The translation, scale, alpha, transform origin, disposal fade curve, and stack
depth formulas are unchanged. Scroll-position reads remain in the layout
placement phase and do not invalidate composition.

### 5. Benchmark permission setup is deterministic

The Macrobenchmark setup grants the target package notification and local
network runtime permissions before launch. A freshly installed benchmark APK
therefore reaches `home_page_root` without permission-sheet timeouts.

### 6. Passive surfaces omit zero-contribution shadow nodes

`LiquidSurface` now passes no outer-shadow producer when shadow rendering is
disabled or alpha resolves to zero. It also omits the interactive inner-shadow
producer for passive or disabled surfaces. Visible outer shadows, press-driven
inner shadows, blur, refraction, highlight, border, content clipping, and the
enabled-state composition boundary keep their existing formulas and timing.

### 7. Overview pills share one Backdrop draw pass

Home, GitHub, and OS overview pills opt into a batched Liquid Glass renderer.
The custom flow layout preserves the 28 dp height, horizontal and vertical
rhythm, RTL placement, typography, color, border, blur, vibrancy, lens
refraction, and highlight. Up to 40 pill bounds are supplied to one lens and
highlight shader, replacing one Backdrop consumer per pill with one consumer
for the complete flow. Empty, unsupported, and oversized cases fall back to the
original `FlowRow` implementation.

The isolated Home overview experiment reduced its selected AVD rendering signal
by approximately 59%. This is a component-level relative result; complete frame
timing remains the release acceptance metric.

### 8. Pager state is visible in Perfetto

The runtime now emits counters for current, target, and settled page indices,
scroll state, programmatic navigation, active navigation, active page Backdrop
producer count, and full-effect page count. The existing
`PerformanceMetricsState` values remain available for JankStats while Perfetto
can align the real pager window with RenderThread `Drawing`, `flush commands`,
and `flush layers` slices.

## Measured result

Navigation frame timing:

| Metric | Initial AVD | After retained-producer gating | Change |
| --- | ---: | ---: | ---: |
| Frame count median | 71 | 72 | +1 |
| CPU P50 | 55.43 ms | 56.75 ms | within AVD noise |
| CPU P90 | 92.74 ms | 89.43 ms | -3.6% |
| CPU P95 | 126.60 ms | 109.27 ms | -13.7% |
| CPU P99 | 165.80 ms | 149.13 ms | -10.1% |
| Overrun P50 | 62.85 ms | 64.94 ms | within AVD noise |
| Overrun P90 | 123.70 ms | 122.78 ms | -0.7% |
| Overrun P95 | 159.10 ms | 140.95 ms | -11.4% |
| Overrun P99 | 188.05 ms | 172.52 ms | -8.3% |

In the optimized iteration-zero trace, `flush layers` was approximately
7.96 ms per submitted frame, compared with approximately 9.80 ms per frame in
the initial trace.

After the AVD was left available for visual review, the same
`benchmarkRelease` suite was run again on `KeiOS_API37_Validation` with
`androidx.benchmark.suppressErrors=EMULATOR`. The run completed successfully;
the suppression only converts the expected emulator-environment warning into a
warning so FrameTiming data can be collected.

| Benchmark | Frame count median | CPU P50 / P90 / P95 / P99 |
| --- | ---: | ---: |
| `homeGitHubMcpHome` | 51 | 59.07 / 96.85 / 131.65 / 155.73 ms |
| `mcpStackedCardsScroll` | 235 | 16.93 / 50.00 / 58.77 / 67.78 ms |

Raw result:
`baselineprofile/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/KeiOS_API37_Validation(AVD) - 17/os.kei.baselineprofile-benchmarkData.json`

This run is a fresh AVD signal rather than a physical-device acceptance
claim. Its navigation tail is higher than the previous AVD run, while the
scroll distribution is lower; the variation reinforces the decision to use
the Xiaomi device for final performance sign-off.

The dedicated scroll benchmark showed substantial AVD variance. Its repeated
optimized run measured CPU P50/P90/P95/P99 of
21.72/53.50/62.22/73.58 ms. The old permanent stack-layer run measured
24.52/52.58/61.77/75.60 ms. This supports visual safety and lower median work,
while the tail difference remains too small for a standalone performance claim.

### Exact v1.11.8 comparison

The reference APK was verified as version `1.11.8` (`11108999`) with certificate
SHA-256 `c5c4c43ba6d03268773122bc99d1e870908609ae5251c7c8ad4b6029b573c82a`.
Both builds were measured with R8, required Baseline Profiles, the same AVD
profile (6 CPU cores, 6 GB RAM, host GPU, 1280 × 2856 at 480 dpi), and five
iterations. `v1.11.8` resolves to commit
`9fef081dd5cb01c706bf6ebc4ff2d14a6a24a1eb` and is the retained reference.

Home dynamic resting:

| Build | CPU P50 / P90 / P95 / P99 | Overrun P50 / P90 / P95 / P99 |
| --- | ---: | ---: |
| v1.11.8 | 6.05 / 20.21 / 21.29 / 23.10 ms | -1.16 / 5.60 / 6.62 / 7.89 ms |
| Current retained Home batch | 19.46 / 22.26 / 23.06 / 25.02 ms | 4.06 / 7.23 / 7.91 / 9.61 ms |

Home → GitHub → MCP → Home:

| Build | CPU P50 / P90 / P95 / P99 | Overrun P50 / P90 / P95 / P99 |
| --- | ---: | ---: |
| v1.11.8 | 32.43 / 65.22 / 84.86 / 104.97 ms | 23.60 / 61.04 / 84.01 / 101.22 ms |
| Current retained source | 22.94 / 66.07 / 75.45 / 96.83 ms | 8.17 / 75.30 / 84.35 / 101.45 ms |

The current navigation median and CPU P95/P99 are close to or better than the
reference. Home resting CPU P90/P95/P99 are within 1.8–2.1 ms, an accepted small
range for this visual revision. The Home median and RenderThread workload retain
a material AVD gap. AVD results remain a screening signal; the Xiaomi acceptance
pass owns the release decision.

RenderThread decomposition from the representative Home iteration:

| Build | Drawing | `eglSwapBuffers` | `dequeueBuffer` | `FillRectOp` / frame | `OpsTask prepare` / frame | `drawLayer` / frame |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| v1.11.8 | 4.736 ms | 1.424 ms | 0.005 ms | 122.0 | 23.0 | 8.0 |
| Current retained Home batch | 16.427 ms | 12.294 ms | 9.239 ms | 208.0 | 49.0 | 17.0 |

The retained Home batch reduced `FillRectOp` from 404.1 to 208.0, `OpsTask
prepare` from 108.6 to 49.0, and `drawLayer` from 29.1 to 17.0 per frame. The
remaining gap is concentrated in GPU completion and BufferQueue wait time:
`waitForBufferRelease` is 9.231 ms per frame. The modern Liquid Glass renderer
therefore remains visually complete and structurally leaner, while its AVD GPU
path has not reached the `v1.11.8` resting cost.

The retained implementation keeps four Home overview cards in one Backdrop
consumer and all overview pills in a second consumer. Its shaders scan the
actual active card and pill counts, preserving blur, vibrancy, lens/refraction,
highlight, inner/outer shadow, card deformation, touch-centered radial
refraction, labels, borders, and click behavior.

## Rejected experiment

Removing the enabled-state identity `graphicsLayer` from the shared
`LiquidSurface` looked structurally attractive because Backdrop already owns an
offscreen layer. Two AVD scroll measurements showed a consistent regression:

| Build | CPU P50 / P90 / P95 / P99 |
| --- | ---: |
| Current stack-layer optimization | 21.72 / 53.50 / 62.22 / 73.58 ms |
| Identity-layer removal, first run | 22.80 / 57.10 / 67.77 / 93.55 ms |
| Identity-layer removal, repeat | 25.22 / 61.56 / 73.44 / 99.95 ms |

The existing layer acts as a useful composition boundary around Backdrop and
card content on this renderer. The experiment was fully reverted; the shared
surface implementation and visual behavior remain unchanged.

Gating the four retained-page `topBarBackdrop` producers by
`isPageActive` was measured twice after an AVD restart. CPU P95/P99 were
`132.66/156.53 ms` and `129.82/166.39 ms`, compared with the
`109.27/149.13 ms` retained-producer baseline. That producer boundary is
also reverted; top-bar sampling remains continuously available for the active
composition contract.

Six additional single-variable experiments were measured and reverted:

- A local HDR transparent-period `saveLayer` did not reduce the retained
  RenderThread layer work.
- Specializing the AGSL lens shader to the active pill count did not reduce the
  real shader/layer submission cost.
- Removing the Home producer's two fallback fills did not improve tail latency.
- Merging the Hero transform layer with the HDR offscreen layer changed layer
  ownership without a measurable RenderThread gain.
- Spatially indexing pills by their parent cards left Drawing effectively flat
  at 16.409 ms and raised CPU P90/P99 to 32.14/36.04 ms.
- Combining the Home card and pill Backdrop consumers reduced `drawLayer` from
  17 to 15 and `FillRectOp` from 208.0 to 192.0 per frame, while Drawing stayed
  at 16.420 ms and `dequeueBuffer` increased to 11.170 ms.

A final MCP-only experiment marked the adjacent target active for two frames
before starting the unchanged 300 ms GitHub → MCP motion. Against the
fade-through candidate it reduced frames above 50 ms only from 9–10 to 8–9,
while the complete route moved from
22.12/64.66/73.32/98.16 ms to 24.90/43.60/77.60/95.20 ms CPU
P50/P90/P95/P99 and from 8.46/74.78/86.90/100.32 ms to
13.20/54.10/93.00/111.90 ms Overrun P50/P90/P95/P99. One marker also expanded
to 1084.62 ms. The higher P50/P95 and Overrun P95/P99 place the narrow local
gain below the acceptance threshold, so target preparation was fully reverted.
Evidence remains at
`artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-mcp-target-prewarm/`.

Each revert restores the accepted visual result and keeps the optimized source
at an independently measurable checkpoint.

Further Home experiments now require a renderer-level reduction in GPU render
target scheduling or effect-pass fusion with the complete Liquid Glass output
held constant. Small structural count reductions no longer justify additional
source complexity. The audit therefore stops this AVD iteration at the retained
two-consumer Home batch and proceeds to physical-device acceptance.

### Retained page-switch refinement

The final main pager keeps one cancellable `pagePosition` owner across
programmatic tab switches, horizontal gestures, page placement, drawing, and
Bottom Bar interpolation. Programmatic jumps preserve the complete spatial
distance, so only the two pages around the current fractional position draw at
any instant. Original 300/400/500 ms durations remain in effect for distances
two/three/four. Adjacent gestures retain their established settle path.

The local Apache-2.0 Miuix sample at `.tmp/miuix` revision `d5c03cca` drives its
complete-distance pager with Compose `animate`, `tween`, and `EaseInOut`. The
v1.11.8 tree also uses that combination in its Foundation and Miuix pager
variants. The retained pager now applies the same driver and curve. This removes
its hand-written frame loop and the more front-loaded `FastOutSlowIn` curve while
preserving the same state, geometry, effects, cancellation, and duration
contracts.

Five settled page tags make the benchmark wait for real animation completion.
The target page root becomes visible earlier in the spatial transition, so page
root visibility alone produces an incomplete measurement window. Duration
selection is now a named pure function with JVM coverage for its 300/400/500 ms
contract. The constant `farJumpAlpha = 1f` relay left by the rejected fade
candidate has also been removed from the controller, coordinator, and pager
layer.

Three distant-jump presentation candidates were measured and rejected:

- Fade-through held aggregate frame timing near the full-distance source, then
  produced a visible black flash when the scene reached alpha zero. Evidence:
  `artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-far-jump-fade-through-settled/`.
- Virtual-adjacent endpoints removed intermediate-page travel and kept both
  endpoint pages drawing for the full animation. CPU P50/P90/P95/P99 reached
  34.62/74.39/77.43/95.52 ms and Overrun reached
  27.91/89.06/95.85/105.31 ms. MCP → Home produced 9–10 frames above 50 ms in
  every run. Evidence:
  `artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-virtual-adjacent/`.
- Moving virtual endpoint translation into draw phase improved CPU P50 by about
  1.13 ms, while two GitHub → MCP markers exceeded 1.1 seconds. The gain stayed
  below the experiment threshold. Evidence:
  `artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-virtual-adjacent-draw-phase/`.

Five same-device `benchmarkRelease` iterations on `emulator-5556`, with R8 and
`CompilationMode.Partial(BaselineProfileMode.Require)`, measured:

| Build | Frame count median / CV | CPU P50 / P90 / P95 / P99 | Overrun P50 / P90 / P95 / P99 |
| --- | ---: | ---: | ---: |
| Full distance, manual `FastOutSlowIn` loop | 72 / 19.44% | 28.4 / 66.0 / 74.2 / 96.0 ms | 19.5 / 73.0 / 88.5 / 104.7 ms |
| Full distance, Compose `EaseInOut` driver | 73 / 5.43% | 23.5 / 62.3 / 74.0 / 97.1 ms | 10.9 / 72.1 / 88.6 / 102.2 ms |

The retained driver lowers CPU P50 by 4.9 ms and P90 by 3.7 ms, holds P95,
keeps P99 within a 1.1 ms AVD range, and reduces five-run frame-count variance.
Marker-scoped Perfetto analysis records zero frames above 50 ms for every Home →
GitHub run. MCP → Home records zero or one frame above 50 ms and has consistent
completion markers. GitHub → MCP continues to record 9–10 frames above 50 ms as
MCP's first full Backdrop draw enters the transition. The measured two-frame MCP
preparation experiment above already established the low-return boundary for
that path.

Clean and HWUI-bar recordings cover Home → GitHub → MCP → Home, Home ↔ BA,
rapid retargeting, and horizontal gesture input. They show continuous direction,
stable endpoints, complete intermediate-page motion, and continuously rendered
content. The AVD remains available with `debug.hwui.profile=visual_bars`.

Raw retained evidence:

- `artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-full-distance-settled/`
- `artifacts/performance-2026-08-01-v1.11.8-baseline/experiments/retained-full-distance-ease-in-out/`

### Production pager and state-pipeline audit

The production bottom-page path resolves through
`rememberMainLoadedPagerState` and `MainLoadedPager`. `MainMiuixPager` and
`MainFoundationPager` remain source-level alternatives. (2026-09-23: both were deleted. Neither
had been constructed since `86a61f1aa` and `2b2fa0775` in May.) The measured Home →
GitHub → MCP → Home journey exclusively uses the loaded pager. Miuix Navigation
3 owns the outer route stack; the retained five-page surface uses the custom
loaded pager.

`MainLoadedPager` keeps all page compositions and state alive. The settled draw
window includes only the settled page, and the moving window includes pages
within `pagePosition ± 1.05`. Fractional position reaches layout through the
lambda form of `Modifier.offset`, while `drawWithContent` clips drawing to the
visible page intersection. Bottom-page switches therefore have one animation
and gesture owner.

The benchmark-release Compose Compiler report contains 2,218 skippable and
2,851 restartable composables. `HomePage`, `HomeOverviewGlassBatchHost`,
`MainLoadedPager`, `MainPagerPageHost`, and
`MainPageContentBackdropScene` are all restartable and skippable. Their pager
and runtime inputs are compiler-stable.

Home observes repository state through lifecycle-aware `StateFlow` collection.
Its runtime clock starts only while the page and data are active and while a
time-sensitive value is visible; the interval is 30 seconds. MCP also uses
lifecycle-aware collection and disconnects its runtime-clock Flow while the
page is inactive. In the representative Home trace, the UI thread ran for
70.70 ms across the three-second marker, approximately 0.39 ms per frame. In
the representative GitHub → MCP marker, the UI thread ran for 53.25 ms while
RenderThread ran for 377.03 ms. The main traversal included 342.15 ms in
`postAndWait`, showing that the UI thread primarily waited for RenderThread.

These results place the current ViewModel, Flow, ticker, Compose stability, and
miuix-nav ownership paths below the render-topology optimization threshold.

### Solid content material Backdrop refinement

GitHub and MCP previously created an empty page-sized `LayerBackdrop` producer
for content cards. Backdrop 2.0.0 records the producer surface fill and child
content into a full-screen `GraphicsLayer`; this producer's child content was
empty, so every content consumer sampled an equivalent solid surface through an
additional page-sized layer.

Their card material now uses `rememberCanvasBackdrop { drawRect(surfaceColor) }`.
The top bar keeps its real scrolling-content `LayerBackdrop`, and an open Sheet
keeps an independent page-sized `LayerBackdrop`. Closed Sheets and solid content
materials therefore leave the top bar as the only persistent layer producer.
OS and BA retain their existing layer-backed content contracts, and Home keeps
its dedicated two-batch Backdrop topology.

The same-device five-iteration navigation comparison is:

| Metric | Empty content producer | Canvas content material | Change |
| --- | ---: | ---: | ---: |
| Frame count median | 73 | 76 | +3 |
| CPU P50 | 21.8 ms | 23.1 ms | +1.3 ms |
| CPU P90 | 58.3 ms | 55.1 ms | -3.2 ms |
| CPU P95 | 77.5 ms | 65.2 ms | -12.3 ms |
| CPU P99 | 109.4 ms | 102.5 ms | -6.9 ms |
| Overrun P50 | 6.7 ms | 9.3 ms | +2.6 ms |
| Overrun P90 | 75.5 ms | 65.8 ms | -9.7 ms |
| Overrun P95 | 95.4 ms | 81.6 ms | -13.8 ms |
| Overrun P99 | 121.0 ms | 99.7 ms | -21.3 ms |

The higher median remains inside the AVD variation band. The tail improvement
is supported by the GitHub → MCP RenderThread decomposition:

| Operation | Empty producer | Canvas material | Change |
| --- | ---: | ---: | ---: |
| `Drawing` | 445.011 ms | 382.466 ms | -14.1% |
| `flush layers` | 192.708 ms | 179.924 ms | -6.6% |
| `flush commands` | 169.901 ms | 128.600 ms | -24.3% |
| Ganesh execute calls | 3,353 | 2,597 | -22.6% |
| `FillRectOp` | 9,878 | 7,308 | -26.0% |
| `TextureOp` | 2,510 | 2,272 | -9.5% |
| `dequeueBuffer` | 24.234 ms | 16.833 ms | -30.5% |
| `waitForBufferRelease` | 24.161 ms | 16.482 ms | -31.8% |
| Texture uploads | 361 | 337 | -6.6% |
| Uploaded pixels | 46.36 M | 43.64 M | -5.9% |

The representative marker contains 13 frame-timeline-backed GitHub → MCP
frames, including 11 jank-classified frames. Their average/maximum CPU duration
is 58.85/103.89 ms. Home → GitHub contains zero frames above 50 ms, and MCP →
Home also contains zero frames above 50 ms. The remaining hotspot is MCP's first
complete Liquid Glass layer build during its incoming transition.

Static screenshots and a transition recording confirm the GitHub and MCP card
materials, top-bar sampling, independent Sheet background sampling, scrim,
surface hierarchy, dynamic background, refraction, highlights, and shadows.
The Home → GitHub motion contains continuous page surfaces and continuous color.

### Final Home static check and rejected draw-state cache

A single-variable Home experiment cached card geometry, registration reads, and
shader uniform arrays once per draw frame. It reduced representative UI
traversal and median actual-frame cost, while Macrobenchmark P90–P99 regressed
after a refreshed Baseline Profile. The code experiment was fully reverted.

Two final five-iteration static runs after the revert measured:

| Run | CPU P50 / P90 / P95 / P99 | Overrun P50 / P90 / P95 / P99 |
| --- | ---: | ---: |
| Original Home renderer | 19.8 / 20.5 / 20.9 / 21.8 ms | 4.1 / 4.9 / 5.4 / 5.9 ms |
| Final run 1 | 20.7 / 21.6 / 22.2 / 24.1 ms | 5.3 / 6.4 / 7.6 / 9.1 ms |
| Final run 2 | 19.3 / 21.2 / 22.1 / 24.9 ms | 4.1 / 6.7 / 7.8 / 11.7 ms |

The representative three-second traces place RenderThread running time at
1249.57 ms before and 1276.16 ms after, a 2.1% AVD-range difference. Total
`Drawing` time is 2954.47 versus 2952.76 ms. `dequeueBuffer` and
`waitForBufferRelease` each decrease by approximately 32.9 ms in the final
trace. Home static rendering therefore remains in the same performance band,
and the remaining P99 spread supports holding the current visual pipeline.

The release Baseline Profile was regenerated after the cache revert. The final
files contain 42,024 baseline rules and 23,868 startup rules. All reverted
`HomeOverviewCardDrawSnapshot`, `HomeOverviewCardRegistrationDrawState`, and
`drawSnapshot` rules are gone; the Canvas Backdrop and real retained-pager
journeys remain captured.

Final AVD traces and benchmark messages are stored under
`build/perfetto-analysis/final-canvas-content/`. The Android 17 AVD result is a
controlled relative comparison. Physical-device review remains the acceptance
gate for touch feel, thermals, and final HWUI bars.

## Backdrop tooling boundary

The Backdrop MCP server was requested during this audit and was not exposed in
the active tool catalog. Diagnosis therefore used the project-locked Backdrop
2.0.0 source, runtime trace counters, R8 Macrobenchmark, Perfetto, and visual
screenshots. The missing MCP surface is recorded so future runs can repeat the
analysis with it when available.

## GPU cache interpretation

After visiting all main pages, both the temporary always-active producer build
and the optimized build reported 279 Scratch RenderTarget cache entries and
153.31 MB total GPU cache on the AVD. Skia retains released scratch targets for
reuse, so `dumpsys gfxinfo` cache totals do not immediately fall when a
producer detaches.

The runtime trace still shows fewer layer-flush costs and lower high-percentile
frame duration. Physical-device peak cache growth remains an explicit final
acceptance measurement.

## Visual validation

The optimized `benchmarkRelease` was validated on the visible
`KeiOS_API37_Validation` AVD. The earlier navigation checkpoint at source commit
`ca4cb236d` was also installed on Xiaomi `25098PN5AC`; that physical package is
R8-minified, profileable, `debuggable=false`, and signed with the release
certificate.

Validated in dark mode:

- Home, GitHub, MCP, OS, and BA page switching.
- MCP stacked-card scroll in both directions.
- GitHub add-tracking Sheet with independent glass background sampling.
- MCP edit-service Sheet.
- Liquid Toast first visible frame, glass material, and completed exit.
- Bottom dock, page overview anchors, card shape, shadow, alpha, and stack order.

The retained two-consumer Home batch received an additional API 37 acceptance
pass with HWUI bars enabled. All four overview cards and their pills preserved
blur, vibrancy, lens/refraction, highlight, border, inner/outer shadow, labels,
and dynamic-background sampling. The WebDAV card preserved synchronized card,
pill, and text deformation during a held press, then opened WebDAV Sync on tap.
The Home → GitHub → MCP → Home route completed with the same process alive. The
process-local log stayed clear of RuntimeShader, SkSL, AGSL, concurrent
modification, and fatal exception entries.

The physical-device smoke check used the user's enabled Apple launcher alias,
confirmed `os.kei/.LauncherAppleDesigns` as the resumed activity, and showed the
complete light-mode Home surface at 1220 × 2656 / density 520. No process-local
fatal exception or ANR appeared after launch. Manual scrolling, transitions,
HWUI bars, thermal behavior, and repeated-loop timing remain with the user for
interactive acceptance.

Saved local evidence lives under
`artifacts/performance-audit-2026-07-30/`,
`artifacts/performance-audit-2026-08-01-home-segmented/`, and
`artifacts/performance-physical-2026-08-01/`. The final retained Home evidence is
under
`artifacts/performance-2026-08-01-v1.11.8-baseline/final-acceptance/`.

On 2026-08-18 those trees were pruned to reclaim disk. Every benchmark had been
captured over five iterations; `iter000` of each was kept and the four repeats
deleted, which took `artifacts/` from 5.4 GB to 1.8 GB. Nothing derived was
touched — all `_analysis.md` chains of evidence, benchmark metric `.txt` files,
screenshots, and XML results survive intact, so every number quoted in this
document is still backed by the file it was read from. What no longer exists is
the ability to re-open the other four iterations of a run. Eight `_analysis.md`
files happened to have been written against `iter002` rather than `iter000`; each
now carries a note naming the surviving sibling trace of the same benchmark in
the same experiment directory, so the phenomenon stays inspectable even though
that exact iteration does not. Any fresh comparison needs a fresh capture
regardless: these traces measure v1.11.8 and v1.12.0 builds, and the entire
large-screen adaptation has landed since.

## Remaining acceptance work

1. The targeted backdrop and Liquid Glass unit tests pass. The full app suite
   still contains the pre-existing `GitHubDetailInfoRowReuseTest` contract
   failure, and `lintDebug` still reports the project backlog; neither was
   expanded by this performance patch.
2. Install the retained Home batch on Xiaomi `25098PN5AC` for the next user
   review. Repeat the five-loop navigation scenario and capture frame timing,
   Perfetto, GPU memory, HWUI bars, and a visual recording.
3. Treat the physical-device run as the release decision. The fixed AVD remains
   the fast, reproducible diagnostic loop for the next high-leverage change.
