package os.kei.core.download.segmented

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartSchedulerTest {

    @Test
    fun `concurrency probe doubles after each candidate confirms a small sample`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 10_000,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 4,
            tuning = testTuning.copy(
                startupActiveConnections = 2,
                tailWindowInitialMultiplier = 0,
            ),
        )
        val first = assertNotNull(scheduler.nextPart(workerId = 0))
        val second = assertNotNull(scheduler.nextPart(workerId = 1))

        scheduler.confirmConcurrencyProbe(0, first.concurrencyProbeGeneration, transferredBytes = 64 * 1024L)
        assertNull(scheduler.nextPart(workerId = 2))
        scheduler.confirmConcurrencyProbe(1, second.concurrencyProbeGeneration, transferredBytes = 64 * 1024L)

        assertNotNull(scheduler.nextPart(workerId = 2))
        assertNotNull(scheduler.nextPart(workerId = 3))
        assertEquals(4, scheduler.stats().peakActiveConnections)
    }

    @Test
    fun `concurrency probe window keeps only the observed connection capacity`() = runBlocking {
        var now = 0L
        val scheduler =
            PartScheduler(
                totalBytes = 100_000,
                initialPartSizeBytes = 1_000,
                maxRetriesPerPart = 1,
                concurrency = 8,
                tuning =
                    testTuning.copy(
                        startupActiveConnections = 2,
                        concurrencyProbeLowWindowMs = 1_000L,
                        rateLimitRecoveryMs = 5_000L,
                        tailWindowInitialMultiplier = 0,
                    ),
                nowMs = { now },
            )
        val first = assertNotNull(scheduler.nextPart(0))
        val second = assertNotNull(scheduler.nextPart(1))
        scheduler.confirmConcurrencyProbe(
            workerId = 0,
            generation = first.concurrencyProbeGeneration,
            transferredBytes = scheduler.concurrencyProbeConfirmBytes,
        )
        scheduler.finish(0, first)
        scheduler.finish(1, second)

        now = 1_000L
        val admitted = listOf(
            assertNotNull(scheduler.nextPart(0)),
        )

        assertEquals(2, scheduler.stats().currentActiveLimit)
        assertNull(scheduler.nextPart(2))
        assertEquals(2, scheduler.stats().peakActiveConnections)
        admitted.forEachIndexed { index, active -> scheduler.finish(index, active) }
    }

    @Test
    fun `progressive growth can reach the configured hard limit`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 1_000_000,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 12,
            tuning = testTuning.copy(
                startupActiveConnections = 2,
                tailWindowInitialMultiplier = 0,
            ),
        )

        confirmProbeWave(scheduler, expectedActive = 2)
        confirmProbeWave(scheduler, expectedActive = 4)
        confirmProbeWave(scheduler, expectedActive = 8)

        val finalWave = (0 until 12).map { workerId -> assertNotNull(scheduler.nextPart(workerId)) }
        assertEquals(12, scheduler.stats().peakActiveConnections)
        finalWave.forEachIndexed { workerId, active ->
            scheduler.finish(workerId, active)
        }
    }

    @Test
    fun `speed profiles keep common APK sizes inside the distributed tail window`() = runBlocking {
        data class Case(
            val label: String,
            val profile: SegmentedDownloadSpeedProfile,
            val totalMiB: Long,
            val partMiB: Long,
            val concurrency: Int,
            val probeWave: Int,
        )
        listOf(
            Case("boost, medium download", SegmentedDownloadSpeedProfile.ForegroundBoost, 96, 4, 6, 4),
            Case("balanced, large APK", SegmentedDownloadSpeedProfile.Balanced, 120, 8, 4, 2),
        ).forEach { case ->
            val partSizeBytes = case.partMiB * 1024L * 1024L
            val scheduler = PartScheduler(
                totalBytes = case.totalMiB * 1024L * 1024L,
                initialPartSizeBytes = partSizeBytes,
                maxRetriesPerPart = 3,
                concurrency = case.concurrency,
                tuning = case.profile.schedulerTuning(),
            )

            confirmProbeWave(scheduler, expectedActive = case.probeWave)
            val secondWave = (0 until case.concurrency).map { workerId ->
                assertNotNull(scheduler.nextPart(workerId), case.label)
            }

            assertTrue(secondWave.all { it.part.length <= partSizeBytes }, case.label)
        }
    }

    @Test
    fun `scheduler allocates head and tail parts on demand before tail window`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 200,
            initialPartSizeBytes = 10,
            maxRetriesPerPart = 2,
            concurrency = 2,
            tuning = testTuning,
        )

        val first = assertNotNull(scheduler.nextPart(workerId = 0))
        assertEquals(DownloadPart(start = 0, endInclusive = 9), first.part)
        scheduler.finish(workerId = 0, active = first)

        val second = assertNotNull(scheduler.nextPart(workerId = 1))
        assertEquals(DownloadPart(start = 190, endInclusive = 199), second.part)
        scheduler.finish(workerId = 1, active = second)

        val third = assertNotNull(scheduler.nextPart(workerId = 0))
        assertEquals(DownloadPart(start = 10, endInclusive = 19), third.part)
    }

    @Test
    fun `scheduler uses smaller parts inside tail window`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 20,
            initialPartSizeBytes = 10,
            maxRetriesPerPart = 2,
            concurrency = 2,
            tuning = testTuning.copy(tailPartsPerConnection = 2),
        )

        val first = assertNotNull(scheduler.nextPart(workerId = 0))
        assertEquals(DownloadPart(start = 0, endInclusive = 4), first.part)
        scheduler.finish(workerId = 0, active = first)

        val second = assertNotNull(scheduler.nextPart(workerId = 1))
        assertEquals(DownloadPart(start = 15, endInclusive = 19), second.part)
        scheduler.finish(workerId = 1, active = second)

        val third = assertNotNull(scheduler.nextPart(workerId = 0))
        assertEquals(DownloadPart(start = 5, endInclusive = 9), third.part)
    }

    @Test
    fun `tail scheduler keeps a bounded fresh part count`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 320,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 8,
            tuning = testTuning.copy(tailPartsPerConnection = 4),
        )
        val parts = buildList {
            var workerId = 0
            while (true) {
                val active = scheduler.nextPart(workerId) ?: break
                add(active.part)
                scheduler.finish(workerId, active)
                workerId = (workerId + 1) % 8
            }
        }

        assertEquals(32, parts.size)
        assertEquals(320, parts.sumOf(DownloadPart::length))
        assertTrue(parts.all { it.length == 10L })
    }

    @Test
    fun `scheduler grows next part size from worker throughput`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 1_000,
            initialPartSizeBytes = 10,
            maxRetriesPerPart = 2,
            concurrency = 1,
            tuning = testTuning.copy(
                maxDynamicPartSizeBytes = 400,
                partSizeTargetDurationMs = 10_000,
            ),
        )

        val first = assertNotNull(scheduler.nextPart(workerId = 0))
        assertEquals(DownloadPart(start = 0, endInclusive = 9), first.part)

        scheduler.finish(workerId = 0, active = first)
        scheduler.recordSuccess(
            workerId = 0,
            part = first.part,
            bytes = first.part.length,
            elapsedMs = 100,
        )

        assertEquals(
            DownloadPart(start = 600, endInclusive = 999),
            assertNotNull(scheduler.nextPart(workerId = 0)).part,
        )
    }

    @Test
    fun `idle worker waits while remaining bytes belong to active part`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 500,
            initialPartSizeBytes = 500,
            maxRetriesPerPart = 2,
            concurrency = 2,
            tuning = testTuning.copy(
                tailWindowInitialMultiplier = 0,
            ),
            nowMs = { 1_000L },
        )
        val active = assertNotNull(scheduler.nextPart(workerId = 0))
        active.advanceTo(100)

        assertNull(scheduler.nextPart(workerId = 1))
        assertEquals(499, active.currentEndInclusive())
        assertEquals(0, scheduler.stats().stealCount)
        assertEquals(0, scheduler.stats().handoffCount)
    }

    @Test
    fun `scheduler reuses a released startup slot before growth`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 1_200,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 2,
            concurrency = 6,
            tuning = testTuning.copy(
                startupActiveConnections = 4,
                tailWindowInitialMultiplier = 0,
            ),
        )
        val active = (0 until 4).map { workerId ->
            assertNotNull(scheduler.nextPart(workerId))
        }

        assertNull(scheduler.nextPart(workerId = 4))

        val completed = active.first()
        scheduler.finish(workerId = 0, active = completed)
        scheduler.recordSuccess(
            workerId = 0,
            part = completed.part,
            bytes = completed.part.length,
            elapsedMs = 100,
        )

        assertNotNull(scheduler.nextPart(workerId = 0))
        Unit
    }

    @Test
    fun `retry budgets are independent for eof and rate limit failures`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 100,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 1,
            tuning = testTuning,
            retryBudgets = RangeRetryBudgets(
                partialEof = 1,
                timeout = 0,
                connectionReset = 0,
                rateLimited = 1,
                transient = 0,
            ),
        )
        val initial = assertNotNull(scheduler.nextPart(workerId = 0))
        scheduler.finish(workerId = 0, active = initial)

        assertTrue(
            scheduler.requeueFailed(
                part = initial.part,
                nextStart = 0,
                failureKind = RangeFailureKind.PartialEof,
            ),
        )
        val afterEof = assertNotNull(scheduler.nextPart(workerId = 0))
        scheduler.finish(workerId = 0, active = afterEof)

        assertTrue(
            scheduler.requeueFailed(
                part = afterEof.part,
                nextStart = 0,
                failureKind = RangeFailureKind.RateLimited,
            ),
        )
        val afterBoth = assertNotNull(scheduler.nextPart(workerId = 0))
        scheduler.finish(workerId = 0, active = afterBoth)

        assertFalse(
            scheduler.requeueFailed(
                part = afterBoth.part,
                nextStart = 0,
                failureKind = RangeFailureKind.PartialEof,
            ),
        )
        assertFalse(
            scheduler.requeueFailed(
                part = afterBoth.part,
                nextStart = 0,
                failureKind = RangeFailureKind.RateLimited,
            ),
        )
        assertEquals(2, scheduler.stats().retryCount)
    }

    @Test
    fun `rate limit strikes reduce concurrency and recover with one probe`() = runBlocking {
        var now = 0L
        val scheduler = PartScheduler(
            totalBytes = 10_000,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 6,
            tuning = testTuning.copy(
                startupActiveConnections = 6,
                tailWindowInitialMultiplier = 0,
                rateLimitMinActiveConnections = 2,
                rateLimitStrikeThreshold = 2,
                rateLimitWindowMs = 1_000,
                rateLimitCooldownMs = 100,
                rateLimitRecoveryMs = 100,
                rateLimitedMinPartSizeBytes = 100,
            ),
            nowMs = { now },
        )

        repeat(2) { workerId ->
            val limited = assertNotNull(scheduler.nextPart(workerId))
            scheduler.finish(workerId, limited)
            scheduler.recordRateLimit(part = limited.part, delayMs = 100)
        }

        val admitted = (0 until 5).map { workerId ->
            assertNotNull(scheduler.nextPart(workerId))
        }
        assertNull(scheduler.nextPart(workerId = 5))

        scheduler.finish(workerId = 0, active = admitted.first())
        now = 99
        assertNotNull(scheduler.nextPart(workerId = 0))
        now = 100
        val probe = assertNotNull(scheduler.nextPart(workerId = 5))

        assertTrue(probe.part.rateProbe)

        scheduler.recordRateLimit(part = probe.part, delayMs = 100)
        scheduler.finish(workerId = 5, active = probe)
        assertNull(scheduler.nextPart(workerId = 5))
    }

    @Test
    fun `rate limited scheduler increases part size floor`() = runBlocking {
        val scheduler = PartScheduler(
            totalBytes = 5_000,
            initialPartSizeBytes = 100,
            maxRetriesPerPart = 1,
            concurrency = 4,
            tuning = testTuning.copy(
                tailWindowInitialMultiplier = 0,
                tailWindowMinDynamicMultiplier = 0,
                rateLimitStrikeThreshold = 1,
                rateLimitedMinPartSizeBytes = 400,
                limitedTailPartsPerConnection = 1,
            ),
        )
        val limited = assertNotNull(scheduler.nextPart(workerId = 0))
        scheduler.finish(workerId = 0, active = limited)
        scheduler.recordRateLimit(part = limited.part, delayMs = 100)

        val next = assertNotNull(scheduler.nextPart(workerId = 1))

        assertEquals(400, next.part.length)
    }

    private companion object {
        val testTuning = PartSchedulerTuning(
            minDynamicPartSizeBytes = 1,
            minTailPartSizeBytes = 1,
            startupActiveConnections = 8,
        )
    }

    private suspend fun confirmProbeWave(
        scheduler: PartScheduler,
        expectedActive: Int,
    ) {
        val wave = (0 until expectedActive).map { workerId ->
            assertNotNull(scheduler.nextPart(workerId))
        }
        wave.forEachIndexed { workerId, active ->
            scheduler.confirmConcurrencyProbe(
                workerId = workerId,
                generation = active.concurrencyProbeGeneration,
                transferredBytes = scheduler.concurrencyProbeConfirmBytes,
            )
        }
        wave.forEachIndexed { workerId, active -> scheduler.finish(workerId, active) }
    }
}
