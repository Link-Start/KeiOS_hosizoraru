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
import kotlinx.coroutines.launch
import os.kei.core.system.RuntimeCommandExecutor
import os.kei.core.system.ShizukuApiUtils
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
    ) {
        if (!runtime.hasActivated) return@LaunchedEffect
        osPageViewModel.loadPersistentState(
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            builtInActivityShortcutCards = textBundle.builtInActivityShortcutCards,
        )
    }
    val persistentState by osPageViewModel.persistentState.collectAsStateWithLifecycle()
    val runtimeState by osPageViewModel.runtimeState.collectAsStateWithLifecycle()
    val queryInput by osPageViewModel.queryInput.collectAsStateWithLifecycle()
    val queryApplied by osPageViewModel.queryApplied.collectAsStateWithLifecycle()
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
    val surfaceColor = uiContext.surfaceColor
    val backdrops = uiContext.backdrops
    val topBarMaterialBackdrop = uiContext.topBarMaterialBackdrop
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
        reloadCards = osPageViewModel::reloadShellCommandCards,
    )
    val cardTransferState =
        rememberOsPageCardTransferState(
            context = context,
            scope = scope,
            overlayState = overlayState,
            activityShortcutCards = activityShortcutCards,
            onActivityShortcutCardsChange = osPageViewModel::updateActivityShortcutCards,
            activityCardExpanded = activityCardExpanded,
            shellCommandCards = shellCommandCards,
            onShellCommandCardsChange = osPageViewModel::updateShellCommandCards,
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
            overlayState = overlayState,
            cardTransferState = cardTransferState,
            activityShortcutCards = activityShortcutCards,
            shellCommandCards = shellCommandCards,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
        )
    val sectionStates = runtimeState.sectionStates
    val actionState =
        createOsPageActionState(
            context = context,
            scope = scope,
            shizukuStatus = shizukuStatus,
            shizukuApiUtils = shizukuApiUtils,
            sectionLoadMutex = osPageViewModel.sectionLoadMutex,
            sectionLoadDeferreds = osPageViewModel.sectionLoadDeferreds,
            visibleCardsProvider = { visibleCards },
            sectionStatesProvider = { sectionStates },
            updateSection = osPageViewModel::updateSection,
            onCachePersistedChanged = osPageViewModel::updateCachePersisted,
            updateVisibleCards = osPageViewModel::updateVisibleCards,
            setTopInfoExpanded = osPageViewModel::updateTopInfoExpanded,
            setShellRunnerExpanded = osPageViewModel::updateShellRunnerExpanded,
            setSystemTableExpanded = osPageViewModel::updateSystemTableExpanded,
            setSecureTableExpanded = osPageViewModel::updateSecureTableExpanded,
            setGlobalTableExpanded = osPageViewModel::updateGlobalTableExpanded,
            setAndroidPropsExpanded = osPageViewModel::updateAndroidPropsExpanded,
            setJavaPropsExpanded = osPageViewModel::updateJavaPropsExpanded,
            setLinuxEnvExpanded = osPageViewModel::updateLinuxEnvExpanded,
            activityShortcutCardsProvider = { activityShortcutCards },
            updateActivityShortcutCards = osPageViewModel::updateActivityShortcutCards,
            googleSystemServiceDefaults = textBundle.googleSystemServiceDefaults,
            updateShellCommandCards = osPageViewModel::updateShellCommandCards,
            runningShellCommandCardIdsProvider = { runtimeState.runningShellCommandCardIds },
            onRunningShellCommandCardIdsChange = osPageViewModel::updateRunningShellCommandCardIds,
            onRefreshingChange = osPageViewModel::updateRefreshing,
            onRefreshProgressChange = osPageViewModel::updateRefreshProgress,
            shellCardCommandRequiredToast = textBundle.shellCardCommandRequiredToast,
            shellRunNoPermissionText = textBundle.shellRunNoPermissionText,
            shellRunNoOutputText = textBundle.shellRunNoOutputText,
            noRefreshableCardText = textBundle.noRefreshableCardText,
            refreshCompletedText = textBundle.refreshCompletedText,
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
        onPackageSuggestionsLoadingChange = overlayState.onGoogleSystemServicePackageSuggestionsLoadingChange,
        onPackageSuggestionsChange = overlayState.onGoogleSystemServicePackageSuggestionsChange,
        onClassSuggestionsLoadingChange = overlayState.onGoogleSystemServiceClassSuggestionsLoadingChange,
        onClassSuggestionsChange = overlayState.onGoogleSystemServiceClassSuggestionsChange,
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
            queryApplied = routeState.queryApplied,
            shizukuStatus = shizukuStatus,
            shellSavedCountLabel = textBundle.shellSavedCountLabel,
            shellCommandCards = routeState.shellCommandCards,
            sectionStates = routeState.sectionStates,
            topInfoExpanded = topInfoExpanded,
            systemTableExpanded = systemTableExpanded,
            secureTableExpanded = secureTableExpanded,
            globalTableExpanded = globalTableExpanded,
            androidPropsExpanded = androidPropsExpanded,
            javaPropsExpanded = javaPropsExpanded,
            linuxEnvExpanded = linuxEnvExpanded,
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
            scope,
            textBundle,
            overlayState,
            actionState,
            routeState,
            shizukuStatus,
            activityCardExpanded,
            shellCommandCardExpanded,
            cardTransferState,
        ) {
            createOsPageMainListActions(
                context = context,
                scope = scope,
                textBundle = textBundle,
                overlayState = overlayState,
                actionState = actionState,
                routeState = routeState,
                shizukuStatus = shizukuStatus,
                activityCardExpanded = activityCardExpanded,
                shellCommandCardExpanded = shellCommandCardExpanded,
                cardTransferState = cardTransferState,
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
            onRefresh = { scope.launch { actionState.refreshAllSections() } },
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
                actionState = actionState,
                overlayTransferActions = overlayTransferActions,
                cardTransferState = cardTransferState,
                textBundle = textBundle,
                onShellCommandCardsChange = osPageViewModel::updateShellCommandCards,
                onRemoveShellCommandCardExpanded = { shellCommandCardExpanded.remove(it) },
                onActivityShortcutCardsChange = osPageViewModel::updateActivityShortcutCards,
                onRemoveActivityCardExpanded = { activityCardExpanded.remove(it) },
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
                exportingCard = overlayState.exportingCard,
                onExportCard = mainListActions.onExportCard,
                onRefreshAll = mainListActions.onRefreshAll,
                contentBottomPadding = runtime.contentBottomPadding,
                showFloatingAddButton =
                    !overlayState.showActivitySuggestionSheet &&
                        !overlayState.showShellCardVisibilityManager,
                onOpenAddActivityShortcutCard = mainListActions.onOpenAddActivityShortcutCard,
                bottomBarVisible = runtime.bottomBarVisible,
                searchExpanded = enableSearchBar && searchExpanded,
                queryInput = queryInput,
                onQueryInputChange = osPageViewModel::updateQueryInput,
                onSearchExpandedChange = { expanded ->
                    searchExpanded = enableSearchBar && expanded
                },
                searchLabel = textBundle.searchLabel,
                floatingDockSide = runtime.floatingDockSide,
            )
        }
    }
}
