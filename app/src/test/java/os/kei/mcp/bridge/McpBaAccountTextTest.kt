package os.kei.mcp.bridge

import org.junit.Test
import os.kei.ui.page.main.ba.support.BA_DAILY_TILE_SLOTS
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountNotificationMode
import os.kei.ui.page.main.ba.support.BaAccountProfile
import os.kei.ui.page.main.ba.support.BaAccountRecord
import os.kei.ui.page.main.ba.support.BaAccountRuntime
import os.kei.ui.page.main.ba.support.BaAccountStoreSnapshot
import os.kei.ui.page.main.ba.support.BaCraftGrade
import os.kei.ui.page.main.ba.support.BaCraftSlot
import os.kei.ui.page.main.ba.support.BaCraftState
import os.kei.ui.page.main.ba.support.BaDailyDoneOutcome
import os.kei.ui.page.main.ba.support.BaDailyTileState
import os.kei.ui.page.main.ba.support.BaGlobalReminderSettings
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import os.kei.ui.page.main.ba.support.slotOf
import os.kei.ui.page.main.ba.support.withBaAccount
import os.kei.ui.page.main.ba.support.withSlot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val HOUR = 60L * 60L * 1000L
private const val START = 1_700_000_000_000L

/**
 * The multi-account MCP lines.
 *
 * `keios.ba.snapshot` can only ever describe one account, so these are what lets an assistant answer
 * "which of my accounts needs attention". Whole-field assertions rather than substring sniffing where
 * the value is the contract.
 */
class McpBaAccountTextTest {
    private fun account(
        id: String,
        name: String = id,
        serverIndex: Int = 2,
        enabled: Boolean = true,
        runtime: BaAccountRuntime = BaAccountRuntime(),
    ): BaAccountRecord =
        BaAccountRecord(
            profile =
                BaAccountProfile(
                    id = BaAccountId(id),
                    serverIndex = serverIndex,
                    displayName = name,
                    nickname = "$name Sensei",
                    friendCode = "FRIEND01",
                    enabled = enabled,
                ),
            runtime = runtime,
        )

    private fun state(
        accounts: List<BaAccountRecord>,
        activeId: String? = accounts.firstOrNull()?.profile?.id?.value,
    ): BaAccountStoreSnapshot =
        BaAccountStoreSnapshot(
            accounts = accounts,
            activeAccountId = activeId?.let(::BaAccountId),
            allAccountsFollowGlobalNotificationSettings = true,
            globalReminderSettings = BaGlobalReminderSettings(),
        )

    /**
     * Stands in for `BASettingsStore.loadSnapshotForAccount`, which needs an MMKV.
     *
     * The base carries a non-default global so the assertions can prove the line reads the snapshot it
     * was handed rather than rebuilding one — the bug this signature exists to prevent.
     */
    private fun snapshotFor(
        accountState: BaAccountStoreSnapshot,
        account: BaAccountRecord,
    ): BaPageSnapshot =
        BaPageSnapshot(craftCardExpanded = false)
            .withBaAccount(accountState = accountState, account = account)

    private fun line(
        accountState: BaAccountStoreSnapshot,
        account: BaAccountRecord,
        tileState: BaDailyTileState = BaDailyTileState(),
        index: Int = 0,
        nowMs: Long = START,
    ): String =
        mcpBaAccountLine(
            profile = account.profile,
            snapshot = snapshotFor(accountState, account),
            active = accountState.activeAccountId == account.profile.id,
            tileSlot = tileState.slotOf(account.profile.id),
            index = index,
            nowMs = nowMs,
        )

    @Test
    fun `each account reports its own runtime, not the active one's`() {
        val accounts =
            listOf(
                account("a", "Kei", runtime = BaAccountRuntime(apCurrent = 12.0, apLimit = 240)),
                account("b", "JP", serverIndex = 0, runtime = BaAccountRuntime(apCurrent = 99.0, apLimit = 180)),
            )
        val accountState = state(accounts, activeId = "a")

        val first = line(accountState, accounts[0], index = 0)
        val second = line(accountState, accounts[1], index = 1)

        assertTrue("account[0]=id:a | name:Kei | active:true" in first, first)
        assertTrue(" | ap:12/240" in first, first)
        // The whole point: the second account is not the active one and still reports its own numbers.
        assertTrue("account[1]=id:b | name:JP | active:false" in second, second)
        assertTrue(" | ap:99/180" in second, second)
        assertTrue(" | serverIndex:0" in second, second)
    }

    @Test
    fun `a disabled account is listed rather than hidden`() {
        // Disabling stops reminders; it does not delete the account, and an assistant asked to audit
        // every account has to be able to see that one is switched off.
        val accounts = listOf(account("a"), account("b", enabled = false))
        val accountState = state(accounts)

        val text = line(accountState, accounts[1], index = 1)
        assertTrue(" | enabled:false" in text, text)
    }

    @Test
    fun `craft is summarised per account`() {
        val runtime =
            BaAccountRuntime(
                craft =
                    BaCraftState(
                        generate =
                            listOf(
                                BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.Low)),
                                BaCraftSlot(startedAtMs = START, grades = listOf(BaCraftGrade.High)),
                            ),
                    ),
            )
        val accounts = listOf(account("a", runtime = runtime))
        val accountState = state(accounts)

        // One hour in: Low (30m) is collectable, High (3h) still running.
        val text = line(accountState, accounts[0], nowMs = START + HOUR)
        assertTrue(" | craftRunning:1" in text, text)
        assertTrue(" | craftReady:1" in text, text)
        assertTrue(" | craftNextCompletionAtMs:${START + 3L * HOUR}" in text, text)
    }

    @Test
    fun `an unbound account reports tile slot minus one`() {
        val accounts = listOf(account("a"), account("b"))
        val accountState = state(accounts)
        val tileState = BaDailyTileState().withSlot(1, BaAccountId("b"))

        assertTrue(" | tileSlot:-1" in line(accountState, accounts[0], tileState, index = 0))
        // Slot indices are the pool's own, not the account's position in the list.
        assertTrue(" | tileSlot:1" in line(accountState, accounts[1], tileState, index = 1))
        assertTrue(BA_DAILY_TILE_SLOTS >= 2)
    }

    @Test
    fun `a dailies preview and a commit differ only in the applied flag`() {
        val outcome =
            BaDailyDoneOutcome(
                apAdjusted = true,
                cafeApCleared = true,
                headpatStarted = true,
                craftSlotsStarted = 2,
            )

        val preview = mcpBaDailyDoneLine("Kei", "a", outcome, applied = false)
        val committed = mcpBaDailyDoneLine("Kei", "a", outcome, applied = true)

        assertEquals(
            "dailyDone[a]=name:Kei | applied:false | changed:true | apAdjusted:true |" +
                " cafeApCleared:true | headpatStarted:true | invite1Started:false |" +
                " invite2Started:false | craftSlotsStarted:2",
            preview,
        )
        // Diffable against the preview without parsing two formats.
        assertEquals(preview.replace("applied:false", "applied:true"), committed)
    }

    @Test
    fun `a run that changes nothing says so rather than looking like a failure`() {
        val text = mcpBaDailyDoneLine("Kei", "a", BaDailyDoneOutcome(), applied = true)

        assertTrue(" | changed:false" in text, text)
        assertTrue(" | craftSlotsStarted:0" in text, text)
    }
}
