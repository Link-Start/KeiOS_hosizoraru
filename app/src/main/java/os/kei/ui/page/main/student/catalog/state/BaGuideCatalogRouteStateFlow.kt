package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

private data class BaGuideCatalogRouteCoreState(
    val catalogDataState: BaGuideCatalogDataUiState,
    val catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    val studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    val favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    val studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
)

private data class BaGuideCatalogRouteLibraryState(
    val core: BaGuideCatalogRouteCoreState,
    val catalogFavoriteEntries: Map<Long, Long>,
    val favoriteBgms: List<GuideBgmFavoriteItem>,
    val bgmCacheSnapshot: BaGuideFavoriteBgmCacheSnapshot,
    val favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
)

internal fun buildBaGuideCatalogRouteStateFlow(
    scope: CoroutineScope,
    dataState: StateFlow<BaGuideCatalogDataUiState>,
    catalogListDerivedStates: StateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>>,
    studentBgmListDerivedState: StateFlow<BaGuideStudentBgmListDerivedState>,
    favoriteBgmListDerivedState: StateFlow<BaGuideFavoriteBgmListDerivedState>,
    studentBgmDisplayedDerivedState: StateFlow<BaGuideStudentBgmDisplayedDerivedState>,
    catalogFavoriteEntries: StateFlow<Map<Long, Long>>,
    favoriteBgms: StateFlow<List<GuideBgmFavoriteItem>>,
    bgmCacheSnapshot: StateFlow<BaGuideFavoriteBgmCacheSnapshot>,
    favoriteBgmOfflineCacheState: StateFlow<BaGuideFavoriteBgmOfflineCacheUiState>,
    nativeBgmMediaNotificationEnabled: StateFlow<Boolean>,
    transferSettings: StateFlow<BaGuideCatalogTransferSettingsUiState>,
): StateFlow<BaGuideCatalogRouteState> {
    val routeCoreState =
        combine(
            dataState,
            catalogListDerivedStates,
            studentBgmListDerivedState,
            favoriteBgmListDerivedState,
            studentBgmDisplayedDerivedState,
        ) {
                catalogDataState,
                catalogListDerivedStates,
                studentBgmListDerivedState,
                favoriteBgmListDerivedState,
                studentBgmDisplayedDerivedState,
            ->
            BaGuideCatalogRouteCoreState(
                catalogDataState = catalogDataState,
                catalogListDerivedStates = catalogListDerivedStates,
                studentBgmListDerivedState = studentBgmListDerivedState,
                favoriteBgmListDerivedState = favoriteBgmListDerivedState,
                studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                BaGuideCatalogRouteCoreState(
                    catalogDataState = dataState.value,
                    catalogListDerivedStates = catalogListDerivedStates.value,
                    studentBgmListDerivedState = studentBgmListDerivedState.value,
                    favoriteBgmListDerivedState = favoriteBgmListDerivedState.value,
                    studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState.value,
                ),
        )

    val routeLibraryState =
        combine(
            routeCoreState,
            catalogFavoriteEntries,
            favoriteBgms,
            bgmCacheSnapshot,
            favoriteBgmOfflineCacheState,
        ) { core, catalogFavoriteEntries, favoriteBgms, bgmCacheSnapshot, favoriteBgmOfflineCacheState ->
            BaGuideCatalogRouteLibraryState(
                core = core,
                catalogFavoriteEntries = catalogFavoriteEntries,
                favoriteBgms = favoriteBgms,
                bgmCacheSnapshot = bgmCacheSnapshot,
                favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
            )
        }.stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue =
                BaGuideCatalogRouteLibraryState(
                    core = routeCoreState.value,
                    catalogFavoriteEntries = catalogFavoriteEntries.value,
                    favoriteBgms = favoriteBgms.value,
                    bgmCacheSnapshot = bgmCacheSnapshot.value,
                    favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState.value,
                ),
        )

    return combine(
        routeLibraryState,
        nativeBgmMediaNotificationEnabled,
        transferSettings,
    ) { libraryState, nativeBgmMediaNotificationEnabled, transferSettings ->
        BaGuideCatalogRouteState(
            catalogDataState = libraryState.core.catalogDataState,
            catalogListDerivedStates = libraryState.core.catalogListDerivedStates,
            studentBgmListDerivedState = libraryState.core.studentBgmListDerivedState,
            favoriteBgmListDerivedState = libraryState.core.favoriteBgmListDerivedState,
            studentBgmDisplayedDerivedState = libraryState.core.studentBgmDisplayedDerivedState,
            catalogFavoriteEntries = libraryState.catalogFavoriteEntries,
            favoriteBgms = libraryState.favoriteBgms,
            bgmCacheSnapshot = libraryState.bgmCacheSnapshot,
            favoriteBgmOfflineCacheState = libraryState.favoriteBgmOfflineCacheState,
            nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
            transferSettings = transferSettings,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue =
            BaGuideCatalogRouteState(
                catalogDataState = routeCoreState.value.catalogDataState,
                catalogListDerivedStates = routeCoreState.value.catalogListDerivedStates,
                studentBgmListDerivedState = routeCoreState.value.studentBgmListDerivedState,
                favoriteBgmListDerivedState = routeCoreState.value.favoriteBgmListDerivedState,
                studentBgmDisplayedDerivedState = routeCoreState.value.studentBgmDisplayedDerivedState,
                catalogFavoriteEntries = catalogFavoriteEntries.value,
                favoriteBgms = favoriteBgms.value,
                bgmCacheSnapshot = bgmCacheSnapshot.value,
                favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState.value,
                nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled.value,
                transferSettings = transferSettings.value,
            ),
    )
}
