package os.kei.core.ext

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

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
 * Shows a short Toast with the given string resource.
 * Replaces `Toast.makeText(context, getString(R.string.x), Toast.LENGTH_SHORT).show()`.
 */
fun Context.showToast(@StringRes stringRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(stringRes), duration).show()
}

/**
 * Shows a short Toast with the given message string.
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Returns a user-friendly error message: the exception's message if non-blank,
 * otherwise the simple class name.
 * Replaces `.message.orEmpty().ifBlank { javaClass.simpleName }` (9+ occurrences).
 */
fun Throwable.userMessage(): String {
    return message?.trim()?.ifBlank { null } ?: javaClass.simpleName
}
