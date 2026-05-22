package os.kei.ui.page.main.os

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import kotlin.math.max

internal data class OsActivityShortcutIconRequest(
    val packageName: String,
    val className: String,
)

internal data class OsActivityShortcutIconLoadResult(
    val bitmaps: Map<String, Bitmap>,
    val missingKeys: Set<String>,
)

internal fun osActivityShortcutIconKey(
    packageName: String,
    className: String,
): String {
    val normalizedPackageName = packageName.trim()
    val normalizedClassName =
        normalizeActivityShortcutClassName(
            packageName = normalizedPackageName,
            className = className,
        )
    return "$normalizedPackageName#$normalizedClassName"
}

internal fun activityShortcutIconRequests(cards: List<OsActivityShortcutCard>): List<OsActivityShortcutIconRequest> =
    cards.map { card ->
        OsActivityShortcutIconRequest(
            packageName = card.config.packageName,
            className = card.config.className,
        )
    }

internal fun activitySuggestionIconRequests(
    packageName: String,
    classSuggestions: List<ShortcutActivityClassOption>,
): List<OsActivityShortcutIconRequest> =
    classSuggestions.map { suggestion ->
        OsActivityShortcutIconRequest(
            packageName = packageName,
            className = suggestion.className,
        )
    }

internal class OsActivityShortcutIconRepository {
    private val cache = ActivityIconBitmapCache()

    fun cachedBitmap(key: String): Bitmap? = cache.get(key)

    fun isMissing(key: String): Boolean = cache.isMissing(key)

    suspend fun loadActivityIcons(
        context: Context,
        requests: List<OsActivityShortcutIconRequest>,
    ): OsActivityShortcutIconLoadResult =
        withContext(AppDispatchers.fileIo) {
            val bitmaps = linkedMapOf<String, Bitmap>()
            val missingKeys = linkedSetOf<String>()
            val appContext = context.applicationContext
            requests
                .distinctBy { request ->
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                }.forEach { request ->
                    val packageName = request.packageName.trim()
                    val className = request.className.trim()
                    if (packageName.isBlank() || className.isBlank()) return@forEach

                    val key = osActivityShortcutIconKey(packageName, className)
                    cache.get(key)?.let { bitmap ->
                        bitmaps[key] = bitmap
                        return@forEach
                    }
                    if (cache.isMissing(key)) {
                        missingKeys.add(key)
                        return@forEach
                    }

                    val bitmap =
                        loadActivityIconBitmap(
                            context = appContext,
                            packageName = packageName,
                            className = className,
                        )
                    cache.put(key, bitmap)
                    if (bitmap == null) {
                        missingKeys.add(key)
                    } else {
                        bitmaps[key] = bitmap
                    }
                }
            OsActivityShortcutIconLoadResult(
                bitmaps = bitmaps,
                missingKeys = missingKeys,
            )
        }
}

private class ActivityIconBitmapCache {
    private companion object {
        const val MAX_ICON_CACHE_ENTRIES = 96
        const val MAX_MISSING_KEYS = 192
    }

    private val lock = Any()
    private val cache =
        object : LruCache<String, Bitmap>(MAX_ICON_CACHE_ENTRIES) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = 1
        }
    private val missingKeys = LinkedHashSet<String>()

    fun get(key: String): Bitmap? =
        synchronized(lock) {
            cache.get(key)
        }

    fun isMissing(key: String): Boolean =
        synchronized(lock) {
            missingKeys.contains(key)
        }

    fun put(
        key: String,
        bitmap: Bitmap?,
    ) {
        synchronized(lock) {
            if (bitmap == null) {
                rememberMissingKey(key)
                return
            }
            missingKeys.remove(key)
            cache.put(key, bitmap)
        }
    }

    private fun rememberMissingKey(key: String) {
        missingKeys.remove(key)
        missingKeys.add(key)
        while (missingKeys.size > MAX_MISSING_KEYS) {
            val oldest = missingKeys.firstOrNull() ?: return
            missingKeys.remove(oldest)
        }
    }
}

private fun loadActivityIconBitmap(
    context: Context,
    packageName: String,
    className: String,
): Bitmap? {
    val normalizedClassName =
        normalizeActivityShortcutClassName(
            packageName = packageName,
            className = className,
        )
    if (normalizedClassName.isBlank()) return null
    val componentName = ComponentName(packageName, normalizedClassName)
    val iconDrawable =
        runCatching {
            context.packageManager.getActivityIcon(componentName)
        }.getOrNull()
            ?: return null

    val sizePx = max(36, (context.resources.displayMetrics.density * 40f).toInt())
    return iconDrawable.toBitmap(sizePx)
}

private fun normalizeActivityShortcutClassName(
    packageName: String,
    className: String,
): String {
    val normalizedClassName = className.trim()
    if (normalizedClassName.isBlank()) return ""
    return if (normalizedClassName.startsWith(".")) {
        "${packageName.trim()}$normalizedClassName"
    } else {
        normalizedClassName
    }
}

private fun Drawable.toBitmap(sizePx: Int): Bitmap {
    if (this is BitmapDrawable) {
        bitmap?.let { bitmapValue ->
            if (bitmapValue.width == sizePx && bitmapValue.height == sizePx) {
                return bitmapValue
            }
            return Bitmap.createScaledBitmap(bitmapValue, sizePx, sizePx, true)
        }
    }

    val width = if (intrinsicWidth > 0) intrinsicWidth else sizePx
    val height = if (intrinsicHeight > 0) intrinsicHeight else sizePx
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return if (width == sizePx && height == sizePx) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
    }
}
