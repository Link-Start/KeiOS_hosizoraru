# Where the frame time goes

Measured on 5eea1f50 (1220x2656, 120Hz LTPO, HyperOS) with Developer options ->
Profile HWUI rendering -> "In adb shell dumpsys gfxinfo". Harness: `scripts/perf/`.

## Reading the instrument

`dumpsys gfxinfo` reports two things that look authoritative and are not. Across three
back-to-back runs of one unchanged build, `Janky frames (legacy)` came out 9.6% / 11.8% / 59.0%,
and `99th gpu percentile` alternates between a real value and the 4950ms overflow bucket. Neither
can support an A/B.

The CPU and GPU **percentiles** are stable to +/-1-2ms across the same runs. Use only those.

The per-frame `PROFILEDATA` block carries 24 columns, not the 14 that older docs describe, and
`Flags` is routinely 32 rather than 0 — filtering on `Flags == 0` silently discards every frame.
`scripts/perf/frame_stages.py` parses by header name and does not filter.

## The stage that matters is not the one the totals suggest

Per-frame decomposition, p50, from the columns rather than the summary:

| journey | total | start_delay | ui_work | sync_wait | rt_cpu | gpu |
|---|---|---|---|---|---|---|
| home dwell (no input) | 20.71 | 0.43 | 0.42 | 0.10 | 8.62 | 11.08 |
| home scroll | 13.52 | 0.29 | 1.25 | 0.04 | 4.74 | 6.54 |
| section switch | 19.25 | 0.38 | 0.45 | 0.10 | 7.52 | 10.58 |
| route push | 12.33 | 0.25 | 0.45 | 0.03 | 4.73 | 6.07 |

`start_delay` is IntendedVsync -> HandleInputStart; `ui_work` is HandleInputStart -> SyncQueued
(input, animation, measure, layout, record draw — all of Compose); `rt_cpu` is
SyncStart -> CommandSubmissionCompleted; `gpu` is CommandSubmissionCompleted -> GpuCompleted.

**The UI thread is not the problem.** Everything Compose does — recomposition, measure, layout,
recording draw commands — totals 0.4-1.3ms. Measure+layout alone is consistently under 0.1ms.
Optimising Compose here would buy nothing.

The cost is entirely RenderThread CPU plus GPU. `swap->completed` equals `gpu` exactly, and
`DequeueBufferDuration` is ~0.01ms, so there is no buffer starvation: the app is doing the work,
not waiting for a buffer.

## Per-page steady state

| page | total p50 | rt_cpu | gpu |
|---|---|---|---|
| home | 13.07 | 3.94 | 6.54 |
| github | 11.70 | 3.55 | 6.13 |
| mcp | 13.13 | 3.84 | 5.12 |
| os | 14.12 | 3.77 | 5.15 |
| ba | 33.23 | 9.61 | 4.69 |

BA looks like the outlier and mostly is not. Its list holds three cards, which do not fill the
screen, so a swipe lands in overscroll rather than scrolling — the stretch is what costs 33ms.
**Left alone, BA renders zero frames in a three-second dwell**, which is the best result of any
page: nothing on it redraws when nothing changes.

Home is the opposite. Idle, untouched, it produces ~40 frames per second at 20.71ms each. That is
the dynamic background driving `invalidateDraw` at its 60fps cap, and every invalidation re-blurs
the whole Liquid Glass stack above it. The shader itself is cheap — it already renders at
`DYNAMIC_BACKGROUND_RENDER_SCALE = 0.25f` and upscales — but the re-blur it forces is not.

The consequence worth naming: **the pipeline is already saturated before the user touches
anything.** Every interaction starts from a full queue.

## Measured and rejected

**`BG_EFFECT_HIGH_FPS` 60 -> 30.** The obvious lever, and it makes things worse, not better:

| | 60fps (shipped) | 30fps |
|---|---|---|
| home idle total p50 | 20.71 | **23.52** |
| home idle gpu p50 | 11.08 | **13.34** |
| section switch `Slow issue draw commands` | 4-5 | **25-26** |
| section switch `Frame deadline missed` | 1-2 | **40-79** |

