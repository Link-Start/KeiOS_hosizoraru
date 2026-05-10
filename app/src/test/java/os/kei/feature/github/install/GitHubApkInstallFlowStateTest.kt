package os.kei.feature.github.install

import org.junit.Test
import os.kei.ui.page.main.github.install.GitHubApkInstallFlowState
import os.kei.ui.page.main.github.install.GitHubApkInstallPhase
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
}
