@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaApReminderKind
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.displayAp
import os.kei.ui.page.main.widget.chrome.BindLazyListScrollBoundsEffect
import os.kei.ui.page.main.widget.chrome.rememberAppPageScrollTarget
import os.kei.ui.page.main.widget.chrome.expandTopAppBarToPageTop
import os.kei.ui.page.main.widget.chrome.isPageSettledAtTop
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import kotlin.time.Duration.Companion.milliseconds

internal interface BaApSuppressionAnchorWriter {
    suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        anchorAtMs: Long,
    )
}

internal interface BaApDismissedUntilWriter {
    suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        dismissedUntilAtMs: Long,
    )
}

private object BaSettingsStoreApSuppressionAnchorWriter : BaApSuppressionAnchorWriter {
    override suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        anchorAtMs: Long,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveAccountApSuppressionAnchor(
                accountId = accountId,
                kind = kind,
                anchorAtMs = anchorAtMs,
            )
        }
    }
}

private object BaSettingsStoreApDismissedUntilWriter : BaApDismissedUntilWriter {
    override suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        dismissedUntilAtMs: Long,
    ) {
        withContext(AppDispatchers.baFetch) {
            BASettingsStore.saveAccountApDismissedUntil(
                accountId = accountId,
                kind = kind,
                dismissedUntilAtMs = dismissedUntilAtMs,
            )
        }
    }
}

internal suspend fun persistBaForegroundApSyncResult(
    request: BaApNotificationSyncRequest,
    result: BaApNotificationSyncResult,
    office: BaOfficeController,
    anchorWriter: BaApSuppressionAnchorWriter = BaSettingsStoreApSuppressionAnchorWriter,
    dismissedUntilWriter: BaApDismissedUntilWriter = BaSettingsStoreApDismissedUntilWriter,
    onLastNotifiedLevel: (Int) -> Unit,
) {
    if (result.committedDuringDelivery) return
    if (result.suppressionAnchorAtMs != null || result.dismissedUntilAtMs != null) {
        withContext(NonCancellable) {
            request.accountId?.let { accountId ->
                result.suppressionAnchorAtMs?.let { anchorAtMs ->
                    anchorWriter.save(
                        accountId = accountId,
                        kind = BaApReminderKind.Ap,
                        anchorAtMs = anchorAtMs,
                    )
                }
                result.dismissedUntilAtMs?.let { dismissedUntilAtMs ->
                    dismissedUntilWriter.save(
                        accountId = accountId,
                        kind = BaApReminderKind.Ap,
                        dismissedUntilAtMs = dismissedUntilAtMs,
                    )
                }
            }
            result.suppressionAnchorAtMs?.let { office.apSuppressionAnchorAtMs = it }
            result.dismissedUntilAtMs?.let { office.apDismissedUntilAtMs = it }
        }
    }
    result.lastNotifiedLevel?.let(onLastNotifiedLevel)
}

internal suspend fun persistBaForegroundApDeliveredResult(
    request: BaApNotificationSyncRequest,
    result: BaApNotificationSyncResult,
    persistRuntimeUpdate: suspend (BaRuntimePersistenceUpdate) -> Unit = { update ->
        update.persistAsync()
    },
    anchorWriter: BaApSuppressionAnchorWriter = BaSettingsStoreApSuppressionAnchorWriter,
    dismissedUntilWriter: BaApDismissedUntilWriter = BaSettingsStoreApDismissedUntilWriter,
) {
    val accountId = requireNotNull(request.accountId) {
        "Foreground BA AP delivery requires an account ID"
    }
    result.lastNotifiedLevel?.let { level ->
        persistRuntimeUpdate(
            BaRuntimePersistenceUpdate(
                accountId = accountId,
                apLastNotifiedLevel = level,
            ),
        )
    }
    result.suppressionAnchorAtMs?.let { anchorAtMs ->
        anchorWriter.save(
            accountId = accountId,
            kind = BaApReminderKind.Ap,
            anchorAtMs = anchorAtMs,
        )
    }
    result.dismissedUntilAtMs?.let { dismissedUntilAtMs ->
        dismissedUntilWriter.save(
            accountId = accountId,
            kind = BaApReminderKind.Ap,
            dismissedUntilAtMs = dismissedUntilAtMs,
        )
    }
}

