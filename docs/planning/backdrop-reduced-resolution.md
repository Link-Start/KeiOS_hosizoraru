# Reduced-resolution Liquid Glass: feasibility

> **2026-09-24: built on the consumer side and rejected** — see `liquid-glass-clip-and-resolution.md`. The
> blocker below is real for reducing the *producer*; wrapping a consumer's draw of the backdrop in `scale(1/f)`
> needs no scale term. The `lens()` risk raised here measured small (11/765 at a quarter), and the frame time
> did not move on the phone: the cost follows pass count, not fill.

Whether the full-screen backdrop can be captured and sampled at a fraction of screen resolution to
cut GPU fill, given `io.github.kyant0:backdrop-android:2.0.0`. Analysis only — nothing implemented.

Sources: the Backdrop documentation (via its MCP) and the shipped AAR, decompiled, because the
documentation does not describe the capture path at all.

## The idea, and why it is reasonable

A blur discards the high-frequency detail that a full-resolution capture pays for. Capturing at
0.5× and upscaling should therefore be near-invisible while cutting the captured pixel count 4×.
This is not speculative in general — KeiOS already does exactly this for its own background shader,
which renders at `DYNAMIC_BACKGROUND_RENDER_SCALE = 0.25f` and upscales through a `graphicsLayer`.

## What the library supports

**Nothing, at the documented level.** Neither `rememberLayerBackdrop` nor `Modifier.layerBackdrop`
takes a scale or resolution parameter, and the docs describe `LayerBackdrop` only as
"coordinates-dependent". The FAQ and every tutorial are silent on capture resolution.

The AAR is more informative than the docs, and splits the answer in two.

### The recording half is already possible

```
LayerRecorderKt.recordLayer(node, drawScope, graphicsLayer, size: IntSize, block)
```

takes the recorded size as an **explicit parameter**; the overload `LayerBackdropNode.draw` calls
just defaults it to `drawScope.size.toIntSize()`. The function is genuinely public — its
`-TdoYBX4` suffix is Kotlin value-class mangling for the `IntSize` parameter, not internal-visibility
mangling, which in this AAR looks like `getOnDraw$backdrop()`. `LayerBackdrop`'s constructor is
public and takes the `GraphicsLayer`, and `getGraphicsLayer()` is public too.

So a downscaled recording could be produced today with a hand-rolled producer modifier.

### The sampling half blocks it

`LayerBackdrop.drawBackdrop` does, in order:

1. `inverseLayerScope.inverseTransform(transform, density, layerBlock)` — undo the consumer's own
   transform so the backdrop does not travel with the glass element
2. `translate(dx, dy)` where the offset is
   `consumer.positionInWindow() - layerCoordinates.positionInWindow()`
3. `GraphicsLayerKt.drawLayer(graphicsLayer)`

**There is no scale term anywhere in that path.** A layer recorded at 0.5× would be drawn at half
size, and the translation — computed in full-resolution layout coordinates — would put it in the
wrong place. The result is not "slightly soft", it is visibly broken.

`layerBlock` is not a way out. It feeds `inverseTransform`, so whatever it declares is applied
*inversely* to the backdrop and *directly* to the glass content — that is its whole purpose, per
the Interactive Glass Bottom Bar tutorial, where moving a scale into `layerBlock` is what stops the
backdrop scaling with a pressed button. Declaring `scaleX = 0.5f` there to upscale the backdrop
would shrink the element to half size.

## The cost is not where the name suggests

Worth correcting before anyone sizes the win: `blur()` appends an
`androidx.compose.ui.graphics.BlurEffect` to the **consumer's** render-effect chain and grows its
`padding`. The blur therefore runs over each glass element's own bounds — a bottom bar, a card —
not over the screen. Blur cost scales with the glass surfaces' area, which is already small.

What is genuinely full-screen is the **capture**: `recordLayer` replaying the whole page into a
GraphicsLayer on every invalidation. That is the `flush layers` stage measured at 674ms of a
1346ms `DrawFrame` total in `route-transition-frame-cost.md`.

