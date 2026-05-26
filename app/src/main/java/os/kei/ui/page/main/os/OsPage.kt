package os.kei.ui.page.main.os

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.os.components.OsPageMainList
import os.kei.ui.page.main.os.components.OsPageMainListRevealPhase
import os.kei.ui.page.main.os.components.OsPageOverlayCoordinator
import os.kei.ui.page.main.os.shortcut.launchGoogleSystemServiceActivity
import os.kei.ui.page.main.os.state.createOsPageActionState
import os.kei.ui.page.main.os.state.rememberOsPageCardTransferEventActions
import os.kei.ui.page.main.os.state.rememberOsPageCardTransferState
import os.kei.ui.page.main.os.state.rememberOsPageOverlayState
import os.kei.ui.page.main.os.state.rememberOsPageOverlayTransferActions
import os.kei.ui.page.main.os.state.rememberOsPageUiContext
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
@Suppress("FunctionName")
fun OsPage(
    runtime: MainPageRuntime,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    enableSearchBar: Boolean = true,
    onShowBottomBar: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val pageBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val osGlassRuntime = LocalGlassEffectRuntime.current
    val uiContext =
        rememberOsPageUiContext(
            enableFullBackdropEffects = runtime.hasActivated,
            enableTopBarBackdropEffects = pageBackdropEffectsEnabled,
        )
    val context = uiContext.context
    val density = uiContext.density
    val textBundle = uiContext.textBundle
    val isDark = uiContext.isDark
    val inactive = uiContext.inactiveColor
    val titleColor = uiContext.titleColor
    val cachedColor = uiContext.cachedColor
    val refreshingColor = uiContext.refreshingColor
    val syncedColor = uiContext.syncedColor
    val shizukuReady = shizukuStatus.contains("granted", ignoreCase = true)
    val lifecycleOwner = LocalLifecycleOwner.current
    val osPageViewModel: OsPageViewModel = viewModel()
    LaunchedEffect(
        runtime.hasActivated,
        textBundle.googleSystemServiceDefaults,
        textBundle.builtInActivityShortcutCards,
        textBundle.builtInShellCommandCards,
    ) {
        if (!runtime.hasActivated) return@LaunchedEffect
        osPageViewModel.loadPersistentState(
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            builtInActivityShortcutCards = textBundle.builtInActivityShortcutCards,
            builtInShellCommandCards = textBundle.builtInShellCommandCards,
        )
    }
    val pageUiState by osPageViewModel.uiState.collectAsStateWithLifecycle()
    val activityIconState by osPageViewModel.activityIconState.collectAsStateWithLifecycle()
    val cardExpansionState by osPageViewModel.cardExpansionState.collectAsStateWithLifecycle()
    val persistentState = pageUiState.persistentState
    val runtimeState = pageUiState.runtimeState
    val activitySuggestionState = pageUiState.activitySuggestionState
    val activitySuggestionChromeState = pageUiState.activitySuggestionChromeState
    val chromeState = pageUiState.chromeState
    val queryInput = pageUiState.queryInput
    val queryApplied = pageUiState.queryApplied
    val rowsDerivedState = pageUiState.rowsDerivedState
    val cardListDerivedState = pageUiState.cardListDerivedState
    val uiSnapshot = persistentState.uiSnapshot
    val topInfoExpanded = uiSnapshot.topInfoExpanded
    val shellRunnerExpanded = uiSnapshot.shellRunnerExpanded
    val systemTableExpanded = uiSnapshot.systemTableExpanded
    val secureTableExpanded = uiSnapshot.secureTableExpanded
    val globalTableExpanded = uiSnapshot.globalTableExpanded
    val androidPropsExpanded = uiSnapshot.androidPropsExpanded
    val javaPropsExpanded = uiSnapshot.javaPropsExpanded
    val linuxEnvExpanded = uiSnapshot.linuxEnvExpanded
    val visibleCards = uiSnapshot.visibleCards
    val activityShortcutCards = cardListDerivedState.activityShortcutCards
    val overlayRuntimeActions = osPageViewModel.overlayRuntimeActions
    val overlayState =
        rememberOsPageOverlayState(
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            showCardManager = runtimeState.showCardManager,
            onShowCardManagerChange = overlayRuntimeActions::updateShowCardManager,
            showActivityVisibilityManager = runtimeState.showActivityVisibilityManager,
            onShowActivityVisibilityManagerChange = overlayRuntimeActions::updateShowActivityVisibilityManager,
            activityVisibilityQuery = runtimeState.activityVisibilityQuery,
            onActivityVisibilityQueryChange = overlayRuntimeActions::updateActivityVisibilityQuery,
            showShellCardVisibilityManager = runtimeState.showShellCardVisibilityManager,
            onShowShellCardVisibilityManagerChange = overlayRuntimeActions::updateShowShellCardVisibilityManager,
            shellCardVisibilityQuery = runtimeState.shellCardVisibilityQuery,
            onShellCardVisibilityQueryChange = overlayRuntimeActions::updateShellCardVisibilityQuery,
            showActivityShortcutEditor = runtimeState.showActivityShortcutEditor,
            onShowActivityShortcutEditorChange = overlayRuntimeActions::updateShowActivityShortcutEditor,
            activityCardEditMode = runtimeState.activityCardEditMode,
            onActivityCardEditModeChange = overlayRuntimeActions::updateActivityCardEditMode,
            editingActivityShortcutCardId = runtimeState.editingActivityShortcutCardId,
            onEditingActivityShortcutCardIdChange = overlayRuntimeActions::updateEditingActivityShortcutCardId,
            editingActivityShortcutBuiltIn = runtimeState.editingActivityShortcutBuiltIn,
            onEditingActivityShortcutBuiltInChange = overlayRuntimeActions::updateEditingActivityShortcutBuiltIn,
            showShellCommandCardEditor = runtimeState.showShellCommandCardEditor,
            onShowShellCommandCardEditorChange = overlayRuntimeActions::updateShowShellCommandCardEditor,
            editingShellCommandCardId = runtimeState.editingShellCommandCardId,
            onEditingShellCommandCardIdChange = overlayRuntimeActions::updateEditingShellCommandCardId,
            showShellCardDeleteConfirm = runtimeState.showShellCardDeleteConfirm,
            onShowShellCardDeleteConfirmChange = overlayRuntimeActions::updateShowShellCardDeleteConfirm,
            showActivityCardDeleteConfirm = runtimeState.showActivityCardDeleteConfirm,
            onShowActivityCardDeleteConfirmChange = overlayRuntimeActions::updateShowActivityCardDeleteConfirm,
            pendingExportContent = runtimeState.pendingExportContent,
            onPendingExportContentChange = overlayRuntimeActions::updatePendingExportContent,
            pendingImportTarget = runtimeState.pendingImportTarget,
            onPendingImportTargetChange = overlayRuntimeActions::updatePendingImportTarget,
            pendingCardImportPreview = runtimeState.pendingCardImportPreview,
            onPendingCardImportPreviewChange = overlayRuntimeActions::updatePendingCardImportPreview,
            cardTransferInProgress = runtimeState.cardTransferInProgress,
            onCardTransferInProgressChange = overlayRuntimeActions::updateCardTransferInProgress,
        )
    val scrollBehavior = MiuixScrollBehavior()
    val contentRevealPhase = OsPageMainListRevealPhase.DOCK
    val shellCommandCards = cardListDerivedState.shellCommandCards
    val activityCardExpanded = cardExpansionState.activityCards
    val shellCommandCardExpanded = cardExpansionState.shellCommandCards
    val surfaceColor = uiContext.surfaceColor
    val backdrops = uiContext.backdrops
    val topBarMaterialBackdrop = uiContext.topBarMaterialBackdrop
    val overlaySheetVisible =
        overlayState.showCardManager ||
            overlayState.showActivityVisibilityManager ||
            overlayState.showShellCardVisibilityManager ||
            overlayState.showShellCommandCardEditor ||
            overlayState.showActivityShortcutEditor ||
            activitySuggestionChromeState.showSheet ||
            overlayState.pendingCardImportPreview != null
    LaunchedEffect(overlaySheetVisible) {
        osPageViewModel.updateOverlaySheetVisible(overlaySheetVisible)
    }
    DisposableEffect(Unit) {
        onDispose {
            onActionBarInteractingChanged(false)
            osPageViewModel.closePersistentShell()
        }
    }
    LaunchedEffect(runtime.contentReady, runtime.isDataActive) {
        val active = runtime.contentReady && runtime.isDataActive
        if (!active) {
            osPageViewModel.closePersistentShell()
        }
    }
    BindOsShellCardReloadOnResume(
        lifecycleOwner = lifecycleOwner,
        reloadCards = {
            osPageViewModel.reloadShellCommandCards(
                builtInShellCommandCards = textBundle.builtInShellCommandCards,
            )
        },
    )
    val cardTransferState =
        rememberOsPageCardTransferState(
            context = context,
            osPageViewModel = osPageViewModel,
            overlayState = overlayState,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            googleSettingsBuiltInSampleDefaults = textBundle.googleSettingsBuiltInSampleDefaults,
            builtInActivityShortcutCards = textBundle.builtInActivityShortcutCards,
        )
    val overlayTransferActions =
        rememberOsPageOverlayTransferActions(
            context = context,
            osPageViewModel = osPageViewModel,
            overlayState = overlayState,
            cardTransferState = cardTransferState,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
        )
    val cardTransferEventActions =
        rememberOsPageCardTransferEventActions(
            context = context,
            overlayState = overlayState,
            retainActivityCardExpansion = osPageViewModel::retainActivityCardExpansion,
            retainShellCommandCardExpansion = osPageViewModel::retainShellCommandCardExpansion,
            textBundle = textBundle,
        )
    BindOsPageEvents(
        events = osPageViewModel.events,
        onLaunchExportDocument = { fileName, content ->
            overlayState.onPendingExportContentChange(content)
            overlayState.onCardTransferInProgressChange(false)
            cardTransferState.exportLauncher.launch(fileName)
        },
        onExportFailed = { error ->
            overlayState.onCardTransferInProgressChange(false)
            context.showToast(
                context.getString(
                    R.string.common_export_failed_with_reason,
                    error.message ?: error.javaClass.simpleName,
                ),
            )
        },
        onCardExportWritten = cardTransferEventActions.onExportWritten,
        onCardExportWriteFailed = cardTransferEventActions.onExportWriteFailed,
        onCardImportPreviewReady = cardTransferEventActions.onImportPreviewReady,
        onCardImportFailed = cardTransferEventActions.onImportFailed,
        onCardTransferCompleted = cardTransferEventActions.onTransferCompleted,
        onActivityCardsImported = cardTransferEventActions.onActivityCardsImported,
        onShellCardsImported = cardTransferEventActions.onShellCardsImported,
        onOperationFailed = { error ->
            overlayState.onCardTransferInProgressChange(false)
            context.showToast(
                context.getString(
                    R.string.common_export_failed_with_reason,
                    error.message ?: error.javaClass.simpleName,
                ),
            )
        },
        onShellCommandCardSaved = {
            context.showToast(textBundle.shellCardSavedToast)
            overlayState.onShowShellCommandCardEditorChange(false)
            overlayState.onShowShellCardDeleteConfirmChange(false)
        },
        onShellCommandCardSaveFailed = {
            context.showToast(textBundle.shellCardCommandRequiredToast)
        },
        onShellCommandCardDeleted = { cardId ->
            osPageViewModel.removeShellCommandCardExpansion(cardId)
            overlayState.onEditingShellCommandCardIdChange(null)
            overlayState.onShowShellCommandCardEditorChange(false)
            context.showToast(textBundle.shellCardDeletedToast)
        },
        onActivityShortcutCardSaved = {
            context.showToast(R.string.os_google_system_service_toast_saved)
            overlayState.onShowActivityShortcutEditorChange(false)
            osPageViewModel.dismissActivitySuggestionSheet()
            overlayState.onShowActivityCardDeleteConfirmChange(false)
            overlayState.onEditingActivityShortcutBuiltInChange(false)
        },
        onActivityShortcutCardDeleted = { cardId ->
            osPageViewModel.removeActivityCardExpansion(cardId)
            overlayState.onEditingActivityShortcutCardIdChange(null)
            overlayState.onShowActivityShortcutEditorChange(false)
            osPageViewModel.dismissActivitySuggestionSheet()
            overlayState.onEditingActivityShortcutBuiltInChange(false)
            context.showToast(textBundle.activityCardDeletedToast)
        },
        onShellCommandCardCommandRequired = {
            context.showToast(textBundle.shellCardCommandRequiredToast)
        },
        onShellCommandCardNoPermission = {
            context.showToast(textBundle.shellRunNoPermissionText)
        },
        onShellCommandCardRunCompleted = {
            context.showLiquidToastOnly(textBundle.shellCardRunCompletedToast)
        },
        onShellCommandCardRunFailed = { error ->
            context.showToast(
                context.getString(
                    R.string.os_shell_card_toast_run_failed,
                    error.javaClass.simpleName,
                ),
            )
        },
        onRefreshCompleted = { refreshed ->
            context.showToast(
                if (refreshed) {
                    textBundle.refreshCompletedText
                } else {
                    textBundle.noRefreshableCardText
                },
            )
        },
        onLaunchActivityShortcut = { config ->
            runCatching {
                launchGoogleSystemServiceActivity(
                    context = context,
                    config = config,
                    defaults = textBundle.googleSystemServiceDefaults,
                )
            }.onFailure { error ->
                context.showToast(
                    context.getString(
                        R.string.os_google_system_service_toast_open_failed,
                        error.javaClass.simpleName,
                    ),
                )
            }
        },
        onActivityShortcutInvalidTarget = {
            context.showToast(R.string.os_google_system_service_toast_invalid_target)
        },
        onShowActivityShortcutEditor = { request ->
            overlayState.onActivityCardEditModeChange(request.editMode)
            overlayState.onEditingActivityShortcutCardIdChange(request.editingCardId)
            overlayState.onEditingActivityShortcutBuiltInChange(request.editingBuiltIn)
            overlayState.onActivityShortcutDraftChange(request.draft)
            overlayState.onShowActivityShortcutEditorChange(true)
        },
        onShowShellCommandCardEditor = { card ->
            overlayState.onEditingShellCommandCardIdChange(card.id)
            overlayState.onShellCommandCardDraftChange(card)
            overlayState.onShowShellCommandCardEditorChange(true)
        },
    )
    val sectionStates = runtimeState.sectionStates
    val actionState =
        createOsPageActionState(
            context = context,
            shizukuStatus = shizukuStatus,
            shizukuApiUtils = shizukuApiUtils,
            osPageViewModel = osPageViewModel,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            shellRunNoOutputText = textBundle.shellRunNoOutputText,
        )

    BindOsCardExpandedStateMaps(
        activityShortcutCards = activityShortcutCards,
        initialGoogleSystemServiceExpanded = uiSnapshot.googleSystemServiceExpanded,
        shellCommandCards = shellCommandCards,
        syncActivityCardExpansion = osPageViewModel::syncActivityCardExpansion,
        syncShellCommandCardExpansion = osPageViewModel::syncShellCommandCardExpansion,
    )

    BindOsScrollToTopEffect(
        scrollToTopSignal = runtime.scrollToTopSignal,
        listState = listState,
    )

    BindOsInitialCacheLoad(
        ready = persistentState.loaded,
        cacheLoaded = runtimeState.cacheLoaded,
        isPageActive = runtime.contentReady && runtime.isDataActive,
        hydrateInitialCache = { isPageActive ->
            osPageViewModel.hydrateInitialCache(
                isPageActive = isPageActive,
                ensureLoad = actionState.ensureLoad,
            )
        },
    )

    BindOsShizukuInvalidation(
        shizukuReady = shizukuReady,
        onInvalidate = osPageViewModel::invalidateShizukuSections,
    )

    BindOsExpandedStatePersistence(
        ready = runtimeState.uiStatePersistenceReady,
        snapshotProvider = {
            uiSnapshot.copy(visibleCards = visibleCards)
        },
        persistSnapshot = osPageViewModel::saveExpandedStateSnapshot,
    )

    BindOsVisibleSectionLoadEffects(
        cacheLoaded = runtimeState.cacheLoaded,
        initialVisibleRefreshComplete = runtimeState.initialVisibleRefreshComplete,
        isDataActive = runtime.contentReady && runtime.isDataActive,
        visibleCards = visibleCards,
        systemTableExpanded = systemTableExpanded,
        secureTableExpanded = secureTableExpanded,
        globalTableExpanded = globalTableExpanded,
        androidPropsExpanded = androidPropsExpanded,
        javaPropsExpanded = javaPropsExpanded,
        linuxEnvExpanded = linuxEnvExpanded,
        ensureLoad = { section -> actionState.ensureLoad(section, false) },
    )
    BindOsActivitySuggestionLoadEffect(
        showActivitySuggestionSheet = activitySuggestionChromeState.showSheet,
        googleSystemServiceSuggestionTarget = activitySuggestionChromeState.target,
        activityShortcutDraftPackageName = overlayState.activityShortcutDraft.packageName,
        context = context,
        requestActivitySuggestions = osPageViewModel::requestActivitySuggestions,
    )
    BindOsActivityShortcutIconPreloadEffect(
        active = runtime.hasActivated,
        activityShortcutCards = activityShortcutCards,
        showActivitySuggestionSheet = activitySuggestionChromeState.showSheet,
        googleSystemServiceSuggestionTarget = activitySuggestionChromeState.target,
        activityShortcutDraftPackageName = overlayState.activityShortcutDraft.packageName,
        packageSuggestions = activitySuggestionState.packageSuggestions,
        classSuggestions = activitySuggestionState.classSuggestions,
        context = context,
        requestActivityShortcutIcons = osPageViewModel::requestActivityShortcutIcons,
        requestPackageIcons = osPageViewModel::requestPackageIcons,
    )

    val routeState =
        rememberOsPageRouteState(
            queryApplied = queryApplied,
            uiSnapshot = uiSnapshot,
            visibleCards = visibleCards,
            activityShortcutCards = activityShortcutCards,
            shellCommandCards = shellCommandCards,
            sectionStates = sectionStates,
            refreshing = runtimeState.refreshing,
            refreshProgress = runtimeState.refreshProgress,
            cachePersisted = runtimeState.cachePersisted,
            runningShellCommandCardIds = runtimeState.runningShellCommandCardIds,
        )

    val derivedState =
        rememberOsPageDerivedState(
            context = context,
            rowsDerivedState = rowsDerivedState,
            shizukuStatus = shizukuStatus,
            shellSavedCountLabel = textBundle.shellSavedCountLabel,
            shellCommandCards = routeState.shellCommandCards,
            sectionStates = routeState.sectionStates,
            isDark = isDark,
            inactiveColor = inactive,
            cachedColor = cachedColor,
            refreshingColor = refreshingColor,
            syncedColor = syncedColor,
            surfaceColor = surfaceColor,
            refreshing = routeState.refreshing,
            refreshProgress = routeState.refreshProgress,
            cachePersisted = routeState.cachePersisted,
            visibleCards = routeState.visibleCards,
            activityShortcutCards = routeState.activityShortcutCards,
        )

    val overviewState = derivedState.overviewUiState.overviewState
    val statusLabel = derivedState.overviewUiState.statusLabel
    val statusColor = derivedState.overviewUiState.statusColor
    val overviewCardColor = derivedState.overviewUiState.overviewCardColor
    val overviewBorderColor = derivedState.overviewUiState.overviewBorderColor
    val indicatorProgress = derivedState.overviewUiState.indicatorProgress
    val indicatorBg = derivedState.overviewUiState.indicatorBg
    val overviewMetricRows = derivedState.overviewMetricRows
    val mainListActions =
        remember(
            context,
            textBundle,
            actionState,
            routeState,
            shizukuStatus,
            osPageViewModel,
        ) {
            createOsPageMainListActions(
                context = context,
                textBundle = textBundle,
                actionState = actionState,
                routeState = routeState,
                shizukuStatus = shizukuStatus,
                osPageViewModel = osPageViewModel,
            )
        }

    CompositionLocalProvider(LocalGlassEffectRuntime provides osGlassRuntime) {
        OsPageScaffoldShell(
            scrollBehavior = scrollBehavior,
            topBarColor = topBarMaterialBackdrop,
            topBarBackdrop = backdrops.topBar,
            layeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
            manageCardsContentDescription = textBundle.manageCardsContentDescription,
            manageActivitiesContentDescription = textBundle.manageActivitiesContentDescription,
            manageShellCardsContentDescription = textBundle.manageShellCardsContentDescription,
            refreshParamsContentDescription = textBundle.refreshParamsContentDescription,
            refreshing = runtimeState.refreshing,
            onOpenCardManager = { overlayState.onShowCardManagerChange(true) },
            onOpenActivityVisibilityManager = { overlayState.onShowActivityVisibilityManagerChange(true) },
            onOpenShellCardVisibilityManager = { overlayState.onShowShellCardVisibilityManagerChange(true) },
            onRefresh = actionState.refreshAllSections,
            onTitleClick = onShowBottomBar,
            onActionBarInteractingChanged = onActionBarInteractingChanged,
        ) { innerPadding ->
            OsPageOverlayCoordinator(
                context = context,
                sheetBackdrop = backdrops.sheet,
                overlayState = overlayState,
                visibleCards = visibleCards,
                activityShortcutCards = activityShortcutCards,
                activityIconBitmaps = activityIconState.bitmaps,
                packageIconBitmaps = activityIconState.packageBitmaps,
                shellCommandCards = shellCommandCards,
                activitySuggestionState = activitySuggestionState,
                activitySuggestionChromeState = activitySuggestionChromeState,
                actionState = actionState,
                overlayTransferActions = overlayTransferActions,
                cardTransferState = cardTransferState,
                textBundle = textBundle,
                osPageViewModel = osPageViewModel,
            )
            OsPageMainList(
                    context = context,
                    listState = listState,
                    innerPadding = innerPadding,
                    scrollBehaviorConnection = scrollBehavior.nestedScrollConnection,
                    contentBackdrop = backdrops.content,
                    isDark = isDark,
                    titleColor = titleColor,
                    refreshing = runtimeState.refreshing,
                    overviewState = overviewState,
                    indicatorProgress = indicatorProgress,
                    statusColor = statusColor,
                    indicatorBg = indicatorBg,
                    statusLabel = statusLabel,
                    overviewCardColor = overviewCardColor,
                    overviewBorderColor = overviewBorderColor,
                    overviewMetricRows = overviewMetricRows,
                    noMatchedResultsText = textBundle.noMatchedResultsText,
                    query = derivedState.query,
                    displayedTopInfoRows = derivedState.displayedTopInfoRows,
                    groupedTopInfoRows = derivedState.groupedTopInfoRows,
                    topInfoExpanded = topInfoExpanded,
                    onTopInfoExpandedChange = osPageViewModel::updateTopInfoExpanded,
                    shellRunnerRows = derivedState.shellRunnerRows,
                    shellRunnerExpanded = shellRunnerExpanded,
                    onShellRunnerExpandedChange = osPageViewModel::updateShellRunnerExpanded,
                    onOpenShellRunner = mainListActions.onOpenShellRunner,
                    shellCommandCards = cardListDerivedState.visibleShellCommandCards,
                    shellCommandCardExpanded = shellCommandCardExpanded,
                    runningShellCommandCardIds = routeState.runningShellCommandCardIds,
                    onShellCommandCardExpandedChange = mainListActions.onShellCommandCardExpandedChange,
                    onOpenShellCommandCardEditor = mainListActions.onOpenShellCommandCardEditor,
                    onRunShellCommandCard = mainListActions.onRunShellCommandCard,
                    activityShortcutCards = cardListDerivedState.visibleActivityShortcutCards,
                    activityIconBitmaps = activityIconState.bitmaps,
                    defaultActivityCardTitle = textBundle.googleSystemServiceDefaultTitle,
                    activityCardExpanded = activityCardExpanded,
                    onActivityCardExpandedChange = mainListActions.onActivityCardExpandedChange,
                    onOpenActivityShortcutCard = mainListActions.onOpenActivityShortcutCard,
                    onOpenActivityShortcutCardEditor = mainListActions.onOpenActivityShortcutCardEditor,
                    displayedSystemRows = derivedState.displayedSystemRows,
                    displayedSecureRows = derivedState.displayedSecureRows,
                    displayedGlobalRows = derivedState.displayedGlobalRows,
                    displayedAndroidRows = derivedState.displayedAndroidRows,
                    displayedJavaRows = derivedState.displayedJavaRows,
                    displayedLinuxRows = derivedState.displayedLinuxRows,
                    prunedSystemRows = derivedState.prunedSystemRows,
                    prunedSecureRows = derivedState.prunedSecureRows,
                    prunedGlobalRows = derivedState.prunedGlobalRows,
                    prunedAndroidRows = derivedState.prunedAndroidRows,
                    prunedJavaRows = derivedState.prunedJavaRows,
                    prunedLinuxRows = derivedState.prunedLinuxRows,
                    systemTableExpanded = systemTableExpanded,
                    onSystemTableExpandedChange = osPageViewModel::updateSystemTableExpanded,
                    secureTableExpanded = secureTableExpanded,
                    onSecureTableExpandedChange = osPageViewModel::updateSecureTableExpanded,
                    globalTableExpanded = globalTableExpanded,
                    onGlobalTableExpandedChange = osPageViewModel::updateGlobalTableExpanded,
                    androidPropsExpanded = androidPropsExpanded,
                    onAndroidPropsExpandedChange = osPageViewModel::updateAndroidPropsExpanded,
                    javaPropsExpanded = javaPropsExpanded,
                    onJavaPropsExpandedChange = osPageViewModel::updateJavaPropsExpanded,
                    linuxEnvExpanded = linuxEnvExpanded,
                    onLinuxEnvExpandedChange = osPageViewModel::updateLinuxEnvExpanded,
                    isCardVisible = mainListActions.isCardVisible,
                    sectionSubtitle = mainListActions.sectionSubtitle,
                    exportingCard = runtimeState.exportingCard,
                    onExportCard = mainListActions.onExportCard,
                    onRefreshAll = mainListActions.onRefreshAll,
                    contentBottomPadding = runtime.contentBottomPadding,
                    showFloatingAddButton =
                        !activitySuggestionChromeState.showSheet &&
                            !overlayState.showShellCardVisibilityManager,
                    onOpenAddActivityShortcutCard = mainListActions.onOpenAddActivityShortcutCard,
                    bottomBarVisible = runtime.bottomBarVisible,
                    searchExpanded = enableSearchBar && chromeState.searchExpanded && !chromeState.overlaySearchSuppressed,
                    queryInput = queryInput,
                    onQueryInputChange = { value ->
                        if (!chromeState.overlaySearchSuppressed) {
                            osPageViewModel.updateQueryInput(value)
                        }
                    },
                    onSearchExpandedChange = { expanded ->
                        osPageViewModel.updateSearchExpanded(enableSearchBar && expanded && !chromeState.overlaySearchSuppressed)
                    },
                    searchLabel = textBundle.searchLabel,
                    floatingDockSide = runtime.floatingDockSide,
                    contentRevealPhase = contentRevealPhase,
            )
        }
    }
}
