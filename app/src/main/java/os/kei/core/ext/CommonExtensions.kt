package os.kei.core.ext

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import os.kei.ui.page.main.widget.glass.AppToastBridge
import os.kei.ui.page.main.widget.glass.LiquidToastDuration

/**
 * Common extension functions to reduce boilerplate across the codebase.
 *
 * These replace repeated patterns like:
 * - `.orEmpty().trim().ifBlank { fallback }` → `.trimOrDefault(fallback)`
 * - `Toast.makeText(ctx, getString(R.string.x), Toast.LENGTH_SHORT).show()` → `ctx.showToast(R.string.x)`
 * - `error.message.orEmpty().ifBlank { error.javaClass.simpleName }` → `error.userMessage()`
 */

/**
 * Returns the trimmed string if non-null and non-blank, otherwise returns [default].
 * Replaces the common `.orEmpty().trim().ifBlank { default }` chain (44+ occurrences).
 */
inline fun String?.trimOrDefault(default: () -> String): String {
    val trimmed = this?.trim().orEmpty()
    return trimmed.ifBlank { default() }
}

/**
 * Returns the trimmed string if non-null and non-blank, otherwise returns [default].
 */
fun String?.trimOrDefault(default: String): String {
    val trimmed = this?.trim().orEmpty()
    return trimmed.ifBlank { default }
}

/**
 * Returns the trimmed string if non-null and non-blank, otherwise returns empty string.
 */
fun String?.trimOrEmpty(): String = this?.trim().orEmpty()

/**
 * Shows a toast message, automatically routing to Liquid Glass Toast when enabled.
 *
 * This is the primary toast entry point for the entire app. It checks [AppToastBridge] first:
 * - If Liquid Toast is enabled and the Compose host is active → shows Liquid Glass Toast
 * - Otherwise → falls back to Android system Toast
 *
 * All 99+ call sites using `context.showToast(...)` automatically get Liquid Toast support
 * without any code changes.
 */
fun Context.showToast(@StringRes stringRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    AppToastBridge.show(
        context = this,
        message = getString(stringRes),
        duration = if (duration == Toast.LENGTH_LONG) {
            LiquidToastDuration.Long
        } else {
            LiquidToastDuration.Short
        }
    )
}

/**
 * Shows a toast with a formatted string resource, routing through [AppToastBridge].
 */
fun Context.showToast(@StringRes stringRes: Int, vararg formatArgs: Any, duration: Int = Toast.LENGTH_SHORT) {
    AppToastBridge.show(
        context = this,
        message = getString(stringRes, *formatArgs),
        duration = if (duration == Toast.LENGTH_LONG) {
            LiquidToastDuration.Long
        } else {
            LiquidToastDuration.Short
        }
    )
}

/**
 * Shows a toast with the given message string, routing through [AppToastBridge].
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    AppToastBridge.show(
        context = this,
        message = message,
        duration = if (duration == Toast.LENGTH_LONG) {
            LiquidToastDuration.Long
        } else {
            LiquidToastDuration.Short
        }
    )
}

/**
 * Returns a user-friendly error message: the exception's message if non-blank,
 * otherwise the simple class name.
 * Replaces `.message.orEmpty().ifBlank { javaClass.simpleName }` (9+ occurrences).
 */
fun Throwable.userMessage(): String {
    return message?.trim()?.ifBlank { null } ?: javaClass.simpleName
}