So reduced resolution would attack the capture, and only indirectly the blur. That is still the
right target — but it also caps the payoff, because **downscaling shrinks rasterization, not the
draw-command walk.** Home idle costs 8.62ms of RenderThread CPU and 11.08ms of GPU
(`hwui-frame-budget.md`); a 0.5× capture can only work on the second number.

## The visual risk is `lens()`, not `blur()`

KeiOS uses `lens(` 34 times, `blur(` 30 and `vibrancy()` 19 — refraction is the *most* used effect,
not the least. Blur is forgiving of a downscaled source by construction. Refraction is not: it
samples the backdrop with displacement and magnifies detail at element edges, which is exactly
where an upscaled capture shows its softness. `vibrancy()` is a colour filter and is indifferent.

So the honest form of "appearance-neutral" here is *conditional*: safe in proportion to blur
radius, risky wherever `lens` does the work. It could not be turned on globally without looking at
the lens surfaces specifically.

## What upstream would need to change

The clean fix is small and needs no new API. `drawBackdrop` already holds both the recorded
`graphicsLayer` and `layerCoordinates`; it could derive

```
scale = layerCoordinates.size / graphicsLayer.size
```

and apply it alongside the existing translate. A full-size recording gives `scale == 1` and behaves
exactly as today, so it is backward compatible, and a downscaled recording then "just works".

The heavier alternative is an explicit `resolutionScale` on `rememberLayerBackdrop` that drives
both the recording size and the compensating scale.

## Recommendation

**Not feasible on 2.0.0 without an upstream change.** Half the mechanism is already public and the
other half has no seam — and the seam that exists (`layerBlock`) is load-bearing for something
else.

Before asking upstream for it, the payoff should be bounded locally, because it is currently
unknown: the capture's share of Home's 11.08ms GPU has not been measured separately from the glass
surfaces' own draw. A cheap way to get that number without touching the library is to compare Home
idle against a build where the full-screen `LayerBackdrop` producer is disabled outright — visually
broken, but it isolates what the capture costs, and it is a measurement, not a candidate fix.

If that number is small, this whole direction is not worth an upstream conversation.

## Measured: the number is zero

Run on the API 37 AVD, which turns out to be representative for this: it holds 120Hz and its Home
idle GPU (11.1-11.6ms) lands within half a millisecond of the physical device's 11.08ms.

Two builds, identical but for `MainPagerLayout`'s producer — `Modifier.layerBackdrop(coordinator
.backdrop)` versus a bare `Modifier`. With it removed the glass has nothing to sample and visibly
goes flat, which is the point: this is not a candidate fix, it is an upper bound. Whatever reduced
resolution could ever save is a *fraction* of what deleting the capture outright saves.

Twelve 3-second Home idle samples per variant, pooled in two groups each:

| | frames / 3s | total p50 | rt_cpu p50 | gpu p50 |
|---|---|---|---|---|
| capture on | 119 / 119 | 16.29 / 16.14 | 3.68 / 4.13 | 11.59 / 11.09 |
| capture off | 119 / 119 | 16.15 / 16.14 | 3.10 / 3.21 | 12.27 / 11.98 |

**Frame time does not move.** 16.14ms against 16.14ms, at an identical 119 frames per three
seconds. The capture does cost about 0.75ms of RenderThread CPU — and removing it *raises* GPU by
about the same, so the two cancel. Plausibly the captured layer is also serving as a cache that
several glass surfaces sample once instead of the page being re-rasterized behind each of them.

So the upper bound on reduced-resolution backdrop capture is **zero**, and a 0.5x capture would be
some fraction of zero. The direction is dead. Nothing to ask upstream for, and the small
`drawBackdrop` change sketched above is not worth proposing on this evidence.

What this also settles: Home's idle cost is not the capture. It is the per-surface effect chains
(`blur` + `lens` + `vibrancy` over each glass element's own bounds) plus the background shader —
work that happens wherever the backdrop pixels come from. Reducing *that* means reducing the
material, which is the line this work is not allowed to cross.
