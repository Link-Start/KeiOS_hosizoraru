package os.kei.ui.page.main.ba

import android.content.Context
import android.widget.Toast
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
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
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
    val notificationSettingsSheetState = buildBaNotificationSettingsSheetState(ui)
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

    fun openNotificationSettingsSheet() {
        ui.openNotificationSettingsSheet(office)
    }

    fun closeNotificationSettingsSheet() {
        ui.closeNotificationSettingsSheet(office)
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

    fun saveNotificationSettings() {
        saveBaNotificationSettings(
            context = context,
            office = office,
            ui = ui,
            notificationSettingsSheetState = notificationSettingsSheetState,
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
    val friendCodeActivated = pageContentState.officeState.idFriendCode != BA_DEFAULT_FRIEND_CODE
    val copyFriendCodeIconTint = if (friendCodeActivated) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.62f)
    }
    val baDockActions = listOf(
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
                    onShowNotificationSettings = ::openNotificationSettingsSheet,
                    onShowDebug = ::openDebugSheet,
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
        BaNotificationSettingsSheet(
            show = ui.showNotificationSettingsSheet,
            backdrop = backdrops.sheet,
            state = notificationSettingsSheetState,
            onApNotifyEnabledChange = { ui.sheetApNotifyEnabled = it },
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
            onDismissRequest = ::closeNotificationSettingsSheet,
            onSaveRequest = ::saveNotificationSettings,
        )
        BaDebugSheet(
            show = ui.showDebugSheet,
            backdrop = backdrops.sheet,
            onSendApTestNotification = {
                office.sendApTestNotification(context = context, showToast = true)
            },
            onSendCafeVisitTestNotification = {
                office.sendCafeVisitTestNotification(
                    context = context,
                    serverIndex = ui.serverIndex,
                    showToast = true
                )
            },
            onSendArenaRefreshTestNotification = {
                office.sendArenaRefreshTestNotification(
                    context = context,
                    serverIndex = ui.serverIndex,
                    showToast = true
                )
            },
            onSendCalendarUpcomingTestNotification = {
                val entry = resolveCalendarDebugEntry(
                    context = context,
                    entries = calendarUiState.entries,
                    useRealData = ui.debugUseRealCalendarPoolData,
                    upcoming = true
                ) ?: return@BaDebugSheet
                notifyBaDebugResult(
                    context = context,
                    sent = BaCalendarPoolNotificationDispatcher.sendCalendarUpcoming(
                        context = context,
                        serverIndex = ui.serverIndex,
                        entry = entry
                    )
                )
            },
            onSendCalendarEndingTestNotification = {
                val entry = resolveCalendarDebugEntry(
                    context = context,
                    entries = calendarUiState.entries,
                    useRealData = ui.debugUseRealCalendarPoolData,
                    upcoming = false
                ) ?: return@BaDebugSheet
                notifyBaDebugResult(
                    context = context,
                    sent = BaCalendarPoolNotificationDispatcher.sendCalendarEnding(
                        context = context,
                        serverIndex = ui.serverIndex,
                        entry = entry
                    )
                )
            },
            onSendPoolUpcomingTestNotification = {
                val entry = resolvePoolDebugEntry(
                    context = context,
                    entries = poolUiState.entries,
                    useRealData = ui.debugUseRealCalendarPoolData,
                    upcoming = true
                ) ?: return@BaDebugSheet
                notifyBaDebugResult(
                    context = context,
                    sent = BaCalendarPoolNotificationDispatcher.sendPoolUpcoming(
                        context = context,
                        serverIndex = ui.serverIndex,
                        entry = entry
                    )
                )
            },
            onSendPoolEndingTestNotification = {
                val entry = resolvePoolDebugEntry(
                    context = context,
                    entries = poolUiState.entries,
                    useRealData = ui.debugUseRealCalendarPoolData,
                    upcoming = false
                ) ?: return@BaDebugSheet
                notifyBaDebugResult(
                    context = context,
                    sent = BaCalendarPoolNotificationDispatcher.sendPoolEnding(
                        context = context,
                        serverIndex = ui.serverIndex,
                        entry = entry
                    )
                )
            },
            onSendCalendarPoolChangeTestNotification = {
                val detail = if (ui.debugUseRealCalendarPoolData) {
                    resolveRealChangeDebugDetail(
                        context = context,
                        calendarEntries = calendarUiState.entries,
                        poolEntries = poolUiState.entries
                    ) ?: return@BaDebugSheet
                } else {
                    context.getString(R.string.ba_debug_sample_change_detail)
                }
                notifyBaDebugResult(
                    context = context,
                    sent = BaCalendarPoolNotificationDispatcher.sendDataChanged(
                        context = context,
                        calendarChangeCount = 1,
                        poolChangeCount = 1,
                        detail = detail
                    )
                )
            },
            useRealCalendarPoolData = ui.debugUseRealCalendarPoolData,
            onUseRealCalendarPoolDataChange = { ui.debugUseRealCalendarPoolData = it },
            onTestCafePlus3Hours = { office.testCafePlus3Hours(context) },
            onDismissRequest = ::closeDebugSheet,
        )
    }
}

