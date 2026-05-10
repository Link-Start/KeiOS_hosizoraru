package os.kei.feature.github.install

import org.junit.Test
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase
import os.kei.ui.page.main.github.install.GitHubApkInstallProgressKind
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubApkInstallFlowStateTest {
    @Test
    fun `ready to install waits for user decision`() {
        val state = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.ReadyToInstall
        )

        assertTrue(state.active)
        assertTrue(state.needsUserDecision)
    }

    @Test
    fun `installing continues without user decision`() {
        val state = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.Installing
        )

        assertTrue(state.active)
        assertFalse(state.needsUserDecision)
    }

    @Test
    fun `cancelled remains active terminal notification state`() {
        val state = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.Cancelled,
            overallProgress = 0.42f
        )

        assertTrue(state.active)
        assertTrue(state.needsUserDecision)
        assertFalse(state.cancellable)
        assertTrue(state.overallProgressPercent == 42)
    }

    @Test
    fun `download progress is determinate only during download phase`() {
        val downloading = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.Downloading,
            progressKind = GitHubApkInstallProgressKind.Download,
            stageProgress = 0.62f
        )
        val installing = GitHubApkInstallFlowState(
            phase = GitHubApkInstallPhase.Installing,
            progressKind = GitHubApkInstallProgressKind.Staging,
            stageProgress = 0.62f
        )

        assertTrue(downloading.showsDeterminateDownloadProgress)
        assertFalse(installing.showsDeterminateDownloadProgress)
    }
}
