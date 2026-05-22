@file:Suppress("PropertyName")

package os.kei.ui.page.main.github.share

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CancellationException
import os.kei.core.ext.showToast

internal const val shareImportTrackMaxAgeMs = 25 * 60 * 1000L
internal const val shareImportTrackUpdateToleranceMs = 2 * 60 * 1000L
internal const val shareImportMinHandleIntervalMs = 1200L

internal val shareImportAttachActions =
    setOf(
        Intent.ACTION_PACKAGE_ADDED,
        Intent.ACTION_PACKAGE_REPLACED,
        Intent.ACTION_PACKAGE_CHANGED,
    )

internal fun toast(
    context: Context,
    resId: Int,
    vararg args: Any,
) {
    context.showToast(context.getString(resId, *args))
}

internal fun Throwable.shouldSuppressShareImportFailureToast(): Boolean {
    if (this is CancellationException) return true
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 6) {
        val message = current.message.orEmpty()
        val className = current.javaClass.name
        if (
            message.contains("left the composition", ignoreCase = true) ||
            className.contains("LeftComposition", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
        depth += 1
    }
    return false
}
