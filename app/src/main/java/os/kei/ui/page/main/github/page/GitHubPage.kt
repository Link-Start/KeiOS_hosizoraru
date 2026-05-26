@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.github.LocalGitHubAppIconBitmaps
import os.kei.ui.page.main.github.query.systemDownloadManagerOption
import os.kei.ui.page.main.github.section.GitHubMainContent
import os.kei.ui.page.main.github.section.GitHubMainContentActions
import os.kei.ui.page.main.github.section.GitHubMainContentControls
import os.kei.ui.page.main.github.section.GitHubMainContentLayout
import os.kei.ui.page.main.github.section.GitHubMainContentOverview
import os.kei.ui.page.main.github.section.GitHubMainContentRevealPhase
import os.kei.ui.page.main.github.section.GitHubMainContentShareImport
import os.kei.ui.page.main.github.section.GitHubMainContentSurfaces
import os.kei.ui.page.main.github.section.GitHubMainContentTracked
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import kotlin.math.abs

@Composable
fun GitHubPage(
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    externalRefreshTriggerToken: Int = 0,
    externalActionsTrackId: String? = null,
    externalActionsSheetToken: Int = 0,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    enableSearchBar: Boolean = true,
    onShowBottomBar: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val openLinkFailureMessage = context.resolveString(R.string.github_error_open_link)
    val systemDmOption = remember(context) { systemDownloadManagerOption(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val isDark = isSystemInDarkTheme()
    val githubPageViewModel: GitHubPageViewModel = applicationViewModel(create = ::GitHubPageViewModel)
    val isListScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val fullBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress &&
            !isListScrolling
    val topBarBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val backdrops =
        rememberMainPageBackdropSet(
            keyPrefix = "github",
            distinctLayers = runtime.hasActivated,
        )
    val topBarColor =
        rememberAppTopBarColor(
            enableBackdropEffects = topBarBackdropEffectsEnabled,
        )

    val state = rememberGitHubPageState(githubPageViewModel)
    var consumedExternalActionsSheetToken by rememberSaveable { mutableIntStateOf(0) }
    val pageUiState by githubPageViewModel.uiState.collectAsStateWithLifecycle()
    val appIconState by githubPageViewModel.appIconState.collectAsStateWithLifecycle()
    val transferState = pageUiState.transferState
    val chromeState = pageUiState.chromeState
    val trackedItemsExpansionState = pageUiState.trackedItemsExpansionState
    val installedOnlineShareTargets = pageUiState.installedOnlineShareTargets
    val checkLogicDownloaderOptions = pageUiState.checkLogicDownloaderOptions
    val contentDerivedState = pageUiState.contentDerivedState
    val appPickerDerivedState = pageUiState.appPickerDerivedState
    val isGitHubPageDataActive = runtime.contentReady && runtime.isDataActive
    LaunchedEffect(isGitHubPageDataActive) {
        githubPageViewModel.setPageDataActive(isGitHubPageDataActive)
    }
    DisposableEffect(githubPageViewModel) {
        onDispose { githubPageViewModel.setPageDataActive(false) }
    }
    LaunchedEffect(isListScrolling) {
        if (!isListScrolling) {
            state.settleScrollChromeVisibility()
        }
    }
    LaunchedEffect(context, state, runtime.hasActivated) {
        if (!runtime.hasActivated) return@LaunchedEffect
        githubPageViewModel.bindContextObservers(state = state)
    }
    SideEffect {
        state.updateScrollBounds(
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward,
        )
    }
    val actions =
        remember(
            context,
            scope,
            state,
            githubPageViewModel,
            githubPageViewModel.repository,
            systemDmOption,
            openLinkFailureMessage,
        ) {
            GitHubPageActions(
                context = context,
                scope = scope,
                state = state,
                viewModel = githubPageViewModel,
                repository = githubPageViewModel.repository,
                systemDmOption = systemDmOption,
                openLinkFailureMessage = openLinkFailureMessage,
            )
        }
    DisposableEffect(actions) {
        onDispose { actions.dispose() }
    }
    val appListPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            scope.launch { actions.reloadApps(forceRefresh = true) }
        }
    val launchAppListPermission: (Intent) -> Unit =
        remember(appListPermissionLauncher) {
            { intent: Intent -> appListPermissionLauncher.launch(intent) }
        }
    val starImportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleGitHubStarImportActivityResult(
                result = result,
                actions = actions,
            )
        }
    val tracksExportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri ->
            handleGitHubTrackExportDestinationResult(
                context = context,
                scope = scope,
                githubPageViewModel = githubPageViewModel,
                uri = uri,
            )
        }
    val tracksImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            handleGitHubTrackImportSourceResult(
                context = context,
                scope = scope,
                state = state,
                actions = actions,
                githubPageViewModel = githubPageViewModel,
                uri = uri,
            )
        }
    val transferCallbacks =
        remember(
            context,
            scope,
            state,
            actions,
            githubPageViewModel,
            tracksExportLauncher,
            tracksImportLauncher,
            starImportLauncher,
        ) {
            buildGitHubPageTrackTransferCallbacks(
                context = context,
                scope = scope,
                state = state,
                actions = actions,
                githubPageViewModel = githubPageViewModel,
                launchTrackedExport = { fileName -> tracksExportLauncher.launch(fileName) },
                launchTrackedImport = { mimeTypes -> tracksImportLauncher.launch(mimeTypes) },
                launchStarImport = { intent -> starImportLauncher.launch(intent) },
            )
        }
    LaunchedEffect(externalRefreshTriggerToken) {
        if (externalRefreshTriggerToken <= 0) return@LaunchedEffect
        actions.refreshAllTracked(showToast = true)
    }
    LaunchedEffect(
        externalActionsSheetToken,
        externalActionsTrackId,
        contentDerivedState.trackedItemIdKey,
        runtime.contentReady,
    ) {
        if (externalActionsSheetToken <= 0 ||
            externalActionsSheetToken == consumedExternalActionsSheetToken ||
            !runtime.contentReady
        ) {
            return@LaunchedEffect
        }
        val trackId = externalActionsTrackId.orEmpty().trim()
        if (trackId.isBlank()) return@LaunchedEffect
        val item = state.trackedItems.firstOrNull { it.id == trackId } ?: return@LaunchedEffect
        consumedExternalActionsSheetToken = externalActionsSheetToken
        state.requestTrackCardFocus(item.id)
        actions.openActionsSheet(item)
    }
    LaunchedEffect(actions, runtime.contentReady) {
        if (!runtime.contentReady) return@LaunchedEffect
        actions.syncActiveShareImportFlowFromStore()
    }
    BindGitHubAppIconPreloadEffect(
        active = runtime.hasActivated,
        trackedPackages = contentDerivedState.trackedIconPreloadPackages,
        installedPackages = contentDerivedState.installedIconPreloadPackages,
        selectedPackageName = state.selectedApp?.packageName.orEmpty(),
        pickerExpanded = state.pickerExpanded,
        appPickerFilteredPackages = appPickerDerivedState.filteredIconPreloadPackages,
        requestAppIcons = { packageNames ->
            githubPageViewModel.requestAppIcons(packageNames)
        },
    )

    BindGitHubPageLifecycleCoordinator(
        context = context,
        listState = listState,
        scrollToTopSignal = runtime.scrollToTopSignal,
        isPageWarmActive = runtime.hasActivated && runtime.isWarmDataActive,
        isPageDataActive = isGitHubPageDataActive,
        state = state,
        actions = actions,
        installedOnlineShareTargets = installedOnlineShareTargets,
        onLaunchAppListPermission = launchAppListPermission,
        onActionBarInteractingChanged = onActionBarInteractingChanged,
    )

    BindGitHubTrackCardFocusCoordinator(
        listState = listState,
        request = state.trackCardFocusRequest,
        sortedTrackIds = contentDerivedState.sortedTrackIds,
        pendingTrackVisible =
            contentDerivedState.showPendingShareImportCard &&
                state.pendingShareImportTrack != null,
        attachCandidateVisible = state.pendingShareImportAttachCandidate != null,
        previewVisible =
            state.pendingShareImportTrack == null &&
                state.pendingShareImportAttachCandidate == null &&
                state.pendingShareImportPreview != null,
        resultVisible =
            state.pendingShareImportPreview == null &&
                state.pendingShareImportTrack == null &&
                state.pendingShareImportAttachCandidate == null &&
                state.pendingShareImportResult != null,
        onConsumed = state::consumeTrackCardFocus,
    )

    val githubGlassRuntime = LocalGlassEffectRuntime.current
    val renderHeavyContent = shouldRenderGitHubHeavyContent(runtime)
    val contentRevealPhase =
        effectiveGitHubPageContentRevealPhase(
            runtime = runtime,
            revealPhase = rememberGitHubPageContentRevealPhase(renderHeavyContent),
        )
    CompositionLocalProvider(
        LocalGlassEffectRuntime provides githubGlassRuntime,
        LocalGitHubAppIconBitmaps provides appIconState.bitmaps,
    ) {
        GitHubMainContent(
            layout =
                GitHubMainContentLayout(
                    contentBottomPadding = runtime.contentBottomPadding,
                    listState = listState,
                    scrollBehavior = scrollBehavior,
                    addButtonScrollConnection = state.addButtonScrollConnection,
                    bottomBarVisible = runtime.bottomBarVisible,
                    floatingDockSide = runtime.floatingDockSide,
                    contentRevealPhase = contentRevealPhase,
                    onShowBottomBar = onShowBottomBar,
                ),
            surfaces =
                GitHubMainContentSurfaces(
                    topBarBackdrop = backdrops.topBar,
                    contentBackdrop = backdrops.content,
                    topBarColor = topBarColor,
                    liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                    isDark = isDark,
                ),
            controls =
                GitHubMainContentControls(
                    searchExpanded = enableSearchBar && chromeState.searchExpanded,
                    trackedSearch = state.trackedSearch,
                    sortMode = state.sortMode,
                    sortDirection = state.sortDirection,
                    trackedFilterMode = state.trackedFilterMode,
                    refreshIntervalHours = state.refreshIntervalHours,
                    showActionMenuPopup = state.showActionMenuPopup,
                    deleteInProgress = state.deleteInProgress,
                    tracksExporting = transferState.tracksExporting,
                    tracksImporting = transferState.tracksImporting,
                ),
            overview =
                GitHubMainContentOverview(
                    refreshState = state.overviewRefreshState,
                    expanded = state.overviewExpanded,
                    refreshProgress = state.refreshProgress,
                    lastRefreshMs = state.lastRefreshMs,
                    lookupConfig = state.lookupConfig,
                    visibleEntries = state.overviewVisibleEntries,
                    metrics = contentDerivedState.trackedUi.overviewMetrics,
                ),
            tracked =
                GitHubMainContentTracked(
                    trackedItems = state.trackedItems,
                    filteredTracked = contentDerivedState.trackedUi.filteredTracked,
                    sortedTracked = contentDerivedState.trackedUi.sortedTracked,
                    appLastUpdatedAtByTrackId = contentDerivedState.appLastUpdatedAtByTrackId,
                    installedAppLabelsByPackage = contentDerivedState.installedAppLabelsByPackage,
                    checkStates = state.checkStates,
                    itemRefreshLoading = state.itemRefreshLoading,
                    apkAssetBundles = state.apkAssetBundles,
                    apkAssetLoading = state.apkAssetLoading,
                    apkAssetErrors = state.apkAssetErrors,
                    apkAssetExpanded = state.apkAssetExpanded,
                    managedInstallLoading = state.managedInstallLoading,
                    actionsRecommendedRunSnapshots = state.actionsRecommendedRunSnapshots,
                    expansionState = trackedItemsExpansionState,
                    relativeTimeNowMillis = contentDerivedState.relativeTimeNowMillis,
                ),
            shareImport =
                GitHubMainContentShareImport(
                    pendingPreview = state.pendingShareImportPreview,
                    pendingTrack = state.pendingShareImportTrack,
                    pendingAttachCandidate = state.pendingShareImportAttachCandidate,
                    pendingResult = state.pendingShareImportResult,
                    showPendingCard = contentDerivedState.showPendingShareImportCard,
                    pendingRepoOverlapCount = contentDerivedState.pendingShareImportRepoOverlapCount,
                    pendingNowMillis = contentDerivedState.pendingShareImportNowMillis,
                ),
            actions =
                GitHubMainContentActions(
                    onTrackedSearchChange = actions::setTrackedSearch,
                    onSearchExpandedChange = { expanded ->
                        githubPageViewModel.updateSearchExpanded(enableSearchBar && expanded)
                    },
                    onShowActionMenuPopupChange = actions::setShowActionMenuPopup,
                    onSortModeChange = actions::setSortMode,
                    onSortDirectionChange = actions::setSortDirection,
                    onTrackedFilterModeChange = actions::setTrackedFilterMode,
                    onRefreshIntervalHoursChange = actions::selectRefreshIntervalHours,
                    onExportTrackedItems = transferCallbacks.onExportTrackedItems,
                    onImportTrackedItems = transferCallbacks.onImportTrackedItems,
                    onOpenStarImport = transferCallbacks.onOpenStarImport,
                    onOpenStrategySheet = actions::openStrategySheet,
                    onOpenCheckLogicSheet = actions::openCheckLogicSheet,
                    onOverviewExpandedChange = actions::setOverviewExpanded,
                    onLocalVersionExpandedChange = actions::setTrackedLocalVersionExpanded,
                    onStableVersionExpandedChange = actions::setTrackedStableVersionExpanded,
                    onPreReleaseVersionExpandedChange = actions::setTrackedPreReleaseVersionExpanded,
                    onOpenOverviewEntrySheet = actions::openOverviewEntrySheet,
                    onRefreshVisibleTracked = {
                        actions.refreshVisibleTracked(
                            items = contentDerivedState.trackedUi.sortedTracked,
                            showToast = true,
                        )
                    },
                    onRetryFailedTracked = { actions.refreshFailedTrackedItems(showToast = true) },
                    onFailedFilterToggle = actions::setFailedFilterEnabled,
                    onRefreshTrackedItem = { actions.refreshTrackedItem(it, showToastOnError = true) },
                    onOpenActionsSheet = actions::openActionsSheet,
                    onOpenTrackSheetForAdd = actions::openTrackSheetForAdd,
                    onOpenTrackSheetForEdit = actions::openTrackSheetForEdit,
                    onRequestDeleteTrackedItem = actions::requestDeleteTrackedItem,
                    onTrackedCardExpandedChange = actions::setTrackedCardExpanded,
                    onCollapseTrackedCard = actions::collapseTrackedCard,
                    onCollapseApkAssetPanel = actions::collapseApkAssetPanel,
                    onLoadApkAssets = { item, itemState, toggleOnlyWhenCached, includeAllAssets, allowLatestReleaseFallback ->
                        actions.loadApkAssets(
                            item = item,
                            itemState = itemState,
                            toggleOnlyWhenCached = toggleOnlyWhenCached,
                            includeAllAssets = includeAllAssets,
                            allowLatestReleaseFallback = allowLatestReleaseFallback,
                        )
                    },
                    onOpenDecisionAssistDetail = actions::openDecisionAssistDetail,
                    onOpenExternalUrl = actions::openExternalUrl,
                    onOpenApkInfo = actions::openApkInfo,
                    onInstallApk = actions::installApkWithKeiOs,
                    onOpenApkInDownloader = actions::openApkInDownloader,
                    onShareApkLink = actions::shareApkLink,
                    onOpenShareImportFlow = actions::openShareImportFlow,
                    onOpenShareImportResult = actions::focusShareImportResult,
                    onCancelActiveShareImportFlow = actions::cancelActiveShareImportFlow,
                    onCancelPendingShareImportTrack = actions::cancelPendingShareImportTrack,
                    onDismissShareImportResult = actions::dismissShareImportResult,
                    onActionBarInteractingChanged = onActionBarInteractingChanged,
                ),
        )
    }

    val apkInfoSheetState by githubPageViewModel.apkInfoSheetState.collectAsStateWithLifecycle()
    val actionsSheetState by githubPageViewModel.actionsSheetState.collectAsStateWithLifecycle()
    val releaseNotesDetailState by githubPageViewModel.releaseNotesDetailState.collectAsStateWithLifecycle()
    val managedInstallConfirmSheetState by githubPageViewModel.managedInstallConfirmSheetState.collectAsStateWithLifecycle()

    CompositionLocalProvider(
        LocalGlassEffectRuntime provides githubGlassRuntime,
        LocalGitHubAppIconBitmaps provides appIconState.bitmaps,
    ) {
        GitHubPageSheetHost(
            context = context,
            backdrops = backdrops,
            state = state,
            actions = actions,
            contentDerivedState = contentDerivedState,
            installedOnlineShareTargets = installedOnlineShareTargets,
            checkLogicDownloaderOptions = checkLogicDownloaderOptions,
            appPickerDerivedState = appPickerDerivedState,
            appPickerPreferences = pageUiState.appPickerPreferences,
            apkInfoSheetState = apkInfoSheetState,
            actionsSheetState = actionsSheetState,
            releaseNotesDetailState = releaseNotesDetailState,
            managedInstallConfirmSheetState = managedInstallConfirmSheetState,
            hasKeiOsSelfTrack = contentDerivedState.hasKeiOsSelfTrack,
            tracksExporting = transferState.tracksExporting,
            tracksImporting = transferState.tracksImporting,
            onEnsureKeiOsSelfTrack = actions::ensureKeiOsSelfTrack,
            onRequestAppPickerState = githubPageViewModel::requestAppPickerState,
            onAppPickerPreferencesChange = githubPageViewModel::saveAppPickerPreferences,
            onRequestApkInfoSheetState = githubPageViewModel::requestApkInfoSheetState,
            onApkInfoSearchQueryChange = githubPageViewModel::updateApkInfoSheetQuery,
            onClearApkInfoSheetState = githubPageViewModel::clearApkInfoSheetState,
            onRequestReleaseNotesDetailState = githubPageViewModel::requestReleaseNotesDetailState,
            onClearReleaseNotesDetailState = githubPageViewModel::clearReleaseNotesDetailState,
            onRequestManagedInstallConfirmSheetState = githubPageViewModel::requestManagedInstallConfirmSheetState,
            onClearManagedInstallConfirmSheetState = githubPageViewModel::clearManagedInstallConfirmSheetState,
            onConfirmTrackImport = transferCallbacks.onConfirmTrackImport,
        )
    }
}

