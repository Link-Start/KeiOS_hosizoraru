package os.kei.ui.page.main.ba

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaApReminderKind
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaApNotificationSyncCoordinatorTest {
    @Test
    fun `foreground persistent read suppresses regenerated AP`() {
        val plan =
            planBaApNotificationSync(
                request =
                    request(
                        currentDisplay = 121,
                        lastNotifiedLevel = 120,
                        keepReadUntilBelowThreshold = true,
                        suppressionAnchorAtMs = NOW_MS - 1_000L,
                    ),
                nowMs = NOW_MS,
            )

        assertFalse(plan.shouldSendThresholdNotification)
        assertFalse(plan.shouldRefreshActiveNotification)
    }

    @Test
    fun `expired foreground dismissal sends same level and clears snooze after delivery`() = runTest {
        val request =
            request(
                currentDisplay = 120,
                lastNotifiedLevel = 120,
                dismissedUntilAtMs = NOW_MS,
            )
        val plan = planBaApNotificationSync(request = request, nowMs = NOW_MS)

        assertTrue(plan.shouldSendThresholdNotification)
        assertTrue(plan.clearDismissedUntilAfterDelivery)

        val result =
            BaApNotificationSyncCoordinator.sync(
                request = request,
                nowMs = NOW_MS,
                delivery = FixedDelivery(sent = true),
            )

        assertEquals(0L, result.dismissedUntilAtMs)
    }

    @Test
    fun `foreground recovery clears read and dismissal state`() {
        val plan =
            planBaApNotificationSync(
                request =
                    request(
                        currentDisplay = 119,
                        suppressionAnchorAtMs = NOW_MS,
                        dismissedUntilAtMs = NOW_MS + BA_AP_DISMISS_SNOOZE_INTERVAL_MS,
                    ),
                nowMs = NOW_MS,
            )

        assertEquals(0L, plan.nextSuppressionAnchorAtMs)
        assertEquals(0L, plan.nextDismissedUntilAtMs)
    }

    @Test
    fun `successful hourly delivery commits anchor before replacement state can run`() = runTest {
        val accountId = BaAccountId("cn-main")
        val office =
            BaOfficeController(
                BaPageSnapshot(
                    apLastNotifiedLevel = 120,
                    apSuppressionAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS,
                ),
            )
        val request =
            request(
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = office.apSuppressionAnchorAtMs,
            ).copy(accountId = accountId)
        val events = mutableListOf<String>()
        val anchorWriter = RecordingAnchorWriter(events)

        val result =
            BaApNotificationSyncCoordinator.sync(
                request = request,
                nowMs = NOW_MS,
                delivery = FixedDelivery(sent = true),
            )
        persistBaForegroundApSyncResult(
            request = request,
            result = result,
            office = office,
            anchorWriter = anchorWriter,
        ) { level ->
            events += "level"
            assertEquals(NOW_MS, office.apSuppressionAnchorAtMs)
            office.applyApLastNotifiedLevel(level)
            val replacement =
                planBaApNotificationSync(
                    request.copy(
                        lastNotifiedLevel = office.apLastNotifiedLevel,
                        suppressionAnchorAtMs = office.apSuppressionAnchorAtMs,
                    ),
                    nowMs = NOW_MS,
                )
            assertFalse(replacement.shouldSendThresholdNotification)
        }

        assertEquals(listOf("anchor", "level"), events)
        assertEquals(listOf(Triple(accountId, BaApReminderKind.Ap, NOW_MS)), anchorWriter.writes)
        assertEquals(NOW_MS, office.apSuppressionAnchorAtMs)
    }

    @Test
    fun `failed or timed out foreground delivery preserves anchor`() = runTest {
        val initialAnchor = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS
        val request =
            request(
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = initialAnchor,
            ).copy(accountId = BaAccountId("cn-main"))

        listOf(
            FixedDelivery(sent = false),
            DelayedDelivery,
        ).forEach { delivery ->
            val office = BaOfficeController(BaPageSnapshot(apSuppressionAnchorAtMs = initialAnchor))
            val anchorWriter = RecordingAnchorWriter(mutableListOf())
            val result =
                BaApNotificationSyncCoordinator.sync(
                    request = request,
                    nowMs = NOW_MS,
                    delivery = delivery,
                    timeoutMs = 1L,
                )

            assertNull(result.suppressionAnchorAtMs)
            persistBaForegroundApSyncResult(
                request = request,
                result = result,
                office = office,
                anchorWriter = anchorWriter,
            ) {}
            assertTrue(anchorWriter.writes.isEmpty())
            assertEquals(initialAnchor, office.apSuppressionAnchorAtMs)
        }
    }

    @Test
    fun `failed Xiaomi style foreground delivery preserves last level and anchor`() = runTest {
        val initialAnchor = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS
        val request =
            request(
                lastNotifiedLevel = 120,
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = initialAnchor,
            ).copy(accountId = BaAccountId("cn-main"))
        val office =
            BaOfficeController(
                BaPageSnapshot(
                    apLastNotifiedLevel = 120,
                    apSuppressionAnchorAtMs = initialAnchor,
                ),
            )
        val anchorWriter = RecordingAnchorWriter(mutableListOf())

        val result =
            BaApNotificationSyncCoordinator.sync(
                request = request,
                nowMs = NOW_MS,
                delivery = FixedDelivery(sent = false),
            )

        assertNull(result.lastNotifiedLevel)
        assertNull(result.suppressionAnchorAtMs)
        persistBaForegroundApSyncResult(
            request = request,
            result = result,
            office = office,
            anchorWriter = anchorWriter,
        ) {}
        assertEquals(120, office.apLastNotifiedLevel)
        assertEquals(initialAnchor, office.apSuppressionAnchorAtMs)
        assertTrue(anchorWriter.writes.isEmpty())
    }

    @Test
    fun `failed threshold delivery does not issue an active refresh post`() = runTest {
        val delivery = RecordingFailedDelivery()

        BaApNotificationSyncCoordinator.sync(
            request = request(lastNotifiedLevel = 119),
            nowMs = NOW_MS,
            delivery = delivery,
        )

        assertEquals(1, delivery.sendAttempts)
        assertEquals(0, delivery.refreshAttempts)
    }

    @Test
    fun `threshold timeout returns the delivery state committed during cancellation cleanup`() = runTest {
        val result =
            BaApNotificationSyncCoordinator.sync(
                request =
                    request(
                        lastNotifiedLevel = 119,
                        keepReadUntilBelowThreshold = false,
                        suppressionAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS,
                    ),
                nowMs = NOW_MS,
                delivery = SlowCommittedDelivery,
                timeoutMs = 1L,
                onThresholdDelivered = {},
            )

        assertEquals(120, result.lastNotifiedLevel)
        assertEquals(NOW_MS, result.suppressionAnchorAtMs)
        assertTrue(result.committedDuringDelivery)
    }

    @Test
    fun `cancellation after delivery still commits anchor and syncs office state`() = runTest {
        val accountId = BaAccountId("cn-main")
        val office = BaOfficeController(BaPageSnapshot(apSuppressionAnchorAtMs = 1L))
        val writer = BlockingAnchorWriter()
        val request =
            request(
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS,
            ).copy(accountId = accountId)
        val result =
            BaApNotificationSyncCoordinator.sync(
                request = request,
                nowMs = NOW_MS,
                delivery = FixedDelivery(sent = true),
            )
        val levelWrites = mutableListOf<Int>()

        val job =
            launch {
                persistBaForegroundApSyncResult(
                    request = request,
                    result = result,
                    office = office,
                    anchorWriter = writer,
                ) { levelWrites += it }
            }
        writer.started.await()
        job.cancel()
        writer.release.complete(Unit)
        job.join()

        assertEquals(listOf(Triple(accountId, BaApReminderKind.Ap, NOW_MS)), writer.writes)
        assertEquals(NOW_MS, office.apSuppressionAnchorAtMs)
        assertEquals(listOf(120), levelWrites)
        assertFalse(
            planBaApNotificationSync(
                request.copy(
                    keepReadUntilBelowThreshold = true,
                    suppressionAnchorAtMs = office.apSuppressionAnchorAtMs,
                ),
                nowMs = NOW_MS,
            ).shouldSendThresholdNotification,
        )
    }

    @Test
    fun `delivery commit callback receives success state before cancellation escapes`() = runTest {
        val committedResult = CompletableDeferred<BaApNotificationSyncResult>()
        val request =
            request(
                keepReadUntilBelowThreshold = false,
                suppressionAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS,
            )

        assertFailsWith<CancellationException> {
            BaApNotificationSyncCoordinator.sync(
                request = request,
                nowMs = NOW_MS,
                delivery = CancellingAfterCommitDelivery,
                onThresholdDelivered = { result -> committedResult.complete(result) },
            )
        }

        assertEquals(
            BaApNotificationSyncResult(
                lastNotifiedLevel = request.currentDisplay,
                suppressionAnchorAtMs = NOW_MS,
            ),
            committedResult.await(),
        )
    }

    @Test
    fun `delivered foreground state uses captured account and waits for durable persistence`() = runTest {
        val accountA = BaAccountId("account-a")
        val officeForAccountB =
            BaOfficeController(
                BaPageSnapshot(
                    apLastNotifiedLevel = 77,
                    apSuppressionAnchorAtMs = 88L,
                ),
            )
        val persistenceStarted = CompletableDeferred<Unit>()
        val releasePersistence = CompletableDeferred<Unit>()
        val persistedUpdates = mutableListOf<BaRuntimePersistenceUpdate>()
        val events = mutableListOf<String>()
        val anchorWriter = RecordingAnchorWriter(events)
        val request = request().copy(accountId = accountA)
        val result =
            BaApNotificationSyncResult(
                lastNotifiedLevel = 120,
                suppressionAnchorAtMs = NOW_MS,
            )

        val job =
            launch {
                persistBaForegroundApDeliveredResult(
                    request = request,
                    result = result,
                    persistRuntimeUpdate = { update ->
                        persistenceStarted.complete(Unit)
                        releasePersistence.await()
                        persistedUpdates += update
                        events += "level"
                    },
                    anchorWriter = anchorWriter,
                )
            }

        persistenceStarted.await()
        assertFalse(job.isCompleted)
        releasePersistence.complete(Unit)
        job.join()

        assertEquals(
            listOf(
                BaRuntimePersistenceUpdate(
                    accountId = accountA,
                    apLastNotifiedLevel = 120,
                ),
            ),
            persistedUpdates,
        )
        assertEquals(listOf(Triple(accountA, BaApReminderKind.Ap, NOW_MS)), anchorWriter.writes)
        assertEquals(listOf("level", "anchor"), events)
        assertEquals(77, officeForAccountB.apLastNotifiedLevel)
        assertEquals(88L, officeForAccountB.apSuppressionAnchorAtMs)
        assertFalse(
            shouldApplyBaForegroundApCommittedResult(
                requestAccountId = accountA,
                activeAccountId = BaAccountId("account-b"),
            ),
        )
        assertTrue(
            shouldApplyBaForegroundApCommittedResult(
                requestAccountId = accountA,
                activeAccountId = accountA,
            ),
        )
    }

    private fun request(
        currentDisplay: Int = 120,
        lastNotifiedLevel: Int = 120,
        keepReadUntilBelowThreshold: Boolean = true,
        suppressionAnchorAtMs: Long = 0L,
        dismissedUntilAtMs: Long = 0L,
    ): BaApNotificationSyncRequest =
        BaApNotificationSyncRequest(
            currentDisplay = currentDisplay,
            limitDisplay = 240,
            thresholdDisplay = 120,
            notifyEnabled = true,
            lastNotifiedLevel = lastNotifiedLevel,
            keepReadUntilBelowThreshold = keepReadUntilBelowThreshold,
            suppressionAnchorAtMs = suppressionAnchorAtMs,
            dismissedUntilAtMs = dismissedUntilAtMs,
        )

    private companion object {
        const val NOW_MS = 10_000_000L
    }
}

