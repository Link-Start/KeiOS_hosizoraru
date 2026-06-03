package os.kei.feature.github.domain

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubRefreshRuntimeStoreTest {
    @After
    fun tearDown() {
        GitHubRefreshRuntimeStore.clear()
    }

    @Test
    fun `begin creates a running scoped session`() {
        val session =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.AllTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 75,
                targetCount = 75,
                nowMs = 1_000L,
            )

        assertNotNull(session)
        val state = GitHubRefreshRuntimeStore.state.value
        assertEquals(session.id, state.sessionId)
        assertEquals(GitHubRefreshRuntimePhase.Running, state.phase)
        assertEquals(GitHubRefreshScope.AllTracked, state.scope)
        assertEquals(GitHubRefreshSource.Page, state.source)
        assertEquals(75, state.totalTrackedCount)
        assertEquals(75, state.targetCount)
        assertEquals(1_000L, state.startedAtMs)
        assertTrue(state.running)
    }

    @Test
    fun `progress from a stale session is ignored`() {
        val staleSession =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.DueTracked,
                source = GitHubRefreshSource.BackgroundTick,
                totalTrackedCount = 75,
                targetCount = 1,
            )
        val activeSession =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.AllTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 75,
                targetCount = 75,
            )
        assertNotNull(staleSession)
        assertNotNull(activeSession)

        GitHubRefreshRuntimeStore.progress(
            sessionId = staleSession.id,
            completedCount = 1,
            updatableCount = 1,
            preReleaseUpdateCount = 0,
            failedCount = 0,
        )

        val state = GitHubRefreshRuntimeStore.state.value
        assertEquals(activeSession.id, state.sessionId)
        assertEquals(0, state.completedCount)
        assertEquals(0, state.updatableCount)
        assertEquals(75, state.targetCount)
    }

    @Test
    fun `complete from a stale session is ignored`() {
        val staleSession =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.DueTracked,
                source = GitHubRefreshSource.BackgroundTick,
                totalTrackedCount = 75,
                targetCount = 1,
            )
        val activeSession =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.VisibleTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 75,
                targetCount = 3,
            )
        assertNotNull(staleSession)
        assertNotNull(activeSession)

        GitHubRefreshRuntimeStore.complete(
            sessionId = staleSession.id,
            completedCount = 1,
            updatableCount = 1,
            preReleaseUpdateCount = 0,
            failedCount = 0,
        )

        val state = GitHubRefreshRuntimeStore.state.value
        assertEquals(activeSession.id, state.sessionId)
        assertEquals(GitHubRefreshRuntimePhase.Running, state.phase)
        assertTrue(state.running)
        assertEquals(0, state.completedCount)
    }

    @Test
    fun `new session supersedes previous session and resets progress`() {
        val first =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.AllTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 10,
                targetCount = 10,
            )
        assertNotNull(first)
        GitHubRefreshRuntimeStore.progress(
            sessionId = first.id,
            completedCount = 6,
            updatableCount = 2,
            preReleaseUpdateCount = 1,
            failedCount = 1,
        )

        val second =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.ShortcutAllTracked,
                source = GitHubRefreshSource.Shortcut,
                totalTrackedCount = 75,
                targetCount = 75,
            )

        assertNotNull(second)
        val state = GitHubRefreshRuntimeStore.state.value
        assertEquals(second.id, state.sessionId)
        assertEquals(0, state.completedCount)
        assertEquals(0, state.updatableCount)
        assertEquals(0, state.preReleaseUpdateCount)
        assertEquals(0, state.failedCount)
        assertEquals(75, state.targetCount)
        assertEquals(GitHubRefreshScope.ShortcutAllTracked, state.scope)
    }

    @Test
    fun `skip policy rejects background session while another session runs`() {
        val active =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.AllTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 75,
                targetCount = 75,
            )
        assertNotNull(active)

        val skipped =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.DueTracked,
                source = GitHubRefreshSource.BackgroundTick,
                totalTrackedCount = 75,
                targetCount = 2,
                policy = GitHubRefreshBeginPolicy.SkipWhenRunning,
            )

        assertNull(skipped)
        assertEquals(active.id, GitHubRefreshRuntimeStore.state.value.sessionId)
        assertTrue(GitHubRefreshRuntimeStore.state.value.running)
    }

    @Test
    fun `cancel closes only the matching session`() {
        val stale =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.DueTracked,
                source = GitHubRefreshSource.BackgroundTick,
                totalTrackedCount = 5,
                targetCount = 1,
            )
        val active =
            GitHubRefreshRuntimeStore.begin(
                scope = GitHubRefreshScope.AllTracked,
                source = GitHubRefreshSource.Page,
                totalTrackedCount = 5,
                targetCount = 5,
            )
        assertNotNull(stale)
        assertNotNull(active)

        GitHubRefreshRuntimeStore.cancel(
            sessionId = stale.id,
            completedCount = 1,
            updatableCount = 1,
            preReleaseUpdateCount = 0,
            failedCount = 0,
        )

        assertTrue(GitHubRefreshRuntimeStore.state.value.running)

        GitHubRefreshRuntimeStore.cancel(
            sessionId = active.id,
            completedCount = 2,
            updatableCount = 1,
            preReleaseUpdateCount = 0,
            failedCount = 0,
        )

        val state = GitHubRefreshRuntimeStore.state.value
        assertFalse(state.running)
        assertEquals(GitHubRefreshRuntimePhase.Cancelled, state.phase)
        assertEquals(2, state.completedCount)
        assertEquals(1, state.updatableCount)
    }
}
