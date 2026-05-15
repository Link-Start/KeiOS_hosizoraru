package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import kotlin.coroutines.cancellation.CancellationException

private const val BGM_CACHE_PREWARM_BATCH_SIZE = 32

internal class BaGuideStudentBgmLookupCoordinator(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val cachedLoader: suspend (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? = { entry ->
        withContext(ioDispatcher) {
            loadCachedStudentBgmFavorite(entry)
        }
    },
    private val networkLoader: suspend (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? = { entry ->
        fetchStudentBgmFavoriteAsync(
            entry = entry,
            networkDispatcher = ioDispatcher,
            parseDispatcher = parseDispatcher
        )
    }
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
            pendingEntries.chunked(BGM_CACHE_PREWARM_BATCH_SIZE).forEach { batch ->
                currentCoroutineContext().ensureActive()
                val cached = batch.mapNotNull { entry ->
                    cachedLoader(entry)?.let { item -> entry.contentId to item }
                }
                if (cached.isNotEmpty()) {
                    _states.update { states ->
                        states + cached.associate { (contentId, item) ->
                            contentId to BaGuideStudentBgmLookupState.Ready(item)
                        }
                    }
                }
                yield()
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
            val resolved = runCatchingCancellable {
                if (allowNetwork) {
                    networkLoader(entry)
                } else {
                    cachedLoader(entry)
                }
            }.getOrNull()
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

    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }
}
