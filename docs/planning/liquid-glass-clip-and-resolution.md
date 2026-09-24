# Liquid glass: where the clip happens, and at what resolution the chain runs

## Current state — 2026-09-24

Ideas taken from miuix (the sample app, and Mishka, which is built on miuix-blur and miuix-squircle),
implemented on top of kyant backdrop 2.0.1 and measured on the phone. Nothing about the material, animation
or effect parameters changes.

- **Shipped: flat-field glass cuts its shape inside its own layer** (`FlatLiquidBackdrop.kt`). `LiquidSurface`
  and the glass controls (`AppLiquidButtons`, `AppLiquidBadges`, `AppLiquidCheckbox`, `AppLiquidSearchField`)
  over a flat field draw through `drawFlatLiquidBackdrop` instead of the library's `drawBackdrop`, outer shadow
  included. Section 1.
- **Shipped: scroll-swapped chrome stays composed** (`KeepComposedUnplaced.kt`). The bottom bar and the
  floating docks keep both their full and compact forms composed and leave the hidden one unplaced. Section 4.
- **Rejected: running the glass chain on a reduced copy** (miuix-blur's idea). Section 2.

Phone `5eea1f50`, `os.kei.diag`, no mirroring, both paths in one build, interleaved, four passes of a fixed
scroll journey each:

| page | RenderThread p50, library → shipped | total p50 | GPU p50 |
|---|---|---|---|
| OS | 4.28 → **3.58** (−16%) | 10.79 → 10.38 | 4.92 → 5.33 |
| MCP | 4.22 → **3.46** (−18%) | 10.72 → 10.30 | 5.18 → 5.50 |
| GitHub | 4.26 → 4.02 (−6%) | 9.33 → 9.09 | 4.20 → 4.19 |
| BA office | 4.53 → 4.32 (−5%) | 13.11 → 13.02 | 4.85 → 4.92 |

Pass ranges do not overlap for OS and MCP RenderThread. GPU rises by ~0.3-0.4ms on OS and MCP (the cut
now happens inside the layer when it renders) and the total still falls. An earlier run with `scrcpy`
mirroring put BA at −17%: mirroring exaggerated the BA figure, which is why that run is not the headline.

The swap frame at the start of each scroll, eight interleaved passes (section 4). The worst UI-thread frame
(animation + layout + record draw) is what keeping the chrome composed removes; at 120Hz the budget is 8.3ms:

| page | worst UI-thread frame per pass, before | chrome kept composed | framestats frames over 33ms |
|---|---|---|---|
| BA office | 13.1-23.4ms | **6.2-9.6ms** | 37 → 20 |
| OS | 11.7-23.8ms | **9.0-12.7ms** | 14 → 7 |

The last column is framestats' `total` (intended vsync to GPU done), which is latency, not a dropped frame.
Frames the display actually held for 33ms or more are one to three per pass in both arms, with the bar pinned
too, and none at all in a later session: section 4 has the reading.

## 1. The clip was a CPU mask on every scroll frame

`atrace` of the BA office scrolling: **18 texture uploads a frame**, sized exactly like the cards (1128×163,
1128×234, 1128×560, …), in the main frame's `flush commands`, not in `flush layers`. `simpleperf` cannot attach
to a release build, so the source was found by elimination (throwaway builds):

| experiment | uploads per frame |
|---|---|
| baseline | 18.5 |
| outer shadows removed | unchanged |
| highlight removed | unchanged |
| the same corner radius as a plain rounded rectangle (visibly different, never shipped) | **5** |
| the whole surface held in one extra offscreen layer | **6.7** |

The library places every glass surface with `placeWithLayer { clip = true; shape = …; compositingStrategy =
Offscreen }` (2.0.1 bytecode). HWUI applies a layer's outline clip when it composites the layer, in screen
space. The app's continuous-corner shapes produce a generic path; an anti-aliased path clip is a software mask
here, and a mask in screen space is invalid the moment the card moves, so every scroll frame rasterised each
card's mask on the RenderThread and uploaded it. A plain rounded rectangle is clipped analytically, which is
why that bound removed the uploads — and also why it could only be a bound.

The fix keeps the path and moves the cut. `drawFlatLiquidBackdrop` places the surface in an offscreen layer
without an outline clip, draws the flat field, the surface colour and the content, and then clears everything
outside the outline, inside the layer. The mask is rendered with the layer, in the layer's own space, and
reused while the layer is unchanged, so scrolling a static card no longer touches it. This is miuix-squircle's
approach (`squircleShaderMask` composites a squircle surface "through one Offscreen layer (cheap to cache and
re-blit while scrolling)"), with the library's own outline instead of miuix's baked squircle SDF, so the
silhouette is unchanged.

Two details decide whether it is pixel-equivalent:

- **Clear outside after drawing; do not clip each draw.** The library clips the finished layer once, so a rim
  pixel keeps `coverage × (field, surface and content composited)`. Clipping each draw compounds the coverage
  on translucent layers (16/255 on the rim in Robolectric). Clearing outside the outline after everything is
  drawn applies it once: Robolectric then matches the library to 1/255 (`FlatLiquidBackdropTest`).
- **The highlight and inner shadow stay outside the layer.** The library draws them from nodes outside its
  placement layer, and the highlight blends `Plus` against what is below. Wrapping the library's whole chain in
  a layer (the 6.7-upload experiment) made it blend against a transparent rim: up to 125/765. So
  `drawFlatLiquidBackdrop` reproduces `HighlightNode` and `InnerShadowNode` from the bytecode with their
  order, layers, blend modes and clips. The library's highlight-shader API is closed to other modules
  (`createShader` is internal, `RuntimeShaderCache` sealed), so `HighlightStyle.Default`'s shader and uniforms
  are reproduced too — including that the library hands it a shape wrapper, never a `CornerBasedShape`, so
  every corner radius is half the short side. Any other highlight style is refused rather than approximated.

Pixels on the A17 AVD, the library's path against the shipped one at the same state (OS, BA, BA scrolled with
the pile formed, GitHub, MCP, Home, an open sheet; light and dark): at most 20/765 in light and 37/765 in dark,
all of it on the anti-aliased rim, where the coverage is now rounded into the layer before compositing. The one
exception above 3% is 129 pixels on the one-pixel right edge of a receding pile card in dark: the pile scales
the card, and its rim is now resampled with the layer instead of cut after scaling.

