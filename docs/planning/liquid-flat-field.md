# Glass over a flat field needs no layer of its own

## Current state — 2026-09-23

Shipped across every page whose glass samples a single colour. Pixel-identical to the glass it replaces,
and the largest RenderThread change these documents record. **Confirmed on the phone** (2026-09-24, below):
BA office total frame time halves and frames over 33ms fall from 150 to 24; Settings loses every frame over
33ms.

Follow-up, 2026-09-24: a flat-field `LiquidSurface`, and the glass controls over a flat field, no longer go
through the library's `drawBackdrop` at all — its outline clip moved inside the surface's layer, which removed a
per-frame CPU mask on every moving card. See `liquid-glass-clip-and-resolution.md`.

## On the phone — 2026-09-24

`5eea1f50` (Android 17, 1220x2656, 120Hz, 8.29ms vsync), `os.kei.diag` with the harness applied, four
interleaved passes each, same scroll journey as the AVD. `scrcpy` was mirroring the device throughout, so
per the frame-performance skill these are A/B deltas, not absolute figures.

| page | total p50 | RT issue→swap p50 | GPU p50 | frames over 33ms | over one vsync |
|---|---|---|---|---|---|
| BA office, before → after | 24.3 → **11.6** | 8.5 → 4.2 | 3.0 → 4.1 | **150 → 24** | 99% → 87% |
| Settings, before → after | 11.6 → **8.8** | 5.3 → 3.3 | 4.6 → 3.8 | **23 → 0** | 93% → 58% |

Pass ranges do not overlap on either page (BA total 23.7-35.6 against 11.0-12.5). The AVD's open question
is answered: BA's GPU stage does rise, by ~1.1ms, and the total still halves because RenderThread falls by
twice that. Pixels, both modes captured at one state by flipping the flag across a background/foreground:
BA (top and scrolled with the pile formed) and OS byte-identical; Settings off by 1-2 levels in one channel
on 9,676 pixels (max 6/765, none over 3%) where the AVD was byte-identical, most likely the phone's
wide-gamut colour path rounding the CPU-composited export differently.

Interleaved in one build (`flat_off` flag against the default), four passes each, scroll journey:

| page | RT issue→swap p50, before → after | total p50, before → after | pass ranges overlap? |
|---|---|---|---|
| BA office | 21.4 → **4.4** | 84.0 → 43.1 | no (21.2-29.2 vs 4.4-4.9) |
| Settings | 13.8 → **3.1** | 51.0 → 18.4 | no |
| OS | 3.8 → 3.7 | 36.9 → 36.6 | yes: no change |
| GitHub | 3.2 → 3.4 | 24.3 → 23.3 | yes: no change |
| MCP | 3.7 → 3.3 | 25.9 → 27.2 | yes: no change |

OS, GitHub and MCP were already ~3.5ms of RenderThread; `liquid-sheet-frame-cost.md` called them done and
this agrees. On BA the GPU stage rose on the AVD (~13-15 → 18.3ms) while total frame time halved and more
frames were drawn in the same journey; on Settings GPU fell (14.1 → 13.0) as the screen-sized scene recording
went away. Whether BA's GPU rise is real or emulator queueing is a question for hardware.

## The idea

A lot of the app's glass samples one colour:

- `MainPageBackdropSet.contentMaterial`, which every card on BA, MCP, GitHub and OS samples, was
  `rememberCanvasBackdrop { drawRect(cardMaterialColor) }`;
- `AppManagedBackgroundHost`'s scene, which every route without a custom background image hands its content
  (Settings, the GitHub history route, …), recorded `drawRect(baseColor)` over an **empty** box;
- the student guide's page backdrop, `rememberAppPageBackdrop`, whose producer box is empty too.

Blurring, lensing, refracting or chromatically splitting one colour returns that colour, and `vibrancy()` is
a saturation matrix, so every glass effect chain in the app computes one known colour from these. Every
surface still recorded that colour into an offscreen layer and ran a `RenderEffect` over it, every frame.

The pieces, in `UniformColorBackdrop.kt`:

1. **`UniformColorBackdrop`** draws exactly what the canvas backdrop drew (`CanvasBackdrop.drawBackdrop` only
   invokes its lambda — read from the 2.0.1 bytecode) and tells a consumer the field is flat. The managed
   scene and the guide page hand one out when no background image is painting, which also skips recording
   the screen-sized scene layer.
2. **`liquidFlatField(backdrop)`** returns what to sample instead: the field after `vibrancy()`, applied
   through the same colour filter on the GPU. A surface samples that and runs **no effects**.
   `DrawBackdropNode.updateEffects` sets its layer's `RenderEffect` from the effect scope; an empty scope
   leaves it null and HWUI draws the layer inline. The clip, highlight, shadows, inner shadow, `layerBlock`
   (press deformation, card-pile transform), export and surface draws are all still `drawBackdrop`'s own.
   Used by `LiquidSurface`, `AppLiquidBadges`, `AppLiquidButtons`, `AppLiquidCheckbox` and
   `AppLiquidSearchField`.
