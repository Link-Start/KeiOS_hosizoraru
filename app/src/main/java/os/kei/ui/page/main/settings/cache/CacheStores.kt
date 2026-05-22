package os.kei.ui.page.main.settings.cache

import android.content.Context

internal object CacheStores {
    private val providers =
        listOf(
            githubCacheEntryProvider(),
            baCalendarCacheEntryProvider(),
            baStudentGuideCacheEntryProvider(),
            osInfoCacheEntryProvider(),
            appIconCacheEntryProvider(),
            baMediaPlaybackCacheEntryProvider(),
            baTempMediaCacheEntryProvider(),
            debugUiDumpCacheEntryProvider(),
            mcpPrefsCacheEntryProvider(),
        )

    fun list(context: Context): List<CacheEntrySummary> {
        val entries = providers.map { provider -> provider.summary(context) }
        return listOf(buildCacheOverview(context, entries)) + entries
    }

    fun clear(
        context: Context,
        id: String,
    ) {
        val provider = providers.firstOrNull { provider -> provider.id == id } ?: return
        provider.clear(context)
        CacheEventStore.markCleared(id)
    }

    fun clearAll(context: Context) {
        list(context)
            .asSequence()
            .filter { entry -> entry.id != "cache_overview" && entry.clearLabel.isNotBlank() }
            .forEach { entry -> clear(context, entry.id) }
    }
}