The outer shadow is reproduced too (`ShadowNode`: the outline blurred in an offscreen layer grown by twice the
radius, the outline cleared out of it, drawn under the content, outside the surface's layer so the layer does
not cut it), and so is `HighlightStyle.Ambient`, which the badges use. With both, the glass controls over a
flat field take the same path. Pixels on the AVD against the library's path (OS, BA, BA scrolled, GitHub,
GitHub scrolled, MCP, Settings, Home; light and dark): at most 3/765 on every page except BA, where the pile's
rims reach 20/765 (light) and 37/765 (dark); Robolectric matches the library to 2/255 with a shadow and an
ambient rim (`FlatLiquidBackdropTest`).

Not for a surface that exports a backdrop to its content, or with a highlight style other than `Default` and
`Ambient` (refused rather than approximated); those keep the library's path.

## 2. The chain on a reduced copy — measured and rejected

miuix-blur's `drawBackdrop` records its source at 1/2…1/16 by blur radius (`downScaleExpFor`: σ² ≥ 12.6
halves, ≥ 90.25 quarters). The obstacle in `backdrop-reduced-resolution.md` — `LayerBackdrop.drawBackdrop` has
no scale term — applies to reducing the *producer*. Reducing the *consumer* needs none: wrap the source's draw
in `scale(1/f)`, which scales the library's translation with everything else, run the chain on the reduced
layer with every pixel-space uniform divided by `f`, and draw it back at `scale(f)`. It was built that way,
with the library's chain reproduced from the bytecode (colour matrix, platform blur with the sigma preserved in
screen pixels, both refraction shaders, the scope's padding rules), and handed to `drawBackdrop` as a backdrop
with empty effects.

