package os.kei.feature.github.domain.fdroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecar
import os.kei.feature.github.data.local.fdroid.FdroidMetadataSidecarWriter
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidRepositorySnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidIndexFormat
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.FdroidTrustPolicy
import os.kei.feature.github.model.GITHUB_FDROID_STRATEGY_ID
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
import os.kei.feature.github.model.GitHubTrackedSourceMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FdroidReleaseCheckSourceTest {
    @Test
    fun `evaluate maps selected fdroid candidate to update check`() = runBlocking {
        var savedSidecar: FdroidMetadataSidecar? = null
        val source = source(
            packageSnapshot(
                version(versionCode = 102, versionName = "1.2.0"),
                version(versionCode = 100, versionName = "1.0.0")
            ),
            metadataWriter = { sidecar -> savedSidecar = sidecar }
        )

        val result = source.evaluate(
            item = fdroidItem(),
            lookupConfig = GitHubLookupConfig(),
            localVersion = "1.0.0",
            localVersionCode = 100,
            forceRefresh = false
        )

        assertEquals(GITHUB_FDROID_STRATEGY_ID, result.strategyId)
        assertEquals(GitHubTrackedReleaseStatus.UpdateAvailable, result.status)
        assertEquals(true, result.hasUpdate)
        assertEquals("1.2.0 (102)", result.stableRelease?.displayVersion)
        assertEquals("102", result.preciseStableApkVersion?.versionCode)
        assertEquals("org.fdroid.fdroid_102.apk", result.preciseStableApkVersion?.assetName)
        assertEquals("fdroid_repository-v1", result.sourceConfigSignature.substringBefore('|'))
        assertEquals("1.2.0", savedSidecar?.selectedVersion?.versionName)
        assertEquals("sha256-102", savedSidecar?.trust?.apkSha256)
        assertEquals("F-Droid", savedSidecar?.repo?.repoName)
        assertEquals(1, savedSidecar?.repo?.packageCount)
    }

    @Test
    fun `evaluate reports failure when package is missing`() = runBlocking {
        val source = FdroidReleaseCheckSource(
            snapshotProvider = staticSnapshotProvider(
                Result.failure(IllegalStateException("package missing"))
            ),
            metadataWriter = { },
            ioDispatcher = Dispatchers.Unconfined,
            deviceSdkProvider = { 37 }
        )

        val result = source.evaluate(
            item = fdroidItem(),
            lookupConfig = GitHubLookupConfig(),
            localVersion = "",
            localVersionCode = -1L,
            forceRefresh = false
        )

        assertEquals(GitHubTrackedReleaseStatus.Failed, result.status)
        assertTrue(result.message.contains("package missing"))
    }

    @Test
    fun `trust policy failures block the check`() = runBlocking {
        // (policy config, the only version offered, fragment the failure message must name)
        val rows = listOf(
            Triple(
                FdroidTrackedAppConfig(trustPolicy = FdroidTrustPolicy.RequireApkHash),
                version(versionCode = 102, versionName = "1.2.0", apkSha256 = ""),
                "APK hash"
            ),
            Triple(
                FdroidTrackedAppConfig(trustPolicy = FdroidTrustPolicy.RequireOfficialSignerIndex),
                version(versionCode = 102, versionName = "1.2.0", signerSha256 = emptyList()),
                "signer index"
            ),
            Triple(
                FdroidTrackedAppConfig(
                    trustPolicy = FdroidTrustPolicy.RequireRepoFingerprint,
                    repoFingerprint = ""
                ),
                version(versionCode = 102, versionName = "1.2.0"),
                "repository fingerprint"
            )
        )

        rows.forEach { (config, offered, expectedMessage) ->
            val result = source(packageSnapshot(offered)).evaluate(
                item = fdroidItem(config),
                lookupConfig = GitHubLookupConfig(),
                localVersion = "1.0.0",
                localVersionCode = 100,
                forceRefresh = false
            )

            assertEquals(GitHubTrackedReleaseStatus.Failed, result.status, "${config.trustPolicy}")
            assertTrue(
                result.message.contains(expectedMessage),
                "${config.trustPolicy}: expected \"$expectedMessage\" in \"${result.message}\""
            )
        }
    }

    private fun source(
        packageSnapshot: FdroidPackageSnapshot,
        metadataWriter: FdroidMetadataSidecarWriter = FdroidMetadataSidecarWriter { }
    ): FdroidReleaseCheckSource {
        return FdroidReleaseCheckSource(
            snapshotProvider = staticLookupSnapshotProvider(
                packageSnapshot = packageSnapshot,
                repositorySnapshot = repositorySnapshot(packageSnapshot)
            ),
            metadataWriter = metadataWriter,
            ioDispatcher = Dispatchers.Unconfined,
            deviceSdkProvider = { 37 }
        )
    }

    private fun packageSnapshot(vararg versions: FdroidVersionSnapshot): FdroidPackageSnapshot {
        return FdroidPackageSnapshot(
            repoUrl = "https://f-droid.org/repo",
            packageName = "org.fdroid.fdroid",
            suggestedVersionCode = 102,
            appName = "F-Droid",
            versions = versions.toList()
        )
    }

    private fun staticSnapshotProvider(
        result: Result<FdroidPackageSnapshot>
    ): FdroidPackageSnapshotProvider {
        return FdroidPackageSnapshotProvider { _, _ -> result }
    }

    private fun staticLookupSnapshotProvider(
        packageSnapshot: FdroidPackageSnapshot,
        repositorySnapshot: FdroidRepositorySnapshot
    ): FdroidPackageSnapshotProvider {
        return object : FdroidPackageSnapshotProvider, FdroidPackageLookupSnapshotProvider {
            override suspend fun loadPackageSnapshot(
                item: GitHubTrackedApp,
                forceRefresh: Boolean
            ): Result<FdroidPackageSnapshot> {
                return Result.success(packageSnapshot)
            }

            override suspend fun loadPackageLookupSnapshot(
                item: GitHubTrackedApp,
                forceRefresh: Boolean
            ): Result<FdroidPackageLookupSnapshot> {
                return Result.success(
                    FdroidPackageLookupSnapshot(
                        packageSnapshot = packageSnapshot,
                        repositorySnapshot = repositorySnapshot
                    )
                )
            }
        }
    }

    private fun fdroidItem(
        fdroidConfig: FdroidTrackedAppConfig = FdroidTrackedAppConfig()
    ): GitHubTrackedApp {
        return GitHubTrackedApp(
            repoUrl = "https://f-droid.org/repo",
            owner = "f-droid.org",
            repo = "repo",
            packageName = "org.fdroid.fdroid",
            appLabel = "F-Droid",
            sourceMode = GitHubTrackedSourceMode.FdroidRepository,
            fdroidConfig = fdroidConfig
        )
    }

    private fun version(
        versionCode: Long,
        versionName: String,
        apkSha256: String = "sha256-$versionCode",
        signerSha256: List<String> = emptyList()
    ) = fdroidVersionFixture(
        versionCode = versionCode,
        versionName = versionName,
        apkName = "org.fdroid.fdroid_$versionCode.apk",
        apkPath = "/repo/org.fdroid.fdroid_$versionCode.apk",
        apkSha256 = apkSha256,
        signerSha256 = signerSha256
    )

    private fun repositorySnapshot(packageSnapshot: FdroidPackageSnapshot): FdroidRepositorySnapshot {
        return FdroidRepositorySnapshot(
            repoUrl = "https://f-droid.org/repo",
            format = FdroidIndexFormat.V2,
            repoName = "F-Droid",
            repoDescription = "Official repository",
            timestampMillis = 1_777_392_000_000L,
            mirrors = emptyList(),
            packages = mapOf(packageSnapshot.packageName to packageSnapshot)
        )
    }
}
