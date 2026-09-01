package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.ba.card.filterVisibleCalendarEntries
import os.kei.ui.page.main.ba.card.filterVisiblePoolEntries
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.AppEdgeStackListTopInset
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState

/**
 * Both lists side by side, under one server panel.
 *
 * On a tablet the tab bar the phone needs is redundant: there is room to show the calendar and the banners
 * at once, and switching between two things you can already both see is a control that does nothing. The
 * server panel stays single because one server selection drives both lists — two pickers would be two
 * spellings of one choice.
 */
@Composable
internal fun BaCalendarPoolTwoColumnLayout(
    innerPadding: PaddingValues,
    primaryState: LazyListState,
    secondaryState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    backdrop: Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    syncText: String,
    syncTextColor: Color,
    showServerPopup: Boolean,
    serverPopupAnchorBounds: IntRect?,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    primary: LazyListScope.() -> Unit,
    secondary: LazyListScope.() -> Unit,
) {
    // The stacked shape hangs its server panel above the list, so the top bar's inset is applied there and
    // the stack line is the list's own constant. Here the panel is the two columns' header and lives inside
    // the keep-alive box, so the inset has to be folded into the stack line instead -- otherwise the line
    // sits at the window's top edge and the panel is drawn behind the top bar.
    val stackLine = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap
    val edgeStackState = rememberAppEdgeStackState(stackLine = stackLine)
    CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
        AppEdgeStackKeepAlive(
            state = edgeStackState,
            modifier = Modifier.fillMaxSize(),
        ) {
            AppPageTwoColumnLists(
                innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                primaryState = primaryState,
                secondaryState = secondaryState,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                bottomExtra = 40.dp,
                topExtra = appEdgeStackKeepAliveTopPadding(stackLine),
                sectionSpacing = 14.dp,
                header = {
                    BaCalendarPoolServerPanel(
                        backdrop = backdrop,
                        serverOptions = serverOptions,
                        serverIndex = serverIndex,
                        syncText = syncText,
                        syncTextColor = syncTextColor,
                        expanded = showServerPopup,
                        anchorBounds = serverPopupAnchorBounds,
                        onExpandedChange = onServerPopupChange,
                        onAnchorBoundsChange = onServerPopupAnchorBoundsChange,
                        onServerSelected = onServerSelected,
                    )
                },
                primary = primary,
                secondary = secondary,
            )
        }
    }
}

@Composable
internal fun BaCalendarPoolStackedLayout(
    innerPadding: PaddingValues,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    backdrop: Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    syncText: String,
    syncTextColor: Color,
    showServerPopup: Boolean,
    serverPopupAnchorBounds: IntRect?,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val edgeStackState = rememberAppEdgeStackState(stackLine = AppEdgeStackListTopInset)

    Column(modifier = Modifier.fillMaxSize()) {
        // Server selection is the stable page anchor. Data cards scroll and stack below it.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = appPageEdgePaddingStart(),
                        end = appPageEdgePaddingEnd(),
                        top = innerPadding.calculateTopPadding() + AppChromeTokens.topBarToHeaderGap,
                    ),
        ) {
            BaCalendarPoolServerPanel(
                backdrop = backdrop,
                serverOptions = serverOptions,
                serverIndex = serverIndex,
                syncText = syncText,
                syncTextColor = syncTextColor,
                expanded = showServerPopup,
                anchorBounds = serverPopupAnchorBounds,
                onExpandedChange = onServerPopupChange,
                onAnchorBoundsChange = onServerPopupAnchorBoundsChange,
                onServerSelected = onServerSelected,
            )
        }

        CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
            // The keep-alive box owns the stack container now, and the list is shifted up inside it, so
            // `appEdgeStackContainer` must not also sit on the list — that is what keeps the stack line
            // measured from the visible top edge rather than from the hidden one.
            AppEdgeStackKeepAlive(
                state = edgeStackState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                AppPageLazyColumn(
                    innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding()),
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                    bottomExtra = 40.dp,
                    // The headroom is invisible viewport above the list, so the content inset has to
                    // absorb it or the first card would start that far off screen.
                    topExtra = appEdgeStackKeepAliveTopPadding(AppEdgeStackListTopInset),
                    sectionSpacing = 14.dp,
                    content = content,
                )
            }
        }
    }
}

