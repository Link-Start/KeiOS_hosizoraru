# MIUIX / InstallerX Performance Reference Priorities

## Context

- Reference check date: 2026-05-28.
- MIUIX reference: `.tmp/miuix` at `363c37c`.
- InstallerX reference: `.tmp/InstallerX` at `68bdff4`.
- InstallerX-Revived reference: `.tmp/InstallerX-Revived` at `f48394a`, refreshed from `origin/main` on 2026-05-28.
- Scope: absorb performance architecture ideas into KeiOS while preserving current Liquid Glass visuals, animation timing, install, notification, and Super Island behavior.
- Clean-room rule: use public behavior and architectural ideas as prompts, then write KeiOS-native code against existing project contracts. Do not copy GPL source, structure, names, or implementation details.

## P0

| Area | Source | KeiOS Landing Direction | Status |
| --- | --- | --- |
| Squircle / rounded surfaces | MIUIX `85ae469`, `68352b1` | Move `AppSquircle` SDF data from first-use runtime generation to Gradle pre-baked generated source, add a global squircle switch, keep RuntimeShader fallback gates. | Landed: `bakeAppSquircleSdf`, generated SDF source, `LocalAppSquircleEnabled`, runtime fallback retained. |
| Nested Backdrop safety | Backdrop Glass Bottom Sheet docs, MIUIX glass components | Audit glass-on-glass paths and route child glass through `exportedBackdrop` where a parent glass surface must become the child backdrop. This avoids self-referential `layerBackdrop` loops and RenderThread crashes. | Landed: shared floating surface supports `exportedBackdrop`; BA BGM mini-player consumes combined/exported backdrop. Continue auditing new glass-on-glass components as they are added. |
| MainScreen route back coordination | InstallerX-Revived `c612369`, `c77de9e` architecture idea | Keep ViewModel navigation as one-off events, keep route/back effects in Route/NavHost, and centralize child-route pop decisions so predictive and commit-only handlers call the same entry. | Landed first pass: `MainScreenBackCoordinator` now owns child-route back behavior and BA catalog return signaling; next pass should audit remaining page-local modal back handlers. |

## P1

| Area | Source | KeiOS Landing Direction | Status |
| --- | --- | --- |
| Backdrop / blur cache | MIUIX `5e75455`, `132586a` | Cache blur/render-effect results by stable inputs, reuse shader uniform buffers, release graphics layers on detach, and skip RuntimeShader effects through one central capability gate. | Landed: `appGlassRuntimeEffectsEnabled()` and `activeGlassBackdrop()` gate shared glass controls, action bars, bottom bars, sliders, status pills, and support blocks. Backdrop library internals keep blur cache ownership. |
| Dynamic backgrounds | InstallerX-Revived `5dfb116` | Keep Home/HDR/background visuals, move frame ticks to `Modifier.Node` with `invalidateDraw()`, update shader uniforms only when inputs change. | Landed: `BgEffectModifier` starts frame ticks only while dynamic background, effect background, and visible alpha are active; shader uniforms already update through cached `BgEffectPainter` setters. |

## P2

| Area | Source | KeiOS Landing Direction | Status |
| --- | --- | --- |
| Text / color producer | MIUIX `94f1ff9` | Use producer/lambda color APIs for animated status text, pills, borders, progress text, and title highlights. | Landed: `StatusPill`, `LiquidLinearProgressBar`, `LiquidMusicProgressBar`, and `LiquidCircularProgressBar` accept color providers; dynamic borders/backgrounds/progress arcs read color in draw/layout paths. Remaining: title highlight and specialty text components. |
| Top bar layout state | MIUIX `81a1401` | Save expensive measured title heights with saveable state and initialize animation values from current visibility state. | Landed: title width estimation is saveable by title, and title card layout is derived through a pure `deriveAppTopBarTitleLayout` function. Remaining: measured-height state for future large-title variants. |
| Popup / reveal animation | MIUIX `054e2a1` | Share one popup reveal helper across dropdown/action menus to reduce per-component clipping animation variants. | Landed: `SnapshotWindowListPopup` uses `snapshotPopupReveal` for shared scale, translation, alpha, and clipping. Existing dropdown/action menus inherit it. |
| Settings row specialization | InstallerX-Revived `6dc4099`, `2d71338` | Continue splitting universal settings rows into narrow navigation/switch/value/action rows and decouple disabled visuals from clickability. | Landed: `SettingsNavigationItem`, `SettingsValueItem`, `SettingsPickerItem`, `SettingsButtonActionItem`, and `SettingsToggleItem(enabled)` wrap the shared row core; disabled visuals and clickability are separated. Direct `SettingsActionItem` use is now kept inside the shared settings support layer. |
| Install/share action state machine | InstallerX-Revived `5291941`, `e56e817` architecture idea | Keep GitHub download/install/share UI contracts stable, but make user actions route through small coordinators with explicit pending, active, result, and notification-facing state. | Planned: useful for the next GitHub managed-install cleanup round after current MainScreen P0 closeout. |

## Verification Log

- 2026-05-28 P0/P1/P2 implementation pass:
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
  - AVD debug smoke for P1: install, launch `os.kei.debug/os.kei.LauncherAndroidDesigns`, swipe page transitions, logcat fatal check.
  - AVD debug smoke for P2: install debug, open Settings, switch to Visual section, open/close theme dropdown, logcat fatal/RenderThread check.
  - P2 continuation: permission rows migrated to `SettingsButtonActionItem`; direct `SettingsActionItem` usage is support-internal only.
- 2026-05-28 InstallerX clean-room P0 navigation pass:
  - Added `MainScreenBackCoordinator` with unit coverage for root, standard child route, and BA catalog back decisions.
  - `ktlint -F` scoped to the touched MainScreen back files.
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
  - `rg "Navigator|navigator|navigate\\(|push\\(|pop\\(" app/src/main/java/os/kei/ui/page/main -g '*ViewModel.kt'`

## Guardrails

- Treat InstallerX-Revived code as concept-only because the repository is GPL-3.0 overall.
- Keep Backdrop changes aligned with `exportedBackdrop` guidance for glass-on-glass.
- Prefer release/benchmark evidence for final performance claims; debug HWUI bars are triage evidence.
- Preserve UI richness. Performance work should move work to draw/layout, cache stable inputs, and reduce self-referential backdrop paths.