private class RecordingAnchorWriter(
    private val events: MutableList<String>,
) : BaApSuppressionAnchorWriter {
    val writes = mutableListOf<Triple<BaAccountId, BaApReminderKind, Long>>()

    override suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        anchorAtMs: Long,
    ) {
        events += "anchor"
        writes += Triple(accountId, kind, anchorAtMs)
    }
}

private class FixedDelivery(
    private val sent: Boolean,
) : BaApNotificationDelivery {
    override suspend fun sendThreshold(request: BaApNotificationSyncRequest): Boolean = sent

    override suspend fun refreshActive(request: BaApNotificationSyncRequest): Boolean = true
}

private object DelayedDelivery : BaApNotificationDelivery {
    override suspend fun sendThreshold(request: BaApNotificationSyncRequest): Boolean {
        delay(Long.MAX_VALUE)
        return true
    }

    override suspend fun refreshActive(request: BaApNotificationSyncRequest): Boolean = true
}

private class RecordingFailedDelivery : BaApNotificationDelivery {
    var sendAttempts = 0
    var refreshAttempts = 0

    override suspend fun sendThreshold(request: BaApNotificationSyncRequest): Boolean {
        sendAttempts += 1
        return false
    }

    override suspend fun refreshActive(request: BaApNotificationSyncRequest): Boolean {
        refreshAttempts += 1
        return true
    }
}