3. **`rememberLiquidContentExport`** (`AppSurfaceBox`, `LiquidRoundedCard`): a card over a flat field hands its
   content `UniformColorBackdrop(surfaceColor over vibrancy(field))` instead of recording an exported layer.
   `DrawBackdropNode` records its export from `onDrawBehind`, the effected backdrop, `onDrawSurface` and
   `onDrawFront`, and `LiquidSurface` uses only the surface. So pills and panels inside a card see a flat
   field too and take path 2 themselves.

Excluded, each for a stated reason: a hue `tint` (it blends against the layer's contents; without a
`RenderEffect` the layer is inline and the blend would reach whatever is underneath), and any effect chain
that changes colour other than `vibrancy()`.

## Two bugs found on the way, both now pinned

**A new backdrop object does not make `DrawBackdropNode` redraw.** Its update calls `invalidateDrawCache`,
which only re-observes the effects. The first version made a fresh `UniformColorBackdrop` per colour, and on
the OS overview card — whose colour follows refresh state — the pills inside kept drawing the grey export
from before the card turned blue. The drawn colours were logged to confirm it. Fixed by keeping one instance
whose `color` is snapshot state, so a change invalidates exactly the draws that read it, the way a
`LayerBackdrop` invalidates its samplers. Robolectric redraws on recomposition regardless and cannot show the
bug, so `UniformColorBackdropTest.aFlatFieldKeepsOneInstanceAndChangesColourInPlace` pins the contract
(mutation-checked).

This also **retracts** the first version's explanation. It blamed pressable cards and gated them out; a
controlled test (the same card, glass path, clickable against still) rendered byte-identically, and the gate
had only coincided with the one card whose colour changes. Pressable surfaces and card-pile cards now take
the flat path, `layerBlock` and inner shadow included.

**A surface that positions its own draws by the effect scope's padding cannot drop its effects.**
`AppOverviewBatchedLiquidPillFlow` draws its pill fills at geometry that assumes the padding its blur and
lens set on the effect scope; with no effects the padding is zero and the fills landed outside the clip —
the OS pills lost their tint entirely. Both overview pill batches keep their effects (they still gain from
sampling a flat export). Every site that does use `liquidFlatField` paints a full-layer `drawRect`, which
padding cannot move.

## Verification

- **Pixels**, `os.kei.diag` on the A17 AVD, glass (`flat_off`) against flat, light and dark: OS, MCP, GitHub,
  BA, Settings (top and scrolled) and the student guide — max 2/765 everywhere once live text (clock,
  countdowns, relative times, badge counts) is excluded; OS, Settings and the guide byte-identical. BA with the
  card pile formed was compared at one scroll state by flipping the flag across a background/foreground
  (`.planning/flat-field-tools/live.sh`): max 3/765 light, 1/765 dark, against a glass-vs-glass control of
  2/765. The final build, with the harness removed, against the glass captures: only live text.
- **Tests.** `UniformColorBackdropTest` (the CPU vibrancy equals `ColorMatrix.setToSaturation(1.5)`; a card
  over a flat field exports `surface over vibrancy(field)` and records nothing; tinted and live-layer cards keep
  their layer; a surface paints the computed colour and follows a colour change; the stable-instance contract).
  `AppManagedBackgroundLiquidBackdropTest`: no background → a flat field of the base colour; a background image
  → the recorded composite. Mutations — dropping the saturation, dropping the surface colour, a fresh instance
  per colour, always recording the scene — each fail a test.

## Measured and not pursued

- **The BA event calendar.** Its cards sample a full-screen vertical gradient, not one colour, so the exact
  trick does not apply. Upper bound, a throwaway build with the cards sampling a flat colour (visibly wrong,
  never shipped), six interleaved passes: RT 19.6 → 17.6ms. The calendar's cost is its covers; an approximate
  gradient path is not worth its risk for 2ms. A first bound run read 40.8 → 18.6, and it was the baseline's
  noise (range 21.7-65.0): re-measure any bound before acting on it.
- **The BA guide catalog** samples the same kind of gradient; not attempted, for the same reason. Its BGM
  track list surfaces are tinted anyway.
- **Home** samples its live animated background; nothing is flat there.
- **Sheets, dialogs, menus, toasts, bars** sample live content or a sheet's export of it.
- **`AppSwitch` and `LiquidProgressBars`** sample a combination with their own track layer, not a flat field.
- **Hue-tinted surfaces over a flat field** (the GitHub tracked card's info panels) keep their layer.
  Supporting them means reproducing Skia's hue blend on the CPU; possible, unverified.

## Reproducing

The harness is kept out of the tree as `.planning/flat-field-ab-harness.patch` (a `flags` directory under
`getExternalFilesDir`, read in `onCreate`/`onResume`; `flat_off` restores the recorded layers), with the
scripts in `.planning/flat-field-tools/`: `ab4.sh` (interleaved A/B), `capr.sh` (cold-launch capture),
`live.sh` (both modes at one state), `pdiff.py`, `sum2.py`. On the phone, screenshots need the display id
(`screencap -p -d <id>`), and tap targets are best read from the test tags with `uiautomator dump`.

Launch with `am start -n os.kei.diag/os.kei.MainActivity`, not `monkey -p`: in this round the captures after
`monkey -p os.kei.diag` showed `os.kei.debug`, and three comparisons measured the wrong app before a
screenshot gave it away. Check the version string in the first capture.
