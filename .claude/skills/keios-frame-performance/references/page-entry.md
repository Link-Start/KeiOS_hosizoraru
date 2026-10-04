# Historical first-entry findings

Use this reference only for first-time page-switch investigations. These captures were taken on phone 5eea1f50 (1220×2656, 120Hz LTPO, HyperOS), releaseDiagnostic, in September 2026. Reproduce the affected journey on the current source before relying on the figures.

## First visit and repeat

The first visit to a tab showed more long frames than returning to that tab in the same process. An isolated GitHub switch recorded 11 frames over 33ms on first entry and 2 on a second visit. The initial UI composition spike was brief; the following frames had elevated GPU time. The original all-tab comparison and noise-floor discussion remain in [the frame-budget record](../../../../docs/planning/hwui-frame-budget.md).

In the same investigation, disabling all backdrop glass on the destination page changed p90 by only a few milliseconds and did not reduce the count of frames over 33ms. That result did not support treating destination-page glass as the cause.

## Home full-effect preference

Historical GitHub first-entry captures:

| Home behavior during the switch | p90 (ms) | worst GPU (ms) | frames over 33ms |
| --- | ---: | ---: | ---: |
| Full effects continue | 46.43 | 44.38 | 11 |
| Background drift pauses; glass continues | 34.15 | 26.73 | 7 |
| Both pause (the default setting) | 29.89 | 22.55 | 4 |

The seven-frame difference was associated with the user's full-effect setting. Do not silently change that preference or weaken its visual behavior to improve a benchmark. Recheck the current setting semantics and source before proposing any behavior change.

The historical investigation pointed to the destination page's first content draw and Home's animation during the transition as candidate work. Treat that as a hypothesis to test, not a current diagnosis. See [the glass and resolution findings](../../../../docs/planning/liquid-glass-clip-and-resolution.md) and the frame-budget record for related measurements.