private object SlowCommittedDelivery : BaApNotificationDelivery {
    override suspend fun sendThreshold(request: BaApNotificationSyncRequest): Boolean = true

    override suspend fun sendThresholdWithCommit(
        request: BaApNotificationSyncRequest,
        onDelivered: suspend () -> Unit,
    ): Boolean {
        withContext(NonCancellable) {
            delay(10L)
            onDelivered()
        }
        return true
    }

    override suspend fun refreshActive(request: BaApNotificationSyncRequest): Boolean = true
}

private object CancellingAfterCommitDelivery : BaApNotificationDelivery {
    override suspend fun sendThreshold(request: BaApNotificationSyncRequest): Boolean = true

    override suspend fun sendThresholdWithCommit(
        request: BaApNotificationSyncRequest,
        onDelivered: suspend () -> Unit,
    ): Boolean {
        withContext(NonCancellable) { onDelivered() }
        throw CancellationException("cancel after accepted delivery")
    }

    override suspend fun refreshActive(request: BaApNotificationSyncRequest): Boolean = true
}

private class BlockingAnchorWriter : BaApSuppressionAnchorWriter {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val writes = mutableListOf<Triple<BaAccountId, BaApReminderKind, Long>>()

    override suspend fun save(
        accountId: BaAccountId,
        kind: BaApReminderKind,
        anchorAtMs: Long,
    ) {
        started.complete(Unit)
        release.await()
        writes += Triple(accountId, kind, anchorAtMs)
    }
}
