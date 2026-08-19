package os.kei.mcp.bridge

import os.kei.ui.page.main.ba.support.BaAccountProfile
import os.kei.ui.page.main.ba.support.BaCraftSummary
import os.kei.ui.page.main.ba.support.BaDailyDoneOutcome
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.calculateApFullAtMs
import os.kei.ui.page.main.ba.support.calculateInviteTicketAvailableMs
import os.kei.ui.page.main.ba.support.calculateNextHeadpatAvailableMs
import os.kei.ui.page.main.ba.support.cafeStorageCap
import os.kei.ui.page.main.ba.support.displayAp
import os.kei.ui.page.main.ba.support.gameKeeServerId
import os.kei.ui.page.main.ba.support.summary

/**
 * One account per line, in the `entry[i]=k:v | k:v` shape the calendar and pool texts already use.
 *
 * This exists because `keios.ba.snapshot` can only ever describe one account, and every account owns its
 * own AP, cafe, cooldowns and craft slots. Without it an assistant cannot answer "which of my accounts
 * needs attention" at all, only "how is the one I last looked at".
 *
 * Takes a resolved [snapshot] rather than building one, so it is a pure formatter and cannot repeat the
 * mistake of assembling an account snapshot that carries default globals — see
 * `BASettingsStore.loadSnapshotForAccount`.
 *
 * Times are absolute epoch millis rather than durations, so a slow round trip cannot turn a countdown
 * into a wrong answer. `tileSlot:-1` means no daily-done tile is bound.
 */
internal fun mcpBaAccountLine(
    profile: BaAccountProfile,
    snapshot: BaPageSnapshot,
    active: Boolean,
    tileSlot: Int?,
    index: Int,
    nowMs: Long,
): String {
    val serverIndex = profile.serverIndex.coerceIn(0, 2)
    val cafeCap = cafeStorageCap(snapshot.cafeLevel.coerceIn(1, 10))
    val craft: BaCraftSummary = snapshot.craft.summary(nowMs)
    return buildString {
        append("account[$index]=id:${profile.id.value}")
        append(" | name:${profile.displayName}")
        append(" | active:$active")
        append(" | enabled:${profile.enabled}")
        append(" | serverIndex:$serverIndex")
        append(" | gameKeeServerId:${gameKeeServerId(serverIndex)}")
        append(" | nickname:${profile.nickname}")
        append(" | friendCode:${profile.friendCode}")
        append(" | notificationMode:${profile.notificationMode.name}")
        append(" | remindersEnabled:${profile.remindersEnabled}")
        append(" | ap:${displayAp(snapshot.apCurrent)}/${snapshot.apLimit}")
        append(
            " | apFullAtMs:${
                calculateApFullAtMs(
                    apLimit = snapshot.apLimit,
                    apCurrent = snapshot.apCurrent,
                    apRegenBaseMs = snapshot.apRegenBaseMs,
                    nowMs = nowMs,
                )
            }",
        )
        append(" | cafeLevel:${snapshot.cafeLevel}")
        append(" | cafeAp:${snapshot.cafeStoredAp}/$cafeCap")
        append(
            " | headpatReadyAtMs:${
                calculateNextHeadpatAvailableMs(
                    lastHeadpatMs = snapshot.coffeeHeadpatMs,
                    serverIndex = serverIndex,
                )
            }",
        )
        append(" | invite1ReadyAtMs:${calculateInviteTicketAvailableMs(snapshot.coffeeInvite1UsedMs)}")
        append(" | invite2ReadyAtMs:${calculateInviteTicketAvailableMs(snapshot.coffeeInvite2UsedMs)}")
        append(" | craftRunning:${craft.runningCount}")
        append(" | craftReady:${craft.readyCount}")
        append(" | craftNextCompletionAtMs:${craft.nextCompletionAtMs ?: 0L}")
        append(" | tileSlot:${tileSlot ?: -1}")
    }
}

/**
 * What a dailies run did, or would do.
 *
 * The same line shape for both, with `applied` carrying the difference, so a preview and a commit can be
 * diffed against each other without a client having to parse two formats.
 */
internal fun mcpBaDailyDoneLine(
    accountName: String,
    accountId: String,
    outcome: BaDailyDoneOutcome,
    applied: Boolean,
): String =
    buildString {
        append("dailyDone[$accountId]=name:$accountName")
        append(" | applied:$applied")
        append(" | changed:${outcome.changedAnything}")
        append(" | apAdjusted:${outcome.apAdjusted}")
        append(" | cafeApCleared:${outcome.cafeApCleared}")
        append(" | headpatStarted:${outcome.headpatStarted}")
        append(" | invite1Started:${outcome.invite1Started}")
        append(" | invite2Started:${outcome.invite2Started}")
        append(" | craftSlotsStarted:${outcome.craftSlotsStarted}")
    }
