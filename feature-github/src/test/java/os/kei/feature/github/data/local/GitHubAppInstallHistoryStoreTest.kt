package os.kei.feature.github.data.local

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import os.kei.feature.github.model.GitHubAppInstallHistoryAction
import os.kei.feature.github.model.GitHubAppInstallHistoryRecord
import os.kei.feature.github.model.GitHubAppInstallHistorySource
import os.kei.feature.github.model.GitHubAppInstallSourceInfo
import os.kei.feature.github.model.GitHubTrackedAppInstallSnapshot
import os.kei.feature.github.model.GitHubTrackedSourceMode

class GitHubAppInstallHistoryStoreTest {
    @Test
    fun `record round trip keeps version and broadcast fields`() {
        val record =
            createRecord(
                id = "record-1",
                action = GitHubAppInstallHistoryAction.Updated,
                previousVersionName = "1.0.0",
                previousVersionCode = 10L,
                currentVersionName = "1.1.0",
                currentVersionCode = 11L,
                replacing = true,
                currentInstallSourceInfo =
                    GitHubAppInstallSourceInfo(
                        installingPackageName = "com.android.packageinstaller",
                        installingPackageLabel = "Package Installer",
                        initiatingPackageName = "com.android.packageinstaller",
                        initiatingPackageLabel = "Package Installer",
                        packageSource = 4,
                    ),
            )

        val decoded =
            GitHubAppInstallHistoryStore.decodeRecord(
                GitHubAppInstallHistoryStore.encodeRecord(record).toString(),
            )

        assertEquals(record, decoded)
    }

    @Test
    fun `snapshot round trip keeps install source info`() {
        val snapshot =
            GitHubTrackedAppInstallSnapshot(
                packageName = "Dev.Example.App",
                versionName = "2.0",
                versionCode = 20L,
                isSystemApp = false,
                appLabel = "Example",
                observedAtMillis = 1_000L,
                installSourceInfo =
                    GitHubAppInstallSourceInfo(
                        installingPackageName = "Com.Android.PackageInstaller",
                        installingPackageLabel = "Package Installer",
                        packageSource = 3,
                    ),
            )

        val decoded =
            GitHubAppInstallHistoryStore.decodeSnapshot(
                GitHubAppInstallHistoryStore.encodeSnapshot(snapshot).toString(),
            )

        assertNotNull(decoded)
        assertEquals("dev.example.app", decoded.packageName)
        assertEquals("2.0", decoded.versionName)
        assertEquals(20L, decoded.versionCode)
        assertEquals("Example", decoded.appLabel)
        assertEquals("com.android.packageinstaller", decoded.installSourceInfo.installingPackageName)
        assertEquals("Package Installer", decoded.installSourceInfo.installingPackageLabel)
        assertEquals(3, decoded.installSourceInfo.packageSource)
    }

    @Test
    fun `record id changes when version changes`() {
        val first = createRecord(currentVersionCode = 10L)
        val second = createRecord(currentVersionCode = 11L)

        assertFalse(GitHubAppInstallHistoryStore.recordId(first) == GitHubAppInstallHistoryStore.recordId(second))
    }

    @Test
    fun `prune predicate uses changed time`() {
        val old = createRecord(changedAtMillis = 1_000L)
        val fresh = createRecord(changedAtMillis = 3_000L)

        assertTrue(GitHubAppInstallHistoryStore.shouldPruneBefore(old, 2_000L))
        assertFalse(GitHubAppInstallHistoryStore.shouldPruneBefore(fresh, 2_000L))
    }

    private fun createRecord(
        id: String = "",
        action: GitHubAppInstallHistoryAction = GitHubAppInstallHistoryAction.Installed,
        changedAtMillis: Long = 1_000L,
        previousVersionName: String = "",
        previousVersionCode: Long = -1L,
        currentVersionName: String = "1.0.0",
        currentVersionCode: Long = 10L,
        replacing: Boolean = false,
        currentInstallSourceInfo: GitHubAppInstallSourceInfo = GitHubAppInstallSourceInfo(),
    ): GitHubAppInstallHistoryRecord =
        GitHubAppInstallHistoryRecord(
            id = id,
            trackId = "owner/repo|dev.example.app",
            action = action,
            source = GitHubAppInstallHistorySource.PackageBroadcast,
            changedAtMillis = changedAtMillis,
            owner = "owner",
            repo = "repo",
            repoUrl = "https://github.com/owner/repo",
            packageName = "dev.example.app",
            appLabel = "Example",
            sourceMode = GitHubTrackedSourceMode.GitHubRepository,
            previousVersionName = previousVersionName,
            previousVersionCode = previousVersionCode,
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            broadcastAction = "android.intent.action.PACKAGE_ADDED",
            replacing = replacing,
            currentInstallSourceInfo = currentInstallSourceInfo,
        )
}