internal fun applyBaForegroundApCommittedResult(
    result: BaApNotificationSyncResult,
    office: BaOfficeController,
) {
    result.suppressionAnchorAtMs?.let { office.apSuppressionAnchorAtMs = it }
    result.dismissedUntilAtMs?.let { office.apDismissedUntilAtMs = it }
    result.lastNotifiedLevel?.let { office.applyApLastNotifiedLevel(it) }
}

internal fun shouldApplyBaForegroundApCommittedResult(
    requestAccountId: BaAccountId?,
    activeAccountId: BaAccountId?,
): Boolean = requestAccountId != null && requestAccountId == activeAccountId

@Composable
internal fun BaPageCommonEffects(
    listState: LazyListState,
    gridState: LazyStaggeredGridState,
    wideLayout: Boolean,
    scrollBehavior: ScrollBehavior,
    scrollToTopSignal: Int,
    isPageActive: Boolean,
    consumedScrollToTopSignal: Int,
    onConsumedScrollToTopSignalChange: (Int) -> Unit,
    office: BaOfficeController,
    runtimePersistenceCoordinator: BaRuntimePersistenceCoordinator,
    onUiNowMsChange: (Long) -> Unit,
    onUiMinuteMsChange: (Long) -> Unit,
    onScrollBoundsChange: (canScrollBackward: Boolean, canScrollForward: Boolean) -> Unit,
    serverIndex: Int,
    onServerChanged: suspend () -> Unit,
    context: Context,
    accountUiState: BaOfficeAccountUiState,
    runtimeEffectsActive: Boolean,
) {
    val currentActiveAccountId = rememberUpdatedState(accountUiState.activeAccountId)
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    val runtimeTickerCoordinator = rememberBaRuntimeTickerCoordinator()
    // Two containers, one on screen: on a tablet the office cards are a staggered grid and the column is
    // idle. Everything that reads a scroll position follows whichever is showing.
    val scrollTarget = rememberAppPageScrollTarget(listState, gridState, wideLayout)
    BindLazyListScrollBoundsEffect(
        listState = scrollTarget.scrollableState,
        isActive = isPageActive,
        onScrollBoundsChange = onScrollBoundsChange,
    )

    LaunchedEffect(runtimePersistenceCoordinator) {
        runtimePersistenceCoordinator.run()
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > consumedScrollToTopSignal) {
            onConsumedScrollToTopSignalChange(scrollToTopSignal)
            scrollTarget.scrollToTop()
            expandTopAppBarToPageTop(
                scrollBehavior = scrollBehavior,
                animationsEnabled = transitionAnimationsEnabled,
            )
        } else {
            onConsumedScrollToTopSignalChange(scrollToTopSignal)
        }
    }

    LaunchedEffect(scrollTarget, scrollBehavior, transitionAnimationsEnabled, snapshotFlowManager) {
        snapshotFlowManager
            .snapshotFlow {
                isPageSettledAtTop(
                    firstVisibleItemIndex = scrollTarget.firstVisibleItemIndex(),
                    firstVisibleItemScrollOffset = scrollTarget.firstVisibleItemScrollOffset(),
                    listScrollInProgress = scrollTarget.scrollableState.isScrollInProgress,
                )
            }.distinctUntilChanged()
            .collectLatest { settledAtTop ->
                if (settledAtTop) {
                    expandTopAppBarToPageTop(
                        scrollBehavior = scrollBehavior,
                        animationsEnabled = transitionAnimationsEnabled,
                    )
                }
            }
    }

    LaunchedEffect(runtimeEffectsActive, scrollTarget, office, runtimeTickerCoordinator) {
        if (runtimeEffectsActive) {
            runtimePersistenceCoordinator.submit(office.normalizeRuntimeState())
            val nowMs = System.currentTimeMillis()
            onUiNowMsChange(nowMs)
            onUiMinuteMsChange(nowMs)
        }
        runtimeTickerCoordinator.run(
            isPageActive = { runtimeEffectsActive },
            isScrollInProgress = { scrollTarget.scrollableState.isScrollInProgress },
        ) { frame ->
            if (frame.applyRuntimeTick) {
                runtimePersistenceCoordinator.submit(office.applyRuntimeTick(frame.nowMs))
            }
            if (frame.updateUiNow) {
                onUiNowMsChange(frame.nowMs)
            }
            if (frame.updateUiMinute) {
                onUiMinuteMsChange(frame.nowMs)
            }
        }
    }

    LaunchedEffect(runtimeEffectsActive, scrollTarget) {
        snapshotFlow { scrollTarget.scrollableState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (runtimeEffectsActive && !scrolling) {
                    val nowMs = System.currentTimeMillis()
                    onUiNowMsChange(nowMs)
                    onUiMinuteMsChange(nowMs)
                }
            }
    }

    LaunchedEffect(office.apCurrent) {
        val target = office.displayApInputText()
        if (office.apCurrentInput != target) office.apCurrentInput = target
    }

    LaunchedEffect(office.apLimit) {
        val target = office.apLimit.toString()
        if (office.apLimitInput != target) office.apLimitInput = target
    }

    LaunchedEffect(office.cafeStoredAp) {
        val target = office.displayCafeStoredApInputText()
        if (office.cafeStoredApInput != target) office.cafeStoredApInput = target
    }

    LaunchedEffect(serverIndex) {
        onServerChanged()
    }

    LaunchedEffect(context, office, accountUiState, runtimeEffectsActive) {
        if (!runtimeEffectsActive) return@LaunchedEffect
        val notificationContext = context.applicationContext
        snapshotFlow {
            val accountNotificationContext = accountUiState.activeNotificationContext()
            BaApNotificationSyncRequest(
                currentDisplay = displayAp(office.apCurrent),
                limitDisplay = office.apLimit.coerceIn(0, BA_AP_LIMIT_MAX),
                thresholdDisplay = office.apNotifyThreshold.coerceIn(0, BA_AP_MAX),
                notifyEnabled = office.apNotifyEnabled,
                lastNotifiedLevel = office.apLastNotifiedLevel,
                notificationId =
                    accountNotificationContext.notificationId(BaAccountNotificationKind.Ap),
                accountDisplayName = accountNotificationContext.accountDisplayName,
                accountId = accountNotificationContext.accountId,
                keepReadUntilBelowThreshold = office.keepApRemindersReadUntilBelowThreshold,
                suppressionAnchorAtMs = office.apSuppressionAnchorAtMs,
                dismissedUntilAtMs = office.apDismissedUntilAtMs,
            )
        }.distinctUntilChanged()
            .collectLatest { request ->
                delay(250.milliseconds)
                val result =
                    BaApNotificationSyncCoordinator.sync(
                        context = notificationContext,
                        request = request,
                        onThresholdDelivered = { deliveredResult ->
                            persistBaForegroundApDeliveredResult(
                                request = request,
                                result = deliveredResult,
                            )
                        },
                    )
                if (
                    result.committedDuringDelivery &&
                    shouldApplyBaForegroundApCommittedResult(
                        requestAccountId = request.accountId,
                        activeAccountId = currentActiveAccountId.value,
                    )
                ) {
                    applyBaForegroundApCommittedResult(result = result, office = office)
                } else if (!result.committedDuringDelivery) {
                    persistBaForegroundApSyncResult(
                        request = request,
                        result = result,
                        office = office,
                    ) { level ->
                        runtimePersistenceCoordinator.submit(office.applyApLastNotifiedLevel(level))
                    }
                }
            }
    }
}

