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
                versionCode = "120",
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
        assertEquals("120", candidate.versionCode)
        assertEquals("duplicate", candidate.eventAction)
        assertEquals(12_100L, candidate.detectedAtMillis)
        assertEquals(11_500L, candidate.firstInstallTimeMs)
    }

    @Test
    fun `reconciliation selects the recent install that belongs to the current share`() {
        data class Case(
            val name: String,
            val armedAtMillis: Long,
            val pendingPackage: String,
            val candidates: List<Pair<String, Long>>,
            val expectedPackage: String?,
        )
        listOf(
            Case(
                name = "ignores package updated before current share was armed",
                armedAtMillis = 10_000L,
                pendingPackage = "",
                candidates = listOf("old.package" to 9_999L),
                expectedPackage = null,
            ),
            Case(
                name = "picks package updated after current share was armed",
                armedAtMillis = 10_000L,
                pendingPackage = "",
                candidates = listOf("old.package" to 9_500L, "new.package" to 12_000L),
                expectedPackage = "new.package",
            ),
            Case(
                name = "uses exact package name when pending track has scanned manifest",
                armedAtMillis = 10_000L,
                pendingPackage = "target.package",
                candidates = listOf("other.package" to 13_000L, "target.package" to 10_100L),
                expectedPackage = "target.package",
            ),
            Case(
                name = "allows exact package timestamp tolerance",
                armedAtMillis = 200_000L,
                pendingPackage = "target.package",
                candidates = listOf("target.package" to 100_000L),
                expectedPackage = "target.package",
            ),
            Case(
                name = "rejects stale exact package snapshot",
                armedAtMillis = 200_000L,
                pendingPackage = "target.package",
                candidates = listOf("target.package" to 70_000L),
                expectedPackage = null,
            ),
            Case(
                name = "stays empty when recent packages are ambiguous",
                armedAtMillis = 10_000L,
                pendingPackage = "",
                candidates = listOf("first.package" to 13_000L, "second.package" to 12_500L),
                expectedPackage = null,
            ),
        ).forEach { case ->
            val candidate = selectRecentInstalledCandidateForPendingTrack(
                pendingTrack = pendingTrack(
                    armedAtMillis = case.armedAtMillis,
                    packageName = case.pendingPackage
                ),
                candidates = case.candidates.map { (packageName, lastUpdateTimeMs) ->
                    installedPackage(packageName = packageName, lastUpdateTimeMs = lastUpdateTimeMs)
                }
            )

            assertEquals(case.expectedPackage, candidate?.packageName, case.name)
        }
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
        versionCode: String = "",
        firstInstallTimeMs: Long = lastUpdateTimeMs
    ) = ShareImportInstalledPackageSnapshot(
        packageName = packageName,
        appLabel = appLabel,
        versionCode = versionCode,
        lastUpdateTimeMs = lastUpdateTimeMs,
        firstInstallTimeMs = firstInstallTimeMs
    )
}