/**
 * The two lanes' contents, filtered against one clock.
 *
 * The phone shape filters each list inside its own wrapper because only one of them is composed at a
 * time. Here both are, so they share a single minute tick: two timers would drift a minute apart and
 * show two different "ends in" answers for the same instant, side by side.
 */
@Composable
internal fun BaCalendarPoolBothColumnsContent(
    innerPadding: PaddingValues,
    calendarListState: LazyListState,
    poolListState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    backdrop: Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    showServerPopup: Boolean,
    serverPopupAnchorBounds: IntRect?,
    showEndedActivities: Boolean,
    showEndedPools: Boolean,
    showCalendarPoolImages: Boolean,
    calendarUiState: BaCalendarUiState,
    poolUiState: BaPoolUiState,
    syncText: String,
    syncTextColor: Color,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenCalendarLink: (String) -> Unit,
) {
    val calendarEntries = calendarUiState.entries
    val poolEntries = poolUiState.entries
    val nowMs =
        rememberBaMinuteTickMs(
            enabled =
                (!calendarUiState.loading && calendarEntries.isNotEmpty()) ||
                    (!poolUiState.loading && poolEntries.isNotEmpty()),
        )
    val visibleCalendarEntries =
        remember(calendarEntries, showEndedActivities, nowMs) {
            filterVisibleCalendarEntries(
                entries = calendarEntries,
                showEndedActivities = showEndedActivities,
                nowMs = nowMs,
            )
        }
    val visiblePoolEntries =
        remember(poolEntries, showEndedPools, nowMs) {
            filterVisiblePoolEntries(
                entries = poolEntries,
                showEndedPools = showEndedPools,
                nowMs = nowMs,
            )
        }

    BaCalendarPoolTwoColumnLayout(
        innerPadding = innerPadding,
        primaryState = calendarListState,
        secondaryState = poolListState,
        nestedScrollConnection = nestedScrollConnection,
        backdrop = backdrop,
        serverOptions = serverOptions,
        serverIndex = serverIndex,
        syncText = syncText,
        syncTextColor = syncTextColor,
        showServerPopup = showServerPopup,
        serverPopupAnchorBounds = serverPopupAnchorBounds,
        onServerPopupChange = onServerPopupChange,
        onServerPopupAnchorBoundsChange = onServerPopupAnchorBoundsChange,
        onServerSelected = onServerSelected,
        primary = {
            baActivityCalendarEntryItems(
                backdrop = backdrop,
                serverIndex = serverIndex,
                visibleEntries = visibleCalendarEntries,
                loading = calendarUiState.loading,
                refreshing = calendarUiState.refreshing,
                error = calendarUiState.error,
                showEndedActivities = showEndedActivities,
                showCalendarPoolImages = showCalendarPoolImages,
                nowMs = nowMs,
                syncTextColor = syncTextColor,
                onOpenCalendarLink = onOpenCalendarLink,
            )
        },
        secondary = {
            baPoolEntryItems(
                backdrop = backdrop,
                serverIndex = serverIndex,
                visibleEntries = visiblePoolEntries,
                loading = poolUiState.loading,
                refreshing = poolUiState.refreshing,
                error = poolUiState.error,
                showEndedPools = showEndedPools,
                showCalendarPoolImages = showCalendarPoolImages,
                nowMs = nowMs,
                syncTextColor = syncTextColor,
                onOpenPoolStudentGuide = onOpenPoolStudentGuide,
                onOpenCalendarLink = onOpenCalendarLink,
            )
        },
    )
}
