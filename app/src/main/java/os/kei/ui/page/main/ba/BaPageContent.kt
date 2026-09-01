@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.ba.card.BaCooldownSlotCard
import os.kei.ui.page.main.ba.card.BaCraftOverviewCard
import os.kei.ui.page.main.ba.card.BaCraftSlotCard
import os.kei.ui.page.main.ba.support.BA_CRAFT_SLOT_COUNT
import os.kei.ui.page.main.ba.support.calculateInviteTicketAvailableMs
import os.kei.ui.page.main.ba.support.calculateNextHeadpatAvailableMs
import os.kei.ui.page.main.ba.support.slotAt
import os.kei.ui.testing.KeiOsTestTags
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import os.kei.ui.page.main.widget.glass.rememberAppEdgeStackState
import os.kei.ui.page.main.widget.glass.appEdgeStackKeepAliveTopPadding
import os.kei.ui.page.main.widget.glass.LocalAppEdgeStackCards
import os.kei.ui.page.main.widget.glass.AppEdgeStackKeepAlive
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import os.kei.ui.page.main.ba.card.BaAccountPagerCard
import os.kei.ui.page.main.ba.card.BaApCard
import os.kei.ui.page.main.ba.card.BaCafeCard
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaCraftFunction
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingStart
import os.kei.ui.page.main.widget.chrome.appPageEdgePaddingEnd
import os.kei.ui.page.main.widget.chrome.AppPageTwoColumnLists

@Immutable
internal data class BaPageContentState(
    val officeState: BaOfficeState,
    val clockState: BaPageClockState,
    val accountUiState: BaOfficeAccountUiState,
    val serverOptions: List<String>,
    val cafeLevelOptions: List<Int>,
    val serverIndex: Int,
    val showCafeLevelPopup: Boolean,
    val cafeLevelPopupAnchorBounds: IntRect?,
    val baCalendarEntries: List<BaCalendarEntry>,
    val baCalendarLoading: Boolean,
    val baCalendarRefreshing: Boolean,
    val baCalendarError: String?,
    val baCalendarLastSyncMs: Long,
    val showEndedActivities: Boolean,
    val showCalendarPoolImages: Boolean,
    val baPoolEntries: List<BaPoolEntry>,
    val baPoolLoading: Boolean,
    val baPoolRefreshing: Boolean,
    val baPoolError: String?,
    val baPoolLastSyncMs: Long,
    val showEndedPools: Boolean,
    val craftCardExpanded: Boolean,
)

@Stable
internal data class BaPageContentActions(
    val onApCurrentInputChange: (String) -> Unit,
    val onApCurrentDone: () -> Unit,
    val onOpenApLimitTools: () -> Unit,
    val onCafeStoredApInputChange: (String) -> Unit,
    val onCafeStoredApDone: () -> Unit,
    val onOpenCafeApTools: () -> Unit,
    val onCafeLevelPopupAnchorBoundsChange: (IntRect?) -> Unit,
    val onCafeLevelPopupChange: (Boolean) -> Unit,
    val onAccountSelected: (BaAccountId) -> Unit,
    val onEditAccount: (BaAccountId) -> Unit,
    val onCafeLevelChange: (Int) -> Unit,
    val onClaimCafeStoredAp: () -> Unit,
    val onTouchHead: () -> Unit,
    val onEditHeadpatCooldown: () -> Unit,
    val onUseInviteTicket1: () -> Unit,
    val onEditInviteTicket1Cooldown: () -> Unit,
    val onUseInviteTicket2: () -> Unit,
    val onEditInviteTicket2Cooldown: () -> Unit,
    val onConfigureCraftSlot: (BaCraftFunction, Int) -> Unit,
    val onClearCraftSlot: (BaCraftFunction, Int) -> Unit,
    val onCraftCardExpandedChange: (Boolean) -> Unit,
    val onRefreshCalendar: () -> Unit,
    val onOpenCalendarLink: (String) -> Unit,
    val onRefreshPool: () -> Unit,
    val onOpenPoolStudentGuide: (String) -> Unit,
)

