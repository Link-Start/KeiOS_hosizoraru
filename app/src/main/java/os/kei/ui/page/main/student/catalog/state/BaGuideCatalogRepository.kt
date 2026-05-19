package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.fetchBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.hydrateBaGuideCatalogReleaseDateIndex
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogBundleComplete
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogCacheExpired
import os.kei.ui.page.main.student.catalog.loadCachedBaGuideCatalogBundle
import kotlin.coroutines.cancellation.CancellationException
import os.kei.core.concurrency.AppDispatchers

internal data class BaGuideCatalogLoadResult(
    val catalog: BaGuideCatalogBundle,
    val error: String?
)

internal class BaGuideCatalogRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val refreshIntervalLoader: () -> Int = BASettingsStore::loadCalendarRefreshIntervalHours,
    private val cachedBundleLoader: () -> BaGuideCatalogBundle? = ::loadCachedBaGuideCatalogBundle,
    private val catalogFetcher: suspend (
        forceRefresh: Boolean,
        networkDispatcher: CoroutineDispatcher,
        parseDispatcher: CoroutineDispatcher
    ) -> BaGuideCatalogBundle =
        ::fetchBaGuideCatalogBundle,
    private val completeChecker: (BaGuideCatalogBundle?) -> Boolean =
        ::isBaGuideCatalogBundleComplete,
    private val expiredChecker: (BaGuideCatalogBundle?, Int, Long) -> Boolean =
        ::isBaGuideCatalogCacheExpired
) {
    suspend fun loadCatalog(
        context: Context?,
        currentCatalog: BaGuideCatalogBundle,
        manualRefresh: Boolean,
        loadFailedText: String,
        refreshFailedKeepCacheText: String
    ): BaGuideCatalogLoadResult {
        val now = System.currentTimeMillis()
        val refreshIntervalHours = withContext(ioDispatcher) {
            refreshIntervalLoader()
        }
        val cachedBundle = withContext(ioDispatcher) {
            cachedBundleLoader()
        }
        val cacheComplete = completeChecker(cachedBundle)
        val cacheExpired = expiredChecker(cachedBundle, refreshIntervalHours, now)

        if (!manualRefresh && cacheComplete && !cacheExpired) {
            return BaGuideCatalogLoadResult(
                catalog = cachedBundle ?: BaGuideCatalogBundle.EMPTY,
                error = null
            )
        }

        val result = try {
            Result.success(
                catalogFetcher(
                    true,
                    ioDispatcher,
                    parseDispatcher
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
        return result.fold(
            onSuccess = { latest ->
                BaGuideCatalogLoadResult(
                    catalog = latest,
                    error = null
                )
            },
            onFailure = {
                val fallback = when {
                    currentCatalog.entriesByTab.values.any { it.isNotEmpty() } -> currentCatalog
                    cacheComplete && cachedBundle != null -> cachedBundle
                    else -> BaGuideCatalogBundle.EMPTY
                }
                BaGuideCatalogLoadResult(
                    catalog = fallback,
                    error = if (fallback.entriesByTab.values.all { it.isEmpty() }) {
                        loadFailedText
                    } else {
                        refreshFailedKeepCacheText
                    }
                )
            }
        )
    }

    suspend fun hydrateReleaseDateIndex(
        source: BaGuideCatalogBundle,
        onBundleUpdated: (BaGuideCatalogBundle) -> Unit
    ): BaGuideCatalogBundle {
        return hydrateBaGuideCatalogReleaseDateIndex(
            source = source,
            maxNetworkFetchPerPass = CATALOG_RELEASE_DATE_FETCH_LIMIT_PER_PASS,
            networkDispatcher = ioDispatcher,
            parseDispatcher = parseDispatcher,
            onBundleUpdated = onBundleUpdated
        )
    }
}
