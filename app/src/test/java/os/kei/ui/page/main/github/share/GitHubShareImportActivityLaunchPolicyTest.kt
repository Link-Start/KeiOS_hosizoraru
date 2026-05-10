package os.kei.ui.page.main.github.share

import org.junit.Test
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubShareImportFlowMode
import kotlin.test.assertEquals

class GitHubShareImportActivityLaunchPolicyTest {
    @Test
    fun `notification first incoming share starts hidden`() {
        assertEquals(
            GitHubShareImportActivityDisplayState.Hidden,
            GitHubShareImportActivityLaunchPolicy.forIncomingShare(
                sharedText = "https://github.com/open-ani/animeko/releases",
                lookupConfig = GitHubLookupConfig(
                    shareImportLinkageEnabled = true,
                    shareImportFlowMode = GitHubShareImportFlowMode.NotificationFirst
                )
            )
        )
    }

    @Test
    fun `sheet assisted incoming share starts sheet`() {
        assertEquals(
            GitHubShareImportActivityDisplayState.Sheet,
            GitHubShareImportActivityLaunchPolicy.forIncomingShare(
                sharedText = "https://github.com/open-ani/animeko/releases",
                lookupConfig = GitHubLookupConfig(
                    shareImportLinkageEnabled = true,
                    shareImportFlowMode = GitHubShareImportFlowMode.SheetAssisted
                )
            )
        )
    }

    @Test
    fun `disabled share import shows disabled state`() {
        assertEquals(
            GitHubShareImportActivityDisplayState.Disabled,
            GitHubShareImportActivityLaunchPolicy.forIncomingShare(
                sharedText = "https://github.com/open-ani/animeko/releases",
                lookupConfig = GitHubLookupConfig(
                    shareImportLinkageEnabled = false,
                    shareImportFlowMode = GitHubShareImportFlowMode.NotificationFirst
                )
            )
        )
    }

    @Test
    fun `invalid incoming share finishes activity`() {
        assertEquals(
            GitHubShareImportActivityDisplayState.Finish,
            GitHubShareImportActivityLaunchPolicy.forIncomingShare(
                sharedText = "hello",
                lookupConfig = GitHubLookupConfig(
                    shareImportLinkageEnabled = true,
                    shareImportFlowMode = GitHubShareImportFlowMode.NotificationFirst
                )
            )
        )
    }
}
