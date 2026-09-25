package os.kei.ui.page.main.ba

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.IntRect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BaApMutationRescheduleTest {
    @Test
    fun `ordinary AP edit callback persists account update before reschedule`() = runTest {
        val fixture = actionFixture()

        fixture.actions.onApCurrentInputChange("123")
        fixture.actions.onApCurrentDone()
        advanceUntilIdle()

        val update = assertNotNull(fixture.persistedUpdates.singleOrNull())
        assertEquals(ACCOUNT_ID, update.accountId)
        assertEquals(123.0, update.apCurrent)
        assertEquals(NOW_MS, update.apSyncMs)
        assertEquals(listOf("persist", "schedule"), fixture.events)
    }

    @Test
    fun `cafe AP edit callback persists account update before reschedule`() = runTest {
        val fixture = actionFixture()

        fixture.actions.onCafeStoredApInputChange("42.5")
        fixture.actions.onCafeStoredApDone()
        advanceUntilIdle()

        val update = assertNotNull(fixture.persistedUpdates.singleOrNull())
        assertEquals(ACCOUNT_ID, update.accountId)
        assertEquals(42.5, update.cafeStoredAp)
        assertEquals(-1, update.cafeApLastNotifiedLevel)
        assertEquals(listOf("persist", "schedule"), fixture.events)
    }

    @Test
    fun `cafe claim callback persists account update before reschedule`() = runTest {
        val fixture = actionFixture()

        fixture.actions.onClaimCafeStoredAp()
        advanceUntilIdle()

        val update = assertNotNull(fixture.persistedUpdates.singleOrNull())
        assertEquals(ACCOUNT_ID, update.accountId)
        assertEquals(150.0, update.apCurrent)
        assertEquals(0.0, update.cafeStoredAp)
        assertEquals(-1, update.cafeApLastNotifiedLevel)
        assertEquals(listOf("persist", "schedule"), fixture.events)
    }

    @Test
    fun `AP limit sheet callback persists merged account runtime before reschedule`() = runTest {
        val office = sheetOffice()
        office.apLimitInput = "200"
        val fixture = sheetFixture(office)
        val expectedOffice = sheetOffice()
        val expectedLimitUpdate = expectedOffice.updateApLimit(200)
        val expectedRegenUpdate = expectedOffice.applyApRegen()
        val expectedRuntimeUpdate =
            when {
                expectedLimitUpdate.runtimeUpdate != null && expectedRegenUpdate != null ->
                    expectedLimitUpdate.runtimeUpdate.mergedWith(expectedRegenUpdate)

                else -> expectedLimitUpdate.runtimeUpdate ?: expectedRegenUpdate
            }

        fixture.callbacks.onSaveApLimit()
        advanceUntilIdle()

        assertEquals(listOf(200), fixture.limits)
        assertEquals(expectedRuntimeUpdate?.withAccountId(ACCOUNT_ID), fixture.updates.single())
        assertEquals(listOf("limit", "runtime", "schedule"), fixture.events)
    }

    @Test
    fun `cafe clear calibration sheet callback persists account update before reschedule`() = runTest {
        val office = sheetOffice()
        val fixture = sheetFixture(office)

        fixture.callbacks.onClearCafeStoredAp()
        advanceUntilIdle()

        assertEquals(ACCOUNT_ID, fixture.updates.single().accountId)
        assertEquals(0.0, fixture.updates.single().cafeStoredAp)
        assertEquals(-1, fixture.updates.single().cafeApLastNotifiedLevel)
        assertEquals(listOf("runtime", "schedule"), fixture.events)
    }

    @Test
    fun `cafe fill calibration sheet callback persists account update before reschedule`() = runTest {
        val office = sheetOffice()
        val fixture = sheetFixture(office)

        fixture.callbacks.onFillCafeStoredAp()
        advanceUntilIdle()

        assertEquals(ACCOUNT_ID, fixture.updates.single().accountId)
        assertEquals(office.cafeStoredAp, fixture.updates.single().cafeStoredAp)
        assertEquals(-1, fixture.updates.single().cafeApLastNotifiedLevel)
        assertEquals(listOf("runtime", "schedule"), fixture.events)
    }

    private fun TestScope.actionFixture(): ActionFixture {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val office =
            BaOfficeController(
                snapshot =
                    BaPageSnapshot(
                        apCurrent = 100.0,
                        apRegenBaseMs = NOW_MS,
                        apSyncMs = NOW_MS,
                        apLimit = 240,
                        cafeLevel = 10,
                        cafeStoredAp = 50.0,
                        cafeLastHourMs = NOW_MS,
                    ),
                clock = FixedBaOfficeClock(NOW_MS),
            )
        val events = mutableListOf<String>()
        val persistedUpdates = mutableListOf<BaRuntimePersistenceUpdate>()
        val coordinator =
            BaOfficeActionCoordinator(
                context = context,
                office = office,
                scope = this,
                serverIndexProvider = { 2 },
                accountIdProvider = { ACCOUNT_ID },
                onSettingsCafeLevelChange = {},
                onCafeLevelPopupAnchorBoundsChange = { _: IntRect? -> },
                onCafeLevelPopupChange = {},
                onOpenApLimitTools = {},
                onOpenCafeApTools = {},
                onOpenCafeCooldownEditSheet = {},
                onOpenCraftSlotEditSheet = {},
                onCraftCardExpandedChange = {},
                onAccountSelected = {},
                onEditAccount = {},
                onRefreshCalendar = {},
                onRefreshPool = {},
                onOpenCalendarLink = {},
                onOpenPoolStudentGuide = {},
                persistRuntimeUpdate = { update ->
                    events += "persist"
                    persistedUpdates += update
                },
                scheduleBaApThreshold = { events += "schedule" },
            )
        return ActionFixture(
            actions = coordinator.buildContentActions(),
            events = events,
            persistedUpdates = persistedUpdates,
        )
    }

    private fun TestScope.sheetFixture(office: BaOfficeController): SheetFixture {
        val events = mutableListOf<String>()
        val limits = mutableListOf<Int>()
        val updates = mutableListOf<BaRuntimePersistenceUpdate>()
        val coordinator =
            BaPageSheetApMutationPersistenceCoordinator(
                accountIdProvider = { ACCOUNT_ID },
                saveApLimit = { limit ->
                    events += "limit"
                    limits += limit
                },
                persistRuntimeUpdate = { update ->
                    events += "runtime"
                    updates += update
                },
                scheduleBaApThreshold = { events += "schedule" },
            )
        val callbacks =
            buildBaPageSheetApMutationCallbacks(
                office = office,
                scope = this,
                persistenceCoordinator = coordinator,
            )
        return SheetFixture(callbacks, events, limits, updates)
    }

    private fun sheetOffice(): BaOfficeController =
        BaOfficeController(
            snapshot =
                BaPageSnapshot(
                    apCurrent = 100.0,
                    apRegenBaseMs = NOW_MS - 12 * 60_000L,
                    apSyncMs = NOW_MS - 12 * 60_000L,
                    apLimit = 240,
                    cafeLevel = 10,
                    cafeStoredAp = 50.0,
                    cafeLastHourMs = NOW_MS,
                ),
            clock = FixedBaOfficeClock(NOW_MS),
        )

    private data class ActionFixture(
        val actions: BaPageContentActions,
        val events: MutableList<String>,
        val persistedUpdates: MutableList<BaRuntimePersistenceUpdate>,
    )

    private data class SheetFixture(
        val callbacks: BaPageSheetApMutationCallbacks,
        val events: List<String>,
        val limits: List<Int>,
        val updates: List<BaRuntimePersistenceUpdate>,
    )

    private companion object {
        val ACCOUNT_ID = BaAccountId("cn-main")
        const val NOW_MS = 20_000_000L
    }
}
