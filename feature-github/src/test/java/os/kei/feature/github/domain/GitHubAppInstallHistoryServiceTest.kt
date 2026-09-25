package os.kei.feature.github.domain

import android.content.Intent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.Test
import os.kei.feature.github.model.GitHubAppInstallHistoryAction
import os.kei.feature.github.model.GitHubAppInstallSourceInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedAppInstallSnapshot

class GitHubAppInstallHistoryServiceTest {
    private data class BroadcastCase(
        val label: String,
        val previous: GitHubTrackedAppInstallSnapshot?,
        val current: GitHubTrackedAppInstallSnapshot?,
        val action: String,
        val replacing: Boolean,
        val expectedAction: GitHubAppInstallHistoryAction,
        val expectedPreviousCode: Long,
        val expectedCurrentCode: Long,
        val expectedRemoveSnapshot: Boolean,
    )

    @Test
    fun `package broadcast maps to history action`() {
        val cases = listOf(
            BroadcastCase(
                label = "added -> install",
                previous = null,
                current = snapshot(versionName = "1.0", versionCode = 10L),
                action = Intent.ACTION_PACKAGE_ADDED,
                replacing = false,
                expectedAction = GitHubAppInstallHistoryAction.Installed,
                expectedPreviousCode = -1L,
                expectedCurrentCode = 10L,
                expectedRemoveSnapshot = false,
            ),
            BroadcastCase(
                label = "replaced with higher version code -> update",
                previous = snapshot(versionName = "1.0", versionCode = 10L),
                current = snapshot(versionName = "1.1", versionCode = 11L),
                action = Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
                expectedAction = GitHubAppInstallHistoryAction.Updated,
                expectedPreviousCode = 10L,
                expectedCurrentCode = 11L,
                expectedRemoveSnapshot = false,
            ),
            BroadcastCase(
                label = "replaced with lower version code -> downgrade",
                previous = snapshot(versionName = "2.0", versionCode = 20L),
                current = snapshot(versionName = "1.0", versionCode = 10L),
                action = Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
                expectedAction = GitHubAppInstallHistoryAction.Downgraded,
                expectedPreviousCode = 20L,
                expectedCurrentCode = 10L,
                expectedRemoveSnapshot = false,
            ),
            BroadcastCase(
                label = "removed (not replacing) -> uninstall and drop snapshot",
                previous = snapshot(versionName = "1.0", versionCode = 10L),
                current = null,
                action = Intent.ACTION_PACKAGE_REMOVED,
                replacing = false,
                expectedAction = GitHubAppInstallHistoryAction.Uninstalled,
                expectedPreviousCode = 10L,
                expectedCurrentCode = -1L,
                expectedRemoveSnapshot = true,
            ),
        )

        cases.forEach { case ->
            val result =
                GitHubAppInstallHistoryService.buildPackageChangeResult(
                    trackedItems = listOf(trackedApp()),
                    previousSnapshot = case.previous,
                    currentSnapshot = case.current,
                    packageName = "dev.example.app",
                    action = case.action,
                    replacing = case.replacing,
                    changedAtMillis = 1_000L,
                )

            val record = result.records.singleOrNull()
            assertNotNull(record, "${case.label}: expected one record, got ${result.records}")
            assertEquals(case.expectedAction, record.action, case.label)
            assertEquals(case.expectedPreviousCode, record.previousVersionCode, case.label)
            assertEquals(case.expectedCurrentCode, record.currentVersionCode, case.label)
            assertEquals(case.current, result.nextSnapshot, "${case.label}: next snapshot")
            assertEquals(case.expectedRemoveSnapshot, result.removeSnapshot, "${case.label}: removeSnapshot")
        }
    }

    @Test
    fun `package update sequence keeps previous snapshot during replacing removal`() {
        val previous = snapshot(versionName = "1.0", versionCode = 10L)

        val result =
            GitHubAppInstallHistoryService.buildPackageChangeResult(
                trackedItems = listOf(trackedApp()),
                previousSnapshot = previous,
                currentSnapshot = null,
                packageName = "dev.example.app",
                action = Intent.ACTION_PACKAGE_REMOVED,
                replacing = true,
                changedAtMillis = 1_000L,
            )

        assertEquals(emptyList(), result.records)
        assertEquals(previous, result.nextSnapshot)
        assertFalse(result.removeSnapshot)
    }

    @Test
    fun `package replaced skips duplicate record when snapshot already matches`() {
        val previous = snapshot(versionName = "1.1", versionCode = 11L)
        val current = snapshot(versionName = "1.1", versionCode = 11L)

        val result =
            GitHubAppInstallHistoryService.buildPackageChangeResult(
                trackedItems = listOf(trackedApp()),
                previousSnapshot = previous,
                currentSnapshot = current,
                packageName = "dev.example.app",
                action = Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
                changedAtMillis = 1_000L,
            )

        assertEquals(emptyList(), result.records)
        assertEquals(current, result.nextSnapshot)
    }

    @Test
    fun `package replaced records update when install source changes`() {
        val previous =
            snapshot(
                versionName = "1.1",
                versionCode = 11L,
                installSourceInfo =
                    GitHubAppInstallSourceInfo(
                        installingPackageName = "com.example.store",
                        installingPackageLabel = "Example Store",
                        packageSource = 2,
                    ),
            )
        val current =
            snapshot(
                versionName = "1.1",
                versionCode = 11L,
                installSourceInfo =
                    GitHubAppInstallSourceInfo(
                        installingPackageName = "com.android.packageinstaller",
                        installingPackageLabel = "Package Installer",
                        packageSource = 4,
                    ),
            )

        val result =
            GitHubAppInstallHistoryService.buildPackageChangeResult(
                trackedItems = listOf(trackedApp()),
                previousSnapshot = previous,
                currentSnapshot = current,
                packageName = "dev.example.app",
                action = Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
                broadcastUid = 12_345,
                changedAtMillis = 1_000L,
            )

        val record = result.records.single()
        assertEquals(GitHubAppInstallHistoryAction.Updated, record.action)
        assertEquals(previous.installSourceInfo, record.previousInstallSourceInfo)
        assertEquals(current.installSourceInfo, record.currentInstallSourceInfo)
        assertEquals(12_345, record.broadcastUid)
    }

    private fun trackedApp(): GitHubTrackedApp =
        GitHubTrackedApp(
            repoUrl = "https://github.com/owner/repo",
            owner = "owner",
            repo = "repo",
            packageName = "dev.example.app",
            appLabel = "Example",
        )

    private fun snapshot(
        versionName: String,
        versionCode: Long,
        installSourceInfo: GitHubAppInstallSourceInfo = GitHubAppInstallSourceInfo(),
    ): GitHubTrackedAppInstallSnapshot =
        GitHubTrackedAppInstallSnapshot(
            packageName = "dev.example.app",
            versionName = versionName,
            versionCode = versionCode,
            isSystemApp = false,
            appLabel = "Example",
            observedAtMillis = 1_000L,
            installSourceInfo = installSourceInfo,
        )
}
