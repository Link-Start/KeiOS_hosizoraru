package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry

internal class BaGuideStudentBgmLookupCoordinator(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cachedLoader: (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? =
        ::loadCachedStudentBgmFavorite,
    private val networkLoader: (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? =
        ::fetchStudentBgmFavorite
) {
    private val _states = MutableStateFlow<Map<Long, BaGuideStudentBgmLookupState>>(emptyMap())
    val states: StateFlow<Map<Long, BaGuideStudentBgmLookupState>> = _states.asStateFlow()

    private var cachePrewarmJob: Job? = null

    fun clear() {
        cachePrewarmJob?.cancel()
        _states.value = emptyMap()
    }

    fun markReadyFromFavorite(
        entry: BaGuideCatalogEntry,
        item: BaGuideStudentBgmResolvedItem
    ) {
        _states.update { states ->
            states + (entry.contentId to BaGuideStudentBgmLookupState.Ready(item))
        }
    }

    fun prewarmCached(entries: List<BaGuideCatalogEntry>) {
        val pendingEntries = entries.filter { entry -> _states.value[entry.contentId] == null }
        if (pendingEntries.isEmpty()) return
        cachePrewarmJob?.cancel()
        cachePrewarmJob = scope.launch {
            val cached = withContext(ioDispatcher) {
                pendingEntries.mapNotNull { entry ->
                    cachedLoader(entry)?.let { item -> entry.contentId to item }
                }
            }
            if (cached.isEmpty()) return@launch
            _states.update { states ->
                states + cached.associate { (contentId, item) ->
                    contentId to BaGuideStudentBgmLookupState.Ready(item)
                }
            }
        }
    }

    fun resolveEntry(
        entry: BaGuideCatalogEntry,
        allowNetwork: Boolean,
        onResolved: (BaGuideStudentBgmResolvedItem?) -> Unit
    ) {
        val current = _states.value[entry.contentId]
        if (current is BaGuideStudentBgmLookupState.Ready) {
            onResolved(current.item)
            return
        }
        if (current == BaGuideStudentBgmLookupState.Loading) return
        _states.update { states ->
            states + (entry.contentId to BaGuideStudentBgmLookupState.Loading)
        }
        scope.launch {
            val resolved = withContext(ioDispatcher) {
                runCatching {
                    if (allowNetwork) {
                        networkLoader(entry)
                    } else {
                        cachedLoader(entry)
                    }
                }.getOrNull()
            }
            if (allowNetwork || resolved != null) {
                _states.update { states ->
                    states + (
                            entry.contentId to if (resolved == null) {
                                BaGuideStudentBgmLookupState.Missing
                            } else {
                                BaGuideStudentBgmLookupState.Ready(resolved)
                            }
                            )
                }
            } else {
                _states.update { states -> states - entry.contentId }
            }
            onResolved(resolved)
        }
    }
}
