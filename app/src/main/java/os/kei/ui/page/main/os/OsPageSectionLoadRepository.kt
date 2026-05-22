package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.shizuku.ShizukuApiUtils

internal sealed interface OsSectionLoadResult {
    data object Joined : OsSectionLoadResult

    data class Loaded(
        val rows: List<InfoRow>,
        val cachePersisted: Boolean,
    ) : OsSectionLoadResult
}

internal class OsPageSectionLoadRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
) {
    private val loadMutex = Mutex()
    private val loadDeferreds: MutableMap<SectionKind, Deferred<List<InfoRow>>> = mutableMapOf()

    fun shouldLoad(
        section: SectionKind,
        forceRefresh: Boolean,
        visibleCards: Set<OsSectionCard>,
        currentState: SectionState,
    ): Boolean {
        if (!visibleSectionKinds(visibleCards).contains(section)) return false
        if (forceRefresh) return true
        if (currentState.loadedFresh) return false
        return currentState.rows.isEmpty()
    }

    suspend fun loadSection(
        section: SectionKind,
        forceRefresh: Boolean,
        visibleCardsProvider: () -> Set<OsSectionCard>,
        context: Context,
        shizukuStatus: String,
        shizukuApiUtils: ShizukuApiUtils,
    ): OsSectionLoadResult =
        coroutineScope {
            var isLoadOwner = false
            lateinit var loadDeferred: Deferred<List<InfoRow>>
            loadMutex.withLock {
                val inFlight = loadDeferreds[section]
                if (inFlight != null && inFlight.isActive && !forceRefresh) {
                    loadDeferred = inFlight
                } else {
                    if (forceRefresh) {
                        inFlight?.cancel()
                    }
                    loadDeferred =
                        async {
                            buildSectionRowsAsync(
                                section = section,
                                context = context,
                                shizukuStatus = shizukuStatus,
                                shizukuApiUtils = shizukuApiUtils,
                                forceRefresh = forceRefresh,
                            )
                        }
                    loadDeferreds[section] = loadDeferred
                    isLoadOwner = true
                }
            }
            if (!isLoadOwner) {
                loadDeferred.await()
                return@coroutineScope OsSectionLoadResult.Joined
            }
            try {
                val rows = loadDeferred.await()
                val cachePersisted =
                    withContext(ioDispatcher) {
                        OsInfoCache.write(section, rows)
                        OsInfoCache
                            .readSnapshot(visibleSectionKinds(visibleCardsProvider()))
                            .hasPersistedCache
                    }
                OsSectionLoadResult.Loaded(
                    rows = rows,
                    cachePersisted = cachePersisted,
                )
            } finally {
                loadMutex.withLock {
                    if (loadDeferreds[section] === loadDeferred) {
                        loadDeferreds.remove(section)
                    }
                }
            }
        }
}
