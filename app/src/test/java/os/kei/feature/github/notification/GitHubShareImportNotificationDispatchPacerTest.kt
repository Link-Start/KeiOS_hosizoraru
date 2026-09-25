package os.kei.feature.github.notification

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

class GitHubShareImportNotificationDispatchPacerTest {
    @Test
    fun `terminal delivery waits for the notification manager safety window`() {
        val pacer = GitHubShareImportNotificationDispatchPacer(
            minimumIntervalMs = 400L,
        )

        pacer.markDispatched(atElapsedRealtimeMs = 1_000L)

        assertEquals(250L, pacer.delayUntilReady(atElapsedRealtimeMs = 1_150L))
    }

    @Test
    fun `delivery proceeds immediately after the notification manager safety window`() {
        val pacer = GitHubShareImportNotificationDispatchPacer(
            minimumIntervalMs = 400L,
        )

        pacer.markDispatched(atElapsedRealtimeMs = 1_000L)

        assertEquals(0L, pacer.delayUntilReady(atElapsedRealtimeMs = 1_400L))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class GitHubShareImportNotificationPostSchedulerTest {
    @Test
    fun `latest terminal state replaces queued progress`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val postedPhases = mutableListOf<GitHubShareImportNotificationPhase>()
        val initialDelivered = CompletableDeferred<Unit>()
        val terminalDelivered = CompletableDeferred<Unit>()
        val scheduler = GitHubShareImportNotificationPostScheduler(
            minimumIntervalMs = 80L,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            nowElapsedRealtimeMs = SystemClock::elapsedRealtime,
        )
        val post: (Context, GitHubShareImportNotificationState) -> Unit = { _, state ->
            synchronized(postedPhases) {
                postedPhases += state.phase
            }
            when (state.phase) {
                GitHubShareImportNotificationPhase.Resolving -> initialDelivered.complete(Unit)
                GitHubShareImportNotificationPhase.PageInstallCompleted ->
                    terminalDelivered.complete(Unit)
                else -> Unit
            }
        }

        scheduler.enqueue(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Resolving,
            ),
            post = post,
        )
        withTimeout(1_000L) { initialDelivered.await() }
        scheduler.enqueue(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.Installing,
            ),
            post = post,
        )
        scheduler.enqueue(
            context = context,
            state = GitHubShareImportNotificationState(
                phase = GitHubShareImportNotificationPhase.PageInstallCompleted,
            ),
            post = post,
        )

        withTimeout(1_000L) { terminalDelivered.await() }
        delay(120L)

        assertEquals(
            listOf(
                GitHubShareImportNotificationPhase.Resolving,
                GitHubShareImportNotificationPhase.PageInstallCompleted,
            ),
            synchronized(postedPhases) { postedPhases.toList() },
        )
    }
}
