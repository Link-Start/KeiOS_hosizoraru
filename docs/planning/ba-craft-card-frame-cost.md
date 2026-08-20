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

## The card rewrite re-measured, and a correction (2026-08-20, later)

The table above was measured with the craft card expanded on an account whose six slots were idle,
against the same card collapsed. That A/B is a toggle on one build and one state, and it holds: the
twelve nested surfaces cost +16.6ms.

What does **not** hold is the figure that got quoted afterwards. "47.13 → 23ms, −51%" compared runs
taken on *different account states* days apart in the same session, which is exactly the mistake
`hwui-frame-budget.md` warns about for refresh-rate mix and is no better here. A controlled
comparison — the pre-rewrite commit and the card build, same device, same session, same account, three
runs each — reads:

| build, scrolling | RT p50 |
|---|---|
| pre-rewrite (3 cafe rows + 6 craft rows, no pile) | 25.98 / 28.52 / 27.58 |
| cards + pile | 27.57 / 27.83 / 28.24 |

**Frame-neutral.** The rewrite's real wins are density and lazy disposal; the frame win claimed for it
was an artefact of comparing two states. Anyone quoting a number for this page must state the account
state it was taken on.

Four more levers, all measured on that same controlled state, all neutral or nearly so:

| lever | RT p50 | verdict |
|---|---|---|
| the edge-stack pile, off | 28.33 / 27.68 / 27.81 | neutral — the pile is free here, unlike on the row layout where it cost +11ms |
| `exportBackdropToContent` off on collapsed cards | 27.48 / 27.57 / 28.06 | neutral — even though nothing in a collapsed card samples it |
| scrolling among the slot cards only, big cards off screen | 27.41 | the slot cards are not carrying the cost either |
| the list's `layerBackdrop` for the top bar removed | 24.82 / 24.90 | **−3ms**, the only real contributor found, and not shippable: the top bar's glass has nothing else to sample |

So the page's ~27ms is not one thing. The full-page capture the top bar needs is the largest single
piece at ~3ms; the rest is spread thin across every glass surface on screen, and no individual card,
panel or pill accounts for enough to be worth a visual trade. The one lever that did pay was making
the slot cards' pills flat (**−7ms** when measured, on the state it was measured on), and that is
already shipped.

**There is no further appreciable appearance-neutral win on this page.** The next real one is the
reduced-resolution capture in `backdrop-reduced-resolution.md`, which the library cannot express
today. Do not re-run the four levers above.

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
