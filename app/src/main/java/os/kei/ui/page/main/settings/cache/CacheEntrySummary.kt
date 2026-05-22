package os.kei.ui.page.main.settings.cache

import os.kei.core.prefs.CacheFreshnessSnapshot

internal data class CacheEntrySummary(
    val id: String,
    val title: String,
    val summary: String,
    val detail: String,
    val activity: String,
    val storage: String,
    val clearLabel: String,
    val cacheBytes: Long = 0L,
    val configBytes: Long = 0L,
    val diskBytes: Long = 0L,
    val memoryBytes: Long = 0L,
    val updatedAtMs: Long = 0L,
    val clearedAtMs: Long = 0L,
    val freshness: CacheFreshnessSnapshot = CacheFreshnessSnapshot.Empty,
)
