package os.kei.ui.page.main.student.catalog.page

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmListInput
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmDisplayedInput
import os.kei.ui.page.main.student.catalog.state.BaGuideStudentBgmListInput

@Stable
internal class BaGuideCatalogPageActions(
    val bindCatalog: (Boolean, Int, String, String) -> Unit,
    val onRefresh: () -> Unit,
    val onToggleCatalogFavorite: (Long) -> Unit,
    val onRequestCatalogListState: (BaGuideCatalogListInput) -> Unit,
    val onRequestStudentBgmListState: (BaGuideStudentBgmListInput) -> Unit,
    val onRequestFavoriteBgmListState: (BaGuideFavoriteBgmListInput) -> Unit,
    val onRequestStudentBgmDisplayedState: (BaGuideStudentBgmDisplayedInput) -> Unit,
    val onToggleBgmFavorite: (GuideBgmFavoriteItem) -> Unit,
    val onRemoveBgmFavorite: (String) -> Unit,
    val onRemoveBgmFavoriteWithToast: (String) -> Unit,
    val onRequestGuideDetailTab: (String, GuideBottomTab) -> Unit,
    val onSetNativeBgmMediaNotificationEnabled: (Boolean) -> Unit,
    val onRequestFavoriteBgmOfflineCache: (List<GuideBgmFavoriteItem>, Boolean, Boolean) -> Unit,
    val onToggleFavoriteBgmOfflineCache: (GuideBgmFavoriteItem, List<GuideBgmFavoriteItem>) -> Unit,
    val onRequestBgmCacheSnapshot: (List<GuideBgmFavoriteItem>, Boolean) -> Unit,
    val onCacheMissingBgms: (List<GuideBgmFavoriteItem>) -> Unit,
    val onCleanInvalidBgmCache: (List<GuideBgmFavoriteItem>) -> Unit,
    val onRequestImportPreview: (Uri?, BaGuideCatalogImportKind) -> Unit,
    val onConfirmFavoritesImport: (BaGuideCatalogImportPreviewState) -> Unit,
    val onSetTransferMediaSaveCustomEnabled: (Boolean) -> Unit,
    val onSetTransferMediaSaveFixedTreeUri: (String) -> Unit,
    val onClearTransferMediaSaveFixedTreeUri: () -> Unit,
    val buildCatalogFavoritesExportJson: BaGuideCatalogJsonExportPayloadBuilder,
    val buildCatalogAllFavoritesExportJson: BaGuideCatalogJsonExportPayloadBuilder,
    val buildBgmFavoritesExportJson: BaGuideCatalogJsonExportPayloadBuilder,
)

@Composable
internal fun rememberBaGuideCatalogPageActions(catalogViewModel: BaGuideCatalogViewModel): BaGuideCatalogPageActions =
    remember(catalogViewModel) {
        BaGuideCatalogPageActions(
            bindCatalog = { transitionAnimationsEnabled, initialFetchDelayMs, loadFailedText, refreshFailedKeepCacheText ->
                catalogViewModel.bind(
                    transitionAnimationsEnabled = transitionAnimationsEnabled,
                    initialFetchDelayMs = initialFetchDelayMs,
                    loadFailedText = loadFailedText,
                    refreshFailedKeepCacheText = refreshFailedKeepCacheText,
                )
            },
            onRefresh = catalogViewModel::requestRefresh,
            onToggleCatalogFavorite = catalogViewModel::toggleCatalogFavorite,
            onRequestCatalogListState = catalogViewModel::requestCatalogListDerivedState,
            onRequestStudentBgmListState = catalogViewModel::requestStudentBgmListDerivedState,
            onRequestFavoriteBgmListState = catalogViewModel::requestFavoriteBgmListDerivedState,
            onRequestStudentBgmDisplayedState = catalogViewModel::requestStudentBgmDisplayedDerivedState,
            onToggleBgmFavorite = catalogViewModel::requestToggleBgmFavorite,
            onRemoveBgmFavorite = catalogViewModel::requestRemoveBgmFavorite,
            onRemoveBgmFavoriteWithToast = { audioUrl ->
                catalogViewModel.requestRemoveBgmFavorite(
                    audioUrl = audioUrl,
                    showToast = true,
                )
            },
            onRequestGuideDetailTab = catalogViewModel::requestGuideDetailTab,
            onSetNativeBgmMediaNotificationEnabled = catalogViewModel::setNativeBgmMediaNotificationEnabled,
            onRequestFavoriteBgmOfflineCache = catalogViewModel::requestFavoriteBgmOfflineCache,
            onToggleFavoriteBgmOfflineCache = catalogViewModel::toggleFavoriteBgmOfflineCache,
            onRequestBgmCacheSnapshot = catalogViewModel::requestBgmCacheSnapshot,
            onCacheMissingBgms = catalogViewModel::cacheMissingBgms,
            onCleanInvalidBgmCache = catalogViewModel::cleanInvalidBgmCache,
            onRequestImportPreview = catalogViewModel::requestCatalogImportPreview,
            onConfirmFavoritesImport = catalogViewModel::confirmCatalogFavoritesImport,
            onSetTransferMediaSaveCustomEnabled = catalogViewModel::setTransferMediaSaveCustomEnabled,
            onSetTransferMediaSaveFixedTreeUri = catalogViewModel::setTransferMediaSaveFixedTreeUri,
            onClearTransferMediaSaveFixedTreeUri = catalogViewModel::clearTransferMediaSaveFixedTreeUri,
            buildCatalogFavoritesExportJson = catalogViewModel::buildCatalogFavoritesExportJson,
            buildCatalogAllFavoritesExportJson = catalogViewModel::buildCatalogAllFavoritesExportJson,
            buildBgmFavoritesExportJson = catalogViewModel::buildBgmFavoritesExportJson,
        )
    }
