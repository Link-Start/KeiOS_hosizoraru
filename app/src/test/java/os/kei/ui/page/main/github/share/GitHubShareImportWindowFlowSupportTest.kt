package os.kei.ui.page.main.github.share

import org.junit.Test
import os.kei.feature.github.data.local.GITHUB_SHARE_IMPORT_RESULT_STATUS_ADDED
import os.kei.feature.github.data.local.GitHubPendingShareImportTrackRecord
import os.kei.feature.github.data.local.GitHubShareImportResultRecord
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
