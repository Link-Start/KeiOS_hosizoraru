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
build, home idle, 118 frames, **2026-09-15**:

| stage | p50 | share |
|---|---|---|
| input | 0.00 | 0.0% |
| animation | 0.19 | 1.3% |
| measure+layout | 0.07 | 0.4% |
| record draw | 0.22 | 1.4% |
| sync | 0.43 | 2.4% |
| **RT issue->swap** | **6.11** | **31.9%** |
| **swap->completed (GPU)** | **10.31** | **59.4%** |
| total | 17.90 | |

**Everything Compose does is 0.48ms of a 17.9ms frame — 3.1%.** Input, animation, measure,
layout and recording draw commands, added together. RenderThread plus GPU is 91%.

So the ceiling on *every* recomposition optimisation in this app is half a millisecond, and
most of that half is animation and draw recording, which cannot go to zero. A change that
halves recomposition and reports a smoother app is reporting noise.

This is not a one-off reading. It reproduces the table in `docs/planning/hwui-frame-budget.md`,
first measured months earlier, on a tree that has changed substantially since.

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