Consistent across runs. Halving the invalidation rate does not halve the work — it spaces
redraws far enough apart that each one arrives colder, and the irregular cadence fights the LTPO
panel. The cost is not per-frame overhead that fewer frames would avoid; it is that each frame is
expensive. Do not retry this as a frame-time fix.

## What is left

The remaining cost is full-screen Liquid Glass blur re-running whenever the background drifts.
Making it cheaper means either fewer glass surfaces sampling a full-screen backdrop, or sampling
a lower-resolution one — the second is appearance-neutral in principle, because a blur discards
the detail a full-resolution capture preserves, but it needs support from the Backdrop library
rather than a change here.

There is no free frame-time win in the paths measured above. Anyone picking this up should start
by reproducing the table in "The stage that matters", not by re-running the rejected experiment.

## Why Home reads differently at different moments

The panel is LTPO and moves between 120Hz and 60Hz on its own. Home idle, measured twice on the
same build, with nothing changed but when the sample was taken:

| Home idle | panel | vsync budget | frame cost p50 | gpu p50 | over deadline |
|---|---|---|---|---|---|
| within ~1s of a scroll | 120Hz | 8.31ms | 19.65 | 11.27 | 100% |
| ~10s after last touch | 60Hz | 16.62ms | 21.29 | 11.25 | 100% |

The work is the same — GPU 11.27 vs 11.25. What changes is the deadline it is scored against, and
the panel crosses between the two a second or so after the last touch. `dumpsys gfxinfo`
accumulates both regimes into one jank figure, so the same build reads well or badly depending on
how the app was being used while the counter was open. Six consecutive settled samples, by
contrast, land within 20.85-22.15ms — the variance is not in the app.

The part that is a real problem: **Home misses the deadline 100% of the time in both regimes.**
A ~20ms frame does not fit an 8.31ms budget and does not fit a 16.62ms one either. Home never
keeps up while its background animates. That is the full-screen glass blur, deferred to a separate
Backdrop investigation.

When comparing builds, reset the counter and drive a fixed journey (`scripts/perf/`) rather than
reading the accumulated figure — otherwise the refresh-rate mix is the variable, not the change.

## Switching into BA

Ranked by cost of Home -> tab, three passes pooled, every tab a first entry:

| switch to | total p50 | p90 | p99 | ui p99 | rt p99 | gpu p99 |
|---|---|---|---|---|---|---|
| github | 17.04 | 33.90 | 48.44 | 24.01 | 22.49 | 28.63 |
| mcp | 18.48 | 44.75 | 59.34 | 25.28 | 38.84 | 31.36 |
| os | 15.34 | 45.96 | 76.96 | 19.44 | 18.46 | 37.99 |
| **ba** | **15.26** | **58.79** | **83.76** | **38.38** | **57.72** | 26.81 |

BA's median is the *best* of the four and its GPU p99 is the *lowest*. The entire complaint is the
tail, and the tail is first activation. Bouncing Home <-> BA repeatedly:

| Home -> BA | p50 | p90 | p99 | ui p99 | rt p99 |
|---|---|---|---|---|---|
| entry #1 | 13.13 | 55.78 | 74.65 | 32.15 | 62.63 |
| entry #2 | 12.74 | 41.26 | 55.78 | 14.32 | 35.94 |
| entry #3 | 12.84 | 43.19 | 59.20 | 11.81 | 36.68 |
| entry #4 | 13.19 | 38.06 | 56.48 | 13.93 | 29.80 |

`MainPageActivationState.hasActivated` keeps a page composed once it has been reached, so a page
composes exactly once per process — which is why only entry #1 pays. First entry costs ~19ms of
extra UI-thread work (composing BA's tree) and ~26ms of extra RenderThread (first rasterization of
its glass layers) over a repeat.

### The candidate worth trying

`MainPageActivationState` marks a page activated from two `LaunchedEffect`s: when it becomes the
settled page, and when it is the scroll target *while `isScrollInProgress` is true*. A tab tap
reaches the second one, so BA's tree composes on the **first frame of the switch animation** —
the worst possible frame to spend 19ms on.

`MainPagerTabJumpController.onPageSelected` already knows the target index synchronously, at tap
time, before `animateToPage` is launched. Marking the target activated there would move that
composition into the touch-response window, ahead of any motion. Same work, same tap, one frame
earlier.

