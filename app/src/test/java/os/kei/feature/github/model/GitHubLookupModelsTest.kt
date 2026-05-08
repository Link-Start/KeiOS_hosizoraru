package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class GitHubLookupModelsTest {
    @Test
    fun `share import flow mode resolves storage ids`() {
        assertEquals(
            GitHubShareImportFlowMode.SheetAssisted,
            GitHubShareImportFlowMode.fromStorageId("sheet_assisted")
        )
        assertEquals(
            GitHubShareImportFlowMode.NotificationFirst,
            GitHubShareImportFlowMode.fromStorageId("notification_first")
        )
    }

    @Test
    fun `unknown share import flow mode falls back to sheet assisted`() {
        assertEquals(
            GitHubShareImportFlowMode.SheetAssisted,
            GitHubShareImportFlowMode.fromStorageId("missing")
        )
    }

    @Test
    fun `profile depth resolves storage ids`() {
        assertEquals(GitHubProfileDepth.Basic, GitHubProfileDepth.fromStorageId("basic"))
        assertEquals(GitHubProfileDepth.Deep, GitHubProfileDepth.fromStorageId("deep"))
        assertEquals(GitHubProfileDepth.Basic, GitHubProfileDepth.fromStorageId("missing"))
    }

    @Test
    fun `profile depth participates in check source signature`() {
        val basic = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Basic)
        val deep = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep)

        assertEquals(false, basic.githubCheckSourceSignature() == deep.githubCheckSourceSignature())
    }
}
