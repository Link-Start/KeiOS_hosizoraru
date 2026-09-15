# Why expanding a card costs so much

Reported as "the History page's HWUI gets much worse when a card is expanded", then again as "every
page that supports stacked cards degrades a little when a card is expanded". Measured on the physical
phone `5eea1f50` (1220x2656), `benchmarkRelease` at `ea6c37b0d`, the user's own 240-record refresh
history, 2026-09-15.

## The answer

**A card's glass layer is rasterised at its full height every frame, and an expanded card can be
taller than the screen.** The part that is scrolled out of the viewport is rasterised for pixels that
never reach it.

One expanded record's `drawBackdrop` layer, read from `atrace`, is `1128 x 3996` on a 2656px-tall
display -- **1.5 screens**, of which at most 2003px can ever be visible.

| scene | glass layer height recorded | visible | frame p50 |
| --- | ---: | ---: | ---: |
| all collapsed (6 cards) | 6 x 354 = 2124 | ~2000 | **14.0 / 14.2** |
| one sparse record expanded | 2029 | 1946 | **13.6** |
| one rich record expanded | **3996** | 2003 | **36.7 / 37.8 / 38.1** |
| the same, plus two collapsed cards on screen | 3996 + 709 | ~2000 | **54.6** |

Rows two and three are the load-bearing pair: **the same visible area, 1.97x the recorded height,
2.7x the frame.** Visible area does not predict cost; recorded height does, at roughly `h^1.47`.

Row four is the same statement from the other side -- pushing *more* of the expanded card off screen
made it **worse**, because the collapsed cards that took its place on screen added their own layers
while the expanded card kept recording all 3996px of itself.

It is layer rasterisation, not composition, upload or the GPU:

| stage | collapsed | expanded |
| --- | ---: | ---: |
| record draw | 1.0 | 1.9 |
| sync (layer upload) | 0.36 | 0.78 |
| **RT issue->swap** | **5.9** | **17.5** |
| swap->completed (GPU) | 3.6 | 4.4 |

`atrace` puts it exactly: `flush layers` goes from **5.87ms/frame to 15.45ms/frame**.

## Why the obvious fix is not available

`Modifier.drawBackdrop` sizes its effect layer from the element's own bounds. The library exposes
`backdrop`, `shape`, `effects`, `onDrawSurface`, `layerBlock`, `shadow` and `exportedBackdrop` --
**no clip, bounds, region or recorded-size parameter**, confirmed against the library's own
documentation. Its maintainers' guidance is the same: the only lever is to make the modifier's layout
bounds match the region you want affected.

So any fix has to shrink the *element*, and the element's geometry is what the material is computed
from.

## The shape a fix would have to take

Give a card taller than the viewport a surface whose bounds are the card intersected with the
viewport **expanded by a margin**, rather than the whole card.

The margin is what makes it appearance-neutral, and it has to clear every edge-local term in the
material at once: `backdropLens` 24dp, `Shadow.Default` 24dp, `backdropBlur` 4dp, `cardCornerRadius`
16dp. Beyond about 24-30dp from an edge the material is uniform and translation-invariant -- the lens
only refracts near the edge, the highlight and inner shadow are edge functions, the blur is local. A
48dp margin puts every spurious edge the band introduces safely off screen, so the visible region is
the same pixels it is today.

What it would buy, on the measured card: 3996px -> ~2900px of recorded height, which at the measured
`h^1.47` is **about 36% off the expanded-card frame** (~37ms -> ~24ms). It does not reach the 14ms
collapsed floor, and it buys nothing at all for cards shorter than the viewport.

Two things would have to be proven before it ships:

- **Pixel verification of the visible band.** The interior-uniformity argument above is an argument,
  not a measurement. Roborazzi against a tall card at several scroll offsets.
- **`exportedBackdrop` is coordinate-dependent** (the library says so). Content inside the card
  samples the card's own surface layer; re-basing that layer on a band moves the coordinate space the
  nested glass computes against.

The alternative is to ask upstream for a viewport/region parameter on `drawBackdrop`, which is the
same request as the one in `backdrop-reduced-resolution.md` and would solve both.

## Measured and rejected -- do not retry

- **Conditional stacked-card content layer.** `LiquidSurface` gives every card on a stacking page a
  permanent `Modifier.graphicsLayer` running `applyAppEdgeStackContentRecession`, which does nothing
  until the card is actually receding. Replacing it with a `DrawModifierNode` that creates the layer
  only while `card.stacked` -- same alpha, same blur, same functions, read at draw time so nothing
  recomposes mid-scroll -- measured **markedly worse on hardware**, confirmed by the user on a
  `benchRelease`. The permanent layer is load-bearing: it is a display-list cache, and removing it
  forces the card's content to be re-recorded into the parent every frame. Reverted.
- **`exportBackdropToContent` disabled outright.** BA office page, cards collapsed: 83.9 against
  84.6. No effect. The sheet document's "~6ms of ~33ms for an unconditional export" does not
  reproduce here. (Not yet measured in the tall-card regime, where the second record would be of a
  3996px layer.)

## The A17 AVD cannot measure this

Recorded because two rounds of work were spent before it was noticed. One unchanged build, one scene,
one script, across a single session: **84.6 / 135.9 / 151.6 / 152.0 / 200.9 / 202.1 / 205.4 / 117.0 /
117.4 / 117.7ms**. A +-40% band against an effect worth ~20%. Every emulator comparison taken more
than a few minutes apart is noise, and the first "19% win" in this investigation was exactly that.

What does work, on hardware and on the emulator alike, is **one build carrying both code paths,
switched by a system property read once per process and flipped between launches**, so drift hits
both sides of the comparison equally:

```kotlin
internal val UsePathA: Boolean by lazy {
    runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, "debug.keios.<name>", "b") as String
    }.getOrNull() == "a"
}
```

```bash
adb -s <serial> shell setprop debug.keios.<name> a && adb -s <serial> shell am force-stop os.kei
```

Interleaved that way, the conditional-layer change measured as exactly nothing on the emulator's BA
page -- a true null for *that* regime, and one that said nothing about the tall-card regime where it
turned out to be a regression.

## Measuring this scene again

The phone reproduces to +-0.3ms, but only with the gesture pinned. Flings move the card in and out of
view between passes and produced 13.7ms and 47ms for what was meant to be the same scene. Use slow
drags, which carry no momentum and return the list to where they started:

```bash
adb shell input swipe 610 1650 610 1400 250   # x4, alternating with the reverse
```

Stay clear of the list's top: a downward drag at the top boundary is pull-to-refresh, not a scroll,
and it contaminated three runs before it was spotted.
