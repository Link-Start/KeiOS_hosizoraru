@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.support.BASessionState
import os.kei.ui.page.main.ba.support.BA_DEFAULT_FRIEND_CODE
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.host.pager.MainPageRuntime
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.widget.chrome.AppScaffold
import os.kei.ui.page.main.widget.chrome.AppTopEndActionBarOverlay
import os.kei.ui.page.main.widget.glass.LocalGlassEffectRuntime
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

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
    val saveFailedFormat = stringResource(R.string.common_save_failed_with_reason)
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdropEffectsEnabled =
        runtime.isPageActive &&
            !runtime.isPagerScrollInProgress
    val backdrops =
        rememberMainPageBackdropSet(
            keyPrefix = "ba",
            refreshOnCompositionEnter = true,
            distinctLayers = pageBackdropEffectsEnabled,
        )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = pageBackdropEffectsEnabled)
    val baServerCn = stringResource(R.string.ba_server_cn)
    val baServerGlobal = stringResource(R.string.ba_server_global)
    val baServerJp = stringResource(R.string.ba_server_jp)
    val serverOptions =
        remember(baServerCn, baServerGlobal, baServerJp) {
            listOf(
                baServerCn,
                baServerGlobal,
                baServerJp,
            )
        }
    val cafeLevelOptions = remember { (1..10).toList() }
    val officeViewModel: BaOfficeViewModel = applicationViewModel(create = ::BaOfficeViewModel)

    // Reset once per cold process start so app relaunch always lands at BA top.
    LaunchedEffect(Unit) {
        if (!BASessionState.didResetScrollOnThisProcess) {
            officeViewModel.clearListScrollState()
            listState.scrollToItem(0)
            BASessionState.didResetScrollOnThisProcess = true
        }
    }

    val officePageUiState by officeViewModel.pageUiState.collectAsStateWithLifecycle()
    val officeChromeUiState = officePageUiState.chromeUiState
    val office = officeViewModel.office
    val officeState = office.state()
    val ui = rememberBaPageUiController()
    val calendarPoolViewModel: BaCalendarPoolViewModel = applicationViewModel(create = ::BaCalendarPoolViewModel)
    val calendarPoolRouteState by calendarPoolViewModel.routeState.collectAsStateWithLifecycle()
    val calendarUiState = calendarPoolRouteState.calendarUiState
    val poolUiState = calendarPoolRouteState.poolUiState
    val baClockState = ui.clockState()
    val currentServerIndex = officePageUiState.serverUiState.serverIndex

    val officeName =
        when (currentServerIndex) {
            0 -> stringResource(R.string.ba_office_name_cn)
            1 -> stringResource(R.string.ba_office_name_global)
            else -> stringResource(R.string.ba_office_name_jp)
        }
    val officeOverviewTitle = stringResource(R.string.ba_office_overview_title, officeName)
    val pagePresentationState =
        remember(
            runtime.isPageActive,
            officeOverviewTitle,
            officeState,
            calendarUiState,
            poolUiState,
            officePageUiState,
            baClockState,
            serverOptions,
            cafeLevelOptions,
        ) {
            buildBaPagePresentationState(
                isPageActive = runtime.isPageActive,
                officeOverviewTitle = officeOverviewTitle,
                officeState = officeState,
                calendarUiState = calendarUiState,
                poolUiState = poolUiState,
                officePageUiState = officePageUiState,
                clockState = baClockState,
                serverOptions = serverOptions,
                cafeLevelOptions = cafeLevelOptions,
            )
        }
    val baRouteState = pagePresentationState.routeState
    val accountUiState = officePageUiState.accountUiState
    val settingsSheetState = pagePresentationState.settingsSheetState
    val notificationSettingsSheetState = pagePresentationState.notificationSettingsSheetState
    val savedSettingsDraftState = pagePresentationState.savedSettingsDraftState
    val savedSettingsSheetState = pagePresentationState.savedSettingsSheetState
    val savedNotificationSettingsSheetState = pagePresentationState.savedNotificationSettingsSheetState
    val pageContentState = pagePresentationState.pageContentState
    val currentServerIndexState = rememberUpdatedState(baRouteState.serverIndex)
    val uiNowMsProvider = remember(ui) { { ui.uiNowMs } }
    val settledWorkActive =
        rememberBaPageSettledWorkActive(
            active = runtime.isSettledDataActive,
        )
    val baGlassRuntime = LocalGlassEffectRuntime.current
    val runtimePersistenceCoordinator =
        rememberBaRuntimePersistenceCoordinator(
            accountIdProvider = { accountUiState.activeAccountId },
        )

    fun openSettingsSheet() {
        officeViewModel.showSettingsSheet(savedSettingsDraftState)
    }

    fun closeSettingsSheet() {
        officeViewModel.hideSettingsSheet(savedSettingsDraftState)
    }

    fun openAccountManagementSheet() {
        officeViewModel.showAccountManagementSheet()
    }

    fun closeAccountManagementSheet() {
        officeViewModel.hideAccountManagementSheet()
    }

    fun openNotificationSettingsSheet() {
        officeViewModel.showNotificationSettingsSheet()
    }

    fun closeNotificationSettingsSheet() {
        officeViewModel.hideNotificationSettingsSheet()
    }

    fun openDebugSheet() {
        officeViewModel.showDebugSheet()
    }

    fun closeDebugSheet() {
        officeViewModel.hideDebugSheet()
    }

    fun refreshCalendar(force: Boolean = false) {
        officeViewModel.refreshCalendar(force)
    }

    fun refreshPool(force: Boolean = false) {
        officeViewModel.refreshPool(force)
    }

    fun refreshAllBaData() {
        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        refreshCalendar(force = true)
        refreshPool(force = true)
    }

    LaunchedEffect(officeViewModel, context) {
        officeViewModel.events.collect { event ->
            when (event) {
                is BaOfficeEvent.SettingsSaved -> {
                    runtimePersistenceCoordinator.submit(event.clampUpdate)
                    runtimePersistenceCoordinator.submit(event.runtimeUpdate)
                    officeViewModel.hideSettingsSheet(
                        BaPageSettingsDraftState(
                            cafeLevel = event.persisted.savedCafeLevel,
                            mediaAdaptiveRotationEnabled = event.persisted.mediaAdaptiveRotationEnabled,
                            mediaSaveCustomEnabled = event.persisted.mediaSaveCustomEnabled,
                            mediaSaveFixedTreeUri = event.persisted.mediaSaveFixedTreeUri,
                            idIndependentByServer = event.persisted.idIndependentByServer,
                        ),
                    )
                }

                is BaOfficeEvent.NotificationSettingsSaved -> {
                    runtimePersistenceCoordinator.submit(event.runtimeUpdate)
                    officeViewModel.hideNotificationSettingsSheet()
                }

                is BaOfficeEvent.OperationFailed -> {
                    context.showToast(
                        saveFailedFormat.format(event.error.message ?: event.error.javaClass.simpleName),
                    )
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    officeViewModel.refreshRuntimeSettingsFromStore()
                    officeViewModel.restoreServerFromStore()
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
            serverIndex = baRouteState.serverIndex,
        )
    }

    fun saveNotificationSettings() {
        runtimePersistenceCoordinator.submit(office.applyRuntimeTick())
        officeViewModel.saveNotificationSettings(
            sheetState = notificationSettingsSheetState,
            serverIndex = baRouteState.serverIndex,
        )
    }

    val pageContentActions =
        remember(
            context,
            office,
            pageScope,
            officeViewModel,
            onOpenPoolStudentGuide,
            onOpenGuideCatalog,
        ) {
            buildBaPageContentActions(
                context = context,
                office = office,
                scope = pageScope,
                serverIndexProvider = { currentServerIndexState.value },
                accountIdProvider = { accountUiState.activeAccountId },
                onServerSelected = officeViewModel::selectServer,
                onSettingsCafeLevelChange = { level ->
                    officeViewModel.updateSettingsDraft { draft -> draft.copy(cafeLevel = level) }
                },
                onOverviewServerPopupAnchorBoundsChange = officeViewModel::updateOverviewServerPopupAnchorBounds,
                onOverviewServerPopupChange = officeViewModel::updateOverviewServerPopupExpanded,
                onCafeLevelPopupAnchorBoundsChange = officeViewModel::updateCafeLevelPopupAnchorBounds,
                onCafeLevelPopupChange = officeViewModel::updateCafeLevelPopupExpanded,
                onAccountSelected = { accountId ->
                    officeViewModel.selectActiveAccount(
                        accountId = accountId,
                        currentRuntimeUpdate =
                            office
                                .applyRuntimeTick()
                                ?.withAccountId(accountUiState.activeAccountId),
                    )
                },
                onRefreshCalendar = { refreshCalendar(force = true) },
                onRefreshPool = { refreshPool(force = true) },
                onOpenCalendarLink = { url -> openBaExternalLink(context = context, url = url) },
                onOpenPoolStudentGuide = onOpenPoolStudentGuide,
                onOpenGuideCatalog = onOpenGuideCatalog,
            )
        }

    BaPageCommonEffects(
        listState = listState,
        scrollBehavior = scrollBehavior,
        scrollToTopSignal = runtime.scrollToTopSignal,
        isPageActive = settledWorkActive,
        consumedScrollToTopSignal = baRouteState.consumedScrollToTopSignal,
        onConsumedScrollToTopSignalChange = officeViewModel::updateConsumedScrollToTopSignal,
        onDisposeActionBarInteraction = { onActionBarInteractingChanged(false) },
        office = office,
        runtimePersistenceCoordinator = runtimePersistenceCoordinator,
        onUiNowMsChange = { ui.uiNowMs = it },
        onUiMinuteMsChange = { ui.uiMinuteMs = it },
        onScrollBoundsChange = runtime.onScrollBoundsChange,
        serverIndex = baRouteState.serverIndex,
        onServerChanged = {
            officeViewModel.markCalendarPoolHydrationReady()
        },
        context = context,
    )

    BaCalendarPoolSyncEffects(
        calendarPoolViewModel = calendarPoolViewModel,
        syncPageActive = settledWorkActive,
        routeState = baRouteState,
    )
    val friendCodeActivated = pageContentState.officeState.idFriendCode != BA_DEFAULT_FRIEND_CODE

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
                    onShowAccountManagement = ::openAccountManagementSheet,
                    onShowSettings = ::openSettingsSheet,
                    onShowNotificationSettings = ::openNotificationSettingsSheet,
                    onShowDebug = ::openDebugSheet,
                    onInteractionChanged = onActionBarInteractingChanged,
                )
            }
            BaPageFloatingDock(
                backdrop = backdrops.content,
                runtime = runtime,
                friendCodeActivated = friendCodeActivated,
                onCopyFriendCode = { office.copyFriendCodeToClipboard(context) },
                onOpenCalendar = { BaActivityCalendarActivity.launch(context) },
                onOpenPool = { BaPoolActivity.launch(context) },
            )
        }

        BaPageSheetHost(
            backdrop = backdrops.sheet,
            context = context,
            office = office,
            viewModel = officeViewModel,
            runtimePersistenceCoordinator = runtimePersistenceCoordinator,
            uiNowMsProvider = uiNowMsProvider,
            routeState = baRouteState,
            chromeUiState = officeChromeUiState,
            settingsSheetState = settingsSheetState,
            notificationSettingsSheetState = notificationSettingsSheetState,
            savedSettingsSheetState = savedSettingsSheetState,
            savedNotificationSettingsSheetState = savedNotificationSettingsSheetState,
            calendarUiState = calendarUiState,
            poolUiState = poolUiState,
            accountUiState = accountUiState,
            onDismissSettings = ::closeSettingsSheet,
            onSaveSettings = ::saveSettings,
            onDismissAccountManagement = ::closeAccountManagementSheet,
            onSelectAccount = { accountId ->
                officeViewModel.selectActiveAccount(
                    accountId = accountId,
                    currentRuntimeUpdate =
                        office
                            .applyRuntimeTick()
                            ?.withAccountId(accountUiState.activeAccountId),
                )
            },
            onDismissNotificationSettings = ::closeNotificationSettingsSheet,
            onSaveNotificationSettings = ::saveNotificationSettings,
            onDismissDebug = ::closeDebugSheet,
        )
    }
}
