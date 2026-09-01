@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.core.ext.showToast
import os.kei.core.ui.resource.resolveString
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.BaCraftState
import os.kei.ui.page.main.ba.support.slotAt
import os.kei.ui.page.main.ba.support.cafeDailyCapacity

internal class BaPageSheetApMutationPersistenceCoordinator(
    private val accountIdProvider: () -> BaAccountId?,
    private val saveApLimit: suspend (Int) -> Unit = { limit ->
        BaOfficeRepository.saveApLimitAsync(limit)
    },
    private val persistRuntimeUpdate: suspend (BaRuntimePersistenceUpdate) -> Unit =
        { update -> update.persistAsync() },
    private val scheduleBaApThreshold: () -> Unit,
    private val saveCraft: suspend (BaAccountId?, BaCraftState) -> Unit =
        { accountId, craft -> BaOfficeRepository.saveCraftAsync(accountId, craft) },
) {
    suspend fun persistApLimit(
        limit: Int,
        runtimeUpdate: BaRuntimePersistenceUpdate?,
    ) {
        persistBaApMutationAndReschedule(
            persist = {
                saveApLimit(limit)
                runtimeUpdate
                    ?.withAccountId(accountIdProvider())
                    ?.let { update -> persistRuntimeUpdate(update) }
            },
            schedule = scheduleBaApThreshold,
        )
    }

    suspend fun persistCafeCalibration(update: BaRuntimePersistenceUpdate) {
        persistBaApMutationAndReschedule(
            persist = {
                persistRuntimeUpdate(update.withAccountId(accountIdProvider()))
            },
            schedule = scheduleBaApThreshold,
        )
    }

    /**
     * Persists craft slots and re-arms the reminder alarm.
     *
     * The reschedule is the point: a craft write moves a completion instant, and the single BA alarm is
     * armed at the earliest one across every kind. Without it the pending alarm keeps the old time and
     * the new slot's reminder is either late or never.
     */
    suspend fun persistCraft(craft: BaCraftState) {
        persistBaApMutationAndReschedule(
            persist = { saveCraft(accountIdProvider(), craft) },
            schedule = scheduleBaApThreshold,
        )
    }
}

internal data class BaPageSheetApMutationCallbacks(
    val onSaveApLimit: () -> Unit,
    val onClearCafeStoredAp: () -> Unit,
    val onFillCafeStoredAp: () -> Unit,
)