internal enum class BaPageContentType {
    Account,
    Ap,
    Cafe,

    /** Every cooldown card is the same shape, so they share a recycling pool. */
    CooldownSlot,
    Craft,

    /** Likewise every craft slot card. */
    CraftSlot,
}

@Composable
internal fun BaPageContent(
    topBarBackdrop: LayerBackdrop,
    backdrop: Backdrop?,
    innerPadding: PaddingValues,
    contentBottomPadding: Dp,
    listState: LazyListState,
    secondaryListState: LazyListState,
    columnCount: Int,
    nestedScrollConnection: NestedScrollConnection,
    state: BaPageContentState,
    actions: BaPageContentActions,
) {
    val pageGap = AppChromeTokens.pageSectionGap
    val topBarToHeaderGap = AppChromeTokens.topBarToHeaderGap
    // This page builds its own LazyColumn rather than going through AppPageLazyColumn, so it takes the
    // large-screen edge padding directly. Identical to `AppChromeTokens.pageHorizontalPadding` on a phone.
    val pageStartPadding = appPageEdgePaddingStart()
    val pageEndPadding = appPageEdgePaddingEnd()
    // Which slot cards are open, for this visit only. Held here rather than in the page state because it
    // is presentation with no consumer outside this list, and a `remember` at the list's own level gives
    // exactly the wanted lifetime: it survives scrolling, and re-entering the page starts compact again.
    val openSlotCards = remember { mutableStateMapOf<String, Unit>() }

    // The office cards stack into the top edge as they leave, the way the GitHub list and BA's own
    // calendar and pool pages already do. The stack line is where a card comes to rest under the top
    // bar, so it is the list's own top inset rather than a constant.
    val stackLine = innerPadding.calculateTopPadding() + topBarToHeaderGap
    val edgeStackState = rememberAppEdgeStackState(stackLine = stackLine)

    // The keep-alive box is what makes the pile more than one card deep: a pinned card keeps travelling
    // upward with the list and the lazy container disposes it once it leaves the viewport, so the list
    // gets extra viewport *above* the visible top and is clipped back to it. The box owns the stack
    // container, never the shifted list — see AppEdgeStackKeepAlive.
    AppEdgeStackKeepAlive(
        state = edgeStackState,
        modifier =
            Modifier
                .fillMaxSize()
                // The top bar samples this, and it has to sample what is *visible*: on the shifted list
                // the recorded layer would start a headroom above the screen and the bar would refract
                // content from off screen.
                .layerBackdrop(topBarBackdrop),
    ) {
        CompositionLocalProvider(LocalAppEdgeStackCards provides edgeStackState) {
    if (columnCount >= 2) {
        AppPageTwoColumnLists(
            innerPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + contentBottomPadding),
            primaryState = listState,
            secondaryState = secondaryListState,
            modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
            topExtra = appEdgeStackKeepAliveTopPadding(stackLine),
            bottomExtra = pageGap,
            sectionSpacing = pageGap,
            // The account switcher belongs to the page, not to a lane: it names whose office this is, and
            // it is a horizontal pager, so halving it would leave the accounts swiped through in a
            // column-wide strip beside the cards they select for.
            header = {
                BaPageCardContent(
                    card = BaPageCard.Account,
                    backdrop = backdrop,
                    state = state,
                    actions = actions,
                    openCards = openSlotCards,
                )
            },
            primary = { baPageCardLane(BaPrimaryLaneGroups, backdrop, state, actions, openSlotCards, pageGap) },
            secondary = { baPageCardLane(BaSecondaryLaneGroups, backdrop, state, actions, openSlotCards, pageGap) },
        )
    } else {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
            state = listState,
            contentPadding =
                PaddingValues(
                    // The headroom is invisible viewport above the list, so the inset has to absorb it or
                    // the first card would start that far off screen.
                    top = appEdgeStackKeepAliveTopPadding(stackLine),
                    bottom = innerPadding.calculateBottomPadding() + contentBottomPadding + pageGap,
                    start = pageStartPadding,
                    end = pageEndPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(pageGap),
        ) {
            BaPageCards.forEach { card ->
                item(key = card.key, contentType = card.contentType) {
                    BaPageCardContent(
                        card = card,
                        backdrop = backdrop,
                        state = state,
                        actions = actions,
                        openCards = openSlotCards,
                    )
                }
            }
        }
    }
        }
    }
}

