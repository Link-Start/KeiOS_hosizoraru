package os.kei.ui.page.main.student.catalog

internal const val BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MIN_HOURS = 12
internal const val BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MAX_HOURS = 24
internal const val BA_GUIDE_CATALOG_FULL_REFRESH_INTERVAL_MS = 3L * 24L * 60L * 60L * 1000L

internal val BaGuideCatalogIncrementalRefreshIntervalOptions: List<Int> =
    listOf(
        BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MIN_HOURS,
        BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MAX_HOURS,
    )

internal enum class BaGuideCatalogRefreshMode {
    Incremental,
    Full,
}

internal fun resolvedBaGuideCatalogIncrementalRefreshIntervalHours(refreshIntervalHours: Int): Int =
    if (refreshIntervalHours >= BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MAX_HOURS) {
        BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MAX_HOURS
    } else {
        BA_GUIDE_CATALOG_INCREMENTAL_REFRESH_MIN_HOURS
    }

internal fun isBaGuideCatalogCacheExpired(
    bundle: BaGuideCatalogBundle?,
    refreshIntervalHours: Int,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    bundle ?: return true
    val intervalMs = resolvedBaGuideCatalogIncrementalRefreshIntervalHours(refreshIntervalHours) * 60L * 60L * 1000L
    if (bundle.syncedAtMs <= 0L) return true
    return (nowMs - bundle.syncedAtMs).coerceAtLeast(0L) >= intervalMs
}

internal fun isBaGuideCatalogFullRefreshExpired(
    bundle: BaGuideCatalogBundle?,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    bundle ?: return true
    val fullSyncedAtMs = bundle.fullSyncedAtMs.takeIf { it > 0L } ?: bundle.syncedAtMs
    if (fullSyncedAtMs <= 0L) return true
    return (nowMs - fullSyncedAtMs).coerceAtLeast(0L) >= BA_GUIDE_CATALOG_FULL_REFRESH_INTERVAL_MS
}

internal fun selectBaGuideCatalogRefreshMode(
    cachedBundle: BaGuideCatalogBundle?,
    manualRefresh: Boolean,
    cacheComplete: Boolean,
    nowMs: Long,
): BaGuideCatalogRefreshMode =
    if (manualRefresh || !cacheComplete || isBaGuideCatalogFullRefreshExpired(cachedBundle, nowMs)) {
        BaGuideCatalogRefreshMode.Full
    } else {
        BaGuideCatalogRefreshMode.Incremental
    }
