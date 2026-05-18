package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubStarImportApkVerification
import os.kei.feature.github.model.GitHubStarImportApkVerificationStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubStarImportApkVerifierTest {
    @Test
    fun `verifier reports latest stable apk assets`() = runBlocking {
        val source = FakeApkVerificationSource(
            manifestBytes = BinaryManifestFixture.build("demo.app"),
            releaseAssets = GitHubStableReleaseApkAssets(
                release = GitHubStableReleaseTarget(
                    tag = "v1.0.0",
                    releaseUrl = "https://github.com/demo/app/releases/tag/v1.0.0"
                ),
                assets = listOf(
                    GitHubReleaseAssetFile(
                        name = "demo-arm64.apk",
                        downloadUrl = "https://example.com/demo.apk",
                        sizeBytes = 10,
                        downloadCount = 1
                    )
                )
            )
        )

        val result = GitHubStarImportApkVerifier(source).verify(
            candidate = importCandidate(
                repo = "app",
                description = "Android APK",
                language = "Kotlin"
            ),
            lookupConfig = GitHubLookupConfig(),
            nowMillis = 100L
        )

        assertEquals(GitHubStarImportApkVerificationStatus.HasApk, result.status)
        assertEquals("v1.0.0", result.releaseTag)
        assertEquals(1, result.apkAssetCount)
        assertEquals("demo-arm64.apk", result.sampleAssetName)
        assertEquals("demo.app", result.packageName)
        assertEquals(100L, result.checkedAtMillis)
    }

    @Test
    fun `verifier returns cached result without calling source`() = runBlocking {
        val cache = FakeApkVerificationCache(
            cached = GitHubStarImportApkVerification(
                owner = "demo",
                repo = "app",
                status = GitHubStarImportApkVerificationStatus.HasApk,
                releaseTag = "cached",
                apkAssetCount = 1,
                checkedAtMillis = 50L
            )
        )
        val source = FakeApkVerificationSource(error = AssertionError("source should not run"))

        val result = GitHubStarImportApkVerifier(source, cache).verify(
            candidate = importCandidate(
                repo = "app",
                description = "Android APK",
                language = "Kotlin"
            ),
            lookupConfig = GitHubLookupConfig(),
            nowMillis = 100L
        )

        assertEquals("cached", result.releaseTag)
        assertTrue(result.fromCache)
    }

    @Test
    fun `verifier scans later apk when first manifest is invalid`() = runBlocking {
        val source = FakeApkVerificationSource(
            manifestBytesByAsset = mapOf(
                "demo-metadata.apk" to byteArrayOf(0x01, 0x02),
                "demo-universal.apk" to BinaryManifestFixture.build("demo.universal")
            ),
            releaseAssets = GitHubStableReleaseApkAssets(
                release = GitHubStableReleaseTarget(
                    tag = "v1.0.1",
                    releaseUrl = "https://github.com/demo/app/releases/tag/v1.0.1"
                ),
                assets = listOf(
                    GitHubReleaseAssetFile(
                        name = "demo-metadata.apk",
                        downloadUrl = "https://example.com/metadata.apk",
                        sizeBytes = 10,
                        downloadCount = 1
                    ),
                    GitHubReleaseAssetFile(
                        name = "demo-universal.apk",
                        downloadUrl = "https://example.com/universal.apk",
                        sizeBytes = 20,
                        downloadCount = 2
                    )
                )
            )
        )

        val result = GitHubStarImportApkVerifier(source).verify(
            candidate = importCandidate(
                repo = "app",
                description = "Android APK",
                language = "Kotlin"
            ),
            lookupConfig = GitHubLookupConfig(),
            nowMillis = 200L
        )

        assertEquals(GitHubStarImportApkVerificationStatus.HasApk, result.status)
        assertEquals("demo-universal.apk", result.sampleAssetName)
        assertEquals("demo.universal", result.packageName)
    }

    @Test
    fun `verifier converts source failure into failed verification`() = runBlocking {
        val result = GitHubStarImportApkVerifier(
            FakeApkVerificationSource(error = IllegalStateException("no stable release"))
        ).verify(
            candidate = importCandidate(
                repo = "tool",
                description = "CLI",
                language = "Rust"
            ),
            lookupConfig = GitHubLookupConfig(),
            nowMillis = 100L
        )

        assertEquals(GitHubStarImportApkVerificationStatus.Failed, result.status)
        assertEquals("no stable release", result.errorMessage)
    }
}

private class FakeApkVerificationSource(
    private val releaseAssets: GitHubStableReleaseApkAssets? = null,
    private val manifestBytes: ByteArray? = null,
    private val manifestBytesByAsset: Map<String, ByteArray> = emptyMap(),
    private val error: Throwable? = null
) : GitHubApkPackageNameScanSource {
    override suspend fun loadLatestStableRelease(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseTarget> {
        return Result.failure(UnsupportedOperationException())
    }

    override suspend fun fetchApkAssets(
        owner: String,
        repo: String,
        release: GitHubStableReleaseTarget,
        lookupConfig: GitHubLookupConfig
    ): Result<List<GitHubReleaseAssetFile>> {
        return Result.failure(UnsupportedOperationException())
    }

    override suspend fun loadLatestStableApkAssets(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig
    ): Result<GitHubStableReleaseApkAssets> {
        error?.let { return Result.failure(it) }
        return Result.success(requireNotNull(releaseAssets))
    }

    override suspend fun readAndroidManifestBytes(
        asset: GitHubReleaseAssetFile,
        lookupConfig: GitHubLookupConfig
    ): Result<ByteArray> {
        manifestBytesByAsset[asset.name]?.let { return Result.success(it) }
        return manifestBytes?.let { Result.success(it) }
            ?: Result.failure(UnsupportedOperationException())
    }
}

private class FakeApkVerificationCache(
    private val cached: GitHubStarImportApkVerification? = null
) : GitHubStarImportApkVerificationCache {
    override fun load(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        refreshIntervalHours: Int,
        nowMillis: Long
    ): GitHubStarImportApkVerification? {
        return cached
    }

    override fun save(
        owner: String,
        repo: String,
        lookupConfig: GitHubLookupConfig,
        verification: GitHubStarImportApkVerification
    ) = Unit
}
