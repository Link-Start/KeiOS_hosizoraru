package os.kei.ui.page.main.widget.glass

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal providing the current [LiquidToastState] to the Compose tree.
 *
 * When `liquidToastEnabled` is true in user preferences, the root scaffold provides a real
 * [LiquidToastState] via this local. Leaf composables can then call:
 * ```kotlin
 * val toastState = LocalLiquidToastState.current
 * toastState?.show("Done!")
 * ```
 *
 * When null, callers should fall back to system Toast via `context.showToast(...)`.
 */
val LocalLiquidToastState = compositionLocalOf<LiquidToastState?> { null }
