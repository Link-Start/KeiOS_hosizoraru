package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkPackageNameScannerTest {
    @Test
    fun `scanner resolves latest stable apk and extracts package name`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.scanned")
        )
        val scanner = GitHubApkPackageNameScanner(source)

        val result = scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = "https://github.com/hosizoraru/KeiOS",
                lookupConfig = GitHubLookupConfig(
                    selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                    apiToken = "token-123"
                )
            )
        ).getOrThrow()

        assertEquals("hosizoraru", result.owner)
        assertEquals("KeiOS", result.repo)
        assertEquals("v1.2.3", result.releaseTag)
        assertEquals("KeiOS-debug.apk", result.assetName)
        assertEquals("os.kei.scanned", result.packageName)
        assertEquals(
            "https://github.com/hosizoraru/KeiOS/releases/download/v1.2.3/KeiOS-debug.apk",
            source.scannedDownloadUrl
        )
    }

    @Test
    fun `scanner keeps atom mode on same fast asset scan contract`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.atom")
        )
        val scanner = GitHubApkPackageNameScanner(source)

        val result = scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = "https://github.com/hosizoraru/KeiOS",
                lookupConfig = GitHubLookupConfig(
                    selectedStrategy = GitHubLookupStrategyOption.AtomFeed
                )
            )
        ).getOrThrow()

        assertEquals("os.kei.atom", result.packageName)
        assertEquals(GitHubLookupStrategyOption.AtomFeed, source.scannedStrategy)
        assertEquals(
            "https://github.com/hosizoraru/KeiOS/releases/download/v1.2.3/KeiOS-debug.apk",
            source.scannedDownloadUrl
        )
    }

    @Test
    fun `scanner reports invalid repository url before network work`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.scanned")
        )
        val scanner = GitHubApkPackageNameScanner(source)

        val message = scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = "bad input",
                lookupConfig = GitHubLookupConfig()
            )
        ).exceptionOrNull()?.message.orEmpty()

        assertTrue(message.contains("Invalid GitHub repository URL"))
        assertEquals(0, source.releaseLoadCount)
    }

    private class FakeScanSource(
        private val manifestBytes: ByteArray
    ) : GitHubApkPackageNameScanSource {
        var releaseLoadCount = 0
        var scannedDownloadUrl = ""
        var scannedStrategy: GitHubLookupStrategyOption? = null

        override fun loadLatestStableRelease(
            owner: String,
            repo: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubStableReleaseTarget> {
            releaseLoadCount += 1
            return Result.success(
                GitHubStableReleaseTarget(
                    tag = "v1.2.3",
                    releaseUrl = "https://github.com/$owner/$repo/releases/tag/v1.2.3"
                )
            )
        }

        override fun fetchApkAssets(
            owner: String,
            repo: String,
            release: GitHubStableReleaseTarget,
            lookupConfig: GitHubLookupConfig
        ): Result<List<GitHubReleaseAssetFile>> {
            return Result.success(
                listOf(
                    GitHubReleaseAssetFile(
                        name = "KeiOS-debug.apk",
                        downloadUrl = "https://github.com/$owner/$repo/releases/download/${release.tag}/KeiOS-debug.apk",
                        apiAssetUrl = "https://api.github.com/repos/$owner/$repo/releases/assets/1",
                        sizeBytes = 1024L,
                        downloadCount = 1
                    )
                )
            )
        }

        override fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> {
            scannedDownloadUrl = asset.downloadUrl
            scannedStrategy = lookupConfig.selectedStrategy
            return Result.success(manifestBytes)
        }
    }
}
