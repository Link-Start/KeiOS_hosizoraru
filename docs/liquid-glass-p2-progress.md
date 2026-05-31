# Liquid Glass P2 Progress

> Started: 2026-05-31
> Scope: P2 items from the Backdrop component audit.

## Status

| Item | Target | Status | Evidence |
|------|--------|--------|----------|
| Dropdown Runtime Scaling | Clamp/scale dropdown blur and lens, especially ActionMenu. | Done | `LiquidGlassDropdown.kt`, `./gradlew :app:compileDebugKotlin` |
| Button Effect Standardization | Route liquid button blur/lens through `GlassStyle` and runtime scaling. | Done | `AppLiquidButtons.kt`, `./gradlew :app:compileDebugKotlin` |
| Chrome Interaction Runtime | Use `GlassEffectRuntime.interactionLensScale` in bottom/action bars. | Planned | `LiquidGlassBottomBar.kt`, `LiquidActionBar.kt` |
| LiquidSurface Runtime Entry | Provide a variant-aware runtime path while preserving raw-effect callers. | Planned | `LiquidSurfaces.kt` |

## Notes

- `UiPerformanceBudget.maxGlassBlur` is the upper bound for normal glass blur.
- Components with strong interaction effects should still honor reduced-effect runtime.
- Existing raw `blurRadius` / `lensRadius` callers should remain source-compatible.