@Composable
private fun BindGitHubTrackCardFocusCoordinator(
    listState: LazyListState,
    request: GitHubTrackCardFocusRequest?,
    sortedTrackIds: List<String>,
    pendingTrackVisible: Boolean,
    attachCandidateVisible: Boolean,
    previewVisible: Boolean,
    resultVisible: Boolean,
    onConsumed: (GitHubTrackCardFocusRequest) -> Unit,
) {
    LaunchedEffect(
        request?.version,
        sortedTrackIds,
        pendingTrackVisible,
        attachCandidateVisible,
        previewVisible,
        resultVisible,
    ) {
        val focusRequest = request ?: return@LaunchedEffect
        val leadingItemCount =
            githubTrackedListLeadingItemCount(
                pendingTrackVisible = pendingTrackVisible,
                attachCandidateVisible = attachCandidateVisible,
                previewVisible = previewVisible,
                resultVisible = resultVisible,
            )
        val targetIndex =
            githubTrackedLazyListIndex(
                targetTrackId = focusRequest.trackId,
                sortedTrackIds = sortedTrackIds,
                leadingItemCount = leadingItemCount,
            ) ?: return@LaunchedEffect
        listState.animateItemToViewportCenter(targetIndex)
        onConsumed(focusRequest)
    }
}

