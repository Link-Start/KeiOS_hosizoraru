package os.kei.core.background

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.page.main.ba.BA_AP_READ_REPEAT_INTERVAL_MS
import os.kei.ui.page.main.ba.support.BaAccountId
import os.kei.ui.page.main.ba.support.BaAccountReminderSnapshot
import os.kei.ui.page.main.ba.support.BaApReminderKind
import os.kei.ui.page.main.ba.support.BaPageSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundAsyncReceiverRunnerTest {
    @Test
    fun `runner finishes pending result once when block completes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val finishCount = AtomicInteger(0)
        val timeoutCount = AtomicInteger(0)

        val job = BackgroundAsyncReceiverRunner.launchWithPendingResult(
            context = context,
            tag = "BackgroundAsyncReceiverRunnerTest",
            timeoutMs = 5_000L,
            finishPending = { finishCount.incrementAndGet() },
            runnerScope = this,
            onTimeout = { timeoutCount.incrementAndGet() }
        ) {}
        job.join()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(1, finishCount.get())
        assertEquals(0, timeoutCount.get())
    }

    @Test
    fun `runner releases pending result and cancels worker on timeout`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val finishCount = AtomicInteger(0)
        val timeoutCount = AtomicInteger(0)
        val blockStarted = CompletableDeferred<Unit>()

        val job = BackgroundAsyncReceiverRunner.launchWithPendingResult(
            context = context,
            tag = "BackgroundAsyncReceiverRunnerTest",
            timeoutMs = 100L,
            finishPending = { finishCount.incrementAndGet() },
            runnerScope = this,
            onTimeout = { timeoutCount.incrementAndGet() }
        ) {
            blockStarted.complete(Unit)
            delay(5_000L)
        }
        blockStarted.await()
        advanceTimeBy(100L)
        runCurrent()

        assertEquals(1, finishCount.get())
        assertEquals(1, timeoutCount.get())
        assertTrue(job.isCancelled)
    }

    @Test
    fun `timeout waits for durable BA delivery commit before rescheduling`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val finishCount = AtomicInteger(0)
        val rescheduled = AtomicBoolean(false)
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val scheduledAtMs = mutableListOf<Long>()
        var durableAnchorAtMs = NOW_MS - BA_AP_READ_REPEAT_INTERVAL_MS
        var durableLastNotifiedLevel = -1

        suspend fun rescheduleOnce() {
            if (!rescheduled.compareAndSet(false, true)) return
            val schedulePlan =
                AppBackgroundScheduler.buildBaReminderScheduleAfterAcknowledgementReconciliation(
                    nowMs = NOW_MS,
                    reconcile = { true },
                    loadReminderSnapshots = {
                        listOf(
                            BaAccountReminderSnapshot(
                                accountId = BaAccountId("cn-main"),
                                displayName = "Main",
                                snapshot = hourlyApSnapshot(anchorAtMs = durableAnchorAtMs),
                            ),
                        )
                    },
                )
            scheduledAtMs += requireNotNull(schedulePlan.schedule).triggerAtMillis
        }

        val job =
            BackgroundAsyncReceiverRunner.launchWithPendingResult(
                context = context,
                tag = "BackgroundAsyncReceiverRunnerTest",
                timeoutMs = 100L,
                awaitWorkerCompletionOnTimeout = true,
                finishPending = { finishCount.incrementAndGet() },
                runnerScope = this,
                onTimeout = { rescheduleOnce() },
            ) {
                try {
                    AppForegroundInfoHandler.persistBaForegroundApReminderWrites(
                        accountId = BaAccountId("cn-main"),
                        writes =
                            BaForegroundApReminderPersistencePolicy.deliveryWrites(
                                kind = BaApReminderKind.Ap,
                                sent = true,
                                currentDisplay = 130,
                                advanceSuppressionAnchorAfterDelivery = true,
                                nowMs = NOW_MS,
                            ),
                        persistWrite = { _, write ->
                            write.lastNotifiedLevel?.let { level ->
                                durableLastNotifiedLevel = level
                            }
                            write.suppressionAnchorAtMs?.let { anchorAtMs ->
                                commitStarted.complete(Unit)
                                releaseCommit.await()
                                durableAnchorAtMs = anchorAtMs
                            }
                        },
                    )
                } finally {
                    rescheduleOnce()
                }
            }

        commitStarted.await()
        advanceTimeBy(100L)
        runCurrent()
        releaseCommit.complete(Unit)
        runCurrent()
        job.join()

        assertEquals(NOW_MS, durableAnchorAtMs)
        assertEquals(130, durableLastNotifiedLevel)
        assertEquals(listOf(NOW_MS + BA_AP_READ_REPEAT_INTERVAL_MS), scheduledAtMs)
        assertEquals(1, finishCount.get())
    }

    private fun hourlyApSnapshot(anchorAtMs: Long): BaPageSnapshot =
        BaPageSnapshot(
            apNotifyEnabled = true,
            apCurrent = 130.0,
            apRegenBaseMs = NOW_MS,
            apNotifyThreshold = 120,
            apLimit = 240,
            apLastNotifiedLevel = 130,
            keepApRemindersReadUntilBelowThreshold = false,
            apSuppressionAnchorAtMs = anchorAtMs,
        )

    private companion object {
        const val NOW_MS = 20_000_000L
    }
}
