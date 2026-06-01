@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Global toast bridge that routes toast messages to either [LiquidToastState] (when the Compose
 * host is active and the user has enabled liquid toast) or falls back to Android system Toast.
 *
 * This solves the fundamental problem: toast calls happen in ViewModels, coroutines, and other
 * non-Compose contexts that can't access CompositionLocals. The bridge provides a single entry
 * point that works everywhere.
 *
 * ## Setup
 *
 * In your root scaffold (e.g. MainScreenNavHost), call:
 * ```kotlin
 * val toastState = rememberLiquidToastState()
 * BindLiquidToastBridge(toastState)
 * LiquidToastHost(state = toastState, backdrop = backdrop)
 * ```
 *
 * ## Usage (anywhere in the app)
 *
 * ```kotlin
 * // Simple message
 * AppToastBridge.show(context, "Done!")
 *
 * // With string resource
 * AppToastBridge.show(context, R.string.toast_success)
 *
 * // With icon
 * AppToastBridge.show(context, "Saved", icon = lucideCheckIcon())
 * ```
 */
object AppToastBridge {
    // WeakReference prevents memory leaks if DisposableEffect.onDispose fails to call unregister.
    // The Compose tree won't be held alive by this global singleton.
    private var activeStateRef = java.lang.ref.WeakReference<LiquidToastState?>(null)
    private var liquidToastEnabledProvider: () -> Boolean = { true }
    private var reduceToastInterruptionEnabledProvider: () -> Boolean = { false }

    /**
     * Register the active [LiquidToastState]. Called by [BindLiquidToastBridge].
     */
    internal fun register(
        state: LiquidToastState,
        liquidToastEnabled: () -> Boolean,
        reduceToastInterruptionEnabled: () -> Boolean,
    ) {
        activeStateRef = java.lang.ref.WeakReference(state)
        liquidToastEnabledProvider = liquidToastEnabled
        reduceToastInterruptionEnabledProvider = reduceToastInterruptionEnabled
    }

    /**
     * Unregister the active state. Called when the Compose host is disposed.
     */
    internal fun unregister(state: LiquidToastState) {
        if (activeStateRef.get() === state) {
            activeStateRef.clear()
            liquidToastEnabledProvider = { true }
            reduceToastInterruptionEnabledProvider = { false }
        }
    }

    /**
     * Show a toast message. Routes to Liquid Toast if enabled and active, otherwise system Toast.
     */
    fun show(
        context: Context,
        message: String,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short,
    ) {
        val state = activeStateRef.get()
        if (state != null && liquidToastEnabledProvider()) {
            state.show(message = message, icon = icon, iconTint = iconTint, duration = duration)
        } else {
            val toastDuration =
                if (duration == LiquidToastDuration.Long) {
                    Toast.LENGTH_LONG
                } else {
                    Toast.LENGTH_SHORT
                }
            Toast.makeText(context, message, toastDuration).show()
        }
    }

    /**
     * Show a low-priority hint only through Liquid Toast. These hints stay silent when Liquid
     * Toast is disabled or when the user reduces toast interruption.
     */
    fun showLiquidOnly(
        message: String,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short,
    ) {
        val state = activeStateRef.get() ?: return
        if (liquidToastEnabledProvider() && !reduceToastInterruptionEnabledProvider()) {
            state.show(message = message, icon = icon, iconTint = iconTint, duration = duration)
        }
    }

    /**
     * Show a toast with a string resource. Routes to Liquid Toast if enabled and active.
     */
    fun show(
        context: Context,
        @StringRes stringRes: Int,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short,
    ) {
        show(
            context = context,
            message = context.getString(stringRes),
            icon = icon,
            iconTint = iconTint,
            duration = duration,
        )
    }

    /**
     * Show a toast with a formatted string resource.
     */
    fun show(
        context: Context,
        @StringRes stringRes: Int,
        vararg formatArgs: Any,
        icon: ImageVector? = null,
        iconTint: Color = Color.Unspecified,
        duration: LiquidToastDuration = LiquidToastDuration.Short,
    ) {
        show(
            context = context,
            message = context.getString(stringRes, *formatArgs),
            icon = icon,
            iconTint = iconTint,
            duration = duration,
        )
    }
}

/**
 * Binds the given [LiquidToastState] to the global [AppToastBridge] for the lifetime of this
 * composable. Place this in your root scaffold alongside [LiquidToastHost].
 */
@Composable
fun BindLiquidToastBridge(
    state: LiquidToastState,
    liquidToastEnabled: Boolean = true,
    reduceToastInterruptionEnabled: Boolean = false,
) {
    val currentLiquidToastEnabled = rememberUpdatedState(liquidToastEnabled)
    val currentReduceToastInterruptionEnabled = rememberUpdatedState(reduceToastInterruptionEnabled)
    DisposableEffect(state) {
        AppToastBridge.register(
            state = state,
            liquidToastEnabled = { currentLiquidToastEnabled.value },
            reduceToastInterruptionEnabled = { currentReduceToastInterruptionEnabled.value },
        )
        onDispose {
            AppToastBridge.unregister(state)
        }
    }
}
