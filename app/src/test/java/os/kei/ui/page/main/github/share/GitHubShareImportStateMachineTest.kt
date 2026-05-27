package os.kei.ui.page.main.github.share

import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GitHubShareImportStateMachineTest {
    @Test
    fun `active preview delivery reports missing preview`() {
        val plan = resolveActivePreviewDeliveryPlan(null)

        assertEquals(GitHubShareImportActivePreviewDeliveryPlan.MissingPreview, plan)
    }

    @Test
    fun `active preview delivery requires install action`() {
        val plan =
            resolveActivePreviewDeliveryPlan(
                preview().copy(sendInstallActionEnabled = false),
            )

        assertEquals(GitHubShareImportActivePreviewDeliveryPlan.InstallActionDisabled, plan)
    }

    @Test
    fun `active preview delivery requires selected asset`() {
        val plan =
            resolveActivePreviewDeliveryPlan(
                preview().copy(
                    assets = emptyList(),
                    sendInstallActionEnabled = true,
                ),
            )

        assertEquals(GitHubShareImportActivePreviewDeliveryPlan.MissingSelectedAsset, plan)
    }

    @Test
    fun `active preview delivery resolves selected asset`() {
        val plan =
            resolveActivePreviewDeliveryPlan(
                preview().copy(sendInstallActionEnabled = true),
            )

        val readyPlan = assertIs<GitHubShareImportActivePreviewDeliveryPlan.Ready>(plan)
        assertEquals("kei", readyPlan.preview.repo)
        assertEquals("keios-release.apk", readyPlan.selectedAsset.name)
    }

    @Test
    fun `selected asset delivery uses direct delivery when managed install is disabled`() {
        val plan =
            resolveSelectedAssetDeliveryPlan(
                preview = preview(),
                selectedAsset = asset(),
                appManagedShareInstallEnabled = false,
                currentManagedProgress = null,
            )

        assertEquals(GitHubShareImportSelectedAssetDeliveryPlan.DirectDelivery, plan)
    }

    @Test
    fun `selected asset delivery launches managed install when enabled`() {
        val plan =
            resolveSelectedAssetDeliveryPlan(
                preview = preview(),
                selectedAsset = asset(),
                appManagedShareInstallEnabled = true,
                currentManagedProgress = null,
            )

        val launchPlan = assertIs<GitHubShareImportSelectedAssetDeliveryPlan.LaunchManagedInstall>(plan)
        assertEquals("keios-release.apk", launchPlan.selectedPreview.selectedAssetName)
        assertTrue(launchPlan.selectedPreview.sendInstallActionEnabled)
        assertEquals(GitHubShareImportPhase.Installing, launchPlan.progress.phase)
        assertEquals("keios-release.apk", launchPlan.progress.assetName)
        assertEquals("KeiOS", launchPlan.progress.targetDisplayName)
        assertEquals(0, launchPlan.progress.progressPercent)
        assertEquals(42_000L, launchPlan.progress.totalBytes)
    }

    @Test
    fun `selected asset delivery commits active managed install when ready`() {
        val currentProgress =
            GitHubShareImportManagedInstallProgress(
                phase = GitHubShareImportPhase.InstallReady,
                assetName = "ready.apk",
                progressPercent = 81,
                totalBytes = 900L,
            )

        val plan =
            resolveSelectedAssetDeliveryPlan(
                preview = preview(),
                selectedAsset = asset(),
                appManagedShareInstallEnabled = true,
                currentManagedProgress = currentProgress,
            )

        val commitPlan = assertIs<GitHubShareImportSelectedAssetDeliveryPlan.CommitManagedInstall>(plan)
        assertEquals(GitHubShareImportPhase.InstallCommitting, commitPlan.progress.phase)
        assertEquals(92, commitPlan.progress.progressPercent)
        assertEquals("ready.apk", commitPlan.progress.assetName)
        assertEquals(900L, commitPlan.progress.totalBytes)
    }

    @Test
    fun `selected asset delivery ignores stale managed progress when feature is disabled`() {
        val plan =
            resolveSelectedAssetDeliveryPlan(
                preview = preview(),
                selectedAsset = asset(),
                appManagedShareInstallEnabled = false,
                currentManagedProgress =
                    GitHubShareImportManagedInstallProgress(
                        phase = GitHubShareImportPhase.InstallReady,
                    ),
            )

        assertEquals(GitHubShareImportSelectedAssetDeliveryPlan.DirectDelivery, plan)
        assertFalse(plan is GitHubShareImportSelectedAssetDeliveryPlan.CommitManagedInstall)
    }

    @Test
    fun `waiting install record keeps manifest package and version`() {
        val record =
            buildWaitingInstallTrackRecord(
                preview = preview(),
                selectedAsset = asset(),
                scannedManifestInfo =
                    GitHubApkManifestInfo(
                        assetName = "keios-release.apk",
                        packageName = "os.kei",
                        versionName = "1.8.0",
                    ),
                armedAtMillis = 1234L,
            )

        assertEquals("https://github.com/os/kei", record.projectUrl)
        assertEquals("os", record.owner)
        assertEquals("kei", record.repo)
        assertEquals("v1", record.releaseTag)
        assertEquals("keios-release.apk", record.assetName)
        assertEquals("os.kei", record.packageName)
        assertEquals("1.8.0", record.versionName)
        assertEquals("kei", record.targetDisplayName)
        assertEquals(1234L, record.armedAtMillis)
    }

    @Test
    fun `waiting install record falls back to preview target display name`() {
        val record =
            buildWaitingInstallTrackRecord(
                preview =
                    preview().copy(
                        repo = "",
                        targetDisplayName = "KeiOS",
                    ),
                selectedAsset = asset().copy(name = ""),
                scannedManifestInfo = null,
                armedAtMillis = 5678L,
            )

        assertEquals("KeiOS", record.targetDisplayName)
        assertEquals(5678L, record.armedAtMillis)
    }

    private fun preview(): GitHubShareImportPreview =
        GitHubShareImportPreview(
            sourceUrl = "https://github.com/os/kei/releases/tag/v1",
            projectUrl = "https://github.com/os/kei",
            owner = "os",
            repo = "kei",
            releaseTag = "v1",
            releaseUrl = "https://github.com/os/kei/releases/tag/v1",
            strategyLabel = "Release",
            assets = listOf(asset()),
            targetDisplayName = "KeiOS",
        )

    private fun asset(): GitHubReleaseAssetFile =
        GitHubReleaseAssetFile(
            name = "keios-release.apk",
            downloadUrl = "https://example.com/keios-release.apk",
            sizeBytes = 42_000L,
            downloadCount = 7,
        )
}