private suspend fun LazyListState.animateItemToViewportCenter(index: Int) {
    animateScrollToItem(index)
    withFrameNanos { }
    val layoutInfo = layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = itemInfo.offset + itemInfo.size / 2
    val deltaPx = itemCenter - viewportCenter
    if (abs(deltaPx) > 2) {
        animateScrollBy(deltaPx.toFloat())
    }
}

internal fun shouldRenderGitHubHeavyContent(runtime: MainPageRuntime): Boolean =
    runtime.contentReady && (!runtime.isPagerScrollInProgress || runtime.isDataActive)

internal fun effectiveGitHubPageContentRevealPhase(
    runtime: MainPageRuntime,
    revealPhase: Int,
): Int =
    if (runtime.isPagerScrollInProgress && runtime.isDataActive) {
        revealPhase.coerceAtMost(GitHubMainContentRevealPhase.TRACKED_PREVIEW)
    } else {
        revealPhase
    }

@Composable
private fun rememberGitHubPageContentRevealPhase(renderHeavyContent: Boolean): Int {
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(renderHeavyContent) {
        if (!renderHeavyContent) {
            phase = 0
            return@LaunchedEffect
        }
        phase = GitHubMainContentRevealPhase.OVERVIEW
        withFrameNanos { }
        phase = GitHubMainContentRevealPhase.OVERVIEW_EXPANDED
        withFrameNanos { }
        phase = GitHubMainContentRevealPhase.SHARE_IMPORT
        withFrameNanos { }
        phase = GitHubMainContentRevealPhase.TRACKED_PREVIEW
        withFrameNanos { }
        phase = GitHubMainContentRevealPhase.TRACKED_ALL
        withFrameNanos { }
        phase = GitHubMainContentRevealPhase.DOCK
    }
    return phase
}
