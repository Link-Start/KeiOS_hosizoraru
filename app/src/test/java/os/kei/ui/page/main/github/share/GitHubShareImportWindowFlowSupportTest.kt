package os.kei.ui.page.main.github.share

import android.content.Intent
import org.junit.Test
import os.kei.core.system.AppPackageChangedEvent
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED
import os.kei.feature.github.data.local.GitHubPendingShareImportManagedInstallRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportPreviewRecord
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubShareImportFlowMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubShareImportWindowFlowSupportTest {
    @Test
    fun `share import result record converts into ui result`() {
        val result = GitHubShareImportResultRecord(
            status = GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED,
            owner = "owner",
            repo = "repo",
            appLabel = "Demo",
            packageName = "demo.package",
            completedAtMillis = 12_000L
        ).toShareImportResult()

        assertNotNull(result)
        assertEquals(GitHubShareImportResultKind.Added, result.kind)
        assertEquals("owner/repo", result.projectLabel)
        assertEquals("Demo", result.appDisplayLabel)
    }

    @Test
    fun `target display name prefers app label then repo then apk name then package`() {
        assertEquals(
            "Demo",
            buildShareImportTargetDisplayName(
                appLabel = "Demo",
                repo = "repo",
                assetName = "demo-arm64.apk",
                packageName = "demo.package"
            )
        )
        assertEquals(
            "repo",
            buildShareImportTargetDisplayName(
                repo = "repo",
                assetName = "demo-arm64.apk",
                packageName = "demo.package"
            )
        )
        assertEquals(
            "demo arm64",
            buildShareImportTargetDisplayName(
                assetName = "demo-arm64.apk",
                packageName = "demo.package"
            )
        )
        assertEquals(
            "demo.package",
            buildShareImportTargetDisplayName(packageName = "demo.package")
        )
    }

    @Test
    fun `preview record keeps target display name through mapper`() {
        val record = GitHubPendingShareImportPreviewRecord(
            sourceUrl = "https://github.com/owner/MicYou/releases/tag/v1",
            projectUrl = "https://github.com/owner/MicYou",
            owner = "owner",
            repo = "MicYou",
            releaseTag = "v1",
            releaseUrl = "https://github.com/owner/MicYou/releases/tag/v1",
            strategyLabel = "Atom Feed",
            assets = listOf(
                GitHubReleaseAssetFile(
                    name = "MicYou.apk",
                    downloadUrl = "https://github.com/owner/MicYou/releases/download/v1/MicYou.apk",
                    sizeBytes = 1024L,
                    downloadCount = 1
                )
            ),
            preferredAssetName = "MicYou.apk",
            targetDisplayName = "MicYou",
            selectedAssetName = "MicYou.apk",
            sendInstallActionEnabled = true,
            createdAtMillis = 10_000L
        )

        val preview = record.toShareImportPreview()
        val roundTrip = preview.toPendingPreviewRecord(createdAtMillis = record.createdAtMillis)

        assertEquals("MicYou", preview.targetDisplayName)
        assertEquals("MicYou.apk", preview.selectedAssetForSend?.name)
        assertEquals(true, preview.sendInstallActionEnabled)
        assertEquals("MicYou", roundTrip.targetDisplayName)
        assertEquals("MicYou.apk", roundTrip.selectedAssetName)
        assertEquals(true, roundTrip.sendInstallActionEnabled)
    }

    @Test
    fun `managed install record keeps parsed apk metadata through ui progress mapper`() {
        val record = GitHubPendingShareImportManagedInstallRecord(
            requestId = "request-1",
            projectUrl = "https://github.com/owner/MicYou",
            owner = "owner",
            repo = "MicYou",
            releaseTag = "v2",
            assetName = "MicYou-arm64.apk",
            appLabel = "MicYou",
            packageName = "os.kei.micyou",
            versionName = "2.0.0",
            versionCode = "200",
            minSdk = "35",
            targetSdk = "36",
            nativeAbis = listOf("arm64-v8a"),
            targetDisplayName = "MicYou",
            progressPhase = GitHubShareImportPhase.InstallReady.name,
            progressPercent = 100,
            downloadedBytes = 1024L,
            totalBytes = 1024L,
            startedAtMillis = 10_000L
        )

        val progress = record.toManagedInstallProgress()

        assertEquals(GitHubShareImportPhase.InstallReady, progress.phase)
        assertEquals("MicYou", progress.appDisplayName)
        assertEquals("os.kei.micyou", progress.packageName)
        assertEquals("2.0.0", progress.versionName)
        assertEquals("200", progress.versionCode)
        assertEquals("35", progress.minSdk)
        assertEquals("36", progress.targetSdk)
        assertEquals(listOf("arm64-v8a"), progress.nativeAbis)
    }

    @Test
    fun `pending track converts installed snapshot into attach candidate`() {
        val pending = pendingTrack(armedAtMillis = 10_000L)
        val candidate = pending.toAttachCandidate(
            packageSnapshot = installedPackage(
                packageName = "new.package",
                appLabel = "",
                lastUpdateTimeMs = 12_000L,
                firstInstallTimeMs = 11_500L
            ),
            eventAction = "duplicate",
            detectedAtMillis = 12_100L
        )

        assertEquals("https://github.com/asadahimeka/pixiv-viewer-app", candidate.projectUrl)
        assertEquals("asadahimeka", candidate.owner)
        assertEquals("pixiv-viewer-app", candidate.repo)
        assertEquals("new.package", candidate.packageName)
        assertEquals("new.package", candidate.appLabel)
        assertEquals("duplicate", candidate.eventAction)
        assertEquals(12_100L, candidate.detectedAtMillis)
        assertEquals(11_500L, candidate.firstInstallTimeMs)
    }

    @Test
    fun `reconciliation ignores package updated before current share was armed`() {
        val pending = pendingTrack(armedAtMillis = 10_000L)
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "old.package",
                    lastUpdateTimeMs = 9_999L
                )
            )
        )

        assertNull(candidate)
    }

    @Test
    fun `reconciliation picks package updated after current share was armed`() {
        val pending = pendingTrack(armedAtMillis = 10_000L)
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "old.package",
                    lastUpdateTimeMs = 9_500L
                ),
                installedPackage(
                    packageName = "new.package",
                    lastUpdateTimeMs = 12_000L
                )
            )
        )

        assertEquals("new.package", candidate?.packageName)
    }

    @Test
    fun `reconciliation uses exact package name when pending track has scanned manifest`() {
        val pending = pendingTrack(
            armedAtMillis = 10_000L,
            packageName = "target.package"
        )
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "other.package",
                    lastUpdateTimeMs = 13_000L
                ),
                installedPackage(
                    packageName = "target.package",
                    lastUpdateTimeMs = 10_100L
                )
            )
        )

        assertEquals("target.package", candidate?.packageName)
    }

    @Test
    fun `reconciliation allows exact package timestamp tolerance`() {
        val pending = pendingTrack(
            armedAtMillis = 200_000L,
            packageName = "target.package"
        )
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "target.package",
                    lastUpdateTimeMs = 100_000L
                )
            )
        )

        assertEquals("target.package", candidate?.packageName)
    }

    @Test
    fun `reconciliation rejects stale exact package snapshot`() {
        val pending = pendingTrack(
            armedAtMillis = 200_000L,
            packageName = "target.package"
        )
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "target.package",
                    lastUpdateTimeMs = 70_000L
                )
            )
        )

        assertNull(candidate)
    }

    @Test
    fun `reconciliation stays empty when recent packages are ambiguous`() {
        val pending = pendingTrack(armedAtMillis = 10_000L)
        val candidate = selectRecentInstalledCandidateForPendingTrack(
            pendingTrack = pending,
            candidates = listOf(
                installedPackage(
                    packageName = "first.package",
                    lastUpdateTimeMs = 13_000L
                ),
                installedPackage(
                    packageName = "second.package",
                    lastUpdateTimeMs = 12_500L
                )
            )
        )

        assertNull(candidate)
    }

    @Test
    fun `package changed event requires fresh package snapshot`() {
        val event = AppPackageChangedEvent(
            action = Intent.ACTION_PACKAGE_CHANGED,
            packageName = "target.package",
            atMillis = 12_000L
        )

        assertFalse(
            isShareImportAttachEventValid(
                event = event,
                armedAtMillis = 200_000L,
                packageLastUpdateTimeMs = 70_000L
            )
        )
        assertTrue(
            isShareImportAttachEventValid(
                event = event,
                armedAtMillis = 200_000L,
                packageLastUpdateTimeMs = 200_000L - shareImportTrackUpdateToleranceMs
            )
        )
    }

    @Test
    fun `coordinator result maps to share import phase consistently`() {
        val pending = pendingTrack(armedAtMillis = 10_000L)
        val candidate = pending.toAttachCandidate(
            packageSnapshot = installedPackage(
                packageName = "target.package",
                lastUpdateTimeMs = 11_000L
            ),
            eventAction = Intent.ACTION_PACKAGE_ADDED,
            detectedAtMillis = 12_000L
        )

        assertEquals(GitHubShareImportPhase.Idle, ShareImportCoordinatorResult.None.toShareImportPhase())
        assertEquals(
            GitHubShareImportPhase.WaitingInstall,
            ShareImportCoordinatorResult.Pending(pending).toShareImportPhase()
        )
        assertEquals(
            GitHubShareImportPhase.InstallDetected,
            ShareImportCoordinatorResult.Detected(candidate).toShareImportPhase()
        )
        assertEquals(
            GitHubShareImportPhase.Added,
            ShareImportCoordinatorResult.AlreadyTracked(candidate).toShareImportPhase()
        )
        assertEquals(
            GitHubShareImportPhase.Failed,
            ShareImportCoordinatorResult.Failed("failed").toShareImportPhase()
        )
    }

    @Test
    fun `notification first uses notification flow for available apk choices`() {
        assertTrue(
            shouldUseNotificationFirstFlow(
                flowMode = GitHubShareImportFlowMode.NotificationFirst,
                assetCount = 1
            )
        )
        assertTrue(
            shouldUseNotificationFirstFlow(
                flowMode = GitHubShareImportFlowMode.NotificationFirst,
                assetCount = 2
            )
        )
        assertFalse(
            shouldUseNotificationFirstFlow(
                flowMode = GitHubShareImportFlowMode.SheetAssisted,
                assetCount = 1
            )
        )
        assertFalse(
            shouldUseNotificationFirstFlow(
                flowMode = GitHubShareImportFlowMode.NotificationFirst,
                assetCount = 0
            )
        )
    }

    private fun pendingTrack(
        armedAtMillis: Long,
        packageName: String = ""
    ) = GitHubPendingShareImportTrackRecord(
        projectUrl = "https://github.com/asadahimeka/pixiv-viewer-app",
        owner = "asadahimeka",
        repo = "pixiv-viewer-app",
        packageName = packageName,
        armedAtMillis = armedAtMillis
    )

    private fun installedPackage(
        packageName: String,
        lastUpdateTimeMs: Long,
        appLabel: String = packageName,
        firstInstallTimeMs: Long = lastUpdateTimeMs
    ) = ShareImportInstalledPackageSnapshot(
        packageName = packageName,
        appLabel = appLabel,
        lastUpdateTimeMs = lastUpdateTimeMs,
        firstInstallTimeMs = firstInstallTimeMs
    )
}
