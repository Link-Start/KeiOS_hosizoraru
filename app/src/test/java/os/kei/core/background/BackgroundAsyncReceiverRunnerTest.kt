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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = BackgroundAsyncReceiverRunnerTestApp::class, sdk = [35])
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
        ) {
            Unit
        }
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
}

class BackgroundAsyncReceiverRunnerTestApp : Application()
