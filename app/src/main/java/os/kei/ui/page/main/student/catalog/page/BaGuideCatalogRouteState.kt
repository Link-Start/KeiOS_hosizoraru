@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogDataUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmCacheSnapshot
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedDerivedState
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListDerivedState

@Stable
internal data class BaGuideCatalogRouteState(
    val catalogDataState: BaGuideCatalogDataUiState,
    val catalogListDerivedStates: Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>,
    val studentBgmListDerivedState: BaGuideStudentBgmListDerivedState,
    val favoriteBgmListDerivedState: BaGuideFavoriteBgmListDerivedState,
    val studentBgmDisplayedDerivedState: BaGuideStudentBgmDisplayedDerivedState,
    val catalogFavoriteEntries: Map<Long, Long>,
    val favoriteBgms: List<GuideBgmFavoriteItem>,
    val bgmCacheSnapshot: BaGuideFavoriteBgmCacheSnapshot,
    val favoriteBgmOfflineCacheState: BaGuideFavoriteBgmOfflineCacheUiState,
    val nativeBgmMediaNotificationEnabled: Boolean,
)

@Composable
internal fun collectBaGuideCatalogRouteState(catalogViewModel: BaGuideCatalogViewModel): BaGuideCatalogRouteState {
    val catalogDataState by catalogViewModel.dataState.collectAsStateWithLifecycle()
    val catalogListDerivedStates by catalogViewModel.catalogListDerivedStates.collectAsStateWithLifecycle()
    val studentBgmListDerivedState by catalogViewModel.studentBgmListDerivedState.collectAsStateWithLifecycle()
    val favoriteBgmListDerivedState by catalogViewModel.favoriteBgmListDerivedState.collectAsStateWithLifecycle()
    val studentBgmDisplayedDerivedState by catalogViewModel.studentBgmDisplayedDerivedState.collectAsStateWithLifecycle()
    val catalogFavoriteEntries by catalogViewModel.catalogFavoriteEntries.collectAsStateWithLifecycle()
    val favoriteBgms by catalogViewModel.favoriteBgms.collectAsStateWithLifecycle()
    val bgmCacheSnapshot by catalogViewModel.bgmCacheSnapshot.collectAsStateWithLifecycle()
    val favoriteBgmOfflineCacheState by catalogViewModel.favoriteBgmOfflineCacheState.collectAsStateWithLifecycle()
    val nativeBgmMediaNotificationEnabled by
        catalogViewModel.nativeBgmMediaNotificationEnabled.collectAsStateWithLifecycle()

    return remember(
        catalogDataState,
        catalogListDerivedStates,
        studentBgmListDerivedState,
        favoriteBgmListDerivedState,
        studentBgmDisplayedDerivedState,
        catalogFavoriteEntries,
        favoriteBgms,
        bgmCacheSnapshot,
        favoriteBgmOfflineCacheState,
        nativeBgmMediaNotificationEnabled,
    ) {
        BaGuideCatalogRouteState(
            catalogDataState = catalogDataState,
            catalogListDerivedStates = catalogListDerivedStates,
            studentBgmListDerivedState = studentBgmListDerivedState,
            favoriteBgmListDerivedState = favoriteBgmListDerivedState,
            studentBgmDisplayedDerivedState = studentBgmDisplayedDerivedState,
            catalogFavoriteEntries = catalogFavoriteEntries,
            favoriteBgms = favoriteBgms,
            bgmCacheSnapshot = bgmCacheSnapshot,
            favoriteBgmOfflineCacheState = favoriteBgmOfflineCacheState,
            nativeBgmMediaNotificationEnabled = nativeBgmMediaNotificationEnabled,
        )
    }
}
