package os.kei.feature.github.data.local

import org.junit.Test
import os.kei.feature.github.domain.GitHubActionsService
import os.kei.feature.github.model.GitHubActionsNotificationHistoryRecord
import os.kei.feature.github.model.GitHubActionsRecommendedRunSnapshot
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GitHubActionsNotificationHistoryStoreTest {
    @Test
    fun `notification history record round trips through json`() {
        val record = createRecord()

        val decoded = GitHubActionsNotificationHistoryStore.decodeRecord(
            GitHubActionsNotificationHistoryStore.encodeRecord(record).toString()
        )

        assertEquals(record, decoded)
    }

    @Test
    fun `notification history id dedupes the same tracked run`() {
        val first = createRecord(notifiedAtMillis = 1000L)
        val refreshed = first.copy(
            notificationContent = "Animeko #44 refreshed",
            notifiedAtMillis = 2000L,
        )
        val otherTrack = first.copy(trackId = "open-ani/animeko|dev.pkg")

        val firstId =
            with(GitHubActionsNotificationHistoryStore) {
                recordId(first.normalizedForStorage())
            }
        val refreshedId =
            with(GitHubActionsNotificationHistoryStore) {
                recordId(refreshed.normalizedForStorage())
            }
        val otherTrackId =
            with(GitHubActionsNotificationHistoryStore) {
                recordId(otherTrack.normalizedForStorage())
            }

        assertEquals(firstId, refreshedId)
        assertNotEquals(firstId, otherTrackId)
    }

    @Test
    fun `service builds notification history from recommended run snapshot`() {
        val snapshot = createSnapshot()
        val record =
            GitHubActionsService().buildGitHubActionsNotificationHistoryRecord(
                snapshot = snapshot,
                notificationTitle = "GitHub Actions 有新构建",
                notificationContent = "Animeko #44 · CI / Benchmark APK",
                notifiedAtMillis = 1778000300000L,
            )

        assertEquals(snapshot.trackId, record.trackId)
        assertEquals(snapshot.owner, record.owner)
        assertEquals(snapshot.repo, record.repo)
        assertEquals(snapshot.workflowName, record.workflowName)
        assertEquals(snapshot.runId, record.runId)
        assertEquals(snapshot.runLabel, record.runLabel)
        assertEquals(snapshot.androidArtifactCount, record.androidArtifactCount)
        assertEquals("Animeko #44 · CI / Benchmark APK", record.notificationContent)
        assertEquals(1778000300000L, record.notifiedAtMillis)
    }

    private fun createRecord(notifiedAtMillis: Long = 1778000300000L): GitHubActionsNotificationHistoryRecord =
        GitHubActionsNotificationHistoryRecord(
            trackId = "open-ani/animeko|me.him188.ani",
            owner = "open-ani",
            repo = "animeko",
            appLabel = "Animeko",
            workflowId = 42L,
            workflowName = "CI / Benchmark APK",
            workflowPath = ".github/workflows/android.yml",
            runId = 4444L,
            runNumber = 44L,
            runAttempt = 1,
            runDisplayName = "Build #44",
            headBranch = "main",
            headSha = "abcdef0",
            event = "workflow_dispatch",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/open-ani/animeko/actions/runs/4444",
            artifactCount = 2,
            androidArtifactCount = 1,
            checkedAtMillis = 1778000200000L,
            notifiedAtMillis = notifiedAtMillis,
            notificationTitle = "GitHub Actions 有新构建",
            notificationContent = "Animeko #44 · CI / Benchmark APK",
        )

    private fun createSnapshot(): GitHubActionsRecommendedRunSnapshot =
        GitHubActionsRecommendedRunSnapshot(
            trackId = "open-ani/animeko|me.him188.ani",
            owner = "open-ani",
            repo = "animeko",
            appLabel = "Animeko",
            workflowId = 42L,
            workflowName = "CI / Benchmark APK",
            workflowPath = ".github/workflows/android.yml",
            runId = 4444L,
            runNumber = 44L,
            runAttempt = 1,
            runDisplayName = "Build #44",
            headBranch = "main",
            headSha = "abcdef0",
            event = "workflow_dispatch",
            status = "completed",
            conclusion = "success",
            htmlUrl = "https://github.com/open-ani/animeko/actions/runs/4444",
            artifactCount = 2,
            androidArtifactCount = 1,
            createdAtMillis = 1778000000000L,
            updatedAtMillis = 1778000100000L,
            checkedAtMillis = 1778000200000L,
        )
}
