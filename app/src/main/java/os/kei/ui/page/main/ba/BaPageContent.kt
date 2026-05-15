package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.ba.card.BaCafeCard
import os.kei.ui.page.main.ba.card.BaOverviewCard
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.widget.chrome.AppChromeTokens

internal data class BaPageContentState(
    val isPageActive: Boolean,
    val officeOverviewTitle: String,
    val officeState: BaOfficeState,
    val uiNowMs: Long,
    val uiMinuteMs: Long,
    val serverOptions: List<String>,
    val cafeLevelOptions: List<Int>,
    val serverIndex: Int,
    val showOverviewServerPopup: Boolean,
    val showCafeLevelPopup: Boolean,
    val overviewServerPopupAnchorBounds: IntRect?,
    val cafeLevelPopupAnchorBounds: IntRect?,
    val baCalendarEntries: List<BaCalendarEntry>,
    val baCalendarLoading: Boolean,
    val baCalendarError: String?,
    val baCalendarLastSyncMs: Long,
    val showEndedActivities: Boolean,
    val showCalendarPoolImages: Boolean,
    val baPoolEntries: List<BaPoolEntry>,
    val baPoolLoading: Boolean,
    val baPoolError: String?,
    val baPoolLastSyncMs: Long,
    val showEndedPools: Boolean,
)

internal data class BaPageContentActions(
    val onApCurrentInputChange: (String) -> Unit,
    val onApCurrentDone: () -> Unit,
    val onApLimitInputChange: (String) -> Unit,
    val onApLimitDone: () -> Unit,
    val onOverviewServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    val onOverviewServerPopupChange: (Boolean) -> Unit,
    val onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    val onCafeLevelPopupChange: (Boolean) -> Unit,
    val onCafeLevelChange: (Int) -> Unit,
    val onServerSelected: (Int) -> Unit,
    val onClaimCafeStoredAp: () -> Unit,
    val onTouchHead: () -> Unit,
    val onForceResetHeadpatCooldown: () -> Unit,
    val onUseInviteTicket1: () -> Unit,
    val onForceResetInviteTicket1Cooldown: () -> Unit,
    val onUseInviteTicket2: () -> Unit,
    val onForceResetInviteTicket2Cooldown: () -> Unit,
    val onRefreshCalendar: () -> Unit,
    val onOpenCalendarLink: (String) -> Unit,
    val onRefreshPool: () -> Unit,
    val onOpenPoolStudentGuide: (String) -> Unit,
    val onOpenGuideCatalog: () -> Unit,
    val onIdNicknameInputChange: (String) -> Unit,
    val onSaveIdNickname: () -> Unit,
    val onIdFriendCodeInputChange: (String) -> Unit,
    val onSaveIdFriendCode: () -> Unit,
)

@Composable
internal fun BaPageContent(
    backdrop: Backdrop?,
    innerPadding: PaddingValues,
    contentBottomPadding: Dp,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    state: BaPageContentState,
    actions: BaPageContentActions,
) {
    val pageGap = AppChromeTokens.pageSectionGap
    val topBarToHeaderGap = AppChromeTokens.topBarToHeaderGap
    val pageHorizontalPadding = AppChromeTokens.pageHorizontalPadding

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        state = listState,
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + topBarToHeaderGap,
            bottom = innerPadding.calculateBottomPadding() + contentBottomPadding + pageGap,
            start = pageHorizontalPadding,
            end = pageHorizontalPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(pageGap),
    ) {
        item {
            BaOverviewCard(
                backdrop = backdrop,
                overviewTitle = state.officeOverviewTitle,
                idFriendCode = state.officeState.idFriendCode,
                idNicknameInput = state.officeState.idNicknameInput,
                onIdNicknameInputChange = actions.onIdNicknameInputChange,
                onSaveIdNickname = actions.onSaveIdNickname,
                idFriendCodeInput = state.officeState.idFriendCodeInput,
                onIdFriendCodeInputChange = actions.onIdFriendCodeInputChange,
                onSaveIdFriendCode = actions.onSaveIdFriendCode,
                uiNowMs = state.uiNowMs,
                uiMinuteMs = state.uiMinuteMs,
                apSyncMs = state.officeState.apSyncMs,
                apLimit = state.officeState.apLimit,
                apCurrent = state.officeState.apCurrent,
                apRegenBaseMs = state.officeState.apRegenBaseMs,
                apCurrentInput = state.officeState.apCurrentInput,
                onApCurrentInputChange = actions.onApCurrentInputChange,
                onApCurrentDone = actions.onApCurrentDone,
                apLimitInput = state.officeState.apLimitInput,
                onApLimitInputChange = actions.onApLimitInputChange,
                onApLimitDone = actions.onApLimitDone,
                cafeStoredAp = state.officeState.cafeStoredAp,
                cafeLevel = state.officeState.cafeLevel,
                serverOptions = state.serverOptions,
                serverIndex = state.serverIndex,
                showOverviewServerPopup = state.showOverviewServerPopup,
                overviewServerPopupAnchorBounds = state.overviewServerPopupAnchorBounds,
                onOverviewServerPopupAnchorBoundsChange = actions.onOverviewServerPopupAnchorBoundsChange,
                onOverviewServerPopupChange = actions.onOverviewServerPopupChange,
                onServerSelected = actions.onServerSelected,
                onClaimCafeStoredAp = actions.onClaimCafeStoredAp,
                onOpenGuideCatalog = actions.onOpenGuideCatalog,
            )
        }

        item {
            BaCafeCard(
                backdrop = backdrop,
                uiNowMs = state.uiMinuteMs,
                serverIndex = state.serverIndex,
                cafeLevel = state.officeState.cafeLevel,
                cafeLevelOptions = state.cafeLevelOptions,
                showCafeLevelPopup = state.showCafeLevelPopup,
                cafeLevelPopupAnchorBounds = state.cafeLevelPopupAnchorBounds,
                onCafeLevelPopupAnchorBoundsChange = actions.onCafeLevelPopupAnchorBoundsChange,
                onCafeLevelPopupChange = actions.onCafeLevelPopupChange,
                onCafeLevelChange = actions.onCafeLevelChange,
                coffeeHeadpatMs = state.officeState.coffeeHeadpatMs,
                coffeeInvite1UsedMs = state.officeState.coffeeInvite1UsedMs,
                coffeeInvite2UsedMs = state.officeState.coffeeInvite2UsedMs,
                onTouchHead = actions.onTouchHead,
                onForceResetHeadpatCooldown = actions.onForceResetHeadpatCooldown,
                onUseInviteTicket1 = actions.onUseInviteTicket1,
                onForceResetInviteTicket1Cooldown = actions.onForceResetInviteTicket1Cooldown,
                onUseInviteTicket2 = actions.onUseInviteTicket2,
                onForceResetInviteTicket2Cooldown = actions.onForceResetInviteTicket2Cooldown,
            )
        }
    }
}
