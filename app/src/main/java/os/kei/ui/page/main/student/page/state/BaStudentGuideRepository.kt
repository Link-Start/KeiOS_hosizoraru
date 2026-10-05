package os.kei.ui.page.main.student.page.state

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideBgmFavoriteRepository
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideStudentDetailCacheMeta
import os.kei.ui.page.main.student.BaGuideStudentDetailFileCacheStore
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.BaStudentGuideCacheSnapshot
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.alignBaGuideStudentDetailCacheMetaWithCatalog
import os.kei.ui.page.main.student.baGuideStudentDetailCacheStore
import os.kei.ui.page.main.student.buildBaGuideStudentDetailCacheMetaFromInfo
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.extractBaGuideReleaseDateSec
import os.kei.ui.page.main.student.decideBaGuideStudentDetailCacheRefresh
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetchGuideInfoAsync
import os.kei.ui.page.main.student.isNpcSatelliteLikeGuide
import os.kei.ui.page.main.student.normalizeStudentGuideSourceUrl
import os.kei.ui.page.main.student.page.support.collectGuideMediaCacheUrls
import os.kei.ui.page.main.student.page.support.collectGuideStaticImagePrefetchUrls
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal data class BaStudentGuideLoadResult(
    val info: BaStudentGuideInfo?,
    val error: String?,
    val validateInBackground: Boolean = false,
    val cacheMeta: BaGuideStudentDetailCacheMeta? = null,
)

internal data class BaStudentGuideMediaSettings(
    val mediaAdaptiveRotationEnabled: Boolean = false,
)

internal data class BaStudentGuideBackgroundValidationSummary(
    val candidateCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
)

private data class BaStudentGuideDetailCacheContext(
    val entry: BaGuideCatalogEntry,
    val store: BaGuideStudentDetailFileCacheStore,
)

