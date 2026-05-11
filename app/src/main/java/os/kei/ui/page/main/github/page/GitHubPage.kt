package os.kei.ui.page.main.github.page

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.feature.github.model.isKeiOsSelfTrack
import os.kei.ui.page.main.github.VersionCheckUi
import os.kei.ui.page.main.github.query.systemDownloadManagerOption
import os.kei.ui.page.main.github.section.GitHubMainContent
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
fun GitHubPage(
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    externalRefreshTriggerToken: Int = 0,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    enableSearchBar: Boolean = true,
    onActionBarInteractingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val openLinkFailureMessage = context.getString(R.string.github_error_open_link)
    val systemDmOption = remember(context) { systemDownloadManagerOption(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val isDark = isSystemInDarkTheme()
    val githubPageViewModel: GitHubPageViewModel = viewModel()
    val isListScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val fullBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val topBarBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val backdrops = rememberMainPageBackdropSet(
        keyPrefix = "github",
        distinctLayers = fullBackdropEffectsEnabled
    )
    val topBarColor = rememberAppTopBarColor(
        enableBackdropEffects = topBarBackdropEffectsEnabled
    )

    val state = rememberGitHubPageState(githubPageViewModel)
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val transferState by githubPageViewModel.transferState.collectAsStateWithLifecycle()
    val installedOnlineShareTargets by githubPageViewModel.installedOnlineShareTargets.collectAsStateWithLifecycle()
    val checkLogicDownloaderOptions by githubPageViewModel.checkLogicDownloaderOptions.collectAsStateWithLifecycle()
    val contentDerivedState by githubPageViewModel.contentDerivedState.collectAsStateWithLifecycle()
    LaunchedEffect(isListScrolling) {
        if (!isListScrolling) {
            state.settleScrollChromeVisibility()
        }
    }
    LaunchedEffect(context, state, runtime.hasActivated) {
        if (!runtime.hasActivated) return@LaunchedEffect
        githubPageViewModel.bindContextObservers(
            context = context,
            state = state
        )
    }
    SideEffect {
        state.updateScrollBounds(
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward
        )
    }
    val actions = remember(
        context,
        scope,
        state,
        githubPageViewModel.repository,
        systemDmOption,
        openLinkFailureMessage
    ) {
        GitHubPageActions(
            context = context,
            scope = scope,
            state = state,
            repository = githubPageViewModel.repository,
            systemDmOption = systemDmOption,
            openLinkFailureMessage = openLinkFailureMessage
        )
    }
    val appListPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            scope.launch { actions.reloadApps(forceRefresh = true) }
        }
    val launchAppListPermission: (Intent) -> Unit = remember(appListPermissionLauncher) {
        { intent: Intent -> appListPermissionLauncher.launch(intent) }
    }
    val starImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleGitHubStarImportActivityResult(
                result = result,
                actions = actions
            )
        }
    val tracksExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        handleGitHubTrackExportDestinationResult(
            context = context,
            scope = scope,
            githubPageViewModel = githubPageViewModel,
            uri = uri
        )
    }
    val tracksImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        handleGitHubTrackImportSourceResult(
            context = context,
            scope = scope,
            state = state,
            actions = actions,
            githubPageViewModel = githubPageViewModel,
            uri = uri
        )
    }
    LaunchedEffect(externalRefreshTriggerToken) {
        if (externalRefreshTriggerToken <= 0) return@LaunchedEffect
        actions.refreshAllTracked(showToast = true)
    }
    LaunchedEffect(actions, runtime.contentReady) {
        if (!runtime.contentReady) return@LaunchedEffect
        actions.syncActiveShareImportFlowFromStore()
    }

    BindGitHubPageLifecycleCoordinator(
        context = context,
        listState = listState,
        scrollToTopSignal = runtime.scrollToTopSignal,
        isPageWarmActive = runtime.hasActivated && runtime.isWarmDataActive,
        isPageDataActive = runtime.contentReady && runtime.isDataActive,
        state = state,
        actions = actions,
        installedOnlineShareTargets = installedOnlineShareTargets,
        onLaunchAppListPermission = launchAppListPermission,
        onActionBarInteractingChanged = onActionBarInteractingChanged
    )

    val hasKeiOsSelfTrack by remember {
        derivedStateOf { state.trackedItems.any { it.isKeiOsSelfTrack() } }
    }
    val githubGlassRuntime = LocalGlassEffectRuntime.current
    CompositionLocalProvider(LocalGlassEffectRuntime provides githubGlassRuntime) {
        GitHubMainContent(
            contentBottomPadding = runtime.contentBottomPadding,
            listState = listState,
            scrollBehavior = scrollBehavior,
            addButtonScrollConnection = state.addButtonScrollConnection,
            topBarBackdrop = backdrops.topBar,
            contentBackdrop = backdrops.content,
            topBarColor = topBarColor,
            bottomBarVisible = runtime.bottomBarVisible,
            liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            searchExpanded = enableSearchBar && searchExpanded,
            trackedSearch = state.trackedSearch,
            sortMode = state.sortMode,
            showFailedOnly = state.showFailedOnly,
            showSortPopup = state.showSortPopup,
            floatingDockSide = runtime.floatingDockSide,
            deleteInProgress = state.deleteInProgress,
            isDark = isDark,
            overviewRefreshState = state.overviewRefreshState,
            overviewExpanded = state.overviewExpanded,
            refreshProgress = state.refreshProgress,
            lastRefreshMs = state.lastRefreshMs,
            lookupConfig = state.lookupConfig,
            overviewVisibleEntries = state.overviewVisibleEntries,
            overviewMetrics = contentDerivedState.trackedUi.overviewMetrics,
            trackedItems = state.trackedItems,
            filteredTracked = contentDerivedState.trackedUi.filteredTracked,
            sortedTracked = contentDerivedState.trackedUi.sortedTracked,
            appLastUpdatedAtByTrackId = contentDerivedState.appLastUpdatedAtByTrackId,
            checkStates = state.checkStates,
            itemRefreshLoading = state.itemRefreshLoading,
            apkAssetBundles = state.apkAssetBundles,
            apkAssetLoading = state.apkAssetLoading,
            apkAssetErrors = state.apkAssetErrors,
            apkAssetExpanded = state.apkAssetExpanded,
            trackedCardExpanded = state.trackedCardExpanded,
            trackedLocalVersionExpanded = state.trackedLocalVersionExpanded,
            trackedStableVersionExpanded = state.trackedStableVersionExpanded,
            trackedPreReleaseVersionExpanded = state.trackedPreReleaseVersionExpanded,
            pendingShareImportPreview = state.pendingShareImportPreview,
            pendingShareImportTrack = state.pendingShareImportTrack,
            pendingShareImportAttachCandidate = state.pendingShareImportAttachCandidate,
            pendingShareImportResult = state.pendingShareImportResult,
            showPendingShareImportCard = contentDerivedState.showPendingShareImportCard,
            pendingShareImportRepoOverlapCount = contentDerivedState.pendingShareImportRepoOverlapCount,
            onTrackedSearchChange = { state.trackedSearch = it },
            onSearchExpandedChange = { expanded ->
                searchExpanded = enableSearchBar && expanded
            },
            onShowSortPopupChange = { state.showSortPopup = it },
            onSortModeChange = { state.sortMode = it },
            onOpenStrategySheet = actions::openStrategySheet,
            onOpenCheckLogicSheet = actions::openCheckLogicSheet,
            onOverviewExpandedChange = actions::setOverviewExpanded,
            onLocalVersionExpandedChange = actions::setTrackedLocalVersionExpanded,
            onStableVersionExpandedChange = actions::setTrackedStableVersionExpanded,
            onPreReleaseVersionExpandedChange = actions::setTrackedPreReleaseVersionExpanded,
            onOpenOverviewEntrySheet = actions::openOverviewEntrySheet,
            onRefreshAllTracked = { actions.refreshAllTracked(showToast = true) },
            onRetryFailedTracked = { actions.refreshFailedTrackedItems(showToast = true) },
            onShowFailedOnlyChange = { state.showFailedOnly = it },
            onRefreshTrackedItem = { actions.refreshTrackedItem(it, showToastOnError = true) },
            onOpenActionsSheet = actions::openActionsSheet,
            onOpenTrackSheetForAdd = actions::openTrackSheetForAdd,
            onOpenTrackSheetForEdit = actions::openTrackSheetForEdit,
            onRequestDeleteTrackedItem = actions::requestDeleteTrackedItem,
            onClearApkAssetUiState = actions::clearApkAssetUiState,
            onCollapseApkAssetPanel = { item, itemState ->
                actions.clearApkAssetUiState(item.id)
                actions.clearApkAssetCache(item, itemState)
            },
            onLoadApkAssets = { item, itemState, toggleOnlyWhenCached, includeAllAssets ->
                actions.loadApkAssets(
                    item = item,
                    itemState = itemState,
                    toggleOnlyWhenCached = toggleOnlyWhenCached,
                    includeAllAssets = includeAllAssets
                )
            },
            onOpenDecisionAssistDetail = { type, item ->
                state.decisionAssistDetailRequest = GitHubDecisionAssistDetailRequest(
                    type = type,
                    item = item
                )
                if (
                    type == GitHubDecisionAssistDetailType.ReleaseNotes &&
                    state.apkAssetBundles[item.id]?.releaseNotesBody.isNullOrBlank()
                ) {
                    actions.loadReleaseNotes(
                        item = item,
                        itemState = state.checkStates[item.id] ?: VersionCheckUi(),
                        clearCache = false
                    )
                }
            },
            onOpenExternalUrl = actions::openExternalUrl,
            onOpenApkInfo = actions::openApkInfo,
            onOpenApkInDownloader = actions::openApkInDownloader,
            onShareApkLink = actions::shareApkLink,
            onOpenShareImportFlow = actions::openShareImportFlow,
            onOpenShareImportResult = actions::focusShareImportResult,
            onCancelActiveShareImportFlow = actions::cancelActiveShareImportFlow,
            onCancelPendingShareImportTrack = actions::cancelPendingShareImportTrack,
            onDismissShareImportResult = actions::dismissShareImportResult,
            onActionBarInteractingChanged = onActionBarInteractingChanged
        )
    }

    val transferCallbacks = remember(
        context,
        scope,
        state,
        actions,
        githubPageViewModel,
        tracksExportLauncher,
        tracksImportLauncher,
        starImportLauncher
    ) {
        buildGitHubPageTrackTransferCallbacks(
            context = context,
            scope = scope,
            state = state,
            actions = actions,
            githubPageViewModel = githubPageViewModel,
            launchTrackedExport = { fileName -> tracksExportLauncher.launch(fileName) },
            launchTrackedImport = { mimeTypes -> tracksImportLauncher.launch(mimeTypes) },
            launchStarImport = { intent -> starImportLauncher.launch(intent) }
        )
    }

    CompositionLocalProvider(LocalGlassEffectRuntime provides githubGlassRuntime) {
        GitHubPageSheetHost(
            context = context,
            backdrops = backdrops,
            state = state,
            actions = actions,
            contentDerivedState = contentDerivedState,
            installedOnlineShareTargets = installedOnlineShareTargets,
            checkLogicDownloaderOptions = checkLogicDownloaderOptions,
            hasKeiOsSelfTrack = hasKeiOsSelfTrack,
            tracksExporting = transferState.tracksExporting,
            tracksImporting = transferState.tracksImporting,
            onEnsureKeiOsSelfTrack = actions::ensureKeiOsSelfTrack,
            onExportTrackedItems = transferCallbacks.onExportTrackedItems,
            onImportTrackedItems = transferCallbacks.onImportTrackedItems,
            onOpenStarImport = transferCallbacks.onOpenStarImport,
            onConfirmTrackImport = transferCallbacks.onConfirmTrackImport
        )
    }

}
