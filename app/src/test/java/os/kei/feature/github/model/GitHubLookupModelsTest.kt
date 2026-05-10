package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertContains
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
    fun `apk install delivery mode resolves storage ids`() {
        assertEquals(
            GitHubApkInstallDeliveryMode.AppShizuku,
            GitHubApkInstallDeliveryMode.fromStorageId("external")
        )
        assertEquals(
            GitHubApkInstallDeliveryMode.AppShizuku,
            GitHubApkInstallDeliveryMode.fromStorageId("app_shizuku")
        )
        assertEquals(
            GitHubApkInstallDeliveryMode.AppShizuku,
            GitHubApkInstallDeliveryMode.fromStorageId("missing")
        )
    }

    @Test
    fun `apk install ui mode resolves storage ids`() {
        assertEquals(
            GitHubApkInstallUiMode.SheetFirst,
            GitHubApkInstallUiMode.fromStorageId("sheet_first")
        )
        assertEquals(
            GitHubApkInstallUiMode.NotificationFirst,
            GitHubApkInstallUiMode.fromStorageId("notification_first")
        )
        assertEquals(
            GitHubApkInstallUiMode.SheetFirst,
            GitHubApkInstallUiMode.fromStorageId("missing")
        )
    }

    @Test
    fun `lookup config defaults to app shizuku install`() {
        val config = GitHubLookupConfig()

        assertEquals(GitHubApkInstallDeliveryMode.AppShizuku, config.apkInstallDeliveryMode)
        assertEquals(GitHubApkInstallUiMode.SheetFirst, config.apkInstallUiMode)
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

    @Test
    fun `profile source signature follows purpose capability set`() {
        val config = GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep)
        val fast =
            config.githubProfileSourceSignature(GitHubRepositoryProfilePurpose.VersionCheckFast)
        val health = config.githubProfileSourceSignature(GitHubRepositoryProfilePurpose.HealthCard)
        val detail = config.githubProfileSourceSignature(GitHubRepositoryProfilePurpose.DetailFull)

        assertEquals(false, fast == health)
        assertEquals(false, health == detail)
        assertContains(detail, GitHubRepositoryProfileCapability.Security.name)
        assertContains(health, GitHubRepositoryProfileCapability.Actions.name)
    }

    @Test
    fun `default profile purpose only promotes enabled health card`() {
        assertEquals(
            GitHubRepositoryProfilePurpose.VersionCheckFast,
            GitHubLookupConfig(
                decisionAssistEnabled = true,
                repositoryHealthCardEnabled = false
            ).defaultRepositoryProfilePurpose()
        )
        assertEquals(
            GitHubRepositoryProfilePurpose.HealthCard,
            GitHubLookupConfig(
                decisionAssistEnabled = true,
                repositoryHealthCardEnabled = true
            ).defaultRepositoryProfilePurpose()
        )
    }
}