It was pixel-equivalent — at most 3/765 at half resolution, 11/765 at a quarter (the 8dp Home cards) — and it
did not pay:

| where | phone result |
|---|---|
| bottom bar, toolbar, Home card batch, dock | neutral (Home scroll 32 → 33 frames over 16.7ms, OS 38 → 40) |
| small controls (buttons, switches, checkbox, badges, slider, progress, search field) | no gain: Skia already reduces a wide blur internally, and the pass overhead dominates at this size |
| a sheet, its scrim, dialogs, menus, toasts | worse while dragging (71 → 196 frames over 16.7ms with the sheet reduced) |
| `LiquidSurface` over a live source (cards inside a dragged sheet) | worse (84 → 161) |

Earlier readings that looked like wins (Home RenderThread −20%) were taken together with the stable-outline
experiment below, which was a bug. On this GPU the frame cost follows layer and pass count and CPU masks, not
fill; fewer pixels in the same number of passes buys nothing. The implementation is in
`.planning/liquid-glass-rework-harness.patch` if a slower GPU ever makes fill matter.

## 3. Also measured and rejected

- **A stable outline object.** Returning one `Shape` whose inner shape changes looked like a win on Home idle
  (RenderThread −8%) and was a bug: the library's `ShapeProvider` rebuilds the outline only when the shape
  object changes, so it kept the first frame's outline, and the Home card batch — whose shape follows the
  cards' measured positions — clipped and highlighted a stale one (394/765). It drew less, which is why it
  measured faster. It also hid a second trap: the library's `lens()` reads corner radii from the shape's type,
  so any wrapper silently removes the refraction.
- **Removing outer shadows** (bound): no effect on the uploads, GPU −0.2ms. Shadows are cheap.
- **Switching to miuix-blur.** Its Gaussian, its highlight and its lack of shadows would change the material,
  and 228 files depend on kyant's types. Its ideas were taken and measured instead.

## 4. The long frame at the start of a scroll

With mirroring off, frames over 33ms were still there: 12-26 per 476-frame BA journey, always at frames 18-28,
the moment the first swipe of a pass got going. The shape was one frame with 5-13ms of `animation`
(composition) and 6-14ms of `record draw`, followed by three to six frames that were only late. Bisected with
throwaway flags, each an interleaved four-pass A/B:

| experiment | BA frames over 33ms | OS |
|---|---|---|
| items pre-warmed with one swipe before measuring | 12 → 18 | 7 → 8 |
| the bottom bar never hides (bound, not shippable) | 12 → **0** | 6 → **0** |
| the bar hides but pages are told it did not | 18 → 6 | 10 → 6 |
| pages read the bar's visibility through a provider, so the page does not recompose | 17 → 19 | 9 → 6 |
| the docks never go compact | 16 → 14 | 9 → 4 |

So it is not first composition of list items and not the page's recomposition: it is the chrome that swaps
form when the bar hides. `AnimatedCompactBottomBar` and both floating docks composed their compact form when
the bar left and their full form again when it came back — the full bottom bar is three `drawBackdrop` layers
and a row of tabs — and composing and first-drawing glass on that frame was the long frame.
`keepComposedUnplaced` keeps both forms composed and lays the hidden one out at zero size, unplaced, with its
semantics cleared. Unplaced alone was not enough: `uiautomator` still listed the hidden compact button, with a
stale position, and `KeepComposedUnplacedTest` fails without the semantics clearing. Pixels at rest and scrolled
on OS, MCP and BA: identical. Result in the table above.

### A regression it shipped with, found by the baseline profile (2026-09-25)

The release capture lost 465 `ui/page/main/ba` rules, nearly all of them the calendar-and-pool page and the
daily-done sheet. Both are opened from BA's floating dock, and `uiautomator` no longer listed the dock's first
action (`ba_dock_open_calendar_pool`) at all, collapsed or expanded: the journey's optional step found nothing
to tap. With the dock file from before `keepComposedUnplaced`, all three actions were listed.