internal class BaStudentGuideRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val bgmFavoriteRepository: BaGuideBgmFavoriteRepository = BaGuideBgmFavoriteRepository(),
    private val clock: BaGuideDataClock = BaGuideSystemDataClock,
    private val refreshIntervalLoader: suspend () -> Int = {
        BASettingsStore.loadCalendarRefreshIntervalHours()
    },
    private val cacheSnapshotLoader: suspend (String) -> BaStudentGuideCacheSnapshot = { sourceUrl ->
        BaStudentGuideStore.loadInfoSnapshot(sourceUrl)
    },
    private val cacheSaver: suspend (BaStudentGuideInfo) -> Unit = { info ->
        BaStudentGuideStore.saveInfo(info)
    },
    private val cacheClearer: suspend (Context, String) -> Unit = { context, sourceUrl ->
        BaStudentGuideStore.clearCachedInfo(sourceUrl)
        BaGuideTempMediaCache.clearGuideCache(context, sourceUrl)
    },
    private val mediaCacheRetainer: suspend (Context, String, Set<String>) -> Unit = { context, sourceUrl, rawUrls ->
        BaGuideTempMediaCache.retainGuideMediaCache(
            context = context,
            sourceUrl = sourceUrl,
            retainedRawUrls = rawUrls,
        )
    },
    private val guideFetcher: suspend (
        String,
        CoroutineDispatcher,
        CoroutineDispatcher,
        BaGuideDataClock,
    ) -> BaStudentGuideInfo = { sourceUrl, networkDispatcher, detailParseDispatcher, dataClock ->
        fetchGuideInfoAsync(
            sourceUrl = sourceUrl,
            networkDispatcher = networkDispatcher,
            parseDispatcher = detailParseDispatcher,
            clock = dataClock,
        )
    },
    private val catalogBundleLoader: suspend () -> BaGuideCatalogBundle? = {
        BaGuideCatalogStore.loadBundle()
    },
    private val detailCacheStoreProvider: (Context) -> BaGuideStudentDetailFileCacheStore =
        ::baGuideStudentDetailCacheStore,
    private val releaseDateExtractor: (BaStudentGuideInfo) -> Long =
        ::extractBaGuideReleaseDateSec,
    private val releaseDateIndexUpserter: suspend (Map<Long, Long>) -> Unit = { releaseDateSecByContentId ->
        BaGuideCatalogStore.upsertReleaseDateIndex(releaseDateSecByContentId)
    },
) {
    private val npcSatelliteGuideFlagCache = ConcurrentHashMap<Long, Boolean>()
    private val inFlightGuideFetches = ConcurrentHashMap<String, CompletableDeferred<Result<BaStudentGuideInfo>>>()

    suspend fun loadCurrentUrlAsync(): String =
        withContext(ioDispatcher) {
            BaStudentGuideStore.loadCurrentUrl()
        }

    suspend fun saveCurrentUrlAsync(sourceUrl: String) {
        withContext(ioDispatcher) {
            BaStudentGuideStore.setCurrentUrl(sourceUrl)
        }
    }

    suspend fun prepareNavigationWarmStart(sourceUrl: String): BaStudentGuideNavigationWarmStart {
        val requestUrl = normalizeStudentGuideSourceUrl(sourceUrl)
        if (requestUrl.isBlank()) return BaStudentGuideNavigationWarmStart(sourceUrl = "")
        val snapshot =
            withContext(ioDispatcher) {
                cacheSnapshotLoader(requestUrl)
            }
        val info = snapshot.info.takeIf { snapshot.isComplete }
        val isNpcSatelliteGuide =
            if (info != null) {
                resolveNpcSatelliteGuide(
                    sourceUrl = requestUrl,
                    info = info,
                )
            } else {
                false
            }
        return BaStudentGuideNavigationWarmStart(
            sourceUrl = requestUrl,
            info = info,
            isNpcSatelliteGuide = isNpcSatelliteGuide,
            contentPresentationState =
                deriveBaStudentGuideContentPresentationState(
                    info = info,
                    isNpcSatelliteGuide = isNpcSatelliteGuide,
                ),
        )
    }

    fun bgmFavoritesFlow(): StateFlow<List<GuideBgmFavoriteItem>> = bgmFavoriteRepository.favoritesFlow()

    suspend fun hydrateBgmFavorites(): List<GuideBgmFavoriteItem> = bgmFavoriteRepository.hydrateFavorites()

    suspend fun toggleBgmFavorite(item: GuideBgmFavoriteItem): Boolean = bgmFavoriteRepository.toggleFavorite(item)

    suspend fun loadMediaSettings(): BaStudentGuideMediaSettings =
        withContext(ioDispatcher) {
            BaStudentGuideMediaSettings(
                mediaAdaptiveRotationEnabled = BASettingsStore.loadMediaAdaptiveRotationEnabled(),
            )
        }

    fun consumeInitialBottomTab(sourceUrl: String): GuideBottomTab? = GuideDetailTabRequestStore.consume(sourceUrl)

    suspend fun clearGuideCache(
        context: Context,
        sourceUrl: String,
    ) {
        val requestUrl = normalizeStudentGuideSourceUrl(sourceUrl)
        if (requestUrl.isBlank()) return
        val catalogEntry = resolveCatalogEntrySource(requestUrl)
        withContext(ioDispatcher) {
            cacheClearer(context, requestUrl)
            val store = detailCacheStoreProvider(context)
            store.remove(requestUrl)
            catalogEntry
                ?.contentId
                ?.takeIf { it > 0L }
                ?.let(store::loadMetaByContentId)
                ?.takeIf { meta -> meta.sourceUrl != requestUrl }
                ?.let { meta -> store.remove(meta.sourceUrl) }
        }
    }

    suspend fun clearOrphanStudentDetailCache(context: Context): Int =
        withContext(ioDispatcher) {
            val activeSourcesByContentId =
                catalogBundleLoader()
                    ?.entries(BaGuideCatalogTab.Student)
                    .orEmpty()
                    .asSequence()
                    .filter { entry -> entry.contentId > 0L }
                    .groupBy(
                        keySelector = { entry -> entry.contentId },
                        valueTransform = { entry -> normalizeStudentGuideSourceUrl(entry.detailUrl) },
                    ).mapValues { (_, urls) ->
                        urls.filter { sourceUrl -> sourceUrl.isNotBlank() }.toSet()
                    }.filterValues { urls -> urls.isNotEmpty() }
            if (activeSourcesByContentId.isEmpty()) return@withContext 0
            val removed =
                detailCacheStoreProvider(context)
                    .removeOrphanStudentMetas(activeSourcesByContentId)
            removed.forEachIndexed { index, meta ->
                if (index % 16 == 0) currentCoroutineContext().ensureActive()
                cacheClearer(context, meta.sourceUrl)
            }
            removed.size
        }

    suspend fun validateFavoriteAndRecentStudentDetails(
        context: Context,
        catalog: BaGuideCatalogBundle,
        favoriteContentIdsByFavoritedAtMs: Map<Long, Long>,
        recentSourceUrls: List<String>,
        maxCandidates: Int = 4,
        maxParallelism: Int = 1,
        loadFailedText: String = "",
        refreshFailedKeepCacheText: String = "",
    ): BaStudentGuideBackgroundValidationSummary {
        val candidates =
            buildFavoriteAndRecentValidationCandidates(
                catalog = catalog,
                favoriteContentIdsByFavoritedAtMs = favoriteContentIdsByFavoritedAtMs,
                recentSourceUrls = recentSourceUrls,
                maxCandidates = maxCandidates,
            )
        if (candidates.isEmpty()) return BaStudentGuideBackgroundValidationSummary()
        val parallelism = maxParallelism.coerceIn(1, 2)
        val semaphore = Semaphore(parallelism)
        val results =
            coroutineScope {
                candidates.map { sourceUrl ->
                    async {
                        semaphore.withPermit {
                            validateStudentDetailCandidate(
                                context = context,
                                sourceUrl = sourceUrl,
                                loadFailedText = loadFailedText,
                                refreshFailedKeepCacheText = refreshFailedKeepCacheText,
                            )
                        }
                    }
                }.awaitAll()
            }
        return BaStudentGuideBackgroundValidationSummary(
            candidateCount = candidates.size,
            completedCount = results.count { it },
            failedCount = results.count { !it },
        )
    }

    suspend fun resolveNpcSatelliteGuide(
        sourceUrl: String,
        info: BaStudentGuideInfo?,
    ): Boolean {
        val catalogMatched = resolveNpcSatelliteGuideSource(sourceUrl)
        return withContext(parseDispatcher) {
            info?.isNpcSatelliteLikeGuide(catalogMatched) ?: catalogMatched
        }
    }

    suspend fun prefetchStaticImages(
        context: Context,
        sourceUrl: String,
        rawUrls: List<String>,
    ) {
        if (sourceUrl.isBlank() || rawUrls.isEmpty()) return
        withContext(ioDispatcher) {
            BaGuideTempMediaCache.prefetchForGuide(
                context = context,
                sourceUrl = sourceUrl,
                rawUrls = rawUrls,
                ioDispatcher = ioDispatcher,
            )
        }
    }

    suspend fun collectStaticImagePrefetchUrls(
        info: BaStudentGuideInfo,
        maxCount: Int,
    ): List<String> =
        withContext(parseDispatcher) {
            collectGuideStaticImagePrefetchUrls(
                info = info,
                maxCount = maxCount,
            )
        }

    suspend fun loadGuide(
        context: Context,
        sourceUrl: String,
        currentInfo: BaStudentGuideInfo?,
        manualRefresh: Boolean,
        forceValidation: Boolean = false,
        loadFailedText: String,
        refreshFailedKeepCacheText: String,
    ): BaStudentGuideLoadResult {
        val requestUrl = sourceUrl.trim()
        if (requestUrl.isBlank()) {
            return BaStudentGuideLoadResult(info = null, error = null)
        }

        val now = clock.nowMs()
        val refreshIntervalHours =
            withContext(ioDispatcher) {
                refreshIntervalLoader()
            }
        val cacheSnapshot =
            withContext(ioDispatcher) {
                cacheSnapshotLoader(requestUrl)
            }
        val catalogEntry = resolveCatalogEntrySource(requestUrl)
        val studentCacheContext =
            catalogEntry
                ?.takeIf { it.tab == BaGuideCatalogTab.Student }
                ?.let { entry ->
                    BaStudentGuideDetailCacheContext(
                        entry = entry,
                        store = detailCacheStoreProvider(context),
                    )
                }
        val cacheInfo = cacheSnapshot.info.takeIf { cacheSnapshot.isComplete }
        val legacyCacheExpired =
            BaStudentGuideStore.isCacheExpired(
                snapshot = cacheSnapshot,
                refreshIntervalHours = refreshIntervalHours,
                nowMs = now,
            )
        val existingStudentMeta =
            if (studentCacheContext != null) {
                withContext(ioDispatcher) {
                    val loaded =
                        studentCacheContext.store.loadMeta(requestUrl)
                            ?: studentCacheContext.store.loadMetaByContentId(studentCacheContext.entry.contentId)
                    loaded?.let { meta ->
                        val aligned =
                            alignBaGuideStudentDetailCacheMetaWithCatalog(
                                meta = meta,
                                sourceUrl = requestUrl,
                                catalogCreatedAtSec = studentCacheContext.entry.createdAtSec,
                                releaseDateSec = studentCacheContext.entry.releaseDateSec,
                                nowMs = now,
                            )
                        if (aligned != meta) {
                            studentCacheContext.store.saveMeta(aligned)
                        }
                        aligned
                    }
                }
            } else {
                null
            }
        val studentMeta =
            if (studentCacheContext != null && cacheInfo != null) {
                existingStudentMeta ?: withContext(ioDispatcher) {
                    buildAndSaveStudentDetailMeta(
                        store = studentCacheContext.store,
                        info = cacheInfo,
                        entry = studentCacheContext.entry,
                        previous = null,
                        nowMs = now,
                    )
                }
            } else {
                existingStudentMeta
            }
        val cacheExpired =
            when {
                forceValidation -> true
                studentMeta != null ->
                    decideBaGuideStudentDetailCacheRefresh(
                        meta = studentMeta,
                        manualRefresh = manualRefresh,
                        nowMs = now,
                        galleryParserVersion = cacheInfo?.galleryParserVersion ?: 0,
                    ).shouldValidate
                else -> legacyCacheExpired
            }
        if (!manualRefresh && !forceValidation && cacheInfo != null && cacheExpired && studentMeta != null) {
            return BaStudentGuideLoadResult(
                info = cacheInfo,
                error = null,
                validateInBackground = true,
                cacheMeta = studentMeta,
            )
        }
        if (!manualRefresh && !forceValidation && cacheInfo != null && !cacheExpired) {
            return BaStudentGuideLoadResult(
                info = cacheInfo,
                error = null,
                cacheMeta = studentMeta,
            )
        }

        val visibleInfo =
            when {
                cacheInfo != null -> cacheInfo
                cacheSnapshot.hasCache -> null
                currentInfo?.sourceUrl == requestUrl -> currentInfo
                else -> null
            }
        val shouldClearLocalCache =
            if (studentCacheContext != null) {
                cacheSnapshot.hasCache && !cacheSnapshot.isComplete
            } else {
                manualRefresh || (cacheSnapshot.hasCache && (cacheExpired || !cacheSnapshot.isComplete))
            }

        val result =
            fetchGuideInfoSingleFlight(requestUrl) {
                guideFetcher(requestUrl, ioDispatcher, parseDispatcher, clock)
            }
        return result.fold(
            onSuccess = { latest ->
                var latestMeta: BaGuideStudentDetailCacheMeta? = null
                withContext(ioDispatcher) {
                    if (shouldClearLocalCache) {
                        cacheClearer(context, requestUrl)
                    }
                    cacheSaver(latest)
                    if (studentCacheContext != null) {
                        mediaCacheRetainer(
                            context,
                            requestUrl,
                            collectGuideMediaCacheUrls(latest).toSet(),
                        )
                        latestMeta = buildAndSaveStudentDetailMeta(
                            store = studentCacheContext.store,
                            info = latest,
                            entry = studentCacheContext.entry,
                            previous = studentMeta,
                            nowMs = clock.nowMs(),
                        )
                    }
                }
                BaStudentGuideLoadResult(
                    info = latest,
                    error = null,
                    cacheMeta = latestMeta,
                )
            },
            onFailure = {
                var failedMeta = studentMeta
                if (studentCacheContext != null && studentMeta != null) {
                    withContext(ioDispatcher) {
                        failedMeta = studentMeta.recordFailure(now)
                        studentCacheContext.store.saveMeta(failedMeta)
                    }
                }
                BaStudentGuideLoadResult(
                    info = visibleInfo,
                    error = if (visibleInfo != null) refreshFailedKeepCacheText else loadFailedText,
                    cacheMeta = failedMeta,
                )
            },
        )
    }

    private suspend fun resolveCatalogEntrySource(sourceUrl: String): BaGuideCatalogEntry? {
        val normalized = normalizeStudentGuideSourceUrl(sourceUrl)
        if (normalized.isBlank()) return null
        val contentId = extractGuideContentIdFromUrl(normalized)
        val bundle =
            withContext(ioDispatcher) {
                catalogBundleLoader()
            } ?: return null
        return withContext(parseDispatcher) {
            val entries = BaGuideCatalogTab.entries.flatMap { tab -> bundle.entries(tab) }
            entries.firstOrNull { entry -> normalizeStudentGuideSourceUrl(entry.detailUrl) == normalized }
                ?: contentId?.let { id -> entries.firstOrNull { entry -> entry.contentId == id } }
        }
    }

    private suspend fun fetchGuideInfoSingleFlight(
        sourceUrl: String,
        fetch: suspend () -> BaStudentGuideInfo,
    ): Result<BaStudentGuideInfo> {
        val key = normalizeStudentGuideSourceUrl(sourceUrl).ifBlank { sourceUrl.trim() }
        if (key.isBlank()) {
            return runCatching { fetch() }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                }
        }
        val ownFetch = CompletableDeferred<Result<BaStudentGuideInfo>>()
        val existingFetch = inFlightGuideFetches.putIfAbsent(key, ownFetch)
        if (existingFetch != null) {
            return existingFetch.await()
        }
        try {
            val result =
                try {
                    Result.success(fetch())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    Result.failure(error)
                }
            ownFetch.complete(result)
            return result
        } catch (error: CancellationException) {
            ownFetch.cancel(error)
            throw error
        } finally {
            inFlightGuideFetches.remove(key, ownFetch)
        }
    }

    private suspend fun buildFavoriteAndRecentValidationCandidates(
        catalog: BaGuideCatalogBundle,
        favoriteContentIdsByFavoritedAtMs: Map<Long, Long>,
        recentSourceUrls: List<String>,
        maxCandidates: Int,
    ): List<String> =
        withContext(parseDispatcher) {
            if (maxCandidates <= 0) return@withContext emptyList()
            val studentEntries = catalog.entries(BaGuideCatalogTab.Student)
            if (studentEntries.isEmpty()) return@withContext emptyList()
            val studentSources =
                studentEntries
                    .mapNotNull { entry ->
                        normalizeStudentGuideSourceUrl(entry.detailUrl)
                            .takeIf { sourceUrl -> sourceUrl.isNotBlank() }
                            ?.let { sourceUrl -> entry.contentId to sourceUrl }
                    }
            val sourceByContentId = studentSources.toMap()
            val validStudentSources = studentSources.mapTo(LinkedHashSet()) { (_, sourceUrl) -> sourceUrl }
            val ordered = LinkedHashSet<String>()
            recentSourceUrls
                .asSequence()
                .map(::normalizeStudentGuideSourceUrl)
                .filter { sourceUrl -> sourceUrl in validStudentSources }
                .forEach { sourceUrl ->
                    if (ordered.size < maxCandidates) {
                        ordered += sourceUrl
                    }
                }
            favoriteContentIdsByFavoritedAtMs
                .asSequence()
                .filter { (contentId, favoritedAtMs) -> contentId > 0L && favoritedAtMs > 0L }
                .sortedByDescending { (_, favoritedAtMs) -> favoritedAtMs }
                .mapNotNull { (contentId, _) -> sourceByContentId[contentId] }
                .forEach { sourceUrl ->
                    if (ordered.size < maxCandidates) {
                        ordered += sourceUrl
                    }
                }
            ordered.toList()
        }

    private suspend fun validateStudentDetailCandidate(
        context: Context,
        sourceUrl: String,
        loadFailedText: String,
        refreshFailedKeepCacheText: String,
    ): Boolean =
        try {
            currentCoroutineContext().ensureActive()
            val firstPass =
                loadGuide(
                    context = context,
                    sourceUrl = sourceUrl,
                    currentInfo = null,
                    manualRefresh = false,
                    forceValidation = false,
                    loadFailedText = loadFailedText,
                    refreshFailedKeepCacheText = refreshFailedKeepCacheText,
                )
            if (!firstPass.validateInBackground) {
                firstPass.error == null
            } else {
                val validation =
                    loadGuide(
                        context = context,
                        sourceUrl = sourceUrl,
                        currentInfo = firstPass.info,
                        manualRefresh = false,
                        forceValidation = true,
                        loadFailedText = loadFailedText,
                        refreshFailedKeepCacheText = refreshFailedKeepCacheText,
                    )
                validation.error == null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }

    private suspend fun buildAndSaveStudentDetailMeta(
        store: BaGuideStudentDetailFileCacheStore,
        info: BaStudentGuideInfo,
        entry: BaGuideCatalogEntry,
        previous: BaGuideStudentDetailCacheMeta?,
        nowMs: Long,
    ): BaGuideStudentDetailCacheMeta? {
        val releaseDateSec = resolveDetailReleaseDateSec(info = info, entry = entry)
        val meta =
            buildBaGuideStudentDetailCacheMetaFromInfo(
                info = info,
                contentId = entry.contentId,
                tab = entry.tab,
                catalogCreatedAtSec = entry.createdAtSec,
                releaseDateSec = releaseDateSec,
                previous = previous,
                nowMs = nowMs,
            ) ?: return null
        store.saveMeta(meta)
        if (entry.releaseDateSec <= 0L && releaseDateSec > 0L) {
            releaseDateIndexUpserter(mapOf(entry.contentId to releaseDateSec))
        }
        return meta
    }

    private fun resolveDetailReleaseDateSec(
        info: BaStudentGuideInfo,
        entry: BaGuideCatalogEntry,
    ): Long {
        entry.releaseDateSec.takeIf { it > 0L }?.let { return it }
        return releaseDateExtractor(info).coerceAtLeast(0L)
    }

    private fun BaGuideStudentDetailCacheMeta.recordFailure(nowMs: Long): BaGuideStudentDetailCacheMeta {
        val nextFailureCount = (failureCount + 1).coerceAtMost(6)
        val retryDelayMs = (30L * 60L * 1000L) * nextFailureCount
        return copy(
            failureCount = nextFailureCount,
            lastFailureAtMs = nowMs,
            nextRetryAtMs = nowMs + retryDelayMs,
        )
    }

    private suspend fun resolveNpcSatelliteGuideSource(sourceUrl: String): Boolean {
        val contentId = extractGuideContentIdFromUrl(sourceUrl) ?: return false
        if (contentId <= 0L) return false
        npcSatelliteGuideFlagCache[contentId]?.let { return it }
        val isNpcSatellite =
            withContext(ioDispatcher) {
                catalogBundleLoader()
                    ?.entries(BaGuideCatalogTab.NpcSatellite)
                    ?.any { entry -> entry.contentId == contentId }
                    ?: false
            }
        npcSatelliteGuideFlagCache[contentId] = isNpcSatellite
        return isNpcSatellite
    }
}
