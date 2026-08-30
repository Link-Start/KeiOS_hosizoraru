# Why scrolling a Liquid sheet is not smooth

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
