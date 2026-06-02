@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.runtime.Composable
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.cafeDailyCapacity

@Composable
internal fun BaPageSheetHost(
    backdrop: Backdrop?,
    context: Context,
    office: BaOfficeController,
    viewModel: BaOfficeViewModel,
    runtimePersistenceCoordinator: BaRuntimePersistenceCoordinator,
    uiNowMsProvider: () -> Long,
    routeState: BaPageRouteState,
    chromeUiState: BaOfficeChromeUiState,
    settingsSheetState: BaSettingsSheetState,
    notificationSettingsSheetState: BaNotificationSettingsSheetState,
    savedSettingsSheetState: BaSettingsSheetState,
    savedNotificationSettingsSheetState: BaNotificationSettingsSheetState,
    calendarUiState: BaCalendarUiState,
    poolUiState: BaPoolUiState,
    accountUiState: BaOfficeAccountUiState,
    onDismissSettings: () -> Unit,
    onSaveSettings: () -> Unit,
    onDismissAccountManagement: () -> Unit,
    onSelectAccount: (BaAccountId) -> Unit,
    onDismissNotificationSettings: () -> Unit,
    onSaveNotificationSettings: () -> Unit,
) {
    BaSettingsSheet(
        show = routeState.showSettingsSheet,
        backdrop = backdrop,
        state = settingsSheetState,
        onMediaAdaptiveRotationEnabledChange = { enabled ->
            viewModel.updateSettingsDraft { draft -> draft.copy(mediaAdaptiveRotationEnabled = enabled) }
        },
        onMediaSaveCustomEnabledChange = { enabled ->
            viewModel.updateSettingsDraft { draft -> draft.copy(mediaSaveCustomEnabled = enabled) }
        },
        onMediaSaveFixedTreeUriChange = { uri ->
            viewModel.updateSettingsDraft { draft -> draft.copy(mediaSaveFixedTreeUri = uri) }
        },
        debugContent = {
            BaDebugControlsHost(
                backdrop = backdrop,
                context = context,
                office = office,
                runtimePersistenceCoordinator = runtimePersistenceCoordinator,
                uiNowMsProvider = uiNowMsProvider,
                routeState = routeState,
                calendarUiState = calendarUiState,
                poolUiState = poolUiState,
                accountUiState = accountUiState,
                onUseRealCalendarPoolDataChange = viewModel::updateDebugUseRealCalendarPoolData,
            )
        },
        hasUnsavedChanges = settingsSheetState != savedSettingsSheetState,
        onDismissRequest = onDismissSettings,
        onSaveRequest = onSaveSettings,
    )
    BaAccountManagementSheet(
        show = routeState.showAccountManagementSheet,
        backdrop = backdrop,
        state = accountUiState,
        onAllAccountsFollowGlobalNotificationSettingsChange =
            viewModel::updateAllAccountsFollowGlobalNotificationSettings,
        onAccountEnabledChange = viewModel::updateAccountEnabled,
        onSelectAccount = onSelectAccount,
        onAddAccount = viewModel::addAccount,
        onUpdateAccount = viewModel::updateAccountProfile,
        onDeleteAccount = viewModel::deleteAccount,
        onMoveAccount = viewModel::moveAccount,
        onDismissRequest = onDismissAccountManagement,
    )
    BaNotificationSettingsSheet(
        show = routeState.showNotificationSettingsSheet,
        backdrop = backdrop,
        state = notificationSettingsSheetState,
        apThresholdMax = (office.apLimit + 200).coerceIn(0, BA_AP_MAX),
        cafeApThresholdMax = cafeDailyCapacity(office.cafeLevel),
        onApNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(apNotifyEnabled = enabled) }
        },
        onCafeApNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(cafeApNotifyEnabled = enabled) }
        },
        onArenaRefreshNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(arenaRefreshNotifyEnabled = enabled) }
        },
        onCafeVisitNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(cafeVisitNotifyEnabled = enabled) }
        },
        onCalendarUpcomingNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(calendarUpcomingNotifyEnabled = enabled) }
        },
        onCalendarEndingNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(calendarEndingNotifyEnabled = enabled) }
        },
        onPoolUpcomingNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(poolUpcomingNotifyEnabled = enabled) }
        },
        onPoolEndingNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(poolEndingNotifyEnabled = enabled) }
        },
        onCalendarPoolChangeNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(calendarPoolChangeNotifyEnabled = enabled) }
        },
        onCalendarPoolNotifyLeadHoursSelected = { hours ->
            viewModel.updateNotificationDraft { draft -> draft.copy(calendarPoolNotifyLeadHours = hours) }
        },
        leadDropdownExpanded = chromeUiState.notificationLeadDropdownExpanded,
        leadDropdownAnchorBounds = chromeUiState.notificationLeadDropdownAnchorBounds,
        onLeadDropdownExpandedChange = viewModel::updateNotificationLeadDropdownExpanded,
        onLeadDropdownAnchorBoundsChange = viewModel::updateNotificationLeadDropdownAnchorBounds,
        onApNotifyThresholdTextChange = { text ->
            viewModel.updateNotificationDraft { draft -> draft.copy(apNotifyThresholdText = text) }
        },
        onApNotifyThresholdDone = viewModel::normalizeApNotifyThresholdText,
        onCafeApNotifyThresholdTextChange = { text ->
            viewModel.updateNotificationDraft { draft -> draft.copy(cafeApNotifyThresholdText = text) }
        },
        onCafeApNotifyThresholdDone = viewModel::normalizeCafeApNotifyThresholdText,
        hasUnsavedChanges = notificationSettingsSheetState != savedNotificationSettingsSheetState,
        onDismissRequest = onDismissNotificationSettings,
        onSaveRequest = onSaveNotificationSettings,
    )
}

