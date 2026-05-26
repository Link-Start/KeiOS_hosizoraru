package os.kei.ui.page.main.student.page.state

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideBgmFavoriteRepository
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.BaGuideTempMediaCache
import os.kei.ui.page.main.student.BaStudentGuideInfo
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetchGuideInfoAsync
import os.kei.ui.page.main.student.isNpcSatelliteLikeGuide
import os.kei.ui.page.main.student.page.support.collectGuideStaticImagePrefetchUrls
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal data class BaStudentGuideLoadResult(
    val info: BaStudentGuideInfo?,
    val error: String?,
)

internal data class BaStudentGuideMediaSettings(
    val mediaAdaptiveRotationEnabled: Boolean = false,
)

internal class BaStudentGuideRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val bgmFavoriteRepository: BaGuideBgmFavoriteRepository = BaGuideBgmFavoriteRepository(),
    private val clock: BaGuideDataClock = BaGuideSystemDataClock,
) {
    private val npcSatelliteGuideFlagCache = ConcurrentHashMap<Long, Boolean>()

    suspend fun loadCurrentUrlAsync(): String =
        withContext(ioDispatcher) {
            BaStudentGuideStore.loadCurrentUrl()
        }

    suspend fun saveCurrentUrlAsync(sourceUrl: String) {
        withContext(ioDispatcher) {
            BaStudentGuideStore.setCurrentUrl(sourceUrl)
        }
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
                BASettingsStore.loadCalendarRefreshIntervalHours()
            }
        val cacheSnapshot =
            withContext(ioDispatcher) {
                BaStudentGuideStore.loadInfoSnapshot(requestUrl)
            }
        val cacheExpired =
            BaStudentGuideStore.isCacheExpired(
                snapshot = cacheSnapshot,
                refreshIntervalHours = refreshIntervalHours,
                nowMs = now,
            )
        val cacheInfo = cacheSnapshot.info.takeIf { cacheSnapshot.isComplete }
        if (!manualRefresh && cacheInfo != null && !cacheExpired) {
            return BaStudentGuideLoadResult(info = cacheInfo, error = null)
        }

        val visibleInfo =
            when {
                cacheInfo != null -> cacheInfo
                cacheSnapshot.hasCache -> null
                currentInfo?.sourceUrl == requestUrl -> currentInfo
                else -> null
            }
        val shouldClearLocalCache =
            manualRefresh || (cacheSnapshot.hasCache && (cacheExpired || !cacheSnapshot.isComplete))

        val result =
            try {
                Result.success(
                    fetchGuideInfoAsync(
                        sourceUrl = requestUrl,
                        networkDispatcher = ioDispatcher,
                        parseDispatcher = parseDispatcher,
                        clock = clock,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        return result.fold(
            onSuccess = { latest ->
                withContext(ioDispatcher) {
                    if (shouldClearLocalCache) {
                        BaStudentGuideStore.clearCachedInfo(requestUrl)
                        BaGuideTempMediaCache.clearGuideCache(context, requestUrl)
                    }
                    BaStudentGuideStore.saveInfo(latest)
                }
                BaStudentGuideLoadResult(info = latest, error = null)
            },
            onFailure = {
                BaStudentGuideLoadResult(
                    info = visibleInfo,
                    error = if (visibleInfo != null) refreshFailedKeepCacheText else loadFailedText,
                )
            },
        )
    }

    private suspend fun resolveNpcSatelliteGuideSource(sourceUrl: String): Boolean {
        val contentId = extractGuideContentIdFromUrl(sourceUrl) ?: return false
        if (contentId <= 0L) return false
        npcSatelliteGuideFlagCache[contentId]?.let { return it }
        val isNpcSatellite =
            withContext(ioDispatcher) {
                BaGuideCatalogStore
                    .loadBundle()
                    ?.entries(BaGuideCatalogTab.NpcSatellite)
                    ?.any { entry -> entry.contentId == contentId }
                    ?: false
            }
        npcSatelliteGuideFlagCache[contentId] = isNpcSatellite
        return isNpcSatellite
    }
}
