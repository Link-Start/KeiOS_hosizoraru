---
name: keios-frame-performance
description: Use when asked to make KeiOS faster, smoother, or less janky — frame drops, stutter, "性能不太行", scroll or transition smoothness, recomposition or Compose performance, battery from redraws. Carries this app's measured frame budget, which contradicts general Compose advice, plus the levers already measured and rejected.
---

# Making KeiOS faster

Read the first section before proposing anything. General Compose performance advice is
correct about Compose and wrong about this app, and following it here has already cost two
rounds of work that measured no change.

## 1. Where the frame time actually goes

Measured on the physical phone `5eea1f50` (1220x2656, 120Hz LTPO, HyperOS), `releaseDiagnostic`
build, home idle, 119 frames, no screen mirroring (see §3), **2026-09-15**:

| stage | p50 | share |
|---|---|---|
| input | 0.00 | 0.0% |
| animation | 0.19 | 1.1% |
| measure+layout | 0.05 | 0.3% |
| record draw | 0.17 | 1.1% |
| sync | 0.49 | 2.6% |
| **RT issue->swap** | **6.72** | **33.1%** |
| **swap->completed (GPU)** | **11.66** | **59.0%** |
| total | 20.06 | |

**Everything Compose does is 0.41ms of a 20.1ms frame — 2.0%.** Input, animation, measure,
layout and recording draw commands, added together. RenderThread plus GPU is 92%.

So the ceiling on *every* recomposition optimisation in this app is half a millisecond, and
most of that half is animation and draw recording, which cannot go to zero. A change that
halves recomposition and reports a smoother app is reporting noise.

This is not a one-off reading. It reproduces the table in `docs/planning/hwui-frame-budget.md`,
first measured months earlier, on a tree that has changed substantially since.

## 1b. Switching into a page: first entry, and only first entry

Home idle is one scene. The complaint is usually the other one — tapping from Home into another
tab. Measured with no screen mirroring, one isolated switch per capture, counter reset immediately
before the tap, medians of three passes:

| home -> tab, first entry | p50 | p90 | p99 |
|---|---|---|---|
| github | 15.39 | 46.43 | 59.53 |
| mcp | 19.15 | 49.56 | 62.43 |
| os | 19.40 | 57.62 | 65.28 |
| ba | 16.24 | 51.81 | 63.20 |

Uniform across all four tabs, so it is not one bad page. Now the same tab a second time in the
same process — and read the last column, which is the one a person feels:

| home -> github | p50 | p90 | p99 | frames over 33ms |
|---|---|---|---|---|
| first entry | 15.39 | 46.43 | 59.53 | **11** |
| second visit | 13.19 | 29.07 | 36.38 | **2** |

Eleven janky frames against two, with the p90 ranges completely disjoint across runs
([41.6, 46.4, 48.2] against [24.4, 28.0, 30.2, 31.5]). `MainPageActivationState.hasActivated`
keeps a page composed once it has been reached, so a page composes and rasterises its glass
exactly once per process: **every tab is rough the first time it is opened after a cold start and
clean forever after.**

The cost is **per page**, not shared between them. Entering `github` first and then measuring
`mcp`'s own first entry, six passes each, moves p50 not at all and the tail by about the noise
floor:

| mcp first entry | p50 | p90 | p99 |
|---|---|---|---|
| as the first switch | 19.30 | 51.62 | 62.81 |
| after github was entered | 19.92 | 43.61 | 51.12 |

What it is made of: ~19ms of extra UI-thread work composing the page's tree, plus the first
rasterisation of its glass layers on RenderThread — and the second is the larger.
**Composition and rasterisation are different events.** A layer is rasterised when it is first
*drawn*, which is when the animation brings the page on screen; composing it earlier does not draw
it earlier. That is why moving the composition to the tap achieved nothing (§4).

An `atrace` of one first switch says where the RenderThread time goes and, usefully, what it is
**not**:

| RenderThread slice | total over the switch | max |
|---|---|---|
| `Drawing 0 0 1220 2656` (full-screen) | 295.2ms | 28.97 |
| `renderFrame` | 233.7ms | 26.36 |
| **`flush layers`** | **160.2ms** | **21.98** |
| `CreateGraphicsPipeline` | **5.8ms** | 0.40 |

The HWUI shader cache grows from 73 to 102 programs across that single switch — 29 new shaders —
which looks like the answer and is not: creating those pipelines costs **5.8ms of the entire
switch** and 3.57ms of its worst frame. Shader compilation is a red herring. `flush layers`, the
layer rasterisation, is the cost.

The open problem is **not** the glass, which §10 establishes by measuring a build with it turned
off. Read that before assuming the material is in the way.

## 2. The two axes generic advice targets are already clean

Check before spending time re-deriving this; re-measure if the tree has moved a lot.

**Stability and skippability.** From the Compose compiler report (see §3 for the command),
1322 composables:

```
restartable but NOT skippable : 0
```

That is the pathological bucket and it is empty. The 212 unstable classes are Activities,
Services, Receivers and network DTOs — none of them reach a composable's parameter list, which
is why nothing fails to skip. `@Stable`/`@Immutable` annotation work has nothing to bite on.

**Deferred reads** — the `var` -> lambda discipline. Already applied throughout:

| pattern | count |
|---|---|
| `graphicsLayer(` with non-lambda args | **0** (all 9 sites are `block = ...`) |
| `rememberUpdatedState` | 205 |
| `Animatable` + `withFrameNanos` (imperative, deferred) | 48 + 15 |
| `animate*AsState` (composition-phase read) | 21 |

The handful of `Modifier.offset(x, y)` sites left take static `dp` constants, not state.

**miuix currency.** Pinned at `0.9.4-5157b503-SNAPSHOT` in `gradle/libs.versions.toml`, which
was upstream `compose-miuix-ui/miuix` HEAD on 2026-09-11. Following upstream is good advice and
is already being done; verify with the GitHub API before assuming we are behind.

## 3. How to measure

Never A/B on `dumpsys gfxinfo`'s `Janky frames` or `99th gpu percentile`: across three
back-to-back runs of one unchanged build, jank came out 9.6% / 11.8% / 59.0%, and the gpu p99
alternates with a 4950ms overflow bucket. The CPU/GPU percentiles hold to +/-1-2ms.

Always measure `os.kei.diag` (`assembleReleaseDiagnostic`) — release with R8, separate
application id. Never `os.kei`: that is the user's real install with their real tracked
repositories, and driving it starts real refreshes. A debug build is not comparable to release.

Requires Developer options -> Profile HWUI rendering -> "In adb shell dumpsys gfxinfo".

**Check for screen mirroring first, every time.** scrcpy holds two virtual displays (its own and
a `screen-mirror-ScreenRecorder`), and the device then composites an extra full copy of the screen
for each, every frame — 38.2ms + 35.4ms of RenderEngine work over a 2.13-second trace, 122 extra
full-screen compositions.

The direction of the error is **not** the one you would guess, so do not try to reason it away:
with mirroring on, home idle measured *faster* — 17.90ms against 20.06ms clean, GPU 10.31 against
11.66. The extra load keeps the GPU governor on a higher clock, so the app's own work finishes
sooner in wall-clock terms. The clean figure is the one that matches this document's historical
20.71ms.

It distorts the tail worse than the median, and unevenly: with mirroring on, first entries
produced 281ms and 125ms p99 outliers that do not exist clean, and an apparent "40% of first entry
is shared between pages" effect that evaporated when re-measured without it. A/B comparisons
survive mirroring because both sides pay it. Absolute figures and tail claims do not.

```bash
adb -s <serial> shell dumpsys SurfaceFlinger --display-id   # any "Virtual display" is a mirror
pgrep -lf scrcpy                                            # and check the host
```

