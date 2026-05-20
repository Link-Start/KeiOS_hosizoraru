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
    fun `system app scanning is disabled by default`() {
        assertEquals(false, GitHubLookupConfig().scanSystemAppsByDefault)
    }

    @Test
    fun `system app scanning participates in check source signature`() {
        val disabled = GitHubLookupConfig(scanSystemAppsByDefault = false)
        val enabled = GitHubLookupConfig(scanSystemAppsByDefault = true)

        assertEquals(
            false,
            disabled.githubCheckSourceSignature() == enabled.githubCheckSourceSignature()
        )
    }

    @Test
    fun `tracked precise apk version mode overrides global config`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example"
        )

        assertEquals(
            true,
            GitHubLookupConfig(preciseApkVersionEnabled = false)
                .forTrackedItem(
                    item.copy(
                        preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Enabled
                    )
                )
                .preciseApkVersionEnabled
        )
        assertEquals(
            false,
            GitHubLookupConfig(preciseApkVersionEnabled = true)
                .forTrackedItem(
                    item.copy(
                        preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Disabled
                    )
                )
                .preciseApkVersionEnabled
        )
        assertEquals(
            true,
            GitHubLookupConfig(preciseApkVersionEnabled = true)
                .forTrackedItem(item)
                .preciseApkVersionEnabled
        )
    }

    @Test
    fun `tracked prefer pre release enables pre release lookup for that item`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example"
        )
        val globalDisabled = GitHubLookupConfig(checkAllTrackedPreReleases = false)

        assertEquals(
            true,
            globalDisabled
                .forTrackedItem(item.copy(preferPreRelease = true))
                .checkAllTrackedPreReleases
        )
        assertEquals(
            false,
            globalDisabled
                .forTrackedItem(item.copy(preferPreRelease = false))
                .checkAllTrackedPreReleases
        )
    }

    @Test
    fun `tracked item lookup resolves precise apk and pre release together`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example",
            preferPreRelease = true,
            preciseApkVersionMode = GitHubTrackedPreciseApkVersionMode.Enabled
        )
        val resolved = GitHubLookupConfig(
            checkAllTrackedPreReleases = false,
            preciseApkVersionEnabled = false
        ).forTrackedItem(item)

        assertEquals(true, resolved.checkAllTrackedPreReleases)
        assertEquals(true, resolved.preciseApkVersionEnabled)
    }

    @Test
    fun `tracked actions update interval mode resolves storage ids`() {
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId("follow_global")
        )
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.Minutes15,
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId("15m")
        )
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.Hours2,
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId("2h")
        )
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.Hours3,
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId("3h")
        )
        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId("missing")
        )
    }

    @Test
    fun `tracked update interval mode resolves storage ids`() {
        assertEquals(
            GitHubTrackedUpdateIntervalMode.FollowGlobal,
            GitHubTrackedUpdateIntervalMode.fromStorageId("follow_global")
        )
        assertEquals(
            GitHubTrackedUpdateIntervalMode.Hour1,
            GitHubTrackedUpdateIntervalMode.fromStorageId("1h")
        )
        assertEquals(
            GitHubTrackedUpdateIntervalMode.Hours6,
            GitHubTrackedUpdateIntervalMode.fromStorageId("6h")
        )
        assertEquals(
            GitHubTrackedUpdateIntervalMode.FollowGlobal,
            GitHubTrackedUpdateIntervalMode.fromStorageId("missing")
        )
    }

    @Test
    fun `tracked update interval follows global or custom hours`() {
        val global = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example"
        )
        val custom = global.copy(updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hour1)

        assertEquals(3L * 60L * 60L * 1000L, global.updateIntervalMs(3))
        assertEquals(1L * 60L * 60L * 1000L, custom.updateIntervalMs(3))
    }

    @Test
    fun `tracked actions update interval follows global or custom minutes`() {
        val global = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example",
            checkActionsUpdates = true
        )
        val custom = global.copy(
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes30
        )
        val customThreeHours = global.copy(
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Hours3
        )

        assertEquals(3L * 60L * 60L * 1000L, global.actionsUpdateIntervalMs(3))
        assertEquals(30L * 60L * 1000L, custom.actionsUpdateIntervalMs(3))
        assertEquals(3L * 60L * 60L * 1000L, customThreeHours.actionsUpdateIntervalMs(1))
    }

    @Test
    fun `direct apk lookup uses subscription pre release switch`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://example.com/app.apk",
            owner = "example.com",
            repo = "app",
            packageName = "com.example.app",
            appLabel = "Example",
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )

        assertEquals(
            false,
            GitHubLookupConfig(checkAllDirectApkPreReleases = false)
                .forTrackedItem(item)
                .checkAllTrackedPreReleases
        )
        assertEquals(
            true,
            GitHubLookupConfig(checkAllDirectApkPreReleases = true)
                .forTrackedItem(item)
                .checkAllTrackedPreReleases
        )
        assertEquals(
            true,
            GitHubLookupConfig(preciseApkVersionEnabled = false)
                .forTrackedItem(item)
                .preciseApkVersionEnabled
        )
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
