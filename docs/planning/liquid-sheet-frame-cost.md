# Why scrolling a Liquid sheet is not smooth

## Current verdict — 2026-09-02

The checked-in release measurements and current source identify two layers of cost:

1. The dominant steady-state category is RenderThread work from `drawBackdrop` layers. The Sheet
   surface records and rasterizes one large offscreen layer, `exportedBackdrop` records that surface
   again for glass children, and every composed visible glass card contributes its own layer. The
   latest representative runs put Sheet scrolling around 108–116ms total with roughly 30–35ms in
   RenderThread. ART compilation cannot remove these layer costs.
2. The Sheet also read `resizedHeightPx` from Composition to calculate grabber expand/collapse
   capability. Every drag delta and height-spring frame could therefore recompose the whole Sheet
   container. The read now happens from callback providers, while `userResized` is read by the exact-
   height Layout modifier. Resize still performs the required Layout work and avoids Composition work.

The modal-presentation freeze remains effective: an untouched Sheet produces zero background-driven
frames. Active dragging and content scrolling legitimately request frames, which exposes the
RenderThread glass floor.

The Baseline Profile now drives one representative Strategy Sheet through expand, content scroll in
both directions, collapse and dismissal. This precompiles gesture dispatch, nested-scroll arbitration,
height layout and settle code on first use. Matching `BaselineProfileMode.Require` and
`CompilationMode.None` frame benchmarks measure its real contribution without attributing the
RenderThread floor to ART.

The matching 2026-09-02 R8 `benchmarkRelease` A/B on the A17 API 37 AVD completed five iterations per
mode. The Profile reduced median Compose recomposition total from 4.07ms to 2.05ms (49.7%) and median
maximum `Choreographer#doFrame` from 81.43ms to 76.44ms (6.1%). End-to-end frame CPU P95 measured
99.82ms with the Profile and 99.13ms with no compilation; frame-overrun P95 measured 128.25ms and
129.01ms. This is direct evidence that the Profile removes meaningful first-use JVM/Compose work while
leaving the visible frame floor almost unchanged. DrawFrame/Drawing median maxima remained about
76.59/68.84ms with the Profile, keeping rendering and glass composition as the next optimization
boundary. These emulator values are diagnostic comparisons, with physical-device Perfetto evidence
required for a shipping performance claim.

Material and motion values remain unchanged: the backdrop source, blur, vibrancy, lens, refraction,
highlight, shadows, fills, corner radius and Folme spring constants retain their current values. The
appearance-preserving long-term lever is cache/reuse support in the backdrop node for an unchanged
source and geometry. That capability is still unavailable through the shipped kyant API; any local
approximation needs pixel-diff proof before adoption.

That cache directly targets content scrolling, where the Sheet geometry stays fixed. A detent resize
changes the surface height and rounded top edge on every frame; preserving the same lens field there
needs incremental/cached layer support upstream, or a pre-rasterized maximum surface with moving clip
and top-edge reconstruction that passes pixel comparison. The current library exposes neither path.

The sections below are the chronological experiment log. Later measurements supersede early
hypotheses while preserving the evidence and rejected directions.

Reported as "the frame rate looks a bit low when scrolling up and down in a liquid sheet". Measured
on the API 37 AVD (`KeiOS_API37_Validation`, 1280x2856 @480dpi, 120Hz, vsync interval 8.33ms) against
the installed `os.kei` 1.13.0 — no rebuild, so this is the shipped build's behaviour. Harness:
`scripts/perf/frame_stages.py` over `dumpsys gfxinfo <pkg> framestats`, per
`hwui-frame-budget.md`'s rule of reading only the per-frame stage columns.

Every row below is two independent runs. They agree to within a few tenths of a millisecond, so the
differences here are far outside the noise this instrument is known to have.

## The headline: scrolling is not the cause

| state | frames / 3s | total p50 | RT issue->swap p50 | gpu p50 |
|---|---|---|---|---|
| Home at rest, no sheet | >=119 (capped) | 16.19 / 16.78 | 3.40 / 3.22 | 11.16 / 11.57 |
| Home page scroll | >=119 | 17.42 / 17.49 | 3.25 / 3.29 | 11.96 / 12.14 |
| **sheet open, at rest, zero input** | 72 / 72 | **124.52 / 124.89** | **36.14 / 36.29** | 6.97 / 7.04 |
| **sheet open, scrolling** | >=119 | **132.99 / 132.87** | **38.55 / 38.34** | 6.94 / 6.29 |
| sheet scrolling, Liquid Glass controls **off** | >=119 | 16.82 / 17.34 | 2.58 / 2.41 | 12.70 / 12.94 |