```bash
# frames a scene draws when nobody touches it, plus deadline misses
cd scripts/perf && D=<serial> PKG=os.kei.diag DWELL=3 ./idle_dwell.sh home

# one fixed journey, aggregate percentiles: home_scroll | section_switch | route_push
cd scripts/perf && D=<serial> ./hwui_journey.sh home_scroll home_scroll

# the table in §1 — per-frame stage decomposition, the only view that says who is slow
adb -s <serial> shell dumpsys gfxinfo os.kei.diag reset
sleep 3
adb -s <serial> shell dumpsys gfxinfo os.kei.diag framestats > /tmp/fs.txt
cd scripts/perf && python3 frame_stages.py /tmp/fs.txt

# Compose stability report -> app/build/compose_compiler/app-composables.csv
./gradlew :app:compileReleaseKotlin -PcomposeCompilerReports=true --rerun-tasks
```

The panel is LTPO and moves between 120Hz and 60Hz on its own about a second after the last
touch, so the same build reads differently depending on when the counter was open. Take
settled samples, and compare like with like: a run at a 16.64ms vsync interval is not
comparable to one at 8.31ms.

## 4. Measured and rejected — do not retry these

Each of these was implemented and measured. They are recorded here so the next round does not
spend itself re-discovering them.

- **`BG_EFFECT_HIGH_FPS` 60 -> 30.** Made everything worse: home idle p50 20.71 -> 23.52,
  gpu 11.08 -> 13.34, frames over deadline 1-2 -> 40-79. Halving the invalidation rate does
  not halve the work; each redraw arrives colder and the irregular cadence fights the panel.
- **Warming the Liquid Glass offscreen layer** on first entry: warming one half worsens the
  other. See the BA first-entry note in `docs/planning/`.
- **Two route-transition fixes**, both rejected — one traded 7% of RenderThread for a visibly
  worse slide. `docs/planning/route-transition-frame-cost.md` has the numbers.
- **Activating the destination page at tap time instead of on the first animation frame**
  (2026-09-15). `hasActivated` was made to return true for the page `onPageSelected` had just
  selected, so the tap's own recomposition composed the destination rather than the first frame of
  the switch. It worked as designed — `home -> github` first entry moved `record draw` p99 from
  17.60 to 57.15 and `RT issue->swap` p99 from 278.95 to 33.05 — and changed nothing a user would
  feel: across four passes per tab, the worst frame neither moved to the tap nor shrank, `os` came
  out marginally worse, and every delta was inside the noise floor. Reverted. The reason it cannot
  work is in §1b: it moves composition, and the cost is rasterisation.
- **Reduced-resolution backdrop capture.** Recording at 0.5x is possible today
  (`recordLayer` takes an explicit size), but `LayerBackdrop.drawBackdrop` has no scale term
  and computes its translation in full-resolution layout coordinates, so the result is broken
  rather than soft. Needs library support. Full analysis:
  `docs/planning/backdrop-reduced-resolution.md`.

## 5. Where a real win would have to come from

The remaining cost is the full-screen Liquid Glass blur re-running whenever the background
drifts, and the background drifts every frame by design. Two directions remain, both in
RenderThread/GPU territory rather than Compose:

- **Fewer glass surfaces sampling a full-screen backdrop.** The cost scales with the count of
  glass controls, not with area or scroll — measured for the sheet in
  `docs/planning/liquid-sheet-frame-cost.md`, where an open sheet costs ~36ms RT at rest.
  Sharing one blur pass between surfaces that sample the same backdrop at the same radius
  would be appearance-neutral; removing surfaces would not be, and is out of bounds (§6).
- **Sampling a cheaper backdrop**, which needs the library change in §4.

## 6. The standing constraint

From the user, repeatedly, and it is not negotiable:

> 请你不要降级 Liquid 组件材质、动画、效果、视觉等来获得性能优化，这属于头痛砍头

Do not trade material quality, animation, or visual effect for frame time. Wasted work inside
those effects is fair game; the effects themselves are not. A proposal that ends in "and the
blur is slightly weaker" is not an optimisation, it is a downgrade with a benchmark attached.

## 7. What to do when asked to optimise

1. Reproduce §1 on the current tree first. If Compose is still ~3% of the frame, say so before
   doing anything else — it changes what the request should be.
