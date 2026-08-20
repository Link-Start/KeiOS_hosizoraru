# Why the Craft Chamber card costs frames

Measured on the API 37 phone AVD (1280x2856, density 480), `benchmarkRelease`, with
`debug.hwui.profile true`. Harness: `scripts/perf/frame_stages.py` over
`dumpsys gfxinfo os.kei framestats`, driven by a fixed journey — enter BA, force the card to a known
expansion state, return to the top, then four flings up and four down.

Read the same way `hwui-frame-budget.md` says to: CPU/GPU **percentiles** only, never
`Janky frames`. Absolute numbers here are an emulator's and are not comparable to the phone's;
the *ratios* between variants are the result.

## The card is the whole regression

Expansion is the only variable — same build, same journey, the state toggled through the card's own
header:

| BA page | RT issue->swap p50 | total p50 | frames |
|---|---|---|---|
| craft card collapsed, scrolling | 30.51 | 99.35 | 120 |
| craft card expanded, scrolling | **47.13** | 133.33 | 111 |

**+16.6ms of RenderThread while scrolling.** Compose is not involved: `measure+layout` is 0.02ms and
`record draw` ~1.1ms in every variant, which is the same finding as `hwui-frame-budget.md` — the cost
is RT plus GPU.

A first version of the journey also reported an "at rest" pair (2.69ms collapsed against 48.96ms
expanded) and those numbers are **discarded**: that journey flung twice to return to the top of the
list before sampling, so it measured the tail of a fling settle, not a resting page. A dwell that
touches nothing renders **zero frames in both states**, expanded or collapsed, which is what
`hwui-frame-budget.md` already recorded for BA. Anyone re-running this must sample without a
preceding fling; the harness's `dwell` journey exists for that.

## What inside the card costs it

Expanding composes 12 nested glass surfaces: six `BaLiquidPanel` rows, each holding one
`AppLiquidTextButton`. Four variants, one lever each, same journey:

| variant | RT p50 | vs shipped | verdict |
|---|---|---|---|
| shipped | 47.13 | — | |
| rows keep glass, no `exportBackdropToContent` | 52.60 | **+5.5** | rejected, *worse* |
| craft card's own glass off (`effectsEnabled = false`) | 48.08 | +1.0 | rejected, no effect |
| row panels flat, buttons still glass | 40.63 | −6.5 | works, but visible |
| rows **and** buttons flat (`LocalLiquidControlsEnabled = false`) | 33.84 | **−13.3** | works, removes the material |

Three things worth keeping from that table.

**The card's own glass is free.** Removing it changed nothing, so the cost does not scale with the
glass area of the expanded card — which rules out the reading of `backdrop-reduced-resolution.md`
that says blur cost follows the consumer's bounds. It follows the *count* of consumers, exactly as
`liquid-sheet-frame-cost.md` found for the sheet.

**Dropping the export makes it slower, and the reason is instructive.** A row that exports gives its
button a row-sized layer to sample. Without the export the button falls back to the card's exported
layer, and `LayerBackdrop.drawBackdrop` ends in `drawLayer(graphicsLayer)` — replaying a layer six
times taller. Nesting glass is not the mistake; the mistake would be flattening the hierarchy so
children sample something bigger. **Do not re-try this.**

**Killing all twelve surfaces lands scrolling at 33.84 against the collapsed card's 30.51** — i.e.
the twelve surfaces *are* the regression, at roughly 1.4ms of RenderThread each while scrolling
(3.8ms each at rest, which matches the sheet investigation's ~3.7ms per composed glass control).

## So there is no free micro-fix

Of the four levers, two do nothing or harm, one removes the material outright, and the only partial
win — flattening the row panels — is visible: the fallback path in `BaLiquidSurfaceColumn` draws
`appSquircleBorder`, which the glass path does not, so the rows gain amber outlines. Verified by
screenshot, not assumed.

That leaves one honest conclusion: **the frame budget of this card is its glass surface count, and
the only lever that does not touch the material is composing fewer of them.** The design change and
the performance fix are the same change.

## What landed

Idle slots became a count with a reveal, and only loaded slots render as rows
(`BaCraftCard`). Same journey, same device, same session:

| BA page, scrolling | surfaces | RT p50 | vs shipped |
|---|---|---|---|
| shipped: six rows always | 12 | 47.13 | — |
| new, nothing running (the common case) | 1 | **34.32** | **−12.8** |
| new, two crafts running | 5 | 42.72 | −4.4 |
| collapsed card, for reference | 0 | 30.51 | −16.6 |

Nothing-running lands within 4ms of the collapsed card, and within 0.5ms of the
"remove all the glass" ceiling in the table above — while every surface that *is* on screen keeps its
full material. A dwell renders zero frames in every one of those states.

The rows are unchanged and one tap further in: same panel, same glass button, same tap-to-configure
and long-press-to-clear, same `BaCraftSlotEditSheet`. Verified on the AVD by revealing the idle slots
and opening the sheet from a revealed row.

## What the count buys, per option

At the measured ~1.4ms of scrolling RT per surface:

| design | surfaces when idle | RT saved | card height |
|---|---|---|---|
| shipped: six rows, one slot each | 12 | — | ~6 rows |
| two slots per row (Generate + Fusion side by side) | 9 | ~4ms | 3 rows |
| six compact chips, no row panels | 6 | ~8ms | ~2 rows |
| show only running/ready slots + one "load a slot" control | 1-2 | ~15ms | 1 line |

Measured after the fact, the last row delivered −12.8ms rather than the ~15ms this slope predicted,
because the per-surface cost is not perfectly linear — 11 surfaces removed came out at ~1.2ms each
while the first four came out at ~2.1ms. Use the slope to rank options, not to promise a figure.

The last one is also what the card's own KDoc already observes — *"most of the time every one of
them is idle"* — and it is why the card was given a fold in the first place. A fold hides the cost
only while it is shut; not composing idle rows removes it while the card is open.

## Reproducing

```bash
adb shell setprop debug.hwui.profile true      # restore to false afterwards
# then, per variant, with the card driven to a known state:
adb shell dumpsys gfxinfo os.kei reset
#   ... fixed journey ...
adb shell dumpsys gfxinfo os.kei framestats > out.txt
python3 scripts/perf/frame_stages.py out.txt
```

Pass condition for a fix: expanded scrolling RT p50 within ~3ms of the collapsed figure on the same
device in the same session. Do not compare across sessions — the LTPO panel's refresh regime moves
the deadline, per `hwui-frame-budget.md`.
