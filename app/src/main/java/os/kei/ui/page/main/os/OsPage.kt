package os.kei.ui.page.main.os

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showLiquidToastOnly
import os.kei.core.ext.showToast
import os.kei.core.system.RuntimeCommandExecutor
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.os.components.OsPageMainList
import os.kei.ui.page.main.os.components.OsPageOverlayCoordinator
import os.kei.ui.page.main.os.state.createOsPageActionState
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
    val fullBackdropEffectsEnabled = pageBackdropEffectsEnabled
    val osGlassRuntime = LocalGlassEffectRuntime.current
    val uiContext =
        rememberOsPageUiContext(
            enableFullBackdropEffects = fullBackdropEffectsEnabled,
            enableTopBarBackdropEffects = pageBackdropEffectsEnabled,
        )
    val context = uiContext.context
    val density = uiContext.density
    val scope = uiContext.scope
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
    val persistentState = pageUiState.persistentState
    val runtimeState = pageUiState.runtimeState
    val activitySuggestionState = pageUiState.activitySuggestionState
    val queryInput = pageUiState.queryInput
    val queryApplied = pageUiState.queryApplied
    val rowsDerivedState = pageUiState.rowsDerivedState
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
    val activityShortcutCards = osActivityShortcutCardsForUi(persistentState)
    val activityCardExpanded = remember { mutableStateMapOf<String, Boolean>() }
    val overlayState = rememberOsPageOverlayState(textBundle.googleSystemServiceDefaults)
    val scrollBehavior = MiuixScrollBehavior()
    val shellCommandCards = persistentState.shellCommandCards
    val shellCommandCardExpanded = remember { mutableStateMapOf<String, Boolean>() }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var overlaySearchSuppressed by remember { mutableStateOf(false) }
    val surfaceColor = uiContext.surfaceColor
    val backdrops = uiContext.backdrops
    val topBarMaterialBackdrop = uiContext.topBarMaterialBackdrop
    val overlaySheetVisible =
        overlayState.showCardManager ||
            overlayState.showActivityVisibilityManager ||
            overlayState.showShellCardVisibilityManager ||
            overlayState.showShellCommandCardEditor ||
            overlayState.showActivityShortcutEditor ||
            overlayState.showActivitySuggestionSheet ||
            overlayState.pendingCardImportPreview != null
    LaunchedEffect(overlaySheetVisible) {
        if (overlaySheetVisible) {
            overlaySearchSuppressed = true
            searchExpanded = false
        } else {
            delay(360)
            overlaySearchSuppressed = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            onActionBarInteractingChanged(false)
            RuntimeCommandExecutor.closePersistentShell()
        }
    }
    LaunchedEffect(runtime.contentReady, runtime.isDataActive) {
        val active = runtime.contentReady && runtime.isDataActive
        if (!active) {
            RuntimeCommandExecutor.closePersistentShell()
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
            activityCardExpanded = activityCardExpanded,
            shellCommandCardExpanded = shellCommandCardExpanded,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            googleSettingsBuiltInSampleDefaults = textBundle.googleSettingsBuiltInSampleDefaults,
            builtInActivityShortcutCards = textBundle.builtInActivityShortcutCards,
            cardImportFailedWithReason = textBundle.cardImportFailedWithReason,
            exportSuccessText = textBundle.exportSuccessText,
        )
    val overlayTransferActions =
        rememberOsPageOverlayTransferActions(
            context = context,
            osPageViewModel = osPageViewModel,
            overlayState = overlayState,
            cardTransferState = cardTransferState,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
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
        onOperationFailed = { error ->
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
            shellCommandCardExpanded.remove(cardId)
            overlayState.onEditingShellCommandCardIdChange(null)
            overlayState.onShowShellCommandCardEditorChange(false)
            context.showToast(textBundle.shellCardDeletedToast)
        },
        onActivityShortcutCardSaved = {
            context.showToast(R.string.os_google_system_service_toast_saved)
            overlayState.onShowActivityShortcutEditorChange(false)
            overlayState.onShowActivitySuggestionSheetChange(false)
            overlayState.onShowActivityCardDeleteConfirmChange(false)
            overlayState.onEditingActivityShortcutBuiltInChange(false)
        },
        onActivityShortcutCardDeleted = { cardId ->
            activityCardExpanded.remove(cardId)
            overlayState.onEditingActivityShortcutCardIdChange(null)
            overlayState.onShowActivityShortcutEditorChange(false)
            overlayState.onShowActivitySuggestionSheetChange(false)
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
    )
    val sectionStates = runtimeState.sectionStates
    val actionState =
        createOsPageActionState(
            context = context,
            scope = scope,
            shizukuStatus = shizukuStatus,
            shizukuApiUtils = shizukuApiUtils,
            osPageViewModel = osPageViewModel,
            sectionLoadMutex = osPageViewModel.sectionLoadMutex,
            sectionLoadDeferreds = osPageViewModel.sectionLoadDeferreds,
            visibleCardsProvider = { visibleCards },
            sectionStatesProvider = { sectionStates },
            updateSection = osPageViewModel::updateSection,
            onCachePersistedChanged = osPageViewModel::updateCachePersisted,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            shellRunNoOutputText = textBundle.shellRunNoOutputText,
        )

    BindOsCardExpandedStateMaps(
        activityShortcutCards = activityShortcutCards,
        activityCardExpanded = activityCardExpanded,
        initialGoogleSystemServiceExpanded = uiSnapshot.googleSystemServiceExpanded,
        shellCommandCards = shellCommandCards,
        shellCommandCardExpanded = shellCommandCardExpanded,
    )

    BindOsScrollToTopEffect(
        scrollToTopSignal = runtime.scrollToTopSignal,
        listState = listState,
    )

    BindOsInitialCacheLoad(
        ready = persistentState.loaded,
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
        showActivitySuggestionSheet = overlayState.showActivitySuggestionSheet,
        googleSystemServiceSuggestionTarget = overlayState.googleSystemServiceSuggestionTarget,
        activityShortcutDraftPackageName = overlayState.activityShortcutDraft.packageName,
        context = context,
        requestActivitySuggestions = osPageViewModel::requestActivitySuggestions,
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
            overlayState,
            actionState,
            routeState,
            shizukuStatus,
            activityCardExpanded,
            shellCommandCardExpanded,
            osPageViewModel,
        ) {
            createOsPageMainListActions(
                context = context,
                textBundle = textBundle,
                overlayState = overlayState,
                actionState = actionState,
                routeState = routeState,
                shizukuStatus = shizukuStatus,
                activityCardExpanded = activityCardExpanded,
                shellCommandCardExpanded = shellCommandCardExpanded,
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
                scope = scope,
                sheetBackdrop = backdrops.sheet,
                overlayState = overlayState,
                visibleCards = visibleCards,
                activityShortcutCards = activityShortcutCards,
                shellCommandCards = shellCommandCards,
                activitySuggestionState = activitySuggestionState,
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
                shellCommandCards = derivedState.visibleShellCommandCards,
                shellCommandCardExpanded = shellCommandCardExpanded,
                runningShellCommandCardIds = routeState.runningShellCommandCardIds,
                onShellCommandCardExpandedChange = mainListActions.onShellCommandCardExpandedChange,
                onOpenShellCommandCardEditor = mainListActions.onOpenShellCommandCardEditor,
                onRunShellCommandCard = mainListActions.onRunShellCommandCard,
                activityShortcutCards = derivedState.visibleActivityShortcutCards,
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
                    !overlayState.showActivitySuggestionSheet &&
                        !overlayState.showShellCardVisibilityManager,
                onOpenAddActivityShortcutCard = mainListActions.onOpenAddActivityShortcutCard,
                bottomBarVisible = runtime.bottomBarVisible,
                searchExpanded = enableSearchBar && searchExpanded && !overlaySearchSuppressed,
                queryInput = queryInput,
                onQueryInputChange = { value ->
                    if (!overlaySearchSuppressed) {
                        osPageViewModel.updateQueryInput(value)
                    }
                },
                onSearchExpandedChange = { expanded ->
                    searchExpanded = enableSearchBar && expanded && !overlaySearchSuppressed
                },
                searchLabel = textBundle.searchLabel,
                floatingDockSide = runtime.floatingDockSide,
            )
        }
    }
}
