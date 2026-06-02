package os.kei.ui.page.main.student.catalog.component

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmResolveRepository
import kotlin.coroutines.cancellation.CancellationException

private const val BGM_CACHE_PREWARM_BATCH_SIZE = 32
private const val BGM_CACHE_PREWARM_PARALLELISM = 4
private const val BGM_VISIBLE_NETWORK_PREWARM_LIMIT = 8
private const val BGM_VISIBLE_NETWORK_PREWARM_BATCH_SIZE = 4
private const val BGM_VISIBLE_NETWORK_PREWARM_PARALLELISM = 2

internal class BaGuideStudentBgmLookupCoordinator(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val repository: BaGuideStudentBgmResolveRepository =
        BaGuideStudentBgmResolveRepository(
            ioDispatcher = ioDispatcher,
            parseDispatcher = parseDispatcher,
        ),
    private val cachedLoader: suspend (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? = { entry ->
        repository.loadCachedFavorite(entry)
    },
    private val networkLoader: suspend (BaGuideCatalogEntry) -> BaGuideStudentBgmResolvedItem? = { entry ->
        repository.fetchFavorite(entry)
    }
) {
    private val _states = MutableStateFlow<Map<Long, BaGuideStudentBgmLookupState>>(emptyMap())
    val states: StateFlow<Map<Long, BaGuideStudentBgmLookupState>> = _states.asStateFlow()

    private var cachePrewarmJob: Job? = null
    private val cachePrewarmCheckedContentIds = mutableSetOf<Long>()
    private var visibleNetworkPrewarmJob: Job? = null
    private val visibleNetworkPrewarmCheckedContentIds = mutableSetOf<Long>()
    private val pendingResolveLock = Any()
    private val pendingResolveCallbacksByContentId =
        mutableMapOf<Long, MutableList<(BaGuideStudentBgmResolvedItem?) -> Unit>>()
    private var lookupGeneration = 0

    fun clear() {
        cachePrewarmJob?.cancel()
        visibleNetworkPrewarmJob?.cancel()
        cachePrewarmCheckedContentIds.clear()
        visibleNetworkPrewarmCheckedContentIds.clear()
        synchronized(pendingResolveLock) {
            lookupGeneration++
            pendingResolveCallbacksByContentId.clear()
        }
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
        val currentStates = _states.value
        val pendingEntries = entries.filter { entry ->
            currentStates[entry.contentId] == null &&
                entry.contentId !in cachePrewarmCheckedContentIds
        }
        if (pendingEntries.isEmpty()) return
        cachePrewarmJob?.cancel()
        cachePrewarmJob = scope.launch {
            val semaphore = Semaphore(BGM_CACHE_PREWARM_PARALLELISM)
            pendingEntries.chunked(BGM_CACHE_PREWARM_BATCH_SIZE).forEach { batch ->
                currentCoroutineContext().ensureActive()
                val cached = batch
                    .map { entry ->
                        async {
                            semaphore.withPermit {
                                runCatchingCancellable {
                                    cachedLoader(entry)?.let { item -> entry.contentId to item }
                                }.getOrNull()
                            }
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
                cachePrewarmCheckedContentIds += batch.map { entry -> entry.contentId }
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

    fun prewarmVisibleNetwork(entries: List<BaGuideCatalogEntry>) {
        val currentStates = _states.value
        val pendingEntries =
            entries
                .asSequence()
                .filter { entry -> entry.contentId > 0L }
                .distinctBy { entry -> entry.contentId }
                .filter { entry ->
                    entry.contentId !in visibleNetworkPrewarmCheckedContentIds &&
                        currentStates[entry.contentId] !is BaGuideStudentBgmLookupState.Ready &&
                        currentStates[entry.contentId] != BaGuideStudentBgmLookupState.Loading &&
                        currentStates[entry.contentId] != BaGuideStudentBgmLookupState.Missing
                }
                .take(BGM_VISIBLE_NETWORK_PREWARM_LIMIT)
                .toList()
        if (pendingEntries.isEmpty()) return
        visibleNetworkPrewarmJob?.cancel()
        visibleNetworkPrewarmJob =
            scope.launch {
                val semaphore = Semaphore(BGM_VISIBLE_NETWORK_PREWARM_PARALLELISM)
                pendingEntries.chunked(BGM_VISIBLE_NETWORK_PREWARM_BATCH_SIZE).forEach { batch ->
                    currentCoroutineContext().ensureActive()
                    val resolved =
                        batch
                            .map { entry ->
                                async {
                                    semaphore.withPermit {
                                        val item =
                                            runCatchingCancellable {
                                                networkLoader(entry)
                                            }.getOrNull()
                                        entry.contentId to item
                                    }
                                }
                            }.awaitAll()
                    visibleNetworkPrewarmCheckedContentIds += batch.map { entry -> entry.contentId }
                    val readyStates =
                        resolved
                            .mapNotNull { (contentId, item) ->
                                item?.let { contentId to BaGuideStudentBgmLookupState.Ready(it) }
                            }.toMap()
                    if (readyStates.isNotEmpty()) {
                        _states.update { states -> states + readyStates }
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
        val contentId = entry.contentId
        val generation: Int
        synchronized(pendingResolveLock) {
            val current = _states.value[contentId]
            if (current is BaGuideStudentBgmLookupState.Ready) {
                onResolved(current.item)
                return
            }
            pendingResolveCallbacksByContentId
                .getOrPut(contentId) { mutableListOf() }
                .add(onResolved)
            if (current == BaGuideStudentBgmLookupState.Loading) return
            generation = lookupGeneration
            _states.update { states ->
                states + (contentId to BaGuideStudentBgmLookupState.Loading)
            }
        }
        scope.launch {
            val resolved = runCatchingCancellable {
                if (allowNetwork) {
                    networkLoader(entry)
                } else {
                    cachedLoader(entry)
                }
            }.getOrNull()
            val callbacks =
                synchronized(pendingResolveLock) {
                    if (generation != lookupGeneration) {
                        emptyList()
                    } else {
                        pendingResolveCallbacksByContentId.remove(contentId).orEmpty()
                    }
                }
            if (callbacks.isEmpty() && generation != lookupGeneration) return@launch
            if (allowNetwork || resolved != null) {
                _states.update { states ->
                    states + (
                            contentId to if (resolved == null) {
                                BaGuideStudentBgmLookupState.Missing
                            } else {
                                BaGuideStudentBgmLookupState.Ready(resolved)
                            }
                            )
                }
            } else {
                _states.update { states -> states - contentId }
            }
            callbacks.forEach { callback -> callback(resolved) }
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
