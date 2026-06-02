package os.kei.ui.page.main.student.catalog.state

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.student.BaGuideBgmFavoriteRepository
import os.kei.ui.page.main.student.BaGuideDataClock
import os.kei.ui.page.main.student.BaGuideSystemDataClock
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.BaGuideCatalogRefreshMode
import os.kei.ui.page.main.student.catalog.BaGuideCatalogStore
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.component.buildBaGuideStudentBgmDisplayedModel
import os.kei.ui.page.main.student.catalog.component.filterAndSortBgmFavorites
import os.kei.ui.page.main.student.catalog.component.withResolvedCatalogStudentMetadata
import os.kei.ui.page.main.student.catalog.fetchBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.filterByCatalogFilters
import os.kei.ui.page.main.student.catalog.filterByQuery
import os.kei.ui.page.main.student.catalog.hydrateBaGuideCatalogReleaseDateIndex
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogBundleComplete
import os.kei.ui.page.main.student.catalog.isBaGuideCatalogCacheExpired
import os.kei.ui.page.main.student.catalog.loadCachedBaGuideCatalogBundle
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportApplyResult
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportKind
import os.kei.ui.page.main.student.catalog.page.BaGuideCatalogImportPreviewState
import os.kei.ui.page.main.student.catalog.page.CatalogFavoritesClock
import os.kei.ui.page.main.student.catalog.page.CatalogFavoritesSystemClock
import os.kei.ui.page.main.student.catalog.page.applyBaGuideCatalogFavoritesImportAsync
import os.kei.ui.page.main.student.catalog.page.buildBaGuideCatalogImportPreviewAsync
import os.kei.ui.page.main.student.catalog.page.buildBgmFavoritesExportJsonAsync
import os.kei.ui.page.main.student.catalog.page.buildCatalogAllFavoritesExportJsonAsync
import os.kei.ui.page.main.student.catalog.page.buildCatalogFavoritesExportJsonAsync
import os.kei.ui.page.main.student.catalog.selectBaGuideCatalogRefreshMode
import os.kei.ui.page.main.student.catalog.selectedCatalogFilterOptionsForDefinitions
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore
import kotlin.coroutines.cancellation.CancellationException

internal data class BaGuideCatalogLoadResult(
    val catalog: BaGuideCatalogBundle,
    val error: String?,
)