2. If the complaint is about a specific page or interaction, measure that one; the pages differ
   a lot (`docs/planning/hwui-frame-budget.md` has the per-page table, and BA renders **zero**
   frames in a three-second dwell, which is the target shape).
3. Only claim an improvement against a settled before/after in the same panel regime, using the
   percentiles rather than the jank counter.

## 8. Measuring one page switch

`hwui_journey.sh section_switch` taps through every tab in one run, so its last 120 frames are
whichever switch happened to be last. To attribute a cost to one switch, isolate it: force-stop,
launch, settle, reset the counter, one tap, capture.

Aggregate percentiles over a ~40-frame window are close to "the worst frame", and swing
accordingly — the first pass after an install is reliably the worst in *both* builds, so discard
it or pool at least four. When the claim is about *where* the cost falls rather than how big it
is, read the per-frame totals instead: the index of the worst frame in the window says whether
work moved, and `frames > 33ms` says whether the user would notice. `frame_stages.py`'s `load()`
is importable for exactly this.

## 9. When framestats is not enough: atrace

`dumpsys gfxinfo` says which *stage* is slow. It cannot say which *work* inside RenderThread is
slow, and that is usually the question. `atrace` can, with no build change:

```bash
adb -s <serial> shell "atrace --async_start -b 32000 gfx view sched"
#   ... drive the interaction ...
adb -s <serial> shell "atrace --async_stop" > trace.txt
```

The output is ftrace text; the useful lines are `tracing_mark_write: B|<pid>|<name>` / `E`. Pair
them per thread into a stack, and aggregate by name — total, max and count per slice. Filter to
the RenderThread comm to separate app work from SurfaceFlinger's.

Slices worth knowing: `flush layers` is the layer capture and rasterisation; `Drawing x y w h`
is a full-screen draw; `CreateGraphicsPipeline` is Vulkan pipeline creation; `HWUI RAM cache: N
shaders` is a running count of compiled shader programs, so its first and last values across a
capture tell you how many were compiled during it; `drawLayersInternal for <name>` is
SurfaceFlinger compositing an extra copy of the screen, which is how mirroring shows up.

## 10. First entry is GPU-side, and it is not the glass

Narrowed 2026-09-15 by placing every frame of one switch in time. First entry into github,
against a second visit to the same tab, `total | record draw | RT | GPU` per frame:

```
first entry, 67-125ms after the tap        second visit, same phase
     67   51.2 |  14.2 |   5.1 |  20.3          33   25.9 | 0.3 | 2.4 | 20.9
     83   46.6 |   1.1 |   3.0 |  41.0          42   24.4 | 0.2 | 1.6 | 22.0
    100   45.3 |   0.2 |   3.3 |  41.2          50   27.5 | 0.4 | 6.9 | 19.1
    116   37.3 |   0.5 |   7.2 |  28.1
```

**The extra cost is GPU fill: a ~60ms window at 41ms/frame against a 12ms idle baseline and a
22ms second-visit peak.** Not composition — one 14.2ms `record draw` spike as the page's tree
composes, then 0.2-1.1ms per frame. Not RenderThread CPU: 2-7ms throughout, and an `atrace` puts
the *average* `renderFrame` slightly **higher** on the second visit (2.88ms) than the first
(1.95ms).

And the GPU is genuinely **busy**, not stalled. `/sys/class/kgsl/kgsl-3d0/gpubusy` (a ~1s window
counter that resets on read, so it gives one sample per second and nothing finer) over the second
containing the switch: 73.4% on first entry against 59.3% on a second visit, three passes each.
Fourteen points of a second is ~130ms of extra busy time, which matches the frame timeline's seven
frames at ~19ms extra almost exactly. It is real work, done once.

### It is not the glass

The obvious conclusion from all of the above is "make the first glass rasterisation cheaper", and
it is wrong. Upper bound, measured the way `backdrop-reduced-resolution.md` bounded the capture
question — a throwaway build with `fullBackdropEffectsEnabled` forced to `false` on the github
page, visibly flat, never shipped:

