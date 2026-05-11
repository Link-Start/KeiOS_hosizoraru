package os.kei.ui.page.main.github.install

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GitHubApkInstallFlowModelsTest {
    @Test
    fun `github install entry sources auto start download by default`() {
        assertTrue(GitHubApkInstallSourceKind.TrackedReleaseAsset.defaultAutoStartDownload())
        assertTrue(GitHubApkInstallSourceKind.ReleaseAsset.defaultAutoStartDownload())
        assertTrue(GitHubApkInstallSourceKind.ActionsArtifact.defaultAutoStartDownload())
    }

    @Test
    fun `manual fallback can keep remote ready as user decision`() {
        val state = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.RemoteReady,
            autoStartDownload = false
        )

        assertFalse(state.autoStartDownload)
        assertTrue(state.needsUserDecision)
    }

    @Test
    fun `notification first falls back to sheet when notification dispatch fails`() {
        val initial = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.RemoteResolving,
            notificationFirst = true,
            sheetVisible = false
        )

        val visible = initial.visibleAfterNotificationResult(notificationShown = false)

        assertTrue(visible.sheetVisible)
        assertFalse(visible.notificationFirst)
    }

    @Test
    fun `notification first stays hidden when notification dispatch succeeds`() {
        val initial = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.RemoteResolving,
            notificationFirst = true,
            sheetVisible = false
        )

        assertSame(
            initial,
            initial.visibleAfterNotificationResult(notificationShown = true)
        )
    }

    @Test
    fun `sheet first keeps sheet visible after notification dispatch fails`() {
        val initial = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.RemoteResolving,
            notificationFirst = false,
            sheetVisible = true
        )

        val visible = initial.visibleAfterNotificationResult(notificationShown = false)

        assertTrue(visible.sheetVisible)
        assertFalse(visible.notificationFirst)
    }
}