internal fun buildBaPageSheetApMutationCallbacks(
    office: BaOfficeController,
    scope: CoroutineScope,
    persistenceCoordinator: BaPageSheetApMutationPersistenceCoordinator,
): BaPageSheetApMutationCallbacks {
    fun persistCafeCalibration(update: BaRuntimePersistenceUpdate) {
        scope.launch {
            persistenceCoordinator.persistCafeCalibration(update)
        }
        office.cafeStoredApInput = office.displayCafeStoredApInputText()
    }

    return BaPageSheetApMutationCallbacks(
        onSaveApLimit = {
            val finalValue =
                office.apLimitInput.toIntOrNull()?.coerceIn(0, BA_AP_LIMIT_MAX)
                    ?: office.apLimit.coerceIn(0, BA_AP_LIMIT_MAX)
            val limitUpdate = office.updateApLimit(finalValue)
            val regenRuntimeUpdate = office.applyApRegen()
            val runtimeUpdate =
                if (limitUpdate.runtimeUpdate != null && regenRuntimeUpdate != null) {
                    limitUpdate.runtimeUpdate.mergedWith(regenRuntimeUpdate)
                } else {
                    limitUpdate.runtimeUpdate ?: regenRuntimeUpdate
                }
            office.apLimitInput = limitUpdate.limit.toString()
            scope.launch {
                persistenceCoordinator.persistApLimit(
                    limit = limitUpdate.limit,
                    runtimeUpdate = runtimeUpdate,
                )
            }
        },
        onClearCafeStoredAp = {
            persistCafeCalibration(office.clearCafeStoredAp())
        },
        onFillCafeStoredAp = {
            persistCafeCalibration(office.fillCafeStoredAp())
        },
    )
}

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
    val sheetScope = rememberCoroutineScope()
    val apMutationPersistenceCoordinator =
        BaPageSheetApMutationPersistenceCoordinator(
            accountIdProvider = { accountUiState.activeAccountId },
            scheduleBaApThreshold = {
                AppBackgroundScheduler.scheduleBaApThreshold(context)
            },
        )
    val apMutationCallbacks =
        buildBaPageSheetApMutationCallbacks(
            office = office,
            scope = sheetScope,
            persistenceCoordinator = apMutationPersistenceCoordinator,
        )

    LaunchedEffect(routeState.showApLimitToolsSheet) {
        if (routeState.showApLimitToolsSheet) {
            office.apLimitInput = office.apLimit.toString()
        }
    }

    fun persistCraft(craft: BaCraftState?) {
        if (craft == null) return
        sheetScope.launch {
            apMutationPersistenceCoordinator.persistCraft(craft)
        }
    }

    fun persistCooldown(update: BaOfficeCooldownPersistenceUpdate?) {
        if (update == null) return
        sheetScope.launch {
            update.persistAsync()
        }
    }

    fun saveCafeCooldownRemaining(
        target: BaCafeCooldownEditTarget,
        remainingMs: Long,
    ) {
        val update =
            when (target) {
                BaCafeCooldownEditTarget.Headpat ->
                    office.updateHeadpatRemainingCooldown(
                        remainingMs = remainingMs,
                        serverIndex = routeState.serverIndex,
                    )

                BaCafeCooldownEditTarget.InviteTicket1 ->
                    office.updateInviteTicket1RemainingCooldown(remainingMs)

                BaCafeCooldownEditTarget.InviteTicket2 ->
                    office.updateInviteTicket2RemainingCooldown(remainingMs)
            }
        persistCooldown(update)
        viewModel.hideCafeCooldownEditSheet()
    }

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
        initialEditAccountId = routeState.accountManagementInitialEditAccountId,
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
        onKeepApRemindersReadUntilBelowThresholdChange = { enabled ->
            viewModel.updateNotificationDraft { draft ->
                draft.copy(keepApRemindersReadUntilBelowThreshold = enabled)
            }
        },
        onArenaRefreshNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(arenaRefreshNotifyEnabled = enabled) }
        },
        onCafeVisitNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(cafeVisitNotifyEnabled = enabled) }
        },
        onCraftNotifyEnabledChange = { enabled ->
            viewModel.updateNotificationDraft { draft -> draft.copy(craftNotifyEnabled = enabled) }
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
    BaApLimitToolsSheet(
        show = routeState.showApLimitToolsSheet,
        backdrop = backdrop,
        apLimitInput = office.apLimitInput,
        onApLimitInputChange = { input ->
            office.apLimitInput = input
        },
        onSaveApLimit = {
            apMutationCallbacks.onSaveApLimit()
            viewModel.hideApLimitToolsSheet()
        },
        onDismissRequest = viewModel::hideApLimitToolsSheet,
    )
    BaCafeApToolsSheet(
        show = routeState.showCafeApToolsSheet,
        backdrop = backdrop,
        cafeLevel = office.cafeLevel,
        cafeStoredAp = office.cafeStoredAp,
        cafeLastHourMs = office.cafeLastHourMs,
        uiMinuteMs = uiNowMsProvider(),
        onClearCafeStoredAp = apMutationCallbacks.onClearCafeStoredAp,
        onFillCafeStoredAp = apMutationCallbacks.onFillCafeStoredAp,
        onDismissRequest = viewModel::hideCafeApToolsSheet,
    )
    // The daily-done template, reached from the floating dock. The same editor the tiles open on a
    // long-press, hosted in-page instead of in BaDailyDoneTemplateActivity's translucent window — which
    // is also what lets it sample the page's own backdrop rather than falling back to an opaque fill.
    BaDailyDoneTemplateSheet(
        show = routeState.dailyDoneSheet.show,
        scope = BaDailyDoneTemplateScope.AllAccounts,
        config = routeState.dailyDoneSheet.config,
        backdrop = backdrop,
        applying = routeState.dailyDoneSheet.applying,
        onSave = viewModel::saveDailyDoneTemplate,
        onApply = { draft ->
            viewModel.applyDailyDoneTemplate(
                config = draft,
                // Read at the tap, not inside the view model: the pending tick belongs to the office
                // this page is showing, and only the page knows which account it is holding it for.
                currentRuntimeUpdate =
                    office
                        .applyRuntimeTick()
                        ?.withAccountId(accountUiState.activeAccountId),
            )
        },
        onDismissRequest = viewModel::hideDailyDoneSheet,
    )
    BaCafeCooldownEditSheet(
        show = routeState.cafeCooldownEditTarget != null,
        target = routeState.cafeCooldownEditTarget,
        backdrop = backdrop,
        coffeeHeadpatMs = office.coffeeHeadpatMs,
        coffeeInvite1UsedMs = office.coffeeInvite1UsedMs,
        coffeeInvite2UsedMs = office.coffeeInvite2UsedMs,
        serverIndex = routeState.serverIndex,
        uiNowMs = uiNowMsProvider(),
        onSaveRemaining = { remainingMs ->
            routeState.cafeCooldownEditTarget?.let { target ->
                saveCafeCooldownRemaining(
                    target = target,
                    remainingMs = remainingMs,
                )
            }
        },
        onDismissRequest = viewModel::hideCafeCooldownEditSheet,
    )
    BaCraftSlotEditSheet(
        show = routeState.craftSlotEditTarget != null,
        target = routeState.craftSlotEditTarget,
        backdrop = backdrop,
        slot =
            routeState.craftSlotEditTarget?.let { target ->
                office.craft.slotAt(target.function, target.index)
            } ?: BaCraftSlot(),
        uiNowMs = uiNowMsProvider(),
        onStart = { draft ->
            routeState.craftSlotEditTarget?.let { target ->
                persistCraft(
                    office.startCraftSlot(
                        function = target.function,
                        index = target.index,
                        slot = draft,
                    ),
                )
            }
            viewModel.hideCraftSlotEditSheet()
        },
        onSave = { draft ->
            routeState.craftSlotEditTarget?.let { target ->
                persistCraft(
                    office.editCraftSlot(
                        function = target.function,
                        index = target.index,
                        slot = draft,
                    ),
                )
            }
            viewModel.hideCraftSlotEditSheet()
        },
        onClear = {
            routeState.craftSlotEditTarget?.let { target ->
                persistCraft(office.clearCraftSlot(target.function, target.index))
            }
            viewModel.hideCraftSlotEditSheet()
        },
        onDismissRequest = viewModel::hideCraftSlotEditSheet,
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
                accountId = accountNotificationContext.accountId,
            )
        },
        onSendCafeApTestNotification = {
            office.sendCafeApTestNotification(
                context = context,
                showToast = true,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.CafeAp),
                accountDisplayName = accountNotificationContext.accountDisplayName,
                accountId = accountNotificationContext.accountId,
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
                accountId = accountNotificationContext.accountId,
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
                accountId = accountNotificationContext.accountId,
            )
        },
        onSendDailyDoneTestNotification = {
            office.sendDailyDoneTestNotification(
                context = context,
                showToast = true,
                accountId = accountNotificationContext.accountId,
            )
        },
        onSendDailyDoneNoCraftTestNotification = {
            office.sendDailyDoneTestNotification(
                context = context,
                showToast = true,
                craftSlotsStarted = 0,
                accountId = accountNotificationContext.accountId,
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
                )
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
                )
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
                )
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
                )
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
