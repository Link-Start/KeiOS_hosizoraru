package os.kei.feature.keepalive.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AccessibilityGuardCheckRunnerTest {
    private val tempDirs = mutableListOf<File>()

    @After
    fun cleanup() {
        tempDirs.forEach { dir -> dir.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `check and record writes successful history`() = runTest {
        val historyStore = historyStore()
        val runner =
            AccessibilityGuardCheckRunner(
                checkOperation =
                    AccessibilityGuardCheckOperation {
                        result(status = AccessibilityGuardCheckStatus.Healthy)
                    },
                historyStore = historyStore,
            )

        val result =
            runner.checkAndRecord(
                reason = AccessibilityGuardCheckReason.Manual,
                triggerAction = "manual_check",
            )

        assertEquals(AccessibilityGuardCheckStatus.Healthy, result.status)
        val history = historyStore.latest(1).single()
        assertEquals(AccessibilityGuardCheckStatus.Healthy, history.status)
        assertEquals("manual_check", history.triggerAction)
        // A history row is stamped when the check finished, not when it started.
        assertEquals(1_100L, history.timestampMs)
        assertEquals(1, history.checkCount)
        assertEquals(1, history.healthyCount)
    }

    @Test
    fun `check and record writes timeout history`() = runTest {
        val historyStore = historyStore()
        val runner =
            AccessibilityGuardCheckRunner(
                checkOperation =
                    AccessibilityGuardCheckOperation {
                        delay(10_000L)
                        result(status = AccessibilityGuardCheckStatus.Healthy)
                    },
                historyStore = historyStore,
                timeoutMs = 100L,
                wallClockMs = { 1_000L },
                elapsedClockMs = { 1_000L },
            )

        val result =
            runner.checkAndRecord(
                reason = AccessibilityGuardCheckReason.BootCompleted,
                triggerAction = "boot",
            )

        assertEquals(AccessibilityGuardCheckStatus.TimedOut, result.status)
        assertEquals("timeout", result.failureReason)
        val history = historyStore.latest(1).single()
        assertEquals(AccessibilityGuardCheckStatus.TimedOut, history.status)
        assertEquals("boot", history.triggerAction)
        assertEquals(1, history.warningCount)
    }

    @Test
    fun `record timeout writes history without running check operation`() = runTest {
        val historyStore = historyStore()
        var called = false
        val runner =
            AccessibilityGuardCheckRunner(
                checkOperation =
                    AccessibilityGuardCheckOperation {
                        called = true
                        result(status = AccessibilityGuardCheckStatus.Healthy)
                    },
                historyStore = historyStore,
            )

        val result =
            runner.recordTimeout(
                reason = AccessibilityGuardCheckReason.PackageReplaced,
                triggerAction = "package:timeout",
            )

        assertEquals(false, called)
        assertEquals(AccessibilityGuardCheckStatus.TimedOut, result.status)
        assertEquals("package:timeout", historyStore.latest(1).single().triggerAction)
    }

    private fun historyStore(): AccessibilityGuardHistoryStore {
        val dir = Files.createTempDirectory("accessibility-guard-runner").toFile()
        tempDirs += dir
        return AccessibilityGuardHistoryStore(File(dir, "history.jsonl"))
    }

    private fun result(status: AccessibilityGuardCheckStatus): AccessibilityGuardCheckResult =
        AccessibilityGuardCheckResult(
            status = status,
            reason = AccessibilityGuardCheckReason.Manual,
            checkCount = 1,
            healthyCount = if (status == AccessibilityGuardCheckStatus.Healthy) 1 else 0,
            warningCount = if (status == AccessibilityGuardCheckStatus.Healthy) 0 else 1,
            startedAtMs = 1_000L,
            finishedAtMs = 1_100L,
            elapsedMs = 100L,
            privilegeStatus = "ready",
            failureReason = "",
        )
}
