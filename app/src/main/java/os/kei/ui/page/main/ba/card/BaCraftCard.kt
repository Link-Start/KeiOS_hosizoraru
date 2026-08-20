package os.kei.ui.page.main.ba.card

import androidx.compose.runtime.Composable
import os.kei.ui.page.main.widget.status.StatusPill
import os.kei.ui.page.main.widget.status.AppStatusColors
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.core.AppStatusPillSize
import os.kei.ui.page.main.ba.support.BA_CRAFT_SLOT_COUNT
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.ba.BaLiquidCard
import os.kei.ui.page.main.ba.BaPageClockState
import os.kei.ui.page.main.ba.support.BaCraftState
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoSeconds
import os.kei.ui.page.main.ba.support.formatBaRemainingTime
import os.kei.ui.page.main.ba.support.summary
import os.kei.ui.testing.KeiOsTestTags
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The Craft Chamber at a glance, in the shape [BaApCard] uses for AP.
 *
 * It replaced a fold. The chamber used to be one card with six rows inside it, and the fold existed
 * because six rows was the tallest thing on the page — but a fold only hides the cost while it is shut,
 * and the rows cost frames whenever it was open: twelve nested glass surfaces, measured at **+16.6ms of
 * RenderThread** (`docs/planning/ba-craft-card-frame-cost.md`).
 *
 * Each slot is now its own card in the list, so they are composed one at a time and stack into the pile
 * like every other card. That leaves this: an overview, with no disclosure of its own, saying what the
 * six of them add up to — how many are running, how many are waiting to be collected, and when the next
 * one lands. Exactly the summary the fold's header used to carry, now standing on its own.
 *
 * The countdown reads [BaPageClockState.uiMinuteMs], the page's existing minute ticker. Nothing here
 * starts a clock of its own: a craft is 30 minutes at the shortest, so a per-second tick would buy
 * nothing but wakeups.
 */
@Composable
internal fun BaCraftOverviewCard(
    backdrop: Backdrop?,
    clockState: BaPageClockState,
    craft: BaCraftState,
) {
    val uiNowMs = clockState.uiMinuteMs.longValue
    val accentAmber = Color(0xFFFBBF24)
    val countdownBlue = Color(0xFF60A5FA)
    val notSyncedText = stringResource(R.string.ba_state_not_synced)
    val summary = craft.summary(uiNowMs)
    val nextAtMs = summary.nextCompletionAtMs

    BaLiquidCard(
        backdrop = backdrop,
        accentColor = accentAmber,
        accentAlpha = 0f,
    ) {
        BaCardHeader(
            title = stringResource(R.string.ba_craft_title),
            modifier = Modifier.testTag(KeiOsTestTags.BaCraftCardHeader),
            // Three counts as three pills, the way the cafe card carries its own header facts. They are
            // flat rather than glass on purpose: the same choice the slot cards' metadata pills make, and
            // for the same reason — a count is a tag, and a tag does not need its own offscreen layer.
            trailing = {
                if (summary.runningCount > 0) {
                    BaCraftCountPill(
                        label = stringResource(R.string.ba_craft_pill_running_format, summary.runningCount),
                        color = countdownBlue,
                    )
                }
                if (summary.readyCount > 0) {
                    BaCraftCountPill(
                        label = stringResource(R.string.ba_craft_pill_ready_format, summary.readyCount),
                        color = AppStatusColors.Fresh,
                    )
                }
                val idleCount = BA_CRAFT_SLOT_TOTAL - summary.runningCount - summary.readyCount
                if (idleCount > 0) {
                    BaCraftCountPill(
                        label = stringResource(R.string.ba_craft_pill_idle_format, idleCount),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            },
        )

        BaCompactDualMetricPanel(
            backdrop = backdrop,
            firstLabel = stringResource(R.string.ba_craft_overview_next),
            firstValue =
                if (nextAtMs != null) {
                    formatBaRemainingTime(nextAtMs, uiNowMs)
                } else {
                    stringResource(R.string.ba_craft_slot_idle_countdown)
                },
            secondLabel = stringResource(R.string.ba_craft_overview_at),
            secondValue =
                if (nextAtMs != null) {
                    formatBaDateTimeNoSeconds(nextAtMs, notSyncedText)
                } else {
                    stringResource(R.string.ba_craft_slot_idle_countdown)
                },
            accentColor = accentAmber,
            valueColor = countdownBlue,
        )
    }
}

/** Every slot of both functions, which is what the overview's counts add up to. */
private const val BA_CRAFT_SLOT_TOTAL = BA_CRAFT_SLOT_COUNT * 2

/** A flat count tag, matching the slot cards' metadata pills rather than the glass time pill. */
@Composable
private fun BaCraftCountPill(
    label: String,
    color: Color,
) {
    CompositionLocalProvider(LocalLiquidParentBackdrop provides null) {
        StatusPill(
            label = label,
            color = color,
            size = AppStatusPillSize.Compact,
            backdrop = null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
