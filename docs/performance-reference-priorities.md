# MIUIX / InstallerX Performance Reference Priorities

## Context

- Reference check date: 2026-05-28.
- MIUIX reference: `.tmp/miuix` at `363c37c`.
- InstallerX reference: `.tmp/InstallerX` at `68bdff4`.
- InstallerX-Revived reference: `.tmp/InstallerX-Revived` at `f48394a`.
- Scope: absorb performance architecture ideas into KeiOS while preserving current Liquid Glass visuals, animation timing, install, notification, and Super Island behavior.

## P0

| Area | Source | KeiOS Landing Direction |
| --- | --- | --- |
| Squircle / rounded surfaces | MIUIX `85ae469`, `68352b1` | Move `AppSquircle` SDF data from first-use runtime generation to Gradle pre-baked generated source, add a global squircle switch, keep RuntimeShader fallback gates. |
| Nested Backdrop safety | Backdrop Glass Bottom Sheet docs, MIUIX glass components | Audit glass-on-glass paths and route child glass through `exportedBackdrop` where a parent glass surface must become the child backdrop. This avoids self-referential `layerBackdrop` loops and RenderThread crashes. |

## P1

| Area | Source | KeiOS Landing Direction |
| --- | --- | --- |
| Backdrop / blur cache | MIUIX `5e75455`, `132586a` | Cache blur/render-effect results by stable inputs, reuse shader uniform buffers, release graphics layers on detach, and skip RuntimeShader effects through one central capability gate. |
| Dynamic backgrounds | InstallerX-Revived `5dfb116` | Keep Home/HDR/background visuals, move frame ticks to `Modifier.Node` with `invalidateDraw()`, update shader uniforms only when inputs change. |

## P2

| Area | Source | KeiOS Landing Direction |
| --- | --- | --- |
| Text / color producer | MIUIX `94f1ff9` | Use producer/lambda color APIs for animated status text, pills, borders, progress text, and title highlights. |
| Top bar layout state | MIUIX `81a1401` | Save expensive measured title heights with saveable state and initialize animation values from current visibility state. |
| Popup / reveal animation | MIUIX `054e2a1` | Share one popup reveal helper across dropdown/action menus to reduce per-component clipping animation variants. |
| Settings row specialization | InstallerX-Revived `6dc4099`, `2d71338` | Continue splitting universal settings rows into narrow navigation/switch/value/action rows and decouple disabled visuals from clickability. |

## Guardrails

- Treat InstallerX-Revived code as concept-only because the repository is GPL-3.0 overall.
- Keep Backdrop changes aligned with `exportedBackdrop` guidance for glass-on-glass.
- Prefer release/benchmark evidence for final performance claims; debug HWUI bars are triage evidence.
- Preserve UI richness. Performance work should move work to draw/layout, cache stable inputs, and reduce self-referential backdrop paths.