@Composable
private fun BaDebugControlsHost(
    backdrop: Backdrop?,
    context: Context,
    office: BaOfficeController,
    runtimePersistenceCoordinator: BaRuntimePersistenceCoordinator,
    uiNowMsProvider: () -> Long,
    routeState: BaPageRouteState,
    calendarUiState: BaCalendarUiState,
    poolUiState: BaPoolUiState,
    accountUiState: BaOfficeAccountUiState,
    onUseRealCalendarPoolDataChange: (Boolean) -> Unit,
) {
    val accountNotificationContext = accountUiState.activeNotificationContext()
    BaDebugControlsContent(
        backdrop = backdrop,
        onSendApTestNotification = {
            office.sendApTestNotification(
                context = context,
                showToast = true,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.Ap),
                accountDisplayName = accountNotificationContext.accountDisplayName,
            )
        },
        onSendCafeApTestNotification = {
            office.sendCafeApTestNotification(
                context = context,
                showToast = true,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.CafeAp),
                accountDisplayName = accountNotificationContext.accountDisplayName,
                onRuntimeUpdate = runtimePersistenceCoordinator::submit,
            )
        },
        onSendCafeVisitTestNotification = {
            office.sendCafeVisitTestNotification(
                context = context,
                serverIndex = routeState.serverIndex,
                showToast = true,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.CafeVisit),
                accountDisplayName = accountNotificationContext.accountDisplayName,
            )
        },
        onSendArenaRefreshTestNotification = {
            office.sendArenaRefreshTestNotification(
                context = context,
                serverIndex = routeState.serverIndex,
                showToast = true,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.ArenaRefresh),
                accountDisplayName = accountNotificationContext.accountDisplayName,
            )
        },
        onSendCalendarUpcomingTestNotification = {
            val uiNowMs = uiNowMsProvider()
            val entries =
                resolveCalendarDebugEntries(
                    context = context,
                    entries = calendarUiState.entries,
                    useRealData = routeState.debugUseRealCalendarPoolData,
                    upcoming = true,
                    nowMs = uiNowMs,
                ) ?: return@BaDebugControlsContent showBaDebugRealDataMissingToast(context)
            notifyBaDebugResult(
                context = context,
                sent =
                    BaCalendarPoolNotificationDispatcher.sendCalendarUpcomingGroup(
                        context = context,
                        serverIndex = routeState.serverIndex,
                        entries = entries,
                    ),
            )
        },
        onSendCalendarEndingTestNotification = {
            val uiNowMs = uiNowMsProvider()
            val entries =
                resolveCalendarDebugEntries(
                    context = context,
                    entries = calendarUiState.entries,
                    useRealData = routeState.debugUseRealCalendarPoolData,
                    upcoming = false,
                    nowMs = uiNowMs,
                ) ?: return@BaDebugControlsContent showBaDebugRealDataMissingToast(context)
            notifyBaDebugResult(
                context = context,
                sent =
                    BaCalendarPoolNotificationDispatcher.sendCalendarEndingGroup(
                        context = context,
                        serverIndex = routeState.serverIndex,
                        entries = entries,
                    ),
            )
        },
        onSendPoolUpcomingTestNotification = {
            val uiNowMs = uiNowMsProvider()
            val entries =
                resolvePoolDebugEntries(
                    context = context,
                    entries = poolUiState.entries,
                    useRealData = routeState.debugUseRealCalendarPoolData,
                    upcoming = true,
                    nowMs = uiNowMs,
                ) ?: return@BaDebugControlsContent showBaDebugRealDataMissingToast(context)
            notifyBaDebugResult(
                context = context,
                sent =
                    BaCalendarPoolNotificationDispatcher.sendPoolUpcomingGroup(
                        context = context,
                        serverIndex = routeState.serverIndex,
                        entries = entries,
                    ),
            )
        },
        onSendPoolEndingTestNotification = {
            val uiNowMs = uiNowMsProvider()
            val entries =
                resolvePoolDebugEntries(
                    context = context,
                    entries = poolUiState.entries,
                    useRealData = routeState.debugUseRealCalendarPoolData,
                    upcoming = false,
                    nowMs = uiNowMs,
                ) ?: return@BaDebugControlsContent showBaDebugRealDataMissingToast(context)
            notifyBaDebugResult(
                context = context,
                sent =
                    BaCalendarPoolNotificationDispatcher.sendPoolEndingGroup(
                        context = context,
                        serverIndex = routeState.serverIndex,
                        entries = entries,
                    ),
            )
        },
        onSendCalendarPoolChangeTestNotification = {
            val uiNowMs = uiNowMsProvider()
            val detail =
                if (routeState.debugUseRealCalendarPoolData) {
                    resolveRealChangeDebugDetail(
                        calendarEntries = calendarUiState.entries,
                        poolEntries = poolUiState.entries,
                        nowMs = uiNowMs,
                    ).takeIf { it.isNotBlank() }
                        ?: return@BaDebugControlsContent showBaDebugRealDataMissingToast(context)
                } else {
                    context.resolveString(R.string.ba_debug_sample_change_detail)
                }
            notifyBaDebugResult(
                context = context,
                sent =
                    BaCalendarPoolNotificationDispatcher.sendDataChanged(
                        context = context,
                        serverIndex = routeState.serverIndex,
                        calendarChangeCount = 1,
                        poolChangeCount = 1,
                        detail = detail,
                    ),
            )
        },
        useRealCalendarPoolData = routeState.debugUseRealCalendarPoolData,
        onUseRealCalendarPoolDataChange = onUseRealCalendarPoolDataChange,
        onTestCafePlus3Hours = {
            runtimePersistenceCoordinator.submit(office.testCafePlus3Hours(context))
        },
    )
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