This is **not** the layer pre-warm recorded in the BA first-entry notes, which rendered glass off
the click path and made the other half worse. Nothing is rendered early here; only the ordering of
composition versus the start of the animation changes. It does need care: `activationState` is
built after the coordinator in `MainPagerLayout`, so the target index has to be threaded out of
`MainPagerTabJumpControllerState` first.

## The switch metric has a noise floor of ~7ms

`switch_into_page.sh` reports p50 for Home -> tab. Six measurement passes taken across one
afternoon, on builds differing by at most a few UI elements:

| pass | github | mcp | os | ba |
|---|---|---|---|---|
| build A, run 1 | 17.04 | 18.48 | 15.34 | 15.26 |
| build A, run 2 | 16.06 | 17.77 | 21.69 | 16.00 |
| build B, run 1 | 15.09 | 14.93 | 23.11 | 12.59 |
| build B, run 2 | 12.12 | 15.17 | 20.95 | 15.16 |
| build C, run 1 | 17.78 | 18.73 | 13.67 | 16.51 |
| build C pooled (6) | 18.48 | 19.45 | 13.80 | 15.55 |

github spans 12.1-18.5 and os spans 13.7-23.1 — 6 to 9ms — while build A and build B differ only
by three dock buttons. Two runs of the *same* build (B) differ by 3ms on github and 2.6ms on ba.

So this metric can rank tabs against each other within one pass, which is what it was built for,
but it cannot resolve a change worth a millisecond or two. A first read of build B against a
build A number taken hours earlier looked like "github 16.06 -> 12.12, frames over budget 46% ->
30%"; six passes later that improvement is gone and github reads worse than where it started.
Nothing in the app changed in between.

The same caution as the jank counter, one level up: **compare only passes taken back-to-back, and
pool several of them.** A number from earlier in the session is not a baseline. If an effect is
smaller than a couple of milliseconds, this instrument cannot see it, and neither reading is
evidence.

## OS is not the expensive tab

Recorded because it was claimed twice from readings the noise floor above does not support.

