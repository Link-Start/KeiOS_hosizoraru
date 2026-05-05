package os.kei.ui.page.main.ba

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.support.BASessionState
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.os.appLucideCalendarIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BAPage(
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    preloadingEnabled: Boolean = false,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    onOpenPoolStudentGuide: (String) -> Unit = {},
    onOpenGuideCatalog: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdropEffectsEnabled = runtime.isPageActive &&
        !runtime.isPagerScrollInProgress
    val fullBackdropEffectsEnabled = pageBackdropEffectsEnabled
    val backdrops = rememberMainPageBackdropSet(
        keyPrefix = "ba",
        refreshOnCompositionEnter = true,
        distinctLayers = fullBackdropEffectsEnabled
    )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    val serverOptions = listOf(
        stringResource(R.string.ba_server_cn),
        stringResource(R.string.ba_server_global),
        stringResource(R.string.ba_server_jp)
    )
    val cafeLevelOptions = remember { (1..10).toList() }

    // Reset once per cold process start so app relaunch always lands at BA top.
    LaunchedEffect(Unit) {
        if (!BASessionState.didResetScrollOnThisProcess) {
            BASettingsStore.clearListScrollState()
            listState.scrollToItem(0)
            BASessionState.didResetScrollOnThisProcess = true
        }
    }

    val officeViewModel: BaOfficeViewModel = viewModel()
    val initialSnapshot = officeViewModel.initialSnapshot
    val office = officeViewModel.office
    val ui = rememberBaPageUiController(initialSnapshot)
    val calendarPoolViewModel: BaCalendarPoolViewModel = viewModel()
    val calendarUiState by calendarPoolViewModel.calendarUiState.collectAsState()
    val poolUiState by calendarPoolViewModel.poolUiState.collectAsState()

    val officeName = when (ui.serverIndex) {
        0 -> stringResource(R.string.ba_office_name_cn)
        1 -> stringResource(R.string.ba_office_name_global)
        else -> stringResource(R.string.ba_office_name_jp)
    }
    val officeOverviewTitle = stringResource(R.string.ba_office_overview_title, officeName)

    val settingsSheetState = buildBaSettingsSheetState(ui)
    val pageContentState = buildBaPageContentState(
        isPageActive = runtime.isPageActive,
        officeOverviewTitle = officeOverviewTitle,
        office = office,
        ui = ui,
        serverOptions = serverOptions,
        cafeLevelOptions = cafeLevelOptions,
        baCalendarEntries = calendarUiState.entries,
        baPoolEntries = poolUiState.entries,
    )
    val syncPageActive = if (preloadingEnabled) runtime.isWarmDataActive else runtime.isDataActive
    val baGlassRuntime = LocalGlassEffectRuntime.current

    fun openSettingsSheet() {
        ui.openSettingsSheet(office)
    }

    fun closeSettingsSheet() {
        ui.closeSettingsSheet(office)
    }

    fun refreshCalendar(force: Boolean = false) {
        ui.refreshCalendar(force)
    }

    fun refreshPool(force: Boolean = false) {
        ui.refreshPool(force)
    }

    fun refreshAllBaData() {
        office.applyCafeStorage()
        office.applyApRegen()
        refreshCalendar(force = true)
        refreshPool(force = true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val savedServerIndex = BASettingsStore.loadServerIndex()
                if (savedServerIndex != ui.serverIndex) {
                    ui.serverIndex = savedServerIndex
                    refreshCalendar(force = true)
                    refreshPool(force = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveSettings() {
        saveBaPageSettings(
            context = context,
            office = office,
            ui = ui,
            settingsSheetState = settingsSheetState,
            onRefreshCalendar = ::refreshCalendar,
            onRefreshPool = ::refreshPool,
        )
    }

    val pageContentActions = buildBaPageContentActions(
        context = context,
        office = office,
        ui = ui,
        onRefreshCalendar = { refreshCalendar(force = true) },
        onRefreshPool = { refreshPool(force = true) },
        onOpenCalendarLink = { url -> openBaExternalLink(context = context, url = url) },
        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
        onOpenGuideCatalog = onOpenGuideCatalog,
    )

    BaPageCommonEffects(
        listState = listState,
        scrollBehavior = scrollBehavior,
        scrollToTopSignal = runtime.scrollToTopSignal,
        isPageActive = runtime.isDataActive,
        consumedScrollToTopSignal = ui.consumedScrollToTopSignal,
        onConsumedScrollToTopSignalChange = { ui.consumedScrollToTopSignal = it },
        onDisposeActionBarInteraction = { onActionBarInteractingChanged(false) },
        office = office,
        onUiNowMsChange = { ui.uiNowMs = it },
        serverIndex = ui.serverIndex,
        onServerChanged = {
            ui.baCalendarLoading = true
            ui.baPoolLoading = true
            ui.baCalendarError = null
            ui.baPoolError = null
            ui.calendarHydrationReady = false
            ui.poolHydrationReady = false
            ui.calendarHydrationReady = true
            ui.poolHydrationReady = true
        },
        context = context,
    )

    LaunchedEffect(
        syncPageActive,
        ui.serverIndex,
        ui.baCalendarReloadSignal,
        ui.calendarRefreshIntervalHours,
        ui.calendarHydrationReady
    ) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = syncPageActive,
            serverIndex = ui.serverIndex,
            reloadSignal = ui.baCalendarReloadSignal,
            calendarRefreshIntervalHours = ui.calendarRefreshIntervalHours,
            hydrationReady = ui.calendarHydrationReady
        )
    }
    LaunchedEffect(
        syncPageActive,
        ui.serverIndex,
        ui.baPoolReloadSignal,
        ui.calendarRefreshIntervalHours,
        ui.poolHydrationReady
    ) {
        calendarPoolViewModel.syncPool(
            isPageActive = syncPageActive,
            serverIndex = ui.serverIndex,
            reloadSignal = ui.baPoolReloadSignal,
            calendarRefreshIntervalHours = ui.calendarRefreshIntervalHours,
            hydrationReady = ui.poolHydrationReady
        )
    }
    LaunchedEffect(calendarUiState) {
        ui.baCalendarLoading = calendarUiState.loading
        ui.baCalendarError = calendarUiState.error
        ui.baCalendarLastSyncMs = calendarUiState.lastSyncMs
    }
    LaunchedEffect(poolUiState) {
        ui.baPoolLoading = poolUiState.loading
        ui.baPoolError = poolUiState.error
        ui.baPoolLastSyncMs = poolUiState.lastSyncMs
    }
    val dockAlignment = if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
        Alignment.BottomStart
    } else {
        Alignment.BottomEnd
    }
    val dockStartPadding = if (runtime.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (runtime.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val bottomBarOffset = if (runtime.bottomBarVisible) 0.dp else AppChromeTokens.floatingBottomBarOuterHeight
    val floatingDockBottom by animateDpAsState(
        targetValue = runtime.contentBottomPadding - 24.dp - bottomBarOffset,
        label = "ba_floating_action_dock_bottom"
    )
    val baDockActions = listOf(
        AppFloatingDockAction(
            icon = osLucideCopyIcon(),
            contentDescription = stringResource(R.string.ba_cd_copy_friend_code),
            iconTint = MiuixTheme.colorScheme.primary,
            onClick = { office.copyFriendCodeToClipboard(context) },
        ),
        AppFloatingDockAction(
            icon = appLucideCalendarIcon(),
            contentDescription = stringResource(R.string.ba_calendar_cd_open_activity),
            iconTint = MiuixTheme.colorScheme.primary,
            onClick = { BaActivityCalendarActivity.launch(context) },
        )
    )

    CompositionLocalProvider(LocalGlassEffectRuntime provides baGlassRuntime) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    BaTopBar(
                        topBarColor = topBarMaterialBackdrop,
                        scrollBehavior = scrollBehavior,
                        titleBackdrop = backdrops.topBar,
                    )
                },
            ) { innerPadding ->
                BaPageContent(
                    backdrop = backdrops.content,
                    innerPadding = innerPadding,
                    contentBottomPadding = runtime.contentBottomPadding,
                    listState = listState,
                    nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                    state = pageContentState,
                    actions = pageContentActions,
                )
            }
            AppTopEndActionBarOverlay {
                BaTopBarActions(
                    backdrop = backdrops.topBar,
                    liquidActionBarLayeredStyleEnabled = liquidActionBarLayeredStyleEnabled,
                    onShowSettings = ::openSettingsSheet,
                    onInteractionChanged = onActionBarInteractingChanged,
                )
            }
            AppFloatingVerticalActionDock(
                backdrop = backdrops.content,
                actions = baDockActions,
                modifier = Modifier
                    .align(dockAlignment)
                    .padding(
                        start = dockStartPadding,
                        end = dockEndPadding,
                        bottom = floatingDockBottom,
                    )
            )
        }

        BaSettingsSheet(
            show = ui.showSettingsSheet,
            backdrop = backdrops.sheet,
            state = settingsSheetState,
            onApNotifyEnabledChange = { ui.sheetApNotifyEnabled = it },
            onArenaRefreshNotifyEnabledChange = { ui.sheetArenaRefreshNotifyEnabled = it },
            onCafeVisitNotifyEnabledChange = { ui.sheetCafeVisitNotifyEnabled = it },
            onApNotifyThresholdTextChange = { ui.sheetApNotifyThresholdText = it },
            onApNotifyThresholdDone = {
                val normalized = ui.sheetApNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
                ui.sheetApNotifyThresholdText = normalized.toString()
            },
            onMediaAdaptiveRotationEnabledChange = { ui.sheetMediaAdaptiveRotationEnabled = it },
            onMediaSaveCustomEnabledChange = { ui.sheetMediaSaveCustomEnabled = it },
            onMediaSaveFixedTreeUriChange = { ui.sheetMediaSaveFixedTreeUri = it },
            onShowEndedActivitiesChange = { ui.sheetShowEndedActivities = it },
            onShowEndedPoolsChange = { ui.sheetShowEndedPools = it },
            onShowCalendarPoolImagesChange = { ui.sheetShowCalendarPoolImages = it },
            onCalendarRefreshIntervalSelected = { hours ->
                applyBaCalendarRefreshInterval(
                    ui = ui,
                    hours = hours,
                    onRefreshCalendar = { refreshCalendar(force = true) },
                    onRefreshPool = { refreshPool(force = true) },
                )
            },
            onDismissRequest = ::closeSettingsSheet,
            onSaveRequest = ::saveSettings,
        )
    }
}