internal class BaGuideCatalogRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    private val parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val refreshIntervalLoader: () -> Int = BaGuideCatalogStore::loadIncrementalRefreshIntervalHours,
    private val cachedBundleLoader: () -> BaGuideCatalogBundle? = ::loadCachedBaGuideCatalogBundle,
    private val catalogFetcher: suspend (
        forceRefresh: Boolean,
        networkDispatcher: CoroutineDispatcher,
        parseDispatcher: CoroutineDispatcher,
        clock: BaGuideDataClock,
        refreshMode: BaGuideCatalogRefreshMode,
    ) -> BaGuideCatalogBundle =
        ::fetchBaGuideCatalogBundle,
    private val completeChecker: (BaGuideCatalogBundle?) -> Boolean =
        ::isBaGuideCatalogBundleComplete,
    private val expiredChecker: (BaGuideCatalogBundle?, Int, Long) -> Boolean =
        ::isBaGuideCatalogCacheExpired,
    private val bgmFavoriteRepository: BaGuideBgmFavoriteRepository = BaGuideBgmFavoriteRepository(),
    private val catalogFavoritesClock: CatalogFavoritesClock = CatalogFavoritesSystemClock,
    private val clock: BaGuideDataClock = BaGuideSystemDataClock,
) {
    fun bgmFavoritesFlow(): StateFlow<List<GuideBgmFavoriteItem>> = bgmFavoriteRepository.favoritesFlow()

    suspend fun hydrateBgmFavorites(): List<GuideBgmFavoriteItem> = bgmFavoriteRepository.hydrateFavorites()

    fun bgmPlaybackSnapshot() = bgmFavoriteRepository.playbackSnapshot()

    suspend fun loadCatalogFavorites(): Map<Long, Long> =
        withContext(ioDispatcher) {
            BaGuideCatalogStore.loadFavorites()
        }

    suspend fun toggleCatalogFavorite(contentId: Long): Map<Long, Long> =
        withContext(ioDispatcher) {
            if (contentId <= 0L) return@withContext BaGuideCatalogStore.loadFavorites()
            BaGuideCatalogStore.toggleFavoriteSnapshot(contentId)
        }

    suspend fun replaceCatalogFavorites(favorites: Map<Long, Long>): Map<Long, Long> =
        withContext(ioDispatcher) {
            val normalized =
                favorites
                    .filterKeys { it > 0L }
                    .filterValues { it > 0L }
                    .toMap()
            BaGuideCatalogStore.saveFavorites(normalized)
            normalized
        }

    suspend fun buildStudentFavoritesExportJson(favorites: Map<Long, Long>): String =
        buildCatalogFavoritesExportJsonAsync(
            favorites = favorites,
            clock = catalogFavoritesClock,
        )

    suspend fun buildAllFavoritesExportJson(favorites: Map<Long, Long>): String =
        buildCatalogAllFavoritesExportJsonAsync(
            favorites = favorites,
            clock = catalogFavoritesClock,
        )

    suspend fun buildBgmFavoritesExportJson(): String = buildBgmFavoritesExportJsonAsync()

    suspend fun buildImportPreview(
        context: Context,
        uri: Uri,
        kind: BaGuideCatalogImportKind,
        currentFavorites: Map<Long, Long>,
    ): BaGuideCatalogImportPreviewState =
        buildBaGuideCatalogImportPreviewAsync(
            context = context,
            uri = uri,
            kind = kind,
            currentFavorites = currentFavorites,
            bgmFavoriteRepository = bgmFavoriteRepository,
            clock = catalogFavoritesClock,
        )

    suspend fun applyFavoritesImport(preview: BaGuideCatalogImportPreviewState): BaGuideCatalogImportApplyResult =
        applyBaGuideCatalogFavoritesImportAsync(
            preview = preview,
            bgmFavoriteRepository = bgmFavoriteRepository,
            clock = catalogFavoritesClock,
        )

    suspend fun loadNativeBgmMediaNotificationEnabled(): Boolean =
        withContext(ioDispatcher) {
            BASettingsStore.loadNativeBgmMediaNotificationEnabled()
        }

    suspend fun saveNativeBgmMediaNotificationEnabled(enabled: Boolean) {
        withContext(ioDispatcher) {
            BASettingsStore.saveNativeBgmMediaNotificationEnabled(enabled)
        }
    }

    suspend fun loadTransferSettings(): BaGuideCatalogTransferSettingsUiState = BaGuideCatalogTransferSettingsRepository.loadSettings()

    suspend fun loadCatalogIncrementalRefreshIntervalHours(): Int =
        withContext(ioDispatcher) {
            BaGuideCatalogStore.loadIncrementalRefreshIntervalHours()
        }

    suspend fun saveCatalogIncrementalRefreshIntervalHours(hours: Int): Int =
        withContext(ioDispatcher) {
            BaGuideCatalogStore.saveIncrementalRefreshIntervalHours(hours)
        }

    suspend fun saveTransferMediaSaveCustomEnabled(enabled: Boolean) {
        BaGuideCatalogTransferSettingsRepository.saveMediaSaveCustomEnabled(enabled)
    }

    suspend fun saveTransferMediaSaveFixedTreeUri(uri: String) {
        BaGuideCatalogTransferSettingsRepository.saveMediaSaveFixedTreeUri(uri)
    }

    suspend fun clearTransferMediaSaveFixedTreeUri() {
        BaGuideCatalogTransferSettingsRepository.clearMediaSaveFixedTreeUri()
    }

    suspend fun toggleBgmFavorite(item: GuideBgmFavoriteItem): Boolean = bgmFavoriteRepository.toggleFavorite(item)

    suspend fun removeBgmFavorite(audioUrl: String) {
        bgmFavoriteRepository.removeFavorite(audioUrl)
    }

    fun requestGuideDetailTab(
        sourceUrl: String,
        tab: GuideBottomTab,
    ) {
        GuideDetailTabRequestStore.request(sourceUrl, tab)
    }

    suspend fun loadCatalog(
        context: Context?,
        currentCatalog: BaGuideCatalogBundle,
        manualRefresh: Boolean,
        loadFailedText: String,
        refreshFailedKeepCacheText: String,
    ): BaGuideCatalogLoadResult {
        val now = clock.nowMs()
        val refreshIntervalHours =
            withContext(ioDispatcher) {
                refreshIntervalLoader()
            }
        val cachedBundle =
            withContext(ioDispatcher) {
                cachedBundleLoader()
            }
        val cacheComplete = completeChecker(cachedBundle)
        val cacheExpired = expiredChecker(cachedBundle, refreshIntervalHours, now)
        val refreshMode =
            selectBaGuideCatalogRefreshMode(
                cachedBundle = cachedBundle,
                manualRefresh = manualRefresh,
                cacheComplete = cacheComplete,
                nowMs = now,
            )

        if (!manualRefresh && cacheComplete && !cacheExpired) {
            return BaGuideCatalogLoadResult(
                catalog = cachedBundle ?: BaGuideCatalogBundle.EMPTY,
                error = null,
            )
        }

        val result =
            try {
                Result.success(
                    catalogFetcher(
                        true,
                        ioDispatcher,
                        parseDispatcher,
                        clock,
                        refreshMode,
                    ),
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
                    error = null,
                )
            },
            onFailure = {
                val fallback =
                    when {
                        currentCatalog.entriesByTab.values.any { it.isNotEmpty() } -> currentCatalog
                        cachedBundle?.entriesByTab?.values?.any { it.isNotEmpty() } == true -> cachedBundle
                        else -> BaGuideCatalogBundle.EMPTY
                    }
                BaGuideCatalogLoadResult(
                    catalog = fallback,
                    error =
                        if (fallback.entriesByTab.values.all { it.isEmpty() }) {
                            loadFailedText
                        } else {
                            refreshFailedKeepCacheText
                        },
                )
            },
        )
    }

    suspend fun hydrateReleaseDateIndex(
        source: BaGuideCatalogBundle,
        onBundleUpdated: (BaGuideCatalogBundle) -> Unit,
    ): BaGuideCatalogBundle =
        hydrateBaGuideCatalogReleaseDateIndex(
            source = source,
            maxNetworkFetchPerPass = CATALOG_RELEASE_DATE_FETCH_LIMIT_PER_PASS,
            networkDispatcher = ioDispatcher,
            parseDispatcher = parseDispatcher,
            clock = clock,
            onBundleUpdated = onBundleUpdated,
        )

    suspend fun deriveCatalogListState(input: BaGuideCatalogListInput): BaGuideCatalogListDerivedState =
        withContext(parseDispatcher) {
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

    suspend fun deriveStudentBgmListState(input: BaGuideStudentBgmListInput): BaGuideStudentBgmListDerivedState =
        withContext(parseDispatcher) {
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

    suspend fun deriveFavoriteBgmListState(input: BaGuideFavoriteBgmListInput): BaGuideFavoriteBgmListDerivedState {
        val favoritesWithCatalogMetadata =
            withContext(parseDispatcher) {
                input.favorites.map { favorite ->
                    favorite.withResolvedCatalogStudentMetadata(input.catalog)
                }
            }
        val metadataIndex =
            withContext(ioDispatcher) {
                runCatching {
                    BaGuideBgmFavoriteMetadataIndex.build(favoritesWithCatalogMetadata)
                }.getOrDefault(BaGuideBgmFavoriteMetadataIndex.Empty)
            }
        return withContext(parseDispatcher) {
            val displayedFavorites =
                filterAndSortBgmFavorites(
                    favorites = favoritesWithCatalogMetadata,
                    searchQuery = input.searchQuery,
                    sortMode = input.sortMode,
                    metadataIndex = metadataIndex,
                )
            val playbackSnapshot = bgmPlaybackSnapshot()
            BaGuideFavoriteBgmListDerivedState(
                displayedFavorites = displayedFavorites,
                tracks =
                    buildFavoriteBgmTracks(
                        favorites = displayedFavorites,
                        playbackSnapshot = playbackSnapshot,
                    ),
                favoritesByTrackId = buildFavoritesByTrackId(displayedFavorites),
                metadataIndex = metadataIndex,
                playbackSnapshot = playbackSnapshot,
                deriving = false,
            )
        }
    }

    suspend fun deriveStudentBgmDisplayedState(input: BaGuideStudentBgmDisplayedInput): BaGuideStudentBgmDisplayedDerivedState =
        withContext(parseDispatcher) {
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
