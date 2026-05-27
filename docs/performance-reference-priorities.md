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
| Install/share action state machine | InstallerX-Revived `5291941`, `e56e817` architecture idea | Keep GitHub download/install/share UI contracts stable, but make user actions route through small coordinators with explicit pending, active, result, and notification-facing state. | Landed: selected-asset delivery, active-preview readiness, waiting-install records, managed-install start/request/progress/staged/success records, and active commit readiness now resolve through pure state-machine helpers with unit coverage. |

## P2

| Area | Source | KeiOS Landing Direction | Status |
| --- | --- | --- |
| Text / color producer | MIUIX `94f1ff9` | Use producer/lambda color APIs for animated status text, pills, borders, progress text, and title highlights. | Complete: `StatusPill`, `LiquidLinearProgressBar`, `LiquidMusicProgressBar`, `LiquidCircularProgressBar`, and `LiquidGlassDropdownItem` dynamic text/icon colors read through providers or draw/layout paths; Home title/icon HDR radial highlight brush is cached by draw size. |
| Top bar layout state | MIUIX `81a1401` | Save expensive measured title heights with saveable state and initialize animation values from current visibility state. | Complete for current KeiOS chrome: title width estimation is saveable by title, title card layout is derived through pure `deriveAppTopBarTitleLayout`, current fixed-height title card has no active large-title measured-height path, and popup reveal animation state now initializes from current visibility. |
| Popup / reveal animation | MIUIX `054e2a1` | Share one popup reveal helper across dropdown/action menus to reduce per-component clipping animation variants. | Complete: `SnapshotWindowListPopup` uses shared `snapshotPopupReveal`, dropdown/action menus inherit it, and popup render/alpha/fraction state initializes from the current `show` value. |
| Settings row specialization | InstallerX-Revived `6dc4099`, `2d71338` | Continue splitting universal settings rows into narrow navigation/switch/value/action rows and decouple disabled visuals from clickability. | Complete: `SettingsNavigationItem`, `SettingsValueItem`, `SettingsPickerItem`, `SettingsButtonActionItem`, and `SettingsToggleItem(enabled)` wrap the shared row core; disabled visuals and clickability are separated. Direct `SettingsActionItem` use is support-internal only. |

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
- 2026-05-28 InstallerX clean-room P1 install/share pass:
  - Added `resolveSelectedAssetDeliveryPlan` for direct delivery, managed-install launch, and active managed-install commit.
  - Routed `GitHubShareImportInstallFlowCoordinator` through the pure plan before invoking delivery side effects.
  - Added unit coverage for disabled managed install, managed launch, commit-ready progress, and stale progress handling.
  - `ktlint -F` scoped to the touched GitHub share/import state-machine files.
  - `./gradlew :app:testDebugUnitTest --tests 'os.kei.ui.page.main.github.share.GitHubShareImportStateMachineTest'`
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
- 2026-05-28 InstallerX clean-room P1 delivery continuation:
  - Added active-preview delivery readiness plans for missing preview, disabled install action, missing selected asset, and ready selected asset.
  - Moved waiting-install track record construction into a pure helper so `GitHubShareImportDeliveryCoordinator` only coordinates IO, notifications, and external delivery.
  - Extended `GitHubShareImportStateMachineTest` for active preview readiness and pending-track record construction.
  - `ktlint -F` scoped to the touched GitHub share/import delivery files.
  - `./gradlew :app:testDebugUnitTest --tests 'os.kei.ui.page.main.github.share.GitHubShareImportStateMachineTest'`
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
- 2026-05-28 InstallerX clean-room P1 managed-install completion:
  - Added `GitHubManagedInstallStateMachine` for managed-install start state, initial active record, install request, progress merge, staged record, attach candidate, and active commit readiness.
  - Reduced `GitHubManagedInstallPrepareActions`, `GitHubManagedInstallProgressNotifier`, and `GitHubManagedInstallResultApplier` to side-effect coordination plus helper calls.
  - Extended state-machine unit tests for managed-install request trimming, record merge, staged data fallback, attach candidate selection, and commit readiness.
  - `ktlint -F` scoped to the touched GitHub share/import managed-install files.
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew :app:compileDebugKotlin`
  - `git diff --check`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
- 2026-05-28 P2 color producer continuation:
  - Routed dropdown/action-menu animated text and icon colors through `ColorProducer` and draw-time icon tinting while keeping the spring transition.
  - Moved Home title/icon HDR radial highlight brush into `drawWithCache`; sweep progress continues to read in draw.
  - `ktlint -F` scoped to touched P2 files.
  - `./gradlew :app:compileDebugKotlin`
- 2026-05-28 P2 completion pass:
  - Initialized shared snapshot popup reveal `Animatable` and render flags from the current `show` value to remove the first-frame reveal mismatch.
  - Audited active P2 rows and marked them complete for current KeiOS chrome, dropdown, popup, Home HDR, and Settings-row surfaces.
  - `ktlint -F` scoped to touched P2 files.
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`
  - `rg "Remaining:|In progress" docs/performance-reference-priorities.md`
  - `rg "collectAsState\\(" app/src/main/java/os/kei -g '*.kt'`
- 2026-05-28 P2 final audit pass:
  - Rechecked the P2 table and implementation state: no `Remaining:` / `In progress` marker remains, no app `collectAsState(` usage remains, and direct `SettingsActionItem` use is confined to the settings support wrapper layer.
  - Confirmed shared popup reveal state initializes from the current `show` value through `SnapshotWindowListPopup` render, alpha, and fraction state.
  - Removed the remaining UI-layer `SharedFlow` parameter from OS page event binding; the Route now owns the flow and passes a remembered event collector into `BindOsPageEvents`.
  - `./gradlew :app:compileDebugKotlin`
  - `./gradlew :app:testDebugUnitTest`
  - `git diff --check`

## Guardrails

- Treat InstallerX-Revived code as concept-only because the repository is GPL-3.0 overall.
- Keep Backdrop changes aligned with `exportedBackdrop` guidance for glass-on-glass.
- Prefer release/benchmark evidence for final performance claims; debug HWUI bars are triage evidence.
- Preserve UI richness. Performance work should move work to draw/layout, cache stable inputs, and reduce self-referential backdrop paths.
