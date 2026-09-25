package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class GitHubLookupModelsTest {
    @Test
    fun `persisted storage ids resolve and unknown ids fall back`() {
        val shareFlow: (String) -> Enum<*> = { GitHubShareImportFlowMode.fromStorageId(it) }
        val profileDepth: (String) -> Enum<*> = { GitHubProfileDepth.fromStorageId(it) }
        val actionsInterval: (String) -> Enum<*> = {
            GitHubTrackedActionsUpdateIntervalMode.fromStorageId(it)
        }
        val updateInterval: (String) -> Enum<*> = { GitHubTrackedUpdateIntervalMode.fromStorageId(it) }
        val cases = listOf<Triple<(String) -> Enum<*>, String, Enum<*>>>(
            Triple(shareFlow, "sheet_assisted", GitHubShareImportFlowMode.SheetAssisted),
            Triple(shareFlow, "notification_first", GitHubShareImportFlowMode.NotificationFirst),
            Triple(shareFlow, "missing", GitHubShareImportFlowMode.SheetAssisted),
            Triple(profileDepth, "basic", GitHubProfileDepth.Basic),
            Triple(profileDepth, "deep", GitHubProfileDepth.Deep),
            Triple(profileDepth, "missing", GitHubProfileDepth.Basic),
            Triple(actionsInterval, "follow_global", GitHubTrackedActionsUpdateIntervalMode.FollowGlobal),
            Triple(actionsInterval, "15m", GitHubTrackedActionsUpdateIntervalMode.Minutes15),
            Triple(actionsInterval, "2h", GitHubTrackedActionsUpdateIntervalMode.Hours2),
            Triple(actionsInterval, "3h", GitHubTrackedActionsUpdateIntervalMode.Hours3),
            Triple(actionsInterval, "missing", GitHubTrackedActionsUpdateIntervalMode.FollowGlobal),
            Triple(updateInterval, "follow_global", GitHubTrackedUpdateIntervalMode.FollowGlobal),
            Triple(updateInterval, "1h", GitHubTrackedUpdateIntervalMode.Hour1),
            Triple(updateInterval, "6h", GitHubTrackedUpdateIntervalMode.Hours6),
            Triple(updateInterval, "24h", GitHubTrackedUpdateIntervalMode.Hours24),
            Triple(updateInterval, "missing", GitHubTrackedUpdateIntervalMode.FollowGlobal),
        )

        cases.forEach { (parse, id, expected) ->
            assertEquals(
                expected,
                parse(id),
                "${expected::class.simpleName}.fromStorageId(\"$id\")",
            )
        }
    }

    @Test
    fun `each check-signature input changes the signature`() {
        val cases = listOf(
            Triple(
                "profileDepth Basic/Deep",
                GitHubLookupConfig(profileDepth = GitHubProfileDepth.Basic),
                GitHubLookupConfig(profileDepth = GitHubProfileDepth.Deep),
            ),
            Triple(
                "scanSystemAppsByDefault false/true",
                GitHubLookupConfig(scanSystemAppsByDefault = false),
                GitHubLookupConfig(scanSystemAppsByDefault = true),
            ),
        )

        cases.forEach { (label, first, second) ->
            assertNotEquals(
                first.githubCheckSourceSignature(),
                second.githubCheckSourceSignature(),
                label,
            )
        }
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
        assertEquals(12L * 60L * 60L * 1000L, global.updateIntervalMs(12))
        assertEquals(1L * 60L * 60L * 1000L, custom.updateIntervalMs(3))
    }

    @Test
    fun `fdroid tracked update interval uses source default and supports daily override`() {
        val global = GitHubTrackedApp(
            repoUrl = "https://f-droid.org/repo",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository
        )
        val daily = global.copy(updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours24)

        assertEquals(12, global.updateIntervalHours(3))
        assertEquals(12L * 60L * 60L * 1000L, global.updateIntervalMs(3))
        assertEquals(24, daily.updateIntervalHours(3))
        assertEquals(24L * 60L * 60L * 1000L, daily.updateIntervalMs(3))
    }

    @Test
    fun `source constraints keep daily update interval scoped to fdroid`() {
        val githubItem = GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "com.example.app",
            appLabel = "Example",
            updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours24,
        ).withSourceModeConstraints()
        val fdroidItem = GitHubTrackedApp(
            repoUrl = "https://f-droid.org/repo",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository,
            updateIntervalMode = GitHubTrackedUpdateIntervalMode.Hours24,
        ).withSourceModeConstraints()

        assertEquals(GitHubTrackedUpdateIntervalMode.FollowGlobal, githubItem.updateIntervalMode)
        assertEquals(GitHubTrackedUpdateIntervalMode.Hours24, fdroidItem.updateIntervalMode)
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
}
