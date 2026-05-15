package os.kei.ui.page.main.ba

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.ba.support.BA_AP_LIMIT_MAX
import os.kei.ui.page.main.ba.support.BA_AP_MAX
import os.kei.ui.page.main.ba.support.BA_AP_REGEN_TICK_MS
import os.kei.ui.page.main.ba.support.displayAp
import os.kei.ui.page.main.widget.chrome.expandTopAppBarToPageTop
import os.kei.ui.page.main.widget.chrome.isPageSettledAtTop
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun BaPageCommonEffects(
    listState: LazyListState,
    scrollBehavior: ScrollBehavior,
    scrollToTopSignal: Int,
    isPageActive: Boolean,
    consumedScrollToTopSignal: Int,
    onConsumedScrollToTopSignalChange: (Int) -> Unit,
    onDisposeActionBarInteraction: () -> Unit,
    office: BaOfficeController,
    onUiNowMsChange: (Long) -> Unit,
    onUiMinuteMsChange: (Long) -> Unit,
    serverIndex: Int,
    onServerChanged: suspend () -> Unit,
    context: Context,
) {
    val transitionAnimationsEnabled = LocalTransitionAnimationsEnabled.current
    val snapshotFlowManager = rememberAppSnapshotFlowManager()

    DisposableEffect(Unit) {
        onDispose { onDisposeActionBarInteraction() }
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > consumedScrollToTopSignal) {
            onConsumedScrollToTopSignalChange(scrollToTopSignal)
            listState.animateScrollToItem(0)
            expandTopAppBarToPageTop(
                scrollBehavior = scrollBehavior,
                animationsEnabled = transitionAnimationsEnabled
            )
        } else {
            onConsumedScrollToTopSignalChange(scrollToTopSignal)
        }
    }

    LaunchedEffect(listState, scrollBehavior, transitionAnimationsEnabled, snapshotFlowManager) {
        snapshotFlowManager.snapshotFlow {
            isPageSettledAtTop(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                listScrollInProgress = listState.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collectLatest { settledAtTop ->
                if (settledAtTop) {
                    expandTopAppBarToPageTop(
                        scrollBehavior = scrollBehavior,
                        animationsEnabled = transitionAnimationsEnabled
                    )
                }
            }
    }

    LaunchedEffect(isPageActive) {
        office.ensureRegenBase()
        office.ensureCafeHourBase()
        office.clampCafeStoredToCap()
        office.applyRuntimeTick()
        while (true) {
            if (isPageActive) {
                delay(BA_AP_REGEN_TICK_MS.milliseconds)
                office.applyRuntimeTick()
            } else {
                // Keep background overhead low on offscreen pager pages.
                delay(5_000.milliseconds)
            }
        }
    }

    LaunchedEffect(isPageActive, listState) {
        if (isPageActive) onUiNowMsChange(System.currentTimeMillis())
        while (true) {
            if (!isPageActive) {
                delay(3_000.milliseconds)
                continue
            }
            if (isPageActive && listState.isScrollInProgress) {
                delay(250.milliseconds)
                continue
            }
            delay(BA_UI_SECOND_TICK_MS.milliseconds)
            if (isPageActive && listState.isScrollInProgress) continue
            onUiNowMsChange(System.currentTimeMillis())
        }
    }

    LaunchedEffect(isPageActive, listState) {
        if (isPageActive) onUiMinuteMsChange(System.currentTimeMillis())
        while (true) {
            if (!isPageActive) {
                delay(30_000.milliseconds)
                continue
            }
            if (isPageActive && listState.isScrollInProgress) {
                delay(BA_UI_SECOND_TICK_MS.milliseconds)
                continue
            }
            delay(baUiMinuteTickDelayMs().milliseconds)
            if (isPageActive && listState.isScrollInProgress) continue
            onUiMinuteMsChange(System.currentTimeMillis())
        }
    }

    LaunchedEffect(isPageActive, listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { scrolling ->
                if (isPageActive && !scrolling) {
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

    LaunchedEffect(office.idNickname) {
        if (office.idNicknameInput != office.idNickname) office.idNicknameInput = office.idNickname
    }

    LaunchedEffect(office.idFriendCode) {
        if (office.idFriendCodeInput != office.idFriendCode) office.idFriendCodeInput = office.idFriendCode
    }

    LaunchedEffect(serverIndex) {
        onServerChanged()
    }

    LaunchedEffect(context, office) {
        val notificationContext = context.applicationContext
        snapshotFlow {
            BaApNotificationSyncRequest(
                currentDisplay = displayAp(office.apCurrent),
                limitDisplay = office.apLimit.coerceIn(0, BA_AP_LIMIT_MAX),
                thresholdDisplay = office.apNotifyThreshold.coerceIn(0, BA_AP_MAX),
                notifyEnabled = office.apNotifyEnabled,
            )
        }
            .distinctUntilChanged()
            .collectLatest { request ->
                delay(250.milliseconds)
                BaApNotificationSyncCoordinator.sync(
                    context = notificationContext,
                    office = office,
                    request = request,
                )
            }
    }
}