| github first entry | p50 | p90 | p99 | GPU p90 | frames > 33ms |
|---|---|---|---|---|---|
| glass on (shipped) | 15.39 | 46.43 | 59.53 | 27.68 | **11** |
| glass off (bound) | 17.82 | 43.06 | 52.04 | 25.09 | **11** |

Removing **all** of a page's backdrop glass buys ~3ms at p90, makes p50 slightly worse, and
removes **not one** janky frame. So the first-entry cost is the page drawing its *content* for the
first time, and the §6 constraint is not in the way of fixing it — there is nothing to trade.

### What is ruled out for first entry

Composition ordering (§4, implemented and reverted). Shader and pipeline *creation* (29 programs
compiled on first entry against zero on a second visit, 4.5ms of CPU for the lot). Reduced-
resolution backdrop capture (upper bound zero). Hosting a sheet backdrop layer nothing samples —
all four pages already gate it, github/ba/mcp through `distinctLayers` and os through
`backdropProducerActive` at `OsPage.kt:509`. Glass entirely, per the table above.

### Answered: it is the Home full-effect setting, and it is a deliberate trade

The switch animation is what makes first entry expensive, not the destination page's first draw.
Forced instant (a throwaway build, `transitionAnimationsEnabled` pinned false): **worst GPU
44.38ms -> 12.14ms** and frames over 33ms 11 -> 5, with only 18 frames drawn instead of 54. During
the slide, *both* pages draw every frame, and Home is the expensive one.

Home does not always pay it. `HomePage.kt` gates both its drift and its glass on the user's
full-effect preference:

```kotlin
val dynamicBackgroundEnabled =
    homeDynamicActive && (homeDynamicFullEffectEnabled || !runtime.isPagerScrollInProgress)
val fullBackdropEffectsEnabled =
    runtime.isPageActive && (homeDynamicFullEffectEnabled || !runtime.isPagerScrollInProgress)
```

With the setting **off** — the default — Home already pauses both during a slide. With it **on**,
Home keeps its animated background and its whole glass stack running through every tab switch.
Measured on github first entry, three passes each:

| home -> github, first entry | p50 | p90 | p99 | worst GPU | frames > 33ms |
|---|---|---|---|---|---|
| full effect on (what this device had) | 15.39 | 46.43 | 59.53 | 44.38 | **11** |
| drift pauses, glass keeps running | 15.08 | 34.15 | 59.14 | 26.73 | **7** |
| both pause = **full effect off, the default** | 15.79 | 29.89 | 66.75 | 22.55 | **4** |

So a default user sees 4 janky frames entering a page and a full-effect user sees 11. **The seven
frames are the price of the setting**, not a defect, and every measurement in this document above
was taken with the setting on.

There is no free fix here. Making full-effect users pause during the slide is exactly removing what
the setting sells them, and it is not a freeze either: `dynamicBackground = false` also moves
`renderScale` from `DYNAMIC_BACKGROUND_RENDER_SCALE` (0.25) to `1f`, so the background changes
texture as well as stopping. That is a visible change, which is why the gate is a preference and
not a blanket optimisation. §6 applies.

### Where a next round should look

The remaining 4 janky frames on the default path are the destination page drawing its content for
the first time — content, not chrome, and GPU-side: glyph atlas uploads for text at sizes the
process has not drawn yet, image decode and upload, vector icon rasterisation, render-target
allocation. None of those is visual, so a fix there would cost no material quality. Note that the
pages share typography, and entering one page first buys the next only about the noise floor
(§1b), which argues against glyphs being the dominant term.

Two notes on tooling before starting. Perfetto on this device exposes **no** `gpu.renderstages`
and no `gpu.counters` — only `android.gpu.memory` — so `swap->completed` cannot be decomposed
there; `adb shell perfetto --query` confirms it. `gpubusy` is the only readable KGSL counter
(`gpuclk`, `max_gpuclk` and the devfreq nodes are permission-denied without root), it is a ~1s
window that resets on read, so it gives one sample per second and nothing finer.
