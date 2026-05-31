# Liquid Glass P1 Progress

> Started: 2026-05-31
> Scope: P1 items from the Backdrop component audit.

## Status

| Item | Target | Status | Evidence |
|------|--------|--------|----------|
| Dialog Backdrop | Use scene backdrop for the dialog surface and export a dialog backdrop for child actions. | Done | `LiquidGlassDialog.kt`, `AppLiquidDialogActions.kt`, `./gradlew :app:compileDebugKotlin` |
| BottomSheet Backdrop | Render the sheet surface with Backdrop and export the sheet layer for nested glass content. | Done | `LiquidGlassBottomSheet.kt`, `AppStandaloneLiquidButtons.kt`, `./gradlew :app:compileDebugKotlin` |
| Switch Static Thumb | Keep a visible static thumb lens and intensify it on press/drag. | Planned | `AppSwitch.kt` |
| ProgressBar Combined Backdrop | Let linear progress bars combine scene/card backdrop with the local track layer. | Planned | `LiquidProgressBars.kt` |

## Notes

- Backdrop reference pattern: scene uses `layerBackdrop`, glass uses `drawBackdrop`.
- Parent glass surfaces should use `exportedBackdrop` when child glass needs to refract the parent surface.
- Slider-like controls should combine scene backdrop and local track backdrop through `rememberCombinedBackdrop`.