OS's frame *shape* differs from the other tabs, and that part is stable across every run: it has
the highest UI-thread work (`ui_work` 2.05-2.82ms against github's 0.53-0.61) and the highest
RenderThread CPU (6.19-7.83 against 3.73-4.75), but the **lowest GPU** (3.96-5.55 against
4.50-7.08). The UI-thread figure is almost entirely `record draw` — 1.5-1.65ms p50 against
github's 0.30 — which is what showing eight Liquid Glass cards costs when the comparison page is
showing one.

None of that makes it slow. Across the last two pooled measurements OS came out **best** of the
four tabs on both totals and deadline misses: p50 13.80/14.09 and 40%/38% over budget, against
github 18.48/17.80 at 55%/54%. Earlier in the same session OS read 20.95-23.11 and looked like the
worst tab. Nothing changed in between that explains it.

CPU-heavy and GPU-light is the shape of a page made of many small cards. It is not waste, and
looking for waste there found none: the list is lazy, collapsed accordions do not compose their
rows, and the edge stack already skips `placeWithLayer` for cards resting below the stack line.

The one thing worth doing was a simplification, not a fix. Every OS card was followed by its own
8dp `Spacer` item, so half the lazy list was empty boxes; every other page expresses the same gap
through `sectionSpacing`, which is `Arrangement.spacedBy`. Converting is provably pixel-identical
(a full-frame diff of before and after differs only in the 45 rows holding the status-bar clock)
and measures **no change at all** to `record draw`: 1.51/1.59/1.65 after against 1.52/1.65/1.57
before. A fixed-height empty Spacer records no draw commands; the item overhead was in composition
and measurement, and `measure+layout` was already 0.04ms. Worth keeping for the code, not for the
frames.

## Idle frames: the metric that needs no per-frame capture

`scripts/perf/idle_dwell.sh` resets the counter, leaves a scene alone for three seconds and counts
what it drew anyway. A scene that redraws nothing when nothing changes reports zero, and the figure
does not swing between runs the way the jank counter does. Measured on `KeiOS_API37_Validation`
(phone AVD, no LTPO, so the cap is 60 rather than the panel-following rate hardware shows):

| scene | idle frames/s | total p50 |
|---|---:|---:|
| home | 59.7 | 24 |
| github / os / mcp / ba | **0** | — |
| pad: github / os / mcp / ba, two lanes | **0** | — |
| Settings route **over Home** | 55.3 | 36 |
| About route **over Home** | 60.0 | 29 |
| Calendar/Pool route **over BA** | 0 | — |

Only Home redraws at rest, which is its animated background and is the design. Everything else is
silent — including every two-lane page, so the wide layouts do not introduce an idle cost.

### A route over Home inherited Home's redraw rate

The two route rows above were a defect, not a design. `BgEffectBackground` already suspends the
drift while a modal presentation covers it, for the reason its own comment gives: the drift cannot be
seen but is still paid for, because the loop invalidates the draw tree and every glass surface above
re-rasterizes. A pushed *route* was not in that gate, so Settings over Home rendered 166 frames in a
three-second dwell at p50 36ms — worse than Home itself, because the route's own glass was
re-composited on every invalidation. Three captures a second apart were **pixel-identical**: 55fps of
the same image.

`rememberNavEntryAtTop` already published the fact and `MainPagerLayout` already used it to stop the
pager's backdrop *capture*; it simply never reached the loop that was driving it. Threading it into
Home's `homeDynamicActive` takes both route rows to 0, leaves Home itself at 61.7/s and p50 25, and
the drift resumes at 181 frames per dwell when the route pops. Playback only: `animTime` accrues from
real elapsed time, so it picks up where it paused rather than jumping.

## Calendar/Pool is the heaviest scene in the app

Scroll, four flings each way, same AVD. `gpu p50` is from the summary; the stages are
`frame_stages.py` over `dumpsys gfxinfo <pkg> framestats` (per-frame capture needs
`setprop debug.hwui.profile true`, which is why the aggregate table above exists).

| scene | frames | total p50 | p90 | gpu p50 | deadline missed |
|---|---:|---:|---:|---:|---:|
| **calendar/pool** | 302 | **57** | 81 | 11 | **65%** |
| **ba office** | 107 | **65** | 101 | 18 | **57%** |
| settings | 381 | 34 | 48 | 15 | 9% |
| os | 457 | 26 | 36 | 18 | 7% |
| github | 306 | 23 | 31 | 17 | 6% |
| mcp | 439 | 22 | 32 | 15 | 7% |

Calendar/Pool has the *lowest* GPU of the set and the worst total, so the cost is not the glass
shader. Stages, against mcp as the control:

| stage p50 | calendar/pool | mcp |
|---|---:|---:|
| all of Compose | 1.07 | 1.15 |
| sync (upload) | **3.00** | 0.17 |
| RT issue->swap | **22.47** | 3.22 |
| swap->completed | 10.35 | 12.45 |

Compose is 1ms on both, as everywhere else. The difference is RenderThread CPU at 7x and layer/bitmap
upload at 18x — full-width photographic covers, in a long list, under glass.

**No appearance-neutral saving was found here.** The obvious one is the decode budget, and it is
already conservative: `GAMEKEE_COVER_DEFAULT_DECODE_DIMENSION` is 960px against a card drawn at
~1194px on this AVD, so the covers are already being *upscaled*. Lowering it would soften the image,
which is the visual cost this work is not allowed to pay. BA office is the overscroll-stretch case
already recorded above, not a new finding.

## What the Backdrop library rules out

Asked against the library's own documentation (`backdrop` MCP), for the lever left open in "What is
left" above:

- **There is no downscale or render-scale for a backdrop.** Capture and sampling happen at screen
  resolution, and no documented option changes that. The "sample a lower-resolution backdrop"
  idea is therefore not available today, and needs a library feature request rather than a change
  here.
- `exportedBackdrop` exists to break the feedback loop when a `drawBackdrop` samples a layer its own
  content draws into — it is a correctness tool, not a way to skip re-recording.
- The three usage mistakes the docs warn about are all already avoided in this codebase: transforms
  go through `drawBackdrop`'s `layerBlock` rather than an enclosing `graphicsLayer` (20+ sites, with
  the reason written down at `LiquidMenuSurface` and `LiquidSurfaces`), `rememberCombinedBackdrop`
  merges the three places that need two sources, and `exportedBackdrop` is contract-tested where a
  layer is reused. There is no waste of that kind left to harvest.