/**
 * One card in the office list, as data.
 *
 * Pure values — objects and a pair of primitives — so the identity a lazy container keys and skips on is
 * stable across recompositions. The alternative, a list of composable lambdas built in composition, would
 * hand every item a new lambda every frame and none of the eighteen cards could ever skip.
 */
internal sealed interface BaPageCard {
    val key: String
    val contentType: BaPageContentType

    data object Account : BaPageCard {
        override val key: String = "ba-account"
        override val contentType: BaPageContentType = BaPageContentType.Account
    }

    data object Ap : BaPageCard {
        override val key: String = "ba-ap"
        override val contentType: BaPageContentType = BaPageContentType.Ap
    }

    data object Cafe : BaPageCard {
        override val key: String = "ba-cafe"
        override val contentType: BaPageContentType = BaPageContentType.Cafe
    }

    data class Cooldown(val kind: BaCooldownKind) : BaPageCard {
        override val key: String = kind.key
        override val contentType: BaPageContentType = BaPageContentType.CooldownSlot
    }

    data object CraftOverview : BaPageCard {
        override val key: String = "ba-craft"
        override val contentType: BaPageContentType = BaPageContentType.Craft
    }

    data class CraftSlot(val function: BaCraftFunction, val index: Int) : BaPageCard {
        override val key: String = "ba-craft-${function.name}-$index"
        override val contentType: BaPageContentType = BaPageContentType.CraftSlot
    }
}

/** The three cafe cooldowns, as an address rather than a built entry. */
internal enum class BaCooldownKind(val key: String) {
    Headpat("ba-cooldown-headpat"),
    InviteTicket1("ba-cooldown-invite-1"),
    InviteTicket2("ba-cooldown-invite-2"),
}

/**
 * What the two-column layout actually packs: a *group* of cards, not a card.
 *
 * A staggered grid places by height, and left to itself it split the office in ways the page is not read
 * in — Cafe landing under Headpat, Invite Ticket 1 in the other column from Invite Ticket 2. The two AP
 * pools are one reading ("how much do I have, in both places"), and the three cafe cooldowns are one set;
 * a teacher scans them together and the game presents them together. So they travel as units and the grid
 * packs those, which keeps the flow for everything genuinely independent — the craft slots — while the
 * groups keep their own lane.
 *
 * The single-column path does **not** use this. On a phone the cards are already in one lane and each is
 * its own list item, so grouping there would only cost laziness and change keys for no visible gain.
 */
private class BaPageCardGroup(
    val key: String,
    val contentType: BaPageContentType,
    val cards: List<BaPageCard>,
)

/** One lane: its groups in order, each group's cards kept together inside one list item. */
private fun LazyListScope.baPageCardLane(
    groups: List<BaPageCardGroup>,
    backdrop: Backdrop?,
    state: BaPageContentState,
    actions: BaPageContentActions,
    openCards: MutableMap<String, Unit>,
    cardGap: Dp,
) {
    groups.forEach { group ->
        item(key = group.key, contentType = group.contentType) {
            Column(verticalArrangement = Arrangement.spacedBy(cardGap)) {
                group.cards.forEach { card ->
                    BaPageCardContent(
                        card = card,
                        backdrop = backdrop,
                        state = state,
                        actions = actions,
                        openCards = openCards,
                    )
                }
            }
        }
    }
}

/**
 * The office list in one column, in the game's own reading order.
 *
 * The phone's shape, and it stays flat: each card is its own list item, exactly as it was before there were
 * lanes at all.
 */
