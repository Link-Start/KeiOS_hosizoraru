package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.filterByCatalogFilters
import os.kei.ui.page.main.student.catalog.filterByQuery
import os.kei.ui.page.main.student.catalog.fetchBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.component.filterAndSortBgmFavorites
import os.kei.ui.page.main.student.catalog.component.buildBaGuideStudentBgmDisplayedModel
import os.kei.ui.page.main.student.catalog.hydrateBaGuideCatalogReleaseDateIndex
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogBundleComplete
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogCacheExpired
import os.kei.ui.page.main.student.catalog.loadCachedBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.selectedCatalogFilterOptionsForDefinitions
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import kotlin.coroutines.cancellation.CancellationException

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
    fun bgmFavoritesFlow(): StateFlow<List<GuideBgmFavoriteItem>> =
        GuideBgmFavoriteStore.favoritesFlow()

    fun bgmFavoritesSnapshot(): List<GuideBgmFavoriteItem> =
        GuideBgmFavoriteStore.favoritesSnapshot()

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

    suspend fun deriveCatalogListState(input: BaGuideCatalogListInput): BaGuideCatalogListDerivedState {
        return withContext(parseDispatcher) {
            val filterDefinitions =
                input.catalog
                    .filterDefinitions(input.tab)
                    .filter { it.type == 0 }
            val activeSelectedFilterOptions =
                selectedCatalogFilterOptionsForDefinitions(
                    selectedOptionIdsByFilterId = input.selectedFilterOptions,
                    definitions = filterDefinitions,
                )
            val sortedEntries =
                input.catalog
                    .entries(input.tab)
                    .sortedByMode(
                        mode = input.sortMode,
                        favoriteCatalogEntries = input.favoriteCatalogEntries,
                        filterDefinitions = filterDefinitions,
                    )
            BaGuideCatalogListDerivedState(
                filteredEntries =
                    sortedEntries
                        .filterByCatalogFilters(activeSelectedFilterOptions)
                        .filterByQuery(input.searchQuery),
                activeFilterCount = activeSelectedFilterOptions.size,
                deriving = false,
            )
        }
    }

    suspend fun deriveStudentBgmListState(input: BaGuideStudentBgmListInput): BaGuideStudentBgmListDerivedState {
        return withContext(parseDispatcher) {
            val allStudentEntries =
                input.catalog
                    .entries(BaGuideCatalogTab.Student)
                    .sortedBy { it.order }
            val favoriteByNormalizedSourceUrl =
                buildMap {
                    input.favorites.forEach { favorite ->
                        val normalizedSourceUrl = normalizeGuideUrl(favorite.sourceUrl)
                        if (normalizedSourceUrl.isNotBlank() && !containsKey(normalizedSourceUrl)) {
                            put(normalizedSourceUrl, favorite)
                        }
                    }
            }
            val favoriteContentIds =
                favoriteStudentBgmEntryContentIds(
                    entries = allStudentEntries,
                    favoriteSourceUrls = favoriteByNormalizedSourceUrl.keys,
                )
            BaGuideStudentBgmListDerivedState(
                allStudentEntries = allStudentEntries,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl,
                favoriteAudioUrls =
                    input.favorites.mapNotNullTo(LinkedHashSet()) { favorite ->
                        favorite.audioUrl.takeIf { it.isNotBlank() }
                    },
                filteredEntries =
                    filterAndSortStudentBgmEntries(
                        entries = allStudentEntries,
                        searchQuery = input.searchQuery,
                        favoriteContentIds = favoriteContentIds,
                    ),
                deriving = false,
            )
        }
    }

    suspend fun deriveFavoriteBgmListState(input: BaGuideFavoriteBgmListInput): BaGuideFavoriteBgmListDerivedState {
        return withContext(parseDispatcher) {
            BaGuideFavoriteBgmListDerivedState(
                displayedFavorites =
                    filterAndSortBgmFavorites(
                        favorites = input.favorites,
                        searchQuery = input.searchQuery,
                        sortMode = input.sortMode,
                    ),
                deriving = false,
            )
        }
    }

    suspend fun deriveStudentBgmDisplayedState(
        input: BaGuideStudentBgmDisplayedInput,
    ): BaGuideStudentBgmDisplayedDerivedState {
        return withContext(parseDispatcher) {
            BaGuideStudentBgmDisplayedDerivedState(
                input = input,
                model =
                    buildBaGuideStudentBgmDisplayedModel(
                        displayedEntries = input.displayedEntries,
                        lookupStates = input.lookupStates,
                        favoriteByNormalizedSourceUrl = input.favoriteByNormalizedSourceUrl,
                        favoriteAudioUrls = input.favoriteAudioUrls,
                    ),
                deriving = false,
            )
        }
    }
}