The cause was where the modifier went. In `AppFloatingVerticalActionDock` it was handed to the compact button's
`modifier` parameter, and `AppFloatingLiquidActionButton` applies that modifier inside a `TooltipBox`. So the
TooltipBox wrapper stayed placed while the button was hidden, with its long-click semantics, at the dock's top
edge. Android's accessibility tree drops a node that a later sibling covers, so the first action disappeared for
TalkBack as well as for `uiautomator`. Both forms are now wrapped in a Box that carries `keepComposedUnplaced`,
the KDoc says to put it on a node that owns the whole form, and
`AppFloatingVerticalActionDockSemanticsTest` checks the merged tree: three long-click nodes expanded, one
collapsed. It fails on the previous version with 4. The other callers already put the modifier on a Box they
own. The dock region is pixel-identical before and after the fix.

### What the 33ms count measured, re-read the same day

framestats `total` runs from the intended vsync to GPU completion. It counts a frame that waited behind a busy
queue as long even when the display showed every refresh, and `dumpsys gfxinfo` keeps only the last ~120
frames, so each pass above is the final swipe and its fling, about one second. Counting instead the frames the
display held for 33ms or more (consecutive `DisplayPresentTime` more than 33ms apart; a 16.6ms gap is not a
miss, the LTPO panel drops to 60Hz on its own):

| run | present gaps ≥ 33ms per pass, before → after |
|---|---|
| chrome kept composed, BA, 8 passes | 2,1,3,2,1,2,2,2 → 1,1,2,1,1,1,1,2 |
| chrome kept composed, OS, 8 passes | 3,3,3,2,3,3,3,2 → 2,2,2,2,3,3,3,3 |
| bottom bar never hides (bound), BA | 2,2,1,2 → 1,2,2,3 |
| same build, later session, BA | 0,0,0,0 (with or without `screenrecord` loading the GPU) |

So the visible hitches do not follow the bar: pinning it removes every framestats frame over 33ms and none of
the present gaps. They fall in the 60→120Hz switch at touch (an `atrace` shows `finalizeDisplayModeChange`
~100ms after the swipe starts, and the gaps sit 100-290ms in), with GPU time per frame 2-3x its steady value
for about ten frames. System Settings shows none under the same swipe, but it draws almost nothing. The GPU
clock could not be read (`kgsl` clock nodes deny shell), so a governor ramp is a likely reading, not a measured
one. What `keepComposedUnplaced` does remove is the UI-thread spike of the swap frame, table above; that is
kept because it is pixel-identical and removes real work, not because it removed visible hitches.

The provider plumbing measured no change and was not kept; composition itself is cheap here, as the
frame-performance skill says, and it was composing *glass* that cost.

## Remaining

- Visible hitches at scroll start (one to three per pass, sometimes none) coincide with the panel's 60→120Hz
  switch and do not change when the bar is pinned; nothing app-side is attributed to them yet. Measure them
  with present gaps, not framestats `total`.
- An untouched sheet now draws no frames at all (the Home drift pauses under it); its cost is while it moves.
- `AppOverviewPillBatch` keeps the library's path: its fills are positioned by the effect scope's padding.

## Reproducing

`.planning/liquid-glass-rework-harness.patch` holds the reduced-resolution experiment tree (flags `flat_kyant`,
`ds_kyant`, `ds_off`, `bg_freeze` and the bounds, read from `getExternalFilesDir("flags")`); the chrome flags
(`bar_pin`, `page_blind`, `old_bar`, `dock_static`, `dock_recompose`, `bar_recompose`) were one-line switches at
the sites named in section 4. Scripts in
`.planning/flat-field-tools/`: `ab4p.sh` (interleaved phone scroll A/B), `abdwell.sh` (idle), `capr.sh` +
`pdiff.py` (pixels), `tails.py`, `hitch.py` (present gaps of 33ms or more per pass), `dvfs.sh` (the same pass with
`screenrecord` loading the GPU). Uploads: `atrace --async_start -b 32000 gfx view sched`, then count `Texture
upload` slices per `renderFrame` for the app's RenderThread.