private val BaPageCards: List<BaPageCard> =
    buildList {
        add(BaPageCard.Account)
        add(BaPageCard.Ap)
        add(BaPageCard.Cafe)
        BaCooldownKind.entries.forEach { kind -> add(BaPageCard.Cooldown(kind)) }
        add(BaPageCard.CraftOverview)
        BaCraftFunction.entries.forEach { function ->
            (0 until BA_CRAFT_SLOT_COUNT).forEach { index ->
                add(BaPageCard.CraftSlot(function = function, index = index))
            }
        }
    }

/**
 * The first column: the two AP pools, then the Generate craft slots.
 *
 * Lanes are assigned, not packed. Two columns that scroll apart have no shared baseline to pack against,
 * and the assignment has to hold still anyway — a teacher reads AP beside Cafe and expects them to stay
 * beside each other whether or not one is expanded.
 */
private val BaPrimaryLaneGroups: List<BaPageCardGroup> =
    buildList {
        add(
            BaPageCardGroup(
                key = "ba-group-pools",
                contentType = BaPageContentType.Ap,
                // One reading: how much do I have, in both places.
                cards = listOf(BaPageCard.Ap, BaPageCard.Cafe),
            ),
        )
        add(craftFunctionGroup(BaCraftFunction.Generate))
    }

/** The second column: the three cafe cooldowns, the Craft Chamber summary, then the Fusion slots. */
private val BaSecondaryLaneGroups: List<BaPageCardGroup> =
    buildList {
        add(
            BaPageCardGroup(
                key = "ba-group-cooldowns",
                contentType = BaPageContentType.CooldownSlot,
                // One set: a teacher scans the three together and the game presents them together.
                cards = BaCooldownKind.entries.map { kind -> BaPageCard.Cooldown(kind) },
            ),
        )
        add(
            BaPageCardGroup(
                key = "ba-group-craft",
                contentType = BaPageContentType.Craft,
                cards = listOf(BaPageCard.CraftOverview),
            ),
        )
        add(craftFunctionGroup(BaCraftFunction.Fusion))
    }

/**
 * All six slots of one craft function, together.
 *
 * The slots are numbered, so splitting a function across columns would read its numbers out of order.
 * Generate takes one column and Fusion the other, each counting 1-6 down its own lane.
 */
private fun craftFunctionGroup(function: BaCraftFunction): BaPageCardGroup =
    BaPageCardGroup(
        key = "ba-group-craft-${function.name}",
        contentType = BaPageContentType.CraftSlot,
        cards =
            (0 until BA_CRAFT_SLOT_COUNT).map { index ->
                BaPageCard.CraftSlot(function = function, index = index)
            },
    )