internal fun shouldRunBaRuntimeEffects(
    isPageActive: Boolean,
    activeAccountId: BaAccountId?,
): Boolean = isPageActive && activeAccountId != null

@Composable
internal fun BaCalendarPoolSyncEffects(
    calendarPoolViewModel: BaCalendarPoolViewModel,
    syncPageActive: Boolean,
    routeState: BaPageRouteState,
) {
    LaunchedEffect(
        calendarPoolViewModel,
        syncPageActive,
        routeState.serverIndex,
        routeState.baCalendarReloadSignal,
        routeState.calendarRefreshIntervalHours,
        routeState.calendarHydrationReady,
    ) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = syncPageActive,
            serverIndex = routeState.serverIndex,
            reloadSignal = routeState.baCalendarReloadSignal,
            calendarRefreshIntervalHours = routeState.calendarRefreshIntervalHours,
            hydrationReady = routeState.calendarHydrationReady,
        )
    }
    LaunchedEffect(
        calendarPoolViewModel,
        syncPageActive,
        routeState.serverIndex,
        routeState.baPoolReloadSignal,
        routeState.calendarRefreshIntervalHours,
        routeState.poolHydrationReady,
    ) {
        calendarPoolViewModel.syncPool(
            isPageActive = syncPageActive,
            serverIndex = routeState.serverIndex,
            reloadSignal = routeState.baPoolReloadSignal,
            calendarRefreshIntervalHours = routeState.calendarRefreshIntervalHours,
            hydrationReady = routeState.poolHydrationReady,
        )
    }
}
