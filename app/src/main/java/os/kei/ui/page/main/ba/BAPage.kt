@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.ba.support.BASessionState
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.cafeDailyCapacity
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.os.appLucideCalendarIcon
import os.kei.ui.page.main.os.appLucideMailIcon
import os.kei.ui.page.main.os.osLucideCopyIcon
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.glass.AppFloatingDockAction
import os.kei.ui.page.main.widget.glass.AppFloatingDockSide
import os.kei.ui.page.main.widget.glass.AppFloatingVerticalActionDock
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import os.kei.ui.page.main.widget.glass.rememberAppFloatingDockBottomState
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BAPage(
    runtime: MainPageRuntime = MainPageRuntime(contentBottomPadding = 72.dp),
    preloadingEnabled: Boolean = false,
    liquidActionBarLayeredStyleEnabled: Boolean = true,
    onShowBottomBar: () -> Unit = {},
    onOpenPoolStudentGuide: (String) -> Unit = {},
    onOpenGuideCatalog: () -> Unit = {},
    onActionBarInteractingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pageScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val fullBackdropEffectsEnabled = pageBackdropEffectsEnabled
    val backdrops =
        rememberMainPageBackdropSet(
            keyPrefix = "ba",
            refreshOnCompositionEnter = true,
            distinctLayers = fullBackdropEffectsEnabled,
        )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    val serverOptions =
        listOf(
            stringResource(R.string.ba_server_cn),
            stringResource(R.string.ba_server_global),
            stringResource(R.string.ba_server_jp),
        )
    val cafeLevelOptions = remember { (1..10).toList() }
    val officeViewModel: BaOfficeViewModel = viewModel()

    // Reset once per cold process start so app relaunch always lands at BA top.
    LaunchedEffect(Unit) {
        if (!BASessionState.didResetScrollOnThisProcess) {
            officeViewModel.clearListScrollState()
            listState.scrollToItem(0)
            BASessionState.didResetScrollOnThisProcess = true
        }
    }

    val defaultBaSnapshot = remember { BaPageSnapshot() }
    val officeSnapshotUiState by officeViewModel.snapshotUiState.collectAsStateWithLifecycle()
    val initialSnapshot = officeSnapshotUiState.snapshot
    val office = officeViewModel.office
    val ui = rememberBaPageUiController(defaultBaSnapshot)
    val calendarPoolViewModel: BaCalendarPoolViewModel = viewModel()
    val calendarPoolRouteState by calendarPoolViewModel.routeState.collectAsStateWithLifecycle()
    val calendarUiState = calendarPoolRouteState.calendarUiState
    val poolUiState = calendarPoolRouteState.poolUiState
    val baRouteState =
        ui.routeState(
            calendarUiState = calendarUiState,
            poolUiState = poolUiState,
        )
    val baClockState = ui.clockState()

    LaunchedEffect(officeSnapshotUiState.loaded, initialSnapshot) {
        if (officeSnapshotUiState.loaded && ui.matchesSnapshot(defaultBaSnapshot)) {
            ui.applySnapshot(initialSnapshot)
        }
    }

    val officeName =
        when (baRouteState.serverIndex) {
            0 -> stringResource(R.string.ba_office_name_cn)
            1 -> stringResource(R.string.ba_office_name_global)
            else -> stringResource(R.string.ba_office_name_jp)
        }
    val officeOverviewTitle = stringResource(R.string.ba_office_overview_title, officeName)

    val settingsSheetState =
        buildBaSettingsSheetState(
            draft = baRouteState.settingsDraftState,
            calendarRefreshIntervalHours = baRouteState.calendarRefreshIntervalHours,
        )
    val notificationSettingsSheetState =
        buildBaNotificationSettingsSheetState(
            draft = baRouteState.notificationDraftState,
        )
    val savedSettingsSheetState =
        BaSettingsSheetState(
            cafeLevel = office.cafeLevel,
            mediaAdaptiveRotationEnabled = baRouteState.mediaAdaptiveRotationEnabled,
            mediaSaveCustomEnabled = baRouteState.mediaSaveCustomEnabled,
            mediaSaveFixedTreeUri = baRouteState.mediaSaveFixedTreeUri,
            idIndependentByServer = baRouteState.idIndependentByServer,
            showEndedActivities = baRouteState.showEndedActivities,
            showEndedPools = baRouteState.showEndedPools,
            showCalendarPoolImages = baRouteState.showCalendarPoolImages,
            calendarRefreshIntervalHours = baRouteState.calendarRefreshIntervalHours,
        )
    val savedNotificationSettingsSheetState =
        buildBaNotificationSettingsSheetState(ui.savedNotificationDraftState())
    val pageContentState =
        buildBaPageContentState(
            isPageActive = runtime.isPageActive,
            officeOverviewTitle = officeOverviewTitle,
            office = office,
            routeState = baRouteState,
            clockState = baClockState,
            serverOptions = serverOptions,
            cafeLevelOptions = cafeLevelOptions,
        )
    val syncPageActive =
        runtime.hasActivated &&
            if (preloadingEnabled) runtime.isWarmDataActive else runtime.isDataActive
    val baGlassRuntime = LocalGlassEffectRuntime.current
    val runtimePersistenceCoordinator = rememberBaRuntimePersistenceCoordinator()

    fun openSettingsSheet() {
        ui.openSettingsSheet(office)
    }

    fun closeSettingsSheet() {
        ui.closeSettingsSheet(office)
    }

    fun openNotificationSettingsSheet() {
        ui.openNotificationSettingsSheet(office)
    }

    fun closeNotificationSettingsSheet() {
        ui.closeNotificationSettingsSheet()
    }

    fun openDebugSheet() {
        ui.openDebugSheet()
    }

    fun closeDebugSheet() {
        ui.closeDebugSheet()
    }

    fun refreshCalendar(force: Boolean = false) {
        ui.refreshCalendar(force)
    }

    fun refreshPool(force: Boolean = false) {
        ui.refreshPool(force)
    }

    fun refreshAllBaData() {
        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        refreshCalendar(force = true)
        refreshPool(force = true)
    }

    LaunchedEffect(officeViewModel) {
        officeViewModel.serverRestoreEvents.collect { event ->
            ui.serverIndex = event.serverIndex
            refreshCalendar(force = true)
            refreshPool(force = true)
        }
    }
    LaunchedEffect(officeViewModel, context) {
        officeViewModel.events.collect { event ->
            when (event) {
                is BaOfficeEvent.SettingsSaved -> {
                    runtimePersistenceCoordinator.submit(event.clampUpdate)
                    ui.showEndedPools = event.persisted.showEndedPools
                    ui.showEndedActivities = event.persisted.showEndedActivities
                    ui.showCalendarPoolImages = event.persisted.showCalendarPoolImages
                    ui.mediaAdaptiveRotationEnabled = event.persisted.mediaAdaptiveRotationEnabled
                    ui.mediaSaveCustomEnabled = event.persisted.mediaSaveCustomEnabled
                    ui.mediaSaveFixedTreeUri = event.persisted.mediaSaveFixedTreeUri
                    ui.idIndependentByServer = event.persisted.idIndependentByServer
                    if (event.refreshCalendar) refreshCalendar(force = true)
                    if (event.refreshPool) refreshPool(force = true)
                    runtimePersistenceCoordinator.submit(event.runtimeUpdate)
                    ui.closeSettingsSheet(office)
                }

                is BaOfficeEvent.NotificationSettingsSaved -> {
                    runtimePersistenceCoordinator.submit(event.runtimeUpdate)
                    ui.applySavedNotificationDraft(event.savedDraft)
                    ui.closeNotificationSettingsSheet()
                }

                is BaOfficeEvent.RefreshIntervalSaved -> {
                    ui.calendarRefreshIntervalHours = event.hours
                    if (event.shouldRefresh) {
                        refreshCalendar(force = true)
                        refreshPool(force = true)
                    }
                }

                is BaOfficeEvent.OperationFailed -> {
                    context.showToast(
                        context.getString(
                            R.string.common_save_failed_with_reason,
                            event.error.message ?: event.error.javaClass.simpleName,
                        ),
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    officeViewModel.restoreServerFromStore(ui.serverIndex)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun saveSettings() {
        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        officeViewModel.saveSettings(
            sheetState = settingsSheetState,
            currentShowEndedActivities = ui.showEndedActivities,
            currentShowCalendarPoolImages = ui.showCalendarPoolImages,
            serverIndex = ui.serverIndex,
        )
    }

    fun saveNotificationSettings() {
        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        officeViewModel.saveNotificationSettings(
            sheetState = notificationSettingsSheetState,
            serverIndex = ui.serverIndex,
        )
    }

    val pageContentActions =
        buildBaPageContentActions(
            context = context,
            office = office,
            ui = ui,
            scope = pageScope,
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
        isPageActive = runtime.contentReady && runtime.isDataActive,
        consumedScrollToTopSignal = ui.consumedScrollToTopSignal,
        onConsumedScrollToTopSignalChange = { ui.consumedScrollToTopSignal = it },
        onDisposeActionBarInteraction = { onActionBarInteractingChanged(false) },
        office = office,
        runtimePersistenceCoordinator = runtimePersistenceCoordinator,
        onUiNowMsChange = { ui.uiNowMs = it },
        onUiMinuteMsChange = { ui.uiMinuteMs = it },
        serverIndex = ui.serverIndex,
        onServerChanged = {
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
        ui.calendarHydrationReady,
    ) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = syncPageActive,
            serverIndex = ui.serverIndex,
            reloadSignal = ui.baCalendarReloadSignal,
            calendarRefreshIntervalHours = ui.calendarRefreshIntervalHours,
            hydrationReady = ui.calendarHydrationReady,
        )
    }
    LaunchedEffect(
        syncPageActive,
        ui.serverIndex,
        ui.baPoolReloadSignal,
        ui.calendarRefreshIntervalHours,
        ui.poolHydrationReady,
    ) {
        calendarPoolViewModel.syncPool(
            isPageActive = syncPageActive,
            serverIndex = ui.serverIndex,
            reloadSignal = ui.baPoolReloadSignal,
            calendarRefreshIntervalHours = ui.calendarRefreshIntervalHours,
            hydrationReady = ui.poolHydrationReady,
        )
    }
    val dockAlignment =
        if (runtime.floatingDockSide == AppFloatingDockSide.Start) {
            Alignment.BottomStart
        } else {
            Alignment.BottomEnd
        }
    val dockStartPadding = if (runtime.floatingDockSide == AppFloatingDockSide.Start) 14.dp else 0.dp
    val dockEndPadding = if (runtime.floatingDockSide == AppFloatingDockSide.End) 14.dp else 0.dp
    val floatingDockBottomState =
        rememberAppFloatingDockBottomState(
            contentBottomPadding = runtime.contentBottomPadding,
            bottomBarVisible = runtime.bottomBarVisible,
            label = "ba_floating_action_dock_bottom",
        )
    val friendCodeActivated = pageContentState.officeState.idFriendCode != BA_DEFAULT_FRIEND_CODE
    val copyFriendCodeIconTint =
        if (friendCodeActivated) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.62f)
        }
    val baDockActions =
        listOf(
            AppFloatingDockAction(
                icon = osLucideCopyIcon(),
                contentDescription = stringResource(R.string.ba_cd_copy_friend_code),
                iconTint = copyFriendCodeIconTint,
                onClick = { office.copyFriendCodeToClipboard(context) },
            ),
            AppFloatingDockAction(
                icon = appLucideCalendarIcon(),
                contentDescription = stringResource(R.string.ba_calendar_cd_open_activity),
                iconTint = MiuixTheme.colorScheme.primary,
                onClick = { BaActivityCalendarActivity.launch(context) },
            ),
            AppFloatingDockAction(
                icon = appLucideMailIcon(),
                contentDescription = stringResource(R.string.ba_pool_cd_open_activity),
                iconTint = MiuixTheme.colorScheme.primary,
                onClick = { BaPoolActivity.launch(context) },
            ),
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
                        onTitleClick = onShowBottomBar,
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
                    onShowNotificationSettings = ::openNotificationSettingsSheet,
                    onShowDebug = ::openDebugSheet,
                    onInteractionChanged = onActionBarInteractingChanged,
                )
            }
            AppFloatingVerticalActionDock(
                backdrop = backdrops.content,
                actions = baDockActions,
                modifier =
                    Modifier
                        .align(dockAlignment)
                        .offset { IntOffset(x = 0, y = -floatingDockBottomState.value.roundToPx()) }
                        .padding(
                            start = dockStartPadding,
                            end = dockEndPadding,
                        ),
            )
        }

        BaSettingsSheet(
            show = ui.showSettingsSheet,
            backdrop = backdrops.sheet,
            state = settingsSheetState,
            onMediaAdaptiveRotationEnabledChange = { ui.sheetMediaAdaptiveRotationEnabled = it },
            onMediaSaveCustomEnabledChange = { ui.sheetMediaSaveCustomEnabled = it },
            onMediaSaveFixedTreeUriChange = { ui.sheetMediaSaveFixedTreeUri = it },
            onIdIndependentByServerChange = { ui.sheetIdIndependentByServer = it },
            onShowEndedActivitiesChange = { ui.sheetShowEndedActivities = it },
            onShowEndedPoolsChange = { ui.sheetShowEndedPools = it },
            onShowCalendarPoolImagesChange = { ui.sheetShowCalendarPoolImages = it },
            onCalendarRefreshIntervalSelected = { hours ->
                officeViewModel.saveRefreshInterval(
                    hours = hours,
                    calendarLastSyncMs = baRouteState.calendarUiState.lastSyncMs,
                )
            },
            hasUnsavedChanges = settingsSheetState != savedSettingsSheetState,
            onDismissRequest = ::closeSettingsSheet,
            onSaveRequest = ::saveSettings,
        )
        BaNotificationSettingsSheet(
            show = ui.showNotificationSettingsSheet,
            backdrop = backdrops.sheet,
            state = notificationSettingsSheetState,
            apThresholdMax = (office.apLimit + 200).coerceIn(0, BA_AP_MAX),
            cafeApThresholdMax = cafeDailyCapacity(office.cafeLevel),
            onApNotifyEnabledChange = { ui.sheetApNotifyEnabled = it },
            onCafeApNotifyEnabledChange = { ui.sheetCafeApNotifyEnabled = it },
            onArenaRefreshNotifyEnabledChange = { ui.sheetArenaRefreshNotifyEnabled = it },
            onCafeVisitNotifyEnabledChange = { ui.sheetCafeVisitNotifyEnabled = it },
            onCalendarUpcomingNotifyEnabledChange = { ui.sheetCalendarUpcomingNotifyEnabled = it },
            onCalendarEndingNotifyEnabledChange = { ui.sheetCalendarEndingNotifyEnabled = it },
            onPoolUpcomingNotifyEnabledChange = { ui.sheetPoolUpcomingNotifyEnabled = it },
            onPoolEndingNotifyEnabledChange = { ui.sheetPoolEndingNotifyEnabled = it },
            onCalendarPoolChangeNotifyEnabledChange = {
                ui.sheetCalendarPoolChangeNotifyEnabled = it
            },
            onCalendarPoolNotifyLeadHoursSelected = { ui.sheetCalendarPoolNotifyLeadHours = it },
            onApNotifyThresholdTextChange = { ui.sheetApNotifyThresholdText = it },
            onApNotifyThresholdDone = {
                val normalized =
                    ui.sheetApNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
                ui.sheetApNotifyThresholdText = normalized.toString()
            },
            onCafeApNotifyThresholdTextChange = { ui.sheetCafeApNotifyThresholdText = it },
            onCafeApNotifyThresholdDone = {
                val normalized =
                    ui.sheetCafeApNotifyThresholdText.toIntOrNull()?.coerceIn(0, BA_AP_MAX) ?: 120
                ui.sheetCafeApNotifyThresholdText = normalized.toString()
            },
            hasUnsavedChanges = notificationSettingsSheetState != savedNotificationSettingsSheetState,
            onDismissRequest = ::closeNotificationSettingsSheet,
            onSaveRequest = ::saveNotificationSettings,
        )
        BaDebugSheet(
            show = ui.showDebugSheet,
            backdrop = backdrops.sheet,
            onSendApTestNotification = {
                office.sendApTestNotification(context = context, showToast = true)
            },
            onSendCafeApTestNotification = {
                office.sendCafeApTestNotification(
                    context = context,
                    showToast = true,
                    onRuntimeUpdate = runtimePersistenceCoordinator::submit,
                )
            },
            onSendCafeVisitTestNotification = {
                office.sendCafeVisitTestNotification(
                    context = context,
                    serverIndex = ui.serverIndex,
                    showToast = true,
                )
            },
            onSendArenaRefreshTestNotification = {
                office.sendArenaRefreshTestNotification(
                    context = context,
                    serverIndex = ui.serverIndex,
                    showToast = true,
                )
            },
            onSendCalendarUpcomingTestNotification = {
                val nowMs = ui.uiNowMs
                val entries =
                    resolveCalendarDebugEntries(
                        context = context,
                        entries = calendarUiState.entries,
                        useRealData = ui.debugUseRealCalendarPoolData,
                        upcoming = true,
                        nowMs = nowMs,
                    ) ?: return@BaDebugSheet showBaDebugRealDataMissingToast(context)
                notifyBaDebugResult(
                    context = context,
                    sent =
                        BaCalendarPoolNotificationDispatcher.sendCalendarUpcomingGroup(
                            context = context,
                            serverIndex = ui.serverIndex,
                            entries = entries,
                        ),
                )
            },
            onSendCalendarEndingTestNotification = {
                val nowMs = ui.uiNowMs
                val entries =
                    resolveCalendarDebugEntries(
                        context = context,
                        entries = calendarUiState.entries,
                        useRealData = ui.debugUseRealCalendarPoolData,
                        upcoming = false,
                        nowMs = nowMs,
                    ) ?: return@BaDebugSheet showBaDebugRealDataMissingToast(context)
                notifyBaDebugResult(
                    context = context,
                    sent =
                        BaCalendarPoolNotificationDispatcher.sendCalendarEndingGroup(
                            context = context,
                            serverIndex = ui.serverIndex,
                            entries = entries,
                        ),
                )
            },
            onSendPoolUpcomingTestNotification = {
                val nowMs = ui.uiNowMs
                val entries =
                    resolvePoolDebugEntries(
                        context = context,
                        entries = poolUiState.entries,
                        useRealData = ui.debugUseRealCalendarPoolData,
                        upcoming = true,
                        nowMs = nowMs,
                    ) ?: return@BaDebugSheet showBaDebugRealDataMissingToast(context)
                notifyBaDebugResult(
                    context = context,
                    sent =
                        BaCalendarPoolNotificationDispatcher.sendPoolUpcomingGroup(
                            context = context,
                            serverIndex = ui.serverIndex,
                            entries = entries,
                        ),
                )
            },
            onSendPoolEndingTestNotification = {
                val nowMs = ui.uiNowMs
                val entries =
                    resolvePoolDebugEntries(
                        context = context,
                        entries = poolUiState.entries,
                        useRealData = ui.debugUseRealCalendarPoolData,
                        upcoming = false,
                        nowMs = nowMs,
                    ) ?: return@BaDebugSheet showBaDebugRealDataMissingToast(context)
                notifyBaDebugResult(
                    context = context,
                    sent =
                        BaCalendarPoolNotificationDispatcher.sendPoolEndingGroup(
                            context = context,
                            serverIndex = ui.serverIndex,
                            entries = entries,
                        ),
                )
            },
            onSendCalendarPoolChangeTestNotification = {
                val detail =
                    if (ui.debugUseRealCalendarPoolData) {
                        resolveRealChangeDebugDetail(
                            calendarEntries = calendarUiState.entries,
                            poolEntries = poolUiState.entries,
                            nowMs = ui.uiNowMs,
                        ).takeIf { it.isNotBlank() }
                            ?: return@BaDebugSheet showBaDebugRealDataMissingToast(context)
                    } else {
                        context.resolveString(R.string.ba_debug_sample_change_detail)
                    }
                notifyBaDebugResult(
                    context = context,
                    sent =
                        BaCalendarPoolNotificationDispatcher.sendDataChanged(
                            context = context,
                            calendarChangeCount = 1,
                            poolChangeCount = 1,
                            detail = detail,
                        ),
                )
            },
            useRealCalendarPoolData = ui.debugUseRealCalendarPoolData,
            onUseRealCalendarPoolDataChange = { ui.debugUseRealCalendarPoolData = it },
            onTestCafePlus3Hours = {
                runtimePersistenceCoordinator.submit(office.testCafePlus3Hours(context))
            },
            onDismissRequest = ::closeDebugSheet,
        )
    }
}

private fun notifyBaDebugResult(
    context: Context,
    sent: Boolean,
) {
    val messageRes =
        if (sent) {
            R.string.ba_toast_calendar_pool_notification_sent
        } else {
            R.string.ba_toast_notification_permission_required
        }
    context.showToast(messageRes)
}

private fun showBaDebugRealDataMissingToast(context: Context) {
    context.showToast(R.string.ba_toast_calendar_pool_real_data_missing)
}