/** One office card, without the list item around it. */
@Composable
private fun BaPageCardContent(
    card: BaPageCard,
    backdrop: Backdrop?,
    state: BaPageContentState,
    actions: BaPageContentActions,
    openCards: MutableMap<String, Unit>,
) {
    when (card) {
        BaPageCard.Account ->
            BaAccountPagerCard(
                backdrop = backdrop,
                accounts = state.accountUiState.accounts,
                activeAccountId = state.accountUiState.activeAccountId,
                serverOptions = state.serverOptions,
                onAccountSelected = actions.onAccountSelected,
                onEditAccount = actions.onEditAccount,
            )

        BaPageCard.Ap ->
            BaApCard(
                backdrop = backdrop,
                clockState = state.clockState,
                apSyncMs = state.officeState.apSyncMs,
                apLimit = state.officeState.apLimit,
                apCurrent = state.officeState.apCurrent,
                apRegenBaseMs = state.officeState.apRegenBaseMs,
                apCurrentInput = state.officeState.apCurrentInput,
                onApCurrentInputChange = actions.onApCurrentInputChange,
                onApCurrentDone = actions.onApCurrentDone,
                onOpenApLimitTools = actions.onOpenApLimitTools,
            )

        BaPageCard.Cafe ->
            BaCafeCard(
                backdrop = backdrop,
                clockState = state.clockState,
                serverIndex = state.serverIndex,
                cafeLevel = state.officeState.cafeLevel,
                cafeStoredAp = state.officeState.cafeStoredAp,
                cafeLastHourMs = state.officeState.cafeLastHourMs,
                cafeStoredApInput = state.officeState.cafeStoredApInput,
                cafeLevelOptions = state.cafeLevelOptions,
                showCafeLevelPopup = state.showCafeLevelPopup,
                cafeLevelPopupAnchorBounds = state.cafeLevelPopupAnchorBounds,
                onCafeLevelPopupAnchorBoundsChange = actions.onCafeLevelPopupAnchorBoundsChange,
                onCafeLevelPopupChange = actions.onCafeLevelPopupChange,
                onCafeLevelChange = actions.onCafeLevelChange,
                onCafeStoredApInputChange = actions.onCafeStoredApInputChange,
                onCafeStoredApDone = actions.onCafeStoredApDone,
                onOpenCafeApTools = actions.onOpenCafeApTools,
                onClaimCafeStoredAp = actions.onClaimCafeStoredAp,
            )

        is BaPageCard.Cooldown ->
            BaCooldownCard(
                backdrop = backdrop,
                entry = baCooldownEntry(kind = card.kind, state = state, actions = actions),
                firstOfItsKind = card.kind == BaCooldownKind.entries.first(),
                state = state,
                openCards = openCards,
            )

        BaPageCard.CraftOverview ->
            BaCraftOverviewCard(
                backdrop = backdrop,
                clockState = state.clockState,
                craft = state.officeState.craft,
            )

        is BaPageCard.CraftSlot ->
            BaCraftSlotCardItem(
                backdrop = backdrop,
                function = card.function,
                index = card.index,
                state = state,
                actions = actions,
                openCards = openCards,
            )
    }
}

/**
 * The three cafe cooldowns, one card each.
 *
 * The maths lives here rather than in the card so the card stays a presentation of "is it ready, and
 * when" — the same shape the craft slots use — instead of learning two different cooldown rules.
 */
private fun baCooldownEntry(
    kind: BaCooldownKind,
    state: BaPageContentState,
    actions: BaPageContentActions,
): BaCooldownCardEntry {
    val office = state.officeState
    val serverIndex = state.serverIndex
    return when (kind) {
        BaCooldownKind.Headpat ->
            BaCooldownCardEntry(
                key = kind.key,
                titleRes = R.string.ba_cafe_action_headpat,
                iconRes = R.drawable.fx_tex_ch0071_prop_05_small,
                usedAtMs = office.coffeeHeadpatMs,
                availableAtOf = { used -> calculateNextHeadpatAvailableMs(used, serverIndex) },
                onUse = actions.onTouchHead,
                onEdit = actions.onEditHeadpatCooldown,
            )

        BaCooldownKind.InviteTicket1 ->
            BaCooldownCardEntry(
                key = kind.key,
                titleRes = R.string.ba_cafe_action_invite_ticket_1,
                iconRes = R.drawable.cafe_icon_coupon_small,
                usedAtMs = office.coffeeInvite1UsedMs,
                availableAtOf = ::calculateInviteTicketAvailableMs,
                onUse = actions.onUseInviteTicket1,
                onEdit = actions.onEditInviteTicket1Cooldown,
            )

        BaCooldownKind.InviteTicket2 ->
            BaCooldownCardEntry(
                key = kind.key,
                titleRes = R.string.ba_cafe_action_invite_ticket_2,
                iconRes = R.drawable.cafe_icon_coupon_small,
                usedAtMs = office.coffeeInvite2UsedMs,
                availableAtOf = ::calculateInviteTicketAvailableMs,
                onUse = actions.onUseInviteTicket2,
                onEdit = actions.onEditInviteTicket2Cooldown,
            )
    }
}