private fun sampleCalendarEntry(
    context: Context,
    upcoming: Boolean,
): BaCalendarEntry {
    val nowMs = System.currentTimeMillis()
    val startAtMs = if (upcoming) nowMs + 60L * 60L * 1000L else nowMs - 2L * 60L * 60L * 1000L
    val endAtMs = if (upcoming) nowMs + 3L * 60L * 60L * 1000L else nowMs + 60L * 60L * 1000L
    return BaCalendarEntry(
        id = -10_001,
        title = context.getString(R.string.ba_debug_sample_calendar_title),
        kindId = 14,
        kindName = "",
        beginAtMs = startAtMs,
        endAtMs = endAtMs,
        linkUrl = "",
        imageUrl = "",
        isRunning = !upcoming
    )
}

private fun samplePoolEntry(
    context: Context,
    upcoming: Boolean,
): BaPoolEntry {
    val nowMs = System.currentTimeMillis()
    val startAtMs = if (upcoming) nowMs + 60L * 60L * 1000L else nowMs - 2L * 60L * 60L * 1000L
    val endAtMs = if (upcoming) nowMs + 3L * 60L * 60L * 1000L else nowMs + 60L * 60L * 1000L
    return BaPoolEntry(
        id = -10_002,
        name = context.getString(R.string.ba_debug_sample_pool_title),
        tagId = 6,
        tagName = "",
        startAtMs = startAtMs,
        endAtMs = endAtMs,
        linkUrl = "",
        imageUrl = "",
        isRunning = !upcoming
    )
}

private fun notifyBaDebugResult(
    context: Context,
    sent: Boolean,
) {
    val messageRes = if (sent) {
        R.string.ba_toast_calendar_pool_notification_sent
    } else {
        R.string.ba_toast_notification_permission_required
    }
    Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
}

private fun resolveCalendarDebugEntry(
    context: Context,
    entries: List<BaCalendarEntry>,
    useRealData: Boolean,
    upcoming: Boolean,
): BaCalendarEntry? {
    if (!useRealData) return sampleCalendarEntry(context, upcoming)
    val nowMs = System.currentTimeMillis()
    val realEntry = if (upcoming) {
        entries
            .filter { it.beginAtMs > nowMs }
            .minByOrNull { it.beginAtMs }
    } else {
        entries
            .filter { it.endAtMs > nowMs }
            .minByOrNull { it.endAtMs }
    }
    if (realEntry != null) return realEntry
    Toast.makeText(
        context,
        context.getString(R.string.ba_toast_calendar_pool_real_data_missing),
        Toast.LENGTH_SHORT
    ).show()
    return null
}

private fun resolvePoolDebugEntry(
    context: Context,
    entries: List<BaPoolEntry>,
    useRealData: Boolean,
    upcoming: Boolean,
): BaPoolEntry? {
    if (!useRealData) return samplePoolEntry(context, upcoming)
    val nowMs = System.currentTimeMillis()
    val realEntry = if (upcoming) {
        entries
            .filter { it.startAtMs > nowMs }
            .minByOrNull { it.startAtMs }
    } else {
        entries
            .filter { it.endAtMs > nowMs }
            .minByOrNull { it.endAtMs }
    }
    if (realEntry != null) return realEntry
    Toast.makeText(
        context,
        context.getString(R.string.ba_toast_calendar_pool_real_data_missing),
        Toast.LENGTH_SHORT
    ).show()
    return null
}

private fun resolveRealChangeDebugDetail(
    context: Context,
    calendarEntries: List<BaCalendarEntry>,
    poolEntries: List<BaPoolEntry>,
): String? {
    val nowMs = System.currentTimeMillis()
    val calendarTitle = calendarEntries
        .filter { it.endAtMs > nowMs }
        .minByOrNull { if (it.beginAtMs > nowMs) it.beginAtMs else it.endAtMs }
        ?.title
        .orEmpty()
    val poolTitle = poolEntries
        .filter { it.endAtMs > nowMs }
        .minByOrNull { if (it.startAtMs > nowMs) it.startAtMs else it.endAtMs }
        ?.name
        .orEmpty()
    val detail = listOf(calendarTitle, poolTitle)
        .filter { it.isNotBlank() }
        .joinToString(separator = " / ")
    if (detail.isNotBlank()) return detail
    Toast.makeText(
        context,
        context.getString(R.string.ba_toast_calendar_pool_real_data_missing),
        Toast.LENGTH_SHORT
    ).show()
    return null
}
