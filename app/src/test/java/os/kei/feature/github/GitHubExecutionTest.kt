package os.kei.feature.github

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class GitHubExecutionTest {
    @Test
    fun `mapOrderedBounded preserves order and caps active work`() = runBlocking {
        val activeCount = AtomicInteger(0)
        val maxActiveCount = AtomicInteger(0)

        val result = GitHubExecution.mapOrderedBounded(
            items = (1..8).toList(),
            maxConcurrency = 3,
            dispatcher = Dispatchers.Default
        ) { value ->
            val active = activeCount.incrementAndGet()
            maxActiveCount.updateAndGet { current -> maxOf(current, active) }
            delay(20.milliseconds)
            activeCount.decrementAndGet()
            value * 2
        }

        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result)
        assertTrue(maxActiveCount.get() <= 3)
    }

    @Test
    fun `mapOrderedBounded propagates task failure`() = runBlocking {
        val error = assertFailsWith<IllegalStateException> {
            GitHubExecution.mapOrderedBounded(
                items = listOf(1, 2, 3),
                maxConcurrency = 2,
                dispatcher = Dispatchers.Default
            ) { value ->
                if (value == 2) error("boom")
                value
            }
        }

        assertEquals("boom", error.message)
    }

    @Test
    fun `firstSuccessBounded returns first successful result`() = runBlocking {
        val result = GitHubExecution.firstSuccessBounded(
            items = listOf(1, 2, 3),
            maxConcurrency = 3,
            dispatcher = Dispatchers.Default
        ) { value ->
            when (value) {
                1 -> {
                    delay(40.milliseconds)
                    Result.failure(IllegalStateException("slow failure"))
                }

                2 -> {
                    delay(10.milliseconds)
                    Result.success("winner")
                }

                else -> {
                    delay(80.milliseconds)
                    Result.success("late")
                }
            }
        }

        assertEquals("winner", result.getOrThrow())
    }

    @Test
    fun `singleFlight shares active work and cleans up after failure`() = runBlocking {
        val singleFlight = GitHubSingleFlight<String, Int>()
        val startedCount = AtomicInteger(0)
        val startGate = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()

        val shared = (1..8).map {
            async(Dispatchers.Default) {
                startGate.await()
                singleFlight.run("same") {
                    startedCount.incrementAndGet()
                    gate.await()
                    Result.success(42)
                }
            }
        }
        startGate.complete(Unit)
        while (startedCount.get() == 0) {
            yield()
        }
        delay(50.milliseconds)
        gate.complete(Unit)

        assertEquals(List(8) { 42 }, shared.awaitAll().map { it.getOrThrow() })
        assertEquals(1, startedCount.get())

        val failed = singleFlight.run("retryable") {
            startedCount.incrementAndGet()
            Result.failure(IllegalStateException("first failure"))
        }
        assertEquals("first failure", failed.exceptionOrNull()?.message)
        val recovered = singleFlight.run("retryable") {
            Result.success(7)
        }
        assertEquals(7, recovered.getOrThrow())
    }
}
