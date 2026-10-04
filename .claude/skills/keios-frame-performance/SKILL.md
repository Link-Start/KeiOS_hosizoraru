---
name: keios-frame-performance
description: Investigate or tune KeiOS frame time, jank, scrolling, or page-transition smoothness.
---

# KeiOS Frame Performance

Use this skill when performance itself is part of the request. Keep task instructions brief and load measurement detail only when capturing data.

## Choose the evidence

- Idle rendering, scrolling, or page entry: start with [the frame-budget record](../../../docs/planning/hwui-frame-budget.md).
- Expanded card layers: read [the expanded-card record](../../../docs/planning/expanded-card-glass-layer.md).
- Route motion: read [the route-transition record](../../../docs/planning/route-transition-frame-cost.md).
- Liquid Glass or Backdrop changes: use the relevant records for [glass clipping and resolution](../../../docs/planning/liquid-glass-clip-and-resolution.md), [flat-field rendering](../../../docs/planning/liquid-flat-field.md), [sheet frame cost](../../../docs/planning/liquid-sheet-frame-cost.md), or [reduced-resolution capture](../../../docs/planning/backdrop-reduced-resolution.md).
- For new timing data, load [measurement setup](references/measurement.md). For a first page entry, also read [page-entry findings](references/page-entry.md). When framestats identifies RenderThread as the slow stage but not the work, read [RenderThread tracing](references/renderthread-trace.md).

Planning records contain dated measurements, not current acceptance results. Recheck the affected source and target before using an old result to choose or accept a change. Identify the measured pipeline stage before proposing a fix; general Compose guidance alone does not establish KeiOS's bottleneck.

Preserve the user's visual-quality constraint: do not reduce Liquid component material, animation, effects, or appearance to improve a performance number. Optimize avoidable work while keeping the selected journey and device conditions comparable.
