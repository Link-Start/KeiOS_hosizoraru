package os.kei.ui.page.main.settings.cache

import android.content.Context

internal data class CacheEntryProvider(
    val id: String,
    val summary: (Context) -> CacheEntrySummary,
    val clear: (Context) -> Unit,
)

internal fun cacheFreshness(
    updatedAtMs: Long,
    bytes: Long,
    rebuildable: Boolean,
    ttlMs: Long? = null,
) = os.kei.core.prefs.CacheFreshnessSnapshot.from(
    lastUpdatedAtMs = updatedAtMs,
    bytes = bytes,
    rebuildable = rebuildable,
    ttlMs = ttlMs,
)