The first four rows are at a forced 1280x2000 window, because the sheet under test (Home's "Bottom
pages" control sheet) only overflows in a shorter window. `frames / 3s` saturates at ~119 because
the gfxinfo ring buffer holds 120 frames, so ">=119" means "producing as fast as it can".

**An open sheet costs 124ms per frame while the user is doing nothing at all.** Scrolling it takes
that to 133ms — about 6% more. The scroll is not what makes it slow; the sheet is already slow, and
scrolling is merely when the eye expects motion and therefore notices.

At the native 1280x2856 window the same at-rest sheet measures **133.87 / 141.14ms total, 38.18 /
39.20ms RT** — so on the real geometry an open sheet sits at roughly 7fps, untouched.

## Four things that are not the cause

**Not Compose.** Every UI-thread stage stays trivial in the expensive case: input 0.00, animation
0.19, measure+layout **0.02**, record draw 0.18ms. This matches `hwui-frame-budget.md` exactly —
"optimising Compose here would buy nothing". Nothing about recomposition, `derivedStateOf`, or lazy
vs eager composition is in play, and the drag-arbitration reads of `contentScroll.canScrollUp`
happen inside `onPreScroll`, outside composition, so they are not causing per-pixel invalidation
either.

**Not the GPU.** `swap->completed` *falls* from ~12ms to ~7ms in the expensive case. The pipeline is
starved of frames rather than fill-bound.

**Not the sheet's own glass area.** Between the 1280x2000 window and native, the sheet's glass grows
from 1136x1437 to 1136x2586 — 1.8x the area — and RT moves only 36.2 -> 38.7ms, +7%. A cost
dominated by the sheet's own full-area blur would have scaled with the area. It does not.

**Not the scroll container.** `SheetContentColumn` is a `Column` + `verticalScroll` rather than a
lazy list, which is a real thing to fix eventually, but it cannot explain a cost that is already
fully present with no scrolling at all.

## Cost is set by how many glass controls are *composed*, not how many are visible

Varying the window changes both how much of the sheet is on screen and how big its glass is, while the
composed control count stays at nine. Baseline, at rest:

| window | switches in view | sheet glass | total p50 | RT p50 |
|---|---|---|---|---|
| 1280x1600 | 5 | 1136x1137 | 124.51 | 36.07 |
| 1280x2000 | 7 | 1136x1437 | 124.44 | 36.55 |
| 1280x2856 | 9 | 1136x2586 | 141.27 | 40.40 |

Sheet glass area grows 2.3x and visible switches 1.8x, but the frame cost moves only 1.12x. Nothing
that scales with area or with visibility can explain that; what is flat is the number of glass
consumers in the composition. Against the glass-off floor of 2.5ms, nine controls costing ~36ms puts
each one at roughly **3.7ms of RenderThread per frame, on screen or not**. Clipping is not culling —
the effect layer is recorded and rasterized either way.

## The cause: cost is linear in the number of glass consumers on screen

The decisive control is the last row of the table. Turning off Settings -> **Liquid Glass controls**
takes the identical journey from 133ms to 17ms, and RenderThread CPU from 38.4ms to 2.5ms — a 15x
reduction that makes the sheet scroll exactly as smoothly as a page.

That toggle does **not** disable the sheet's own glass. The app's own description says so ("ActionBar,
Sheet, and Dialog are always Liquid Glass"), and a screenshot with it off confirms it: the page
behind is still visibly blurred and refracted through the sheet surface. What it does disable is the
glass on the *controls*. So the sheet's one big blur is affordable; the controls inside it are not.

Counted on screen in that one sheet: **9 glass switches and 4 glass pills**, plus the close button
and four glass cards. Each `AppSwitch` is not one cheap element — it is

- a `Modifier.layerBackdrop(trackBackdrop)` **producer**, recording its track into a GraphicsLayer,
- plus a thumb `drawBackdrop` **consumer** sampling a `rememberCombinedBackdrop(backdrop,
  trackBackdrop)` — two sources, not one,
- with `vibrancy()` + `blur()` + `safeLiquidLens(..., chromaticAberration = true, depthEffect =
  true)`, an ambient highlight, a drop shadow and an inner shadow.

`chromaticAberration = true` is the most expensive refraction mode available, and it is on every
switch thumb in the app.

The scaling holds across surfaces, which is why this is not really a sheet bug:

| surface | total p50 | RT issue->swap p50 |
|---|---|---|
| Home (few glass elements) | 16.2 | 3.4 |
| Settings at rest | 16.4 | 11.3 |
| Settings **scrolling** | 65.9 | 19.7 |
| sheet | 124.5-141.1 | 36.1-39.2 |

Settings scrolling is ~15fps by the same mechanism. The sheet is simply the densest glass surface in
the app, so it is where the effect became impossible to miss.

## Why it redraws continuously even at rest

Something must be invalidating for an untouched sheet to produce 72 frames in three seconds. It is
not the sheet: Home *by itself* already produces >=119 frames in three seconds at rest. That is the
dynamic background driving `invalidateDraw` at its 60fps cap, already documented in
`hwui-frame-budget.md` — "the pipeline is already saturated before the user touches anything".

For contrast, MCP at rest produces almost nothing (20 rows of dump against Home's 140), confirming
that the continuous invalidation is Home's and not universal.

So the sheet does not create the redraw. It multiplies the cost of a redraw that was already
happening, from 16ms to 124ms, because every invalidation re-draws the whole glass stack above the
background.

## The library mechanism, from the AAR

Decompiled `io.github.kyant0:backdrop-android:2.0.0` (jadx), `DrawBackdropNode`:

`draw(ContentDrawScope)` runs, in order: `onDrawBehind`, `drawBackdropLayer`, `onDrawSurface`,
`drawContent()`, `onDrawFront`, and then — only when `exportedBackdrop != null` — a `recordLayer`
into the exported layer whose block invokes `onDrawBehind`, **`drawBackdropLayer` a second time**,
`onDrawSurface` and `onDrawFront` (notably *not* `drawContent`, which is what the docs mean by
"exportedBackdrop will skip drawing the content").

And `drawBackdropLayer` is not a cheap replay: it calls `recordLayer(...)` to **re-record** the
node's own effect layer every time, then `drawLayer`s it. The layer is forced to
`CompositingStrategy.Offscreen` in `layoutLayerBlock`, so it is a genuine offscreen rasterization —
the same cost named in `ba-first-entry-frame-floor`.

This is worth knowing, but note what the measurements say about it: the double record applies to the
**sheet surface** (which sets `exportedBackdrop`), and the sheet surface is demonstrably *not* the
dominant term. It is a real inefficiency, not the one to fix first.

## What I got wrong first

Reading the source before measuring, the obvious hypothesis was that the sheet's own large
`drawBackdrop` — sampling the scene, blurring and lensing an area covering 80% of the screen, and
doing it twice per frame because of `exportedBackdrop` — was the cost, with the content being a
descendant that re-triggers it. That hypothesis is wrong, disproved twice over: independently by the
glass-controls toggle (which leaves the sheet's blur fully on and still recovers 15x) and by the area
scaling (1.8x area, +7% cost). Recorded because it is a plausible-looking dead end that someone
reading `LiquidSheetSurface.kt` will arrive at again.

## Candidate fixes

Candidates 2 and 4 are now built and measured — see the section below. 1 and 3 are still open.

In rough order of expected payoff per unit of risk. All of these need an A/B before being believed —
`backdrop-reduced-resolution.md` is the cautionary tale of a direction whose measured upper bound
turned out to be exactly zero.

1. **Make the switch thumb cheaper.** `chromaticAberration = true` on every thumb, over a
   two-source combined backdrop, is the most expensive configuration in the library applied to the
   most-repeated control in the app. Try `chromaticAberration = false`, and try whether the thumb
   needs to sample its own track at all rather than just the parent backdrop. This is a per-element
   constant on a term that multiplies by 13.
2. **Stop the idle invalidation reaching a covered stack.** While a modal sheet is up, the page
   behind it is not meaningfully visible — it is blurred beyond recognition. Freezing the scene
   backdrop for the duration of a modal presentation would cut the redraw rate to zero at rest.
   Note there is no existing mechanism for this: `SnapshotWindowBottomSheet` and
   `AppSnapshotFlowManager` are Compose `snapshotFlow` state plumbing, nothing to do with freezing a
   backdrop.
3. **Cap the number of simultaneously-glass controls.** A table of 13 glass elements is past the
   point where the material reads as material; the HIG line KeiOS already follows elsewhere is "use
   Liquid Glass effects sparingly", and "use the regular variant when components have a significant
   amount of text".
4. **Make `SheetContentColumn` lazy.** Correct regardless, and it bounds the count in 1 and 3, but
   on its own it fixes nothing here — the at-rest measurement proves the cost does not need scrolling.

Not a candidate: reducing backdrop capture resolution. Already measured at an upper bound of zero in
`backdrop-reduced-resolution.md`.

## Implemented and measured: the modal freeze, and laziness

Two of the four candidates above were built and A/B'd on the same AVD, same release variant, same
journeys. Numbers pooled over two runs per condition (four for the last one, to establish spread).

**Sheet scroll**, window 1280x2000, eight 90ms flings:

| build | p50 | mean | >50ms | >100ms | RT mean |
|---|---|---|---|---|---|
| baseline | 132.92 | 132.43 | 100% | 100% | 38.74 |
| **+ modal freeze** | **17.47** | 38.24 | 27% | 6% | 9.82 |
| **+ modal freeze + lazy** | **17.27** | 36.44 | 26% | 0% | 9.15 |
| glass controls off (ceiling) | 16.83 | 18.18 | 0% | 0% | 2.48 |
| plain page scroll (reference) | 17.47 | 17.36 | 0% | 0% | 3.35 |

**Sheet at rest**, native 1280x2856:

| build | frames in 3s | p50 | RT mean |
|---|---|---|---|
| baseline | 131 | 134.14 | 38.92 |
| + modal freeze | **0** | — | — |
| + modal freeze + lazy | **0** | — | — |

### The modal freeze is the whole win

`LiquidOverlayHostState.hasPresentation` now gates the dynamic background's playback, and with it the
colour-stage interpolation — that second one is load-bearing, because `colorStage` is read inside the
draw lambda and would have kept invalidating at spring rate on its own, leaving the gate looking
useless.

At rest the result is not "faster", it is **`Total frames rendered: 0`**. An open sheet now redraws
nothing at all, which is the same property `hwui-frame-budget.md` singles out as BA's best-in-app
behaviour. Scrolling drops from 133ms to 17.5ms at the median — identical to a plain page scroll.

Verified that the freeze releases: Home renders 245 frames in three seconds, 0 with a sheet open, and
**245 again after dismissing it**. Not a one-way latch.

The residual is honest and visible in the table: 26% of frames during an *active drag* still exceed
50ms, against 0% for the glass-off ceiling. The freeze removes invalidations the app did not need; it
does nothing about the cost of the ones it does need. While a finger is actually moving, the glass
stack is legitimately redrawing, and that still costs ~30ms of RenderThread.

### Laziness is real but marginal, for a structural reason

`SheetContentLazyColumn` is added and Home's "Bottom pages" sheet migrated to it. The effect is small
but consistent — the two conditions do not overlap across runs:

| condition | RT mean per run |
|---|---|
| modal freeze only | 9.57, 10.07 |
| + lazy | 9.33, 8.97, 8.90, 8.82 |

About 0.8ms, ~8% of RT mean. That is the *ceiling for this sheet*, not a disappointing result from a
good lever, and the dose-response above says why: nine of the sheet's glass switches live inside a
single `SheetSectionCard`, which is one lazy item and is always at least partly on screen, so it can
never be culled. What laziness can drop here is the Debug card — one switch — which at ~3.7ms per
control is exactly the ~0.8ms observed.

To collect the rest, the table card's rows would have to become individually cullable, which means
breaking one card into many and is a visual change, not a refactor. The cheaper direction is candidate
1: cut the per-control constant.

## Reproducing

Enable `debug.hwui.profile true`, restart the app, then for the at-rest case simply open Home's
"Bottom pages" sheet, `dumpsys gfxinfo os.kei reset`, wait three seconds without touching the
screen, and dump `framestats`. The at-rest measurement needs no window override and no gesture
scripting, which makes it the cheapest possible regression check for any of the fixes above.

---

# Re-measured, and the conclusion above is wrong

Everything from here down supersedes the per-control model in the sections above. Measured on the
same API 37 AVD, against a **release** build (`:app:installRelease`, R8 on) at `wm size 1280x2000`,
eight 90ms flings inside Home's "Bottom pages" sheet. Two runs per condition unless noted.

## Where it stands now

| condition | total p50 | RT issue->swap p50 | record draw p50 |
|---|---|---|---|
| release, sheet at rest | **0 frames** | — | — |
| release, sheet scrolling | 108.2 / 107.8 | 32.5 / 34.8 | 0.63 |
| debug, sheet scrolling | 99.8 | 32.2 | 4.98 |

The modal freeze still works: an untouched sheet renders nothing. Scrolling one still costs ~108ms a
frame, 100% of frames over the 8.33ms interval.

**Release and debug are the same speed.** RenderThread is 32ms in both. The only stage release wins
is `record draw`, 0.63ms against 4.98ms, which is 4% of the frame. That answers the question of why
the release channel feels no better than a debug build: R8 and `isDebuggable = false` optimise the UI
thread, and the UI thread is not where the time goes.

## What the cost is not

Each row is a build with one thing changed, measured the same way.

| build | RT p50 | verdict |
|---|---|---|
| baseline | 32.5 / 34.8 | — |
| switch thumb `chromaticAberration = false` | 33.3 | no effect |
| switch thumb samples parent backdrop only (no track layer) | 37.0 | no effect |
| **switch thumb `drawBackdrop` removed entirely** | 34.1 | **no effect** |
| sheet blur radius -> 2px | 33.0 | no effect |
| glass drawn by a content-free sibling, content translated separately | 33.5 | no effect |

The third row is the one that settles it. Deleting the glass from every switch thumb — not tuning it,
removing it — changes nothing. **The controls are not the cost**, and the "9 controls x 3.7ms"
arithmetic above is an artefact of inferring a per-control constant from a global toggle that
disables glass on everything at once.

The last row also rules out the obvious structural fix. Moving the sheet's glass out of the content's
ancestry, so that scrolling cannot invalidate it, changes nothing: the glass layer re-records every
frame regardless of whether the scrolling content is inside it.

## What the cost is

| build | total p50 | RT issue->swap p50 |
|---|---|---|
| baseline | 108.2 | 32.5 |
| `exportedBackdrop = null` | 99.4 | 27.6 |
| **sheet surface `glassEnabled = false`** | **13.0** | **1.77** |

Turning off the sheet's own `drawBackdrop` takes RenderThread from 32ms to 1.77ms and the frame from
108ms to 13ms, with frame production going from 83 to 118 in the same window — saturated.

So the split is roughly:

- the sheet's own backdrop layer: **~27ms**
- the `exportedBackdrop` second record of it: **~6ms**
- everything else in the sheet, nine glass switches included: **~2ms**

This directly contradicts "Not the sheet's own glass area" above. That section's evidence was that
growing the sheet's area 1.8x moved the cost only 7%, and the inference — that a cost dominated by
the sheet's blur would have scaled with area — does not follow. A large **fixed** per-frame term does
not scale with area either. The doc had already identified the mechanism and then set it aside: the
decompiled `DrawBackdropNode` re-records its effect layer on every draw via `drawBackdropLayer`, at
`CompositingStrategy.Offscreen`, and does it a second time when `exportedBackdrop != null`. That is
the cost, and the 6ms recovered by dropping the export is the second record being skipped.

Tuning the effects does not help because the effects are not the expensive part — the per-frame
record and rasterize of the layer is, whatever is drawn into it.

## Where this points

The background behind an open sheet is already frozen — that is what the modal freeze does, and why
an idle sheet renders zero frames. So the sheet's refraction input is **constant** for the whole life
of the presentation, and yet the layer is re-rasterized on every frame that anything else moves. The
shape of the fix is the modal freeze one level down: rasterize the sheet's glass once and reuse it
while its inputs are unchanged, re-rendering only when the backdrop or the sheet's geometry actually
changes.

Two smaller things fall out on the way:

- **`exportedBackdrop` is unconditional.** It is only needed when a control inside the sheet samples
  the sheet's own surface. Making it conditional on that is worth ~6ms of the ~33ms on any sheet
  whose contents do not need it.
- **`GlassEffectRuntime.reducedProgress` is dead code in the app.** Every page provides a
  `GlassEffectRuntime()` with the default `reducedProgress = 0f`, so `blurScaleFor`/`lensScaleFor`
  always return 1.0 and the whole quality-reduction path is inert outside the debug catalogue. Given
  that blur radius measured as free, driving it is unlikely to pay — recorded so nobody spends a day
  wiring it up expecting a win.

## Not yet verified on hardware

Every number here is from the emulator, where 27ms of *RenderThread CPU* for one offscreen layer is
suspicious on its face and may be an artefact of the AVD's rendering path. The user reports the
problem on real devices, so the next measurement should be the same A/B on hardware before any fix is
designed around these proportions.

---

# The fix, and where else it applies

## What the cost actually decomposes into

Measured on the API 37 AVD, release builds, scrolling. Keep the sheet's own glass, disable glass on
its content only:

| GitHub strategy sheet | total p50 | RT p50 | sync p50 |
|---|---|---|---|
| baseline | 132.9 | 38.6 | 9.27 |
| content glass off | 59.4 | 15.9 | **0.29** |

So a long sheet is **surface term + one offscreen layer per composed glass element**, and `sync` —
layer upload — is the tell: 9.27ms against 0.29ms is dozens of layers going to the GPU every frame.
Short sheets are surface-bound and long sheets are content-bound, which is why the earlier
experiments on Home's nine switch thumbs found nothing: nine 40x24dp thumbs inside one
always-visible card is a content term of about zero.

## `Modifier.cullWhenFullyClipped`

Compose clips a scrolled-away child and then draws it anyway. For a glass surface that is a layer
recorded, rasterized and uploaded for pixels that never reach the screen. Skipping the draw of a
fully clipped element is visually identical by construction.

It lives on `AppSurfaceCard`, which is the shared base for `AppFeatureCard` (47 sites),
`AppOverviewCard` (9), `SheetSectionCard`/`SheetSurfaceCard` (every sheet) and
`AppLiquidExpandableCardFrame` (9 accordion sites) — roughly 114 call sites from one modifier.

| | total p50 | RT p50 | frames |
|---|---|---|---|
| GitHub track editor, eager and untouched | 91.1 | 24.05 | 95 |
| + cull | **74.3** | **17.9** | **119** (saturated) |
| GitHub strategy sheet, lazified | 123.5 | 33.3 | 83 |
| + cull | **116.1** | **30.7** | 90 |

It culls on zero *clipped area* read from layout, never on position: a partly visible card reports
positive area and keeps drawing. The edge-stack pile was the case that could have broken — a card
receding under the top edge is scaled and blurred but still has area, so it keeps drawing. Verified
on the BA office page mid-scroll.

## Where the same trick does *not* pay

Recorded because each of these looks like an obvious next step and is not.

- **Direct `LiquidSurface` users.** Applied and A/B'd on the BGM track list, the app's longest run of
  them: 165.9 -> 166.6 total, RT 36.1 -> 39.0, sync 12.6 -> 13.6. No gain, slightly worse. Those rows
  are inside a `LazyColumn`, so laziness has already stopped composing them and there is nothing left
  to skip. Reverted. **Culling and laziness overlap; whichever comes first takes the win.**
- **Floating chrome and overlays** — `AppLiquidFloatingSurface`, toolbars, docks, `LiquidMenuSurface`,
  `LiquidToastSurface`, `LiquidModalSurface`. Always on screen, so the cull is a no-op.
- **Small controls** — `StatusPill` (171 sites), `AppSwitch`, `AppLiquidCheckbox`, sliders, search
  fields. They are descendants of cards, so they are already skipped whenever their card is, and
  their own layers are too small to matter: deleting the glass from all nine switch thumbs on a sheet
  measured as no change at all.
- **Migrating the other 34 eager sheets to `SheetContentLazyColumn`.** Worth much less now than it
  looked: the strategy sheet gained only 8% from culling *because* laziness had already taken that
  win, and the two overlap. Culling gets most of it without touching any sheet. Note also that
  automating the conversion failed twice on real Kotlin — `} else {` seams, and `if` used as an
  expression inside an argument list — so this route is manual and error-prone for little return.

## What is left

- **The sheet surface floor, ~16ms RT.** From the decomposition above, that is what remains after all
  content is dealt with. It is one `drawBackdrop` re-recording an offscreen layer every frame whose
  input — the page behind, already frozen by the modal freeze — has not changed. Tuning what it draws
  is free (chromatic aberration, blur radius and the thumb's track backdrop all measured as no-ops),
  so this needs caching at the backdrop-node level rather than anything in app code.
- **Glass on *visible* cards**, which culling cannot touch. The untried lever is the BA office trick:
  over the uniform middle of a sheet surface a blur returns the same field, so a card could composite
  a flat fill and land on the same pixels. Needs pixel verification before it is trusted.
- **The BGM track list is its own problem**: 166ms total, RT 36ms, sync 12.6ms, already lazy. Its cost
  is visible content — many rows each carrying glass and album art — not anything culling addresses.

---

# Scan: what is left across the app

Every surface below measured the same way — API 37 AVD, release build, six 90ms flings, native
window. Ranked by RenderThread, which is where every one of these frames is spent.

| surface | total p50 | RT p50 | sync p50 |
|---|---|---|---|
| BGM track list (guide catalog) | 166.6 | 36.1 | **12.6** |
| BA office page | 101.9 | 32.1 | 0.30 |
| GitHub strategy sheet | 116.1 | 30.7 | 6.2 |
| Settings | 74.4 | 22.3 | 0.21 |
| GitHub track editor sheet | 74.3 | 17.9 | 4.5 |
| MCP page | 30.9 | 4.0 | 0.16 |
| GitHub page | 16.0 | 3.4 | 0.17 |
| Home, idle | 16.5 | 3.3 | — |
| **BA office page, idle** | **0 frames** | — | — |

The GitHub page at RT 3.4 and MCP at 4.0 are done; nothing there is worth touching. BA renders
*nothing at all* when untouched, which remains the best idle behaviour in the app.

## `sync` says which lever applies

The scan turns up two different signatures, and the `sync` stage — layer upload — separates them.

- **High sync** (BGM 12.6, strategy sheet 6.2, track editor 4.5) means *many* composed glass layers
  going to the GPU each frame. That is what culling and laziness fix, and it is why the sheets
  responded to `cullWhenFullyClipped`.
- **Low sync with high RT** (BA 0.30 against RT 32.1; Settings 0.21 against RT 22.3) means *few but
  large* layers. Culling cannot help — the layers are on screen. Only shrinking the glass area, or
  flattening it where the field beneath is uniform, moves these.

Reading `sync` first tells you which of the two problems you have, before spending a build on the
wrong fix.

## Where the remaining headroom is

1. **BA office page, RT 32.1, low sync.** The worst page left, and the flattening already applied to
   its *nested* panels (`BaLiquidPanelUniformFillSourceTest`) does not cover the outer cards, which
   are still full-width live glass over a card-dense list. Same argument should extend to them:
   blurring a locally uniform field returns that field.
2. **BGM track list, RT 36.1, high sync 12.6.** Already lazy, so the cost is *visible* rows, each
   carrying its own `LiquidSurface` plus album art. High sync on an already-lazy list is the one
   place a per-row flatten would pay, since the rows sit on a uniform page background.
3. **Settings, RT 22.3, low sync.** Same shape as BA: switch rows inside large glass cards. The
   switches themselves are not the cost — that is measured, twice.
4. **The sheet surface floor, ~16ms RT.** Unchanged, and still needs caching at the backdrop-node
   level rather than anything reachable from app code.

## Not worth another look

- **`AppSwitch` and the small controls.** Deleting the glass from every switch thumb measured as no
  change, and Settings — the most switch-dense surface in the app — has a `sync` of 0.21, which is
  the signature of large layers rather than many. The per-control model is dead twice over.
- **`GlassEffectRuntime.reducedProgress`**, still inert app-wide, and blur radius measured free, so
  driving it would buy nothing.

---

# Measuring an A/B without overwriting the app

Frame-time work is two builds differing in one thing. Overwriting `os.kei` to compare them is worse
than it sounds: the device only ever holds one of the pair, going back means rebuilding, and a
diagnostic with the glass switched off can be left sitting on the device looking exactly like a
shipped regression. That happened.

`:app:installReleaseDiagnostic` installs `os.kei.diag` **beside** `os.kei`, labelled "KeiOS diag" on
the launcher. It is `initWith(release)` — R8 and all — because a diagnostic that optimises
differently measures a different app.

Two things had to be fixed before the pair agreed, and both are worth knowing because either one
silently biases every number:

1. **The diagnostic shipped without a baseline profile.** It looked for
   `src/releaseDiagnostic/generated/baselineProfiles`, which does not exist, so it was unprofiled
   against a profiled release. Measured the BA page ~9ms of RenderThread slower from identical
   source. Now wired to the same `src/release/generated/baselineProfiles`.
2. **A fresh install has empty storage.** On a data-driven page that is not the same screen — BA with
   no account configured renders a different card list. `scripts/perf/clone_app_data.sh` copies one
   build's data onto the other (needs root, so emulator only) and fixes ownership and SELinux labels
   after the copy.

With both in place the two builds agree from identical source: BA RT 39.99 against 39.48, total
132.73 against 129.49.

**Absolute numbers drift between sessions** — the same BA journey measured 32.1ms of RenderThread
earlier in the day and 39-40ms after two more apps were installed on the AVD. Only A/B pairs measured
back to back are worth comparing, which is the other reason to have both builds resident at once.

---

# BA and BGM: what the levers are actually worth

Both measured with the `os.kei` / `os.kei.diag` pair, cloned data, back to back.

## The ceilings

| | RT p50, as shipped | RT p50, control glass off |
|---|---|---|
| BA office page | 32.1 | 5.5 |
| BGM track list | 36.1 | 10.1 |

On both, the glass *is* the cost. Unlike the sheets, none of it is being spent on invisible pixels —
these are the surfaces the user is looking at — so `cullWhenFullyClipped` has nothing to take.

## BA decomposes into the pile and the cards

| BA office page | RT p50 |
|---|---|
| as shipped | 32.1 |
| edge-stack pile disabled | 16.3 |
| all control glass disabled | 5.5 |

So roughly **16ms pile, 10ms card glass, 5.5ms floor**. The pile's half is not waste: it is the cost
of *showing* two or three extra receding glass cards that would otherwise be off screen. Turning it
off is a feature change, not an optimisation.

## BGM: two hypotheses tested, one paid a little

`BaGuideBgmTrackList` chunks tracks 18 to a lazy item, each chunk a single `LiquidSurface` about
3000px tall — taller than the screen, so laziness can never cull one.

| BGM track list | RT p50 | sync p50 |
|---|---|---|
| as shipped | 31.3 - 35.2 | 13.1 - 13.4 |
| `chromaticAberration = false` on the chunk surface | **30.8** | 12.8 |
| `BGM_TRACK_CHUNK_SIZE` 18 -> 6 | 37.9 | 13.3 |

- **Chromatic aberration is not free at this size.** On a 40x24dp switch thumb it measured as
  nothing; on a surface the size of the screen it is worth ~12% of RenderThread. The earlier "not the
  cost" finding was true only for the thing it was measured on. It remains an appearance change —
  the fringe is the effect — so it is offered, not taken.
- **Chunk size is not the lever.** Cutting it to 6 made things slightly worse and moved `sync` by
  0.01ms, so the oversized layer is not what the upload stage is spending 13ms on. Whatever drives
  that 13ms is still unidentified, and it is the single largest unexplained number left in the app.

## Where that leaves both

No appearance-neutral lever remains for either surface in app code. Every remaining option — dropping
aberration, thinning the pile, fewer glass rows — trades the material for frames, which is the wrong
trade for this app. The one path that keeps the appearance exactly is caching the rasterized glass so
a layer whose inputs have not changed is not re-recorded, which lives in the backdrop node rather
than in our components.

---

# kyant backdrop 2.0.1 / shapes 1.2.1

Both are packaging-only releases. Verified rather than assumed:

- `backdrop-android` **sources are byte-identical** between 2.0.0 and 2.0.1, common sources too.
- The compiled public API is unchanged — 84 members across `DrawBackdropModifierKt`,
  `DrawBackdropNode` and `LayerBackdropKt`, `diff` clean.
- 63 class files differ only through recompilation; the one real change is
  `META-INF/backdrop.kotlin_module` becoming `META-INF/Glass_backdrop.kotlin_module`, so the Gradle
  module was renamed upstream. No `glass` artifact is published yet, so this looks like groundwork.
- `shapes` 1.2.0 -> 1.2.1 sources are byte-identical as well.

A/B'd anyway with the `os.kei` / `os.kei.diag` pair on the BA page: RT 38.65 against 38.73. No
regression, no gain. Taken to stay current, not for performance.

## What the docs MCP settles

- **There is no cache/freeze API.** Nothing in the documentation exposes a way to stop `drawBackdrop`
  re-recording its offscreen layer when the source and geometry are unchanged. The ~16ms sheet floor
  is therefore not reachable from app code with the shipped API, which confirms the conclusion above
  rather than replacing it.
- **`exportedBackdrop` is required for our sheets, not optional.** The documented purpose is exactly
  the "a child samples the parent surface" case, which is what every sheet card does. The ~6ms it
  costs buys the cards their glass, so dropping it is the appearance trade the pixel diff already
  showed.
- **Invalidation timing is undocumented.** The docs describe the dataflow and say nothing about when
  a `drawBackdrop` redraws or whether effects re-run per frame, so the per-frame re-record stays an
  observed behaviour rather than a specified one.
- **`rememberCanvasBackdrop` is coordinates-independent**, unlike `rememberLayerBackdrop`. It is the
  one primitive this work has not tried, and it is interesting precisely where the sampled field is
  uniform: a coordinate-independent source cannot need re-sampling as the element scrolls. Untested.

One incidental difference worth noting: the official Glass Slider tutorial has the thumb sample
`backdrop = trackBackdrop` alone, where `AppSwitch` samples `rememberCombinedBackdrop(backdrop,
trackBackdrop)`. Measured as free either way, so this is a note rather than a finding.

## `rememberCanvasBackdrop` was already the answer, and it is already applied

Not a new API — it exists in 2.0.0 and `MainPageBackdropSet` has used it all along, as
`contentMaterial`: `rememberCanvasBackdrop { drawRect(cardMaterialColor) }`, with the reasoning and
the measurement recorded there. Composing the scene under a page's cards instead "took the 1% low
from 13 fps to 6". BA, MCP, GitHub and OS all pass `backdrops.contentMaterial` to their content,
which is why MCP sits at RT 4.0 and GitHub at 3.4.

The guide catalog does not: `rememberBaGuideCatalogSceneBackdrop()` is a plain `rememberLayerBackdrop`,
so the BGM track chunks sample a live screen-sized scene layer rather than a flat material. That
looked like the missing application of an established in-house pattern.

Tested, and it is not:

| BGM track list | total p50 | RT p50 | sync p50 |
|---|---|---|---|
| as shipped (live scene layer) | 132.9 | 36.3 | 10.1 |
| chunk samples a canvas material instead | 149.4 | 34.5 | 13.3 |

No gain — worse on total and on sync. The source a `drawBackdrop` samples is cheap either way; what
costs is rasterizing its own effect layer, and swapping a live layer for a flat colour does not
change the size of that layer. Consistent with every other negative result here: blur radius, lens
mode, chunk size and backdrop source are all free, and only the existence and area of the layer
matter.

That is now three tested hypotheses for the BGM list — aberration (~12%, appearance cost), chunk size
(nothing), backdrop source (nothing) — and its 13ms `sync` is still unexplained.

---

# StatusPill is 29% of the BA page, and the "small controls are free" finding was wrong

Measured with the `os.kei` / `os.kei.diag` pair, cloned data, BA office page scroll:

| BA office page | total p50 | RT p50 |
|---|---|---|
| as shipped | 141.0 | 42.0 |
| every `StatusPill` on its non-glass path | **99.6** | **29.8** |

**~12ms of RenderThread, 29% of the page.** Every pill with a backdrop becomes a `StatusPillLiquid`,
which is a `LiquidSurface`, which is one offscreen layer — and BA shows a dozen or more at once.

This contradicts the earlier conclusion, recorded twice above, that the small controls are free. That
was measured on nine switch thumbs on one sheet and on Settings' `sync` figure, and it generalised to
"small controls" when what it actually supported was "nine switch thumbs". `StatusPill` has 171 call
sites; a pill-dense page is a different question from a switch-dense one, and nobody had asked it.

## The static path is not the fix

`StatusPill` already has a non-glass branch, and it is what the measurement above used. It is not
visually equivalent: pixel-diffed against the glass build on the same screen, **1.73% of sampled
pixels differ by more than 3%, max delta 741 of 765**. `fallbackOptics` is a different look by
design, not a cheaper rendering of the same look, so this branch is not available as an optimisation.

## What is available

The BA office-card treatment, applied to pills. A pill sits on a card whose surface is a flat fill —
BA cards sample the `contentMaterial` canvas backdrop — so the field beneath a pill is uniform, and
blurring or lensing a uniform field returns that field. A pill could composite its colour over the
card material and draw a plain squircle, landing on the same pixels without an offscreen layer.

Two things make this more promising than the earlier flattening work:

- **Pills are never interactive.** `StatusPillLiquid` passes `isInteractive = false`, so the press
  deformation that forced `BaLiquidPanelUniformFillSourceTest` to gate flattening on "no gesture"
  does not apply here at all.
- **It generalises.** 171 call sites, and the pill-dense surfaces are exactly the expensive ones —
  BA, Settings, the GitHub tracked cards, the release page.

It has to be pixel-verified against the glass build before it ships, to a much tighter bound than the
static path manages — the target is "no visible difference", not "close enough".
