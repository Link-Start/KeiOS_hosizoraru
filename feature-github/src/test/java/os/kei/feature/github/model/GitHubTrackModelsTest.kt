package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackModelsTest {
    @Test
    fun `default kei os tracked app points at current app package and repo`() {
        val item = defaultKeiOsTrackedApp()

        assertEquals("https://github.com/hosizoraru/KeiOS", item.repoUrl)
        assertEquals("hosizoraru", item.owner)
        assertEquals("KeiOS", item.repo)
        assertEquals(KEI_OS_RELEASE_PACKAGE_NAME, item.packageName)
        assertEquals("KeiOS", item.appLabel)
    }

    @Test
    fun `kei os self track badge matches current app package and repo`() {
        val item = defaultKeiOsTrackedApp()

        assertTrue(item.isKeiOsSelfTrack())
    }

    @Test
    fun `kei os actions lookup target uses release package name`() {
        val item = defaultKeiOsTrackedApp()
        val lookupItem = item.asKeiOsActionsRunLookupItem()

        assertEquals("hosizoraru", lookupItem.owner)
        assertEquals("KeiOS", lookupItem.repo)
        assertEquals(KEI_OS_RELEASE_PACKAGE_NAME, lookupItem.packageName)
        assertEquals("hosizoraru/KeiOS|os.kei", lookupItem.id)
        assertTrue(lookupItem.isKeiOsReleaseTrack())
    }

    @Test
    fun `direct apk identity preserves host and full path`() {
        val identity =
            buildDirectApkTrackIdentity("https://telegram.org/dl/android/apk-public-beta")

        assertEquals("telegram.org", identity?.owner)
        assertEquals("dl-android-apk-public-beta", identity?.repo)
        assertEquals("telegram.org/dl/android/apk-public-beta", identity?.displayName)
        assertEquals("apk-public-beta.apk", identity?.assetName)
    }

    @Test
    fun `direct apk track id keeps repository ids stable`() {
        val direct = GitHubTrackedApp(
            repoUrl = "https://telegram.org/dl/android/apk",
            owner = "telegram.org",
            repo = "dl-android-apk",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram",
            sourceMode = GitHubTrackedSourceMode.DirectApk
        )
        val repository = GitHubTrackedApp(
            repoUrl = "https://github.com/telegram/telegram-android",
            owner = "telegram",
            repo = "telegram-android",
            packageName = "org.telegram.messenger",
            appLabel = "Telegram"
        )

        assertEquals("direct_apk|telegram.org/dl-android-apk|org.telegram.messenger", direct.id)
        assertEquals("telegram/telegram-android|org.telegram.messenger", repository.id)
    }

    @Test
    fun `git repository identity parses https and ssh clone urls`() {
        val gitee = buildGitRepositoryTrackIdentity("https://gitee.com/demo/app.git")
        val gitlab = buildGitRepositoryTrackIdentity("git@gitlab.com:group/subgroup/app.git")

        assertEquals(GitRepositoryPlatform.Gitee, gitee?.platform)
        assertEquals("gitee.com", gitee?.host)
        assertEquals("demo", gitee?.namespace)
        assertEquals("app", gitee?.repo)
        assertEquals("gitee.com/demo", gitee?.owner)
        assertEquals("gitee.com/demo/app", gitee?.displayName)
        assertEquals(GitRepositoryPlatform.GitLab, gitlab?.platform)
        assertEquals("group/subgroup", gitlab?.namespace)
        assertEquals("gitlab.com/group/subgroup", gitlab?.owner)
        assertEquals("app", gitlab?.repo)
    }

    @Test
    fun `git repository identity trims platform page suffixes`() {
        val identity =
            buildGitRepositoryTrackIdentity("https://gitlab.com/group/subgroup/app/-/tree/main")

        assertEquals("gitlab.com", identity?.host)
        assertEquals("group/subgroup", identity?.namespace)
        assertEquals("app", identity?.repo)
        assertEquals("gitlab.com/group/subgroup/app", identity?.displayName)
    }

    @Test
    fun `git repository track id includes host scoped owner`() {
        val git = GitHubTrackedApp(
            repoUrl = "https://gitee.com/demo/app",
            owner = "gitee.com/demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            sourceMode = GitHubTrackedSourceMode.GitRepository
        )

        assertEquals("git_repository|gitee.com/demo/app|com.demo.app", git.id)
    }

    @Test
    fun `source mode aliases parse git platforms`() {
        assertEquals(GitHubTrackedSourceMode.GitRepository, GitHubTrackedSourceMode.fromStorageId("git"))
        assertEquals(GitHubTrackedSourceMode.GitRepository, GitHubTrackedSourceMode.fromStorageId("gitee"))
        assertEquals(GitHubTrackedSourceMode.GitRepository, GitHubTrackedSourceMode.fromStorageId("gitlab"))
        assertEquals(GitHubTrackedSourceMode.DirectApk, GitHubTrackedSourceMode.fromStorageId("subscription"))
    }

    @Test
    fun `github git repository can map to release lookup item`() {
        val git = GitHubTrackedApp(
            repoUrl = "git@github.com:demo/app.git",
            owner = "github.com/demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            sourceMode = GitHubTrackedSourceMode.GitRepository,
            checkActionsUpdates = true,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        )

        val lookupItem = git.githubReleaseLookupItemOrNull()

        assertEquals(GitHubTrackedSourceMode.GitHubRepository, lookupItem?.sourceMode)
        assertEquals("https://github.com/demo/app", lookupItem?.repoUrl)
        assertEquals("demo", lookupItem?.owner)
        assertEquals("app", lookupItem?.repo)
        assertEquals(false, lookupItem?.checkActionsUpdates)
        assertEquals(GitHubTrackedActionsUpdateIntervalMode.FollowGlobal, lookupItem?.actionsUpdateIntervalMode)
    }

    @Test
    fun `actions interval clears when actions check is disabled`() {
        val item = GitHubTrackedApp(
            repoUrl = "https://github.com/demo/app",
            owner = "demo",
            repo = "app",
            packageName = "com.demo.app",
            appLabel = "Demo",
            checkActionsUpdates = false,
            actionsUpdateIntervalMode = GitHubTrackedActionsUpdateIntervalMode.Minutes15
        ).withSourceModeConstraints()

        assertEquals(
            GitHubTrackedActionsUpdateIntervalMode.FollowGlobal,
            item.actionsUpdateIntervalMode
        )
    }
}