@Composable
private fun BaCooldownCard(
    backdrop: Backdrop?,
    entry: BaCooldownCardEntry,
    firstOfItsKind: Boolean,
    state: BaPageContentState,
    openCards: MutableMap<String, Unit>,
) {
    val uiNowMs = state.clockState.uiMinuteMs.longValue
    val availableAt = entry.availableAtOf(entry.usedAtMs)
    val ready = entry.usedAtMs <= 0L || availableAt <= uiNowMs
    val label = stringResource(entry.titleRes)
    BaCooldownSlotCard(
        backdrop = backdrop,
        title = label,
        iconRes = entry.iconRes,
        ready = ready,
        usedAtMs = entry.usedAtMs,
        // A ready cooldown's stored instant is in the past, and "available at some time yesterday"
        // is not what the teacher wants to read; now is the honest answer.
        availableAtMs = if (ready) uiNowMs else availableAt,
        accentColor = BaSlotCardAccent.Cafe,
        countdownColor = BaSlotCardAccent.Countdown,
        uiNowMs = uiNowMs,
        expanded = openCards.containsKey(entry.key),
        onExpandedChange = { open ->
            if (open) openCards[entry.key] = Unit else openCards.remove(entry.key)
        },
        onUse = entry.onUse,
        onEditCooldown = entry.onEdit,
        actionLabel = label,
        // The profile journey's handle on a cooldown card; the first one is enough.
        cardTestTag = KeiOsTestTags.BaCooldownCardFirst.takeIf { firstOfItsKind },
        adjustTestTag = KeiOsTestTags.BaCooldownAdjustButton.takeIf { firstOfItsKind },
    )
}

/** One of the six Craft Chamber slots per function, in the game's own order. */
@Composable
private fun BaCraftSlotCardItem(
    backdrop: Backdrop?,
    function: BaCraftFunction,
    index: Int,
    state: BaPageContentState,
    actions: BaPageContentActions,
    openCards: MutableMap<String, Unit>,
) {
    val key = "ba-craft-${function.name}-$index"
    val uiNowMs = state.clockState.uiMinuteMs.longValue
    val first = function == BaCraftFunction.Generate && index == 0
    BaCraftSlotCard(
        backdrop = backdrop,
        title =
            stringResource(
                R.string.ba_craft_slot_button_format,
                stringResource(baCraftFunctionLabelRes(function)),
                // The game numbers its slots from one; do not leak the index.
                index + 1,
            ),
        function = function,
        slot = state.officeState.craft.slotAt(function, index),
        accentColor = BaSlotCardAccent.Craft,
        countdownColor = BaSlotCardAccent.Countdown,
        uiNowMs = uiNowMs,
        expanded = openCards.containsKey(key),
        onExpandedChange = { open ->
            if (open) openCards[key] = Unit else openCards.remove(key)
        },
        onConfigure = { actions.onConfigureCraftSlot(function, index) },
        onClear = { actions.onClearCraftSlot(function, index) },
        // The profile journey's way in: the first card is how it knows the section is open, and the
        // configure button inside it is how it reaches the craft sheet.
        cardTestTag = KeiOsTestTags.BaCraftSlotCardFirst.takeIf { first },
        buttonTestTag = KeiOsTestTags.BaCraftSlotFirst.takeIf { first },
    )
}

private class BaCooldownCardEntry(
    val key: String,
    val titleRes: Int,
    val iconRes: Int,
    val usedAtMs: Long,
    val availableAtOf: (Long) -> Long,
    val onUse: () -> Unit,
    val onEdit: () -> Unit,
)

/**
 * The accents the slot cards carry, kept beside the list that emits them.
 *
 * The cafe pink and craft amber were literals inside the two old cards; a card per slot means the same
 * two colours are now read from two files, so they live in one place rather than being re-typed.
 */
private object BaSlotCardAccent {
    val Cafe = Color(0xFFF472B6)
    val Craft = Color(0xFFFBBF24)
    val Countdown = Color(0xFF60A5FA)
}
