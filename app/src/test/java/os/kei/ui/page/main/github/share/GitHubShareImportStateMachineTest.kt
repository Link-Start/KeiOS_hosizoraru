package os.kei.ui.page.main.github.share

import org.junit.Test
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.install.GitHubApkInstallProgress
import os.kei.feature.github.install.GitHubApkInstallRequest
import os.kei.feature.github.install.GitHubApkInstallResult
import os.kei.feature.github.install.GitHubApkInstallStage
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
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

    @Test
    fun `managed install start state builds initial progress and active record`() {
        val startState =
            buildManagedInstallStartState(
                preview = preview(),
                selectedAsset = asset(),
                requestId = "request-1",
            )
        val record =
            buildManagedInstallInitialRecord(
                preview = preview(),
                selectedAsset = asset(),
                startState = startState,
            )

        assertEquals("request-1", startState.requestId)
        assertEquals("KeiOS", startState.targetDisplayName)
        assertEquals(GitHubShareImportPhase.Installing, startState.initialProgress.phase)
        assertEquals("request-1", record.requestId)
        assertEquals("keios-release.apk", record.assetName)
        assertEquals("KeiOS", record.targetDisplayName)
        assertEquals(GitHubShareImportPhase.Installing.name, record.progressPhase)
        assertEquals(42_000L, record.totalBytes)
    }

    @Test
    fun `managed install request trims manifest fields and native abis`() {
        val request =
            buildManagedInstallRequest(
                preview = preview(),
                selectedAsset = asset(),
                lookupConfig = GitHubLookupConfig(),
                startState =
                    GitHubManagedInstallStartState(
                        requestId = "request-2",
                        targetDisplayName = "KeiOS",
                        initialProgress =
                            GitHubShareImportManagedInstallProgress(
                                phase = GitHubShareImportPhase.Installing,
                            ),
                    ),
                manifestInfo =
                    GitHubApkManifestInfo(
                        assetName = "keios-release.apk",
                        appLabel = " KeiOS ",
                        packageName = " os.kei ",
                        versionName = " 1.8.0 ",
                        versionCode = " 180 ",
                        minSdk = " 35 ",
                        targetSdk = " 37 ",
                        nativeAbis = listOf(" arm64-v8a ", "", " x86_64 "),
                    ),
                resolvedDownloadUrl = "https://example.com/keios.apk",
            )

        assertEquals("request-2", request.requestId)
        assertEquals("KeiOS", request.scannedAppLabel)
        assertEquals("os.kei", request.scannedPackageName)
        assertEquals("1.8.0", request.scannedVersionName)
        assertEquals("180", request.scannedVersionCode)
        assertEquals("35", request.scannedMinSdk)
        assertEquals("37", request.scannedTargetSdk)
        assertEquals(listOf("arm64-v8a", "x86_64"), request.scannedNativeAbis)
        assertEquals("https://example.com/keios.apk", request.resolvedDownloadUrl)
    }

    @Test
    fun `managed install progress merges active record`() {
        val activeRecord = managedInstallRecord()
        val request = installRequest()
        val progress =
            GitHubApkInstallProgress(
                stage = GitHubApkInstallStage.Downloading,
                progressPercent = 48,
                downloadedBytes = 480L,
                totalBytes = 1000L,
                sessionId = 77,
                appLabel = "Runtime Label",
                packageName = "os.kei.runtime",
            )

        val nextRecord =
            mergeManagedInstallProgressRecord(
                activeRecord = activeRecord,
                request = request,
                progress = progress,
            )

        assertEquals(77, nextRecord.sessionId)
        assertEquals("Runtime Label", nextRecord.appLabel)
        assertEquals("os.kei.runtime", nextRecord.packageName)
        assertEquals("1.8.0", nextRecord.versionName)
        assertEquals(GitHubShareImportPhase.InstallDownloading.name, nextRecord.progressPhase)
        assertEquals(48, nextRecord.progressPercent)
        assertEquals(480L, nextRecord.downloadedBytes)
        assertEquals(1000L, nextRecord.totalBytes)
    }

    @Test
    fun `staged managed install record prefers staged data with request fallback`() {
        val record =
            buildStagedManagedInstallRecord(
                activeRecord = managedInstallRecord(),
                request = installRequest(),
                result =
                    GitHubApkInstallResult.Staged(
                        requestId = "request-3",
                        sessionId = 91,
                        packageName = "",
                        appLabel = "Staged Label",
                        versionName = "",
                        versionCode = "",
                        minSdk = "",
                        targetSdk = "",
                        downloadedBytes = 900L,
                        totalBytes = 1000L,
                    ),
            )

        assertEquals(91, record.sessionId)
        assertEquals("Staged Label", record.appLabel)
        assertEquals("os.kei", record.packageName)
        assertEquals("1.8.0", record.versionName)
        assertEquals("180", record.versionCode)
        assertEquals("35", record.minSdk)
        assertEquals("37", record.targetSdk)
        assertEquals(GitHubShareImportPhase.InstallReady.name, record.progressPhase)
        assertEquals(100, record.progressPercent)
    }

    @Test
    fun `managed install attach candidate prefers installed snapshot`() {
        val candidate =
            buildManagedInstallAttachCandidate(
                preview = preview(),
                request = installRequest(),
                result =
                    GitHubApkInstallResult.Succeeded(
                        requestId = "request-4",
                        sessionId = 9,
                        packageName = " os.kei ",
                        appLabel = "Result Label",
                        firstInstallTimeMs = 111L,
                    ),
                snapshot =
                    ShareImportInstalledPackageSnapshot(
                        packageName = "os.kei",
                        appLabel = "Installed Label",
                        versionName = "1.8.1",
                        versionCode = "181",
                        lastUpdateTimeMs = 222L,
                        firstInstallTimeMs = 333L,
                    ),
                eventAction = "managed-install",
                detectedAtMillis = 444L,
            )

        assertEquals("os.kei", candidate.packageName)
        assertEquals("Installed Label", candidate.appLabel)
        assertEquals("1.8.1", candidate.versionName)
        assertEquals("181", candidate.versionCode)
        assertEquals("managed-install", candidate.eventAction)
        assertEquals(444L, candidate.detectedAtMillis)
        assertEquals(333L, candidate.firstInstallTimeMs)
    }

    @Test
    fun `active managed install commit requires ready phase and session id`() {
        assertTrue(
            shouldCommitActiveManagedInstall(
                managedInstallRecord().copy(
                    sessionId = 7,
                    progressPhase = GitHubShareImportPhase.InstallReady.name,
                ),
            ),
        )
        assertFalse(
            shouldCommitActiveManagedInstall(
                managedInstallRecord().copy(
                    sessionId = -1,
                    progressPhase = GitHubShareImportPhase.InstallReady.name,
                ),
            ),
        )
        assertFalse(
            shouldCommitActiveManagedInstall(
                managedInstallRecord().copy(
                    sessionId = 7,
                    progressPhase = GitHubShareImportPhase.Installing.name,
                ),
            ),
        )
        assertFalse(shouldCommitActiveManagedInstall(null))
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

    private fun installRequest(): GitHubApkInstallRequest =
        GitHubApkInstallRequest(
            owner = "os",
            repo = "kei",
            releaseTag = "v1",
            projectUrl = "https://github.com/os/kei",
            asset = asset(),
            lookupConfig = GitHubLookupConfig(),
            targetDisplayName = "KeiOS",
            scannedAppLabel = "KeiOS",
            scannedPackageName = "os.kei",
            scannedVersionName = "1.8.0",
            scannedVersionCode = "180",
            scannedMinSdk = "35",
            scannedTargetSdk = "37",
            scannedNativeAbis = listOf("arm64-v8a"),
            requestId = "request-3",
            startedAtMillis = 10L,
        )

    private fun managedInstallRecord(): GitHubPendingShareImportManagedInstallRecord =
        GitHubPendingShareImportManagedInstallRecord(
            requestId = "request-3",
            projectUrl = "https://github.com/os/kei",
            owner = "os",
            repo = "kei",
            releaseTag = "v1",
            assetName = "keios-release.apk",
            appLabel = "",
            packageName = "os.kei",
            versionName = "",
            versionCode = "",
            minSdk = "",
            targetSdk = "",
            nativeAbis = emptyList(),
            targetDisplayName = "KeiOS",
            sessionId = -1,
            progressPhase = GitHubShareImportPhase.Installing.name,
            progressPercent = 0,
            downloadedBytes = 0L,
            totalBytes = 42_000L,
            startedAtMillis = 10L,
        )
}
