package os.kei.core.background

import android.app.Application
import android.app.job.JobScheduler
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class AppBackgroundTickReceiverTest {
    @Test
    fun `github tick enqueues scheduler job instead of running in receiver`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(GITHUB_BACKGROUND_REFRESH_JOB_ID)

        AppBackgroundTickReceiver().onReceive(
            context,
            Intent(context, AppBackgroundTickReceiver::class.java).apply {
                action = AppBackgroundTickReceiver.ACTION_GITHUB_TICK
            },
        )

        val pendingJob = scheduler.getPendingJob(GITHUB_BACKGROUND_REFRESH_JOB_ID)

        assertNotNull(pendingJob)
        assertEquals(
            GitHubBackgroundRefreshJobService::class.java.name,
            pendingJob.service.className,
        )
        assertNotNull(pendingJob.requiredNetwork)
    }

    @Test
    fun `github job enqueue is idempotent while job is pending`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(GITHUB_BACKGROUND_REFRESH_JOB_ID)

        assertTrue(GitHubBackgroundRefreshJobService.enqueueNow(context))
        assertTrue(GitHubBackgroundRefreshJobService.enqueueNow(context))

        val githubJobs = scheduler.allPendingJobs
            .filter { job -> job.id == GITHUB_BACKGROUND_REFRESH_JOB_ID }

        assertEquals(1, githubJobs.size)
        assertEquals(
            GitHubBackgroundRefreshJobService::class.java.name,
            githubJobs.single().service.className,
        )
    }

    @Test
    fun `github job info stores enqueue time for refresh history diagnostics`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val jobInfo = GitHubBackgroundRefreshJobService.buildJobInfo(
            context = context,
            enqueuedAtMillis = 123_456L,
        )

        assertEquals(
            123_456L,
            jobInfo.extras.getLong("github_background_enqueued_at_ms"),
        )
    }

    @Test
    fun `legacy WebDAV alarm enqueues a network constrained job`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(WEBDAV_AUTO_SYNC_JOB_ID)

        AppBackgroundTickReceiver().onReceive(
            context,
            Intent(context, AppBackgroundTickReceiver::class.java).apply {
                action = AppBackgroundTickReceiver.ACTION_WEBDAV_SYNC
            },
        )

        val pendingJob = scheduler.getPendingJob(WEBDAV_AUTO_SYNC_JOB_ID)
        assertNotNull(pendingJob)
        assertEquals(WebDavAutoSyncJobService::class.java.name, pendingJob.service.className)
        assertNotNull(pendingJob.requiredNetwork)
        assertTrue(pendingJob.isPersisted)
    }

    @Test
    fun `WebDAV job preserves due time and waits for network`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val jobInfo = WebDavAutoSyncJobService.buildJobInfo(
            context = context,
            dueAtMillis = 160_000L,
            nowMillis = 100_000L,
        )

        assertEquals(60_000L, jobInfo.minLatencyMillis)
        assertEquals(160_000L, jobInfo.extras.getLong("webdav_auto_sync_due_at_ms"))
        assertEquals(100_000L, jobInfo.extras.getLong("webdav_auto_sync_enqueued_at_ms"))
        assertNotNull(jobInfo.requiredNetwork)
        assertTrue(jobInfo.isPersisted)
    }
}
