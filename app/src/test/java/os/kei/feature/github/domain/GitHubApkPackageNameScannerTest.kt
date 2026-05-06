package os.kei.feature.github.domain

import org.junit.Test
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkPackageNameScanRequest
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubLookupStrategyOption
import java.util.Collections
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
    fun `scanner extracts package name from selected apk asset`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.selected")
        )
        val scanner = GitHubApkPackageNameScanner(source)
        val asset = GitHubReleaseAssetFile(
            name = "KeiOS-selected.apk",
            downloadUrl = "https://github.com/hosizoraru/KeiOS/releases/download/v1.2.3/KeiOS-selected.apk",
            apiAssetUrl = "https://api.github.com/repos/hosizoraru/KeiOS/releases/assets/42",
            sizeBytes = 2048L,
            downloadCount = 1
        )

        val packageName = scanner.scanAssetPackageName(
            asset = asset,
            lookupConfig = GitHubLookupConfig(
                selectedStrategy = GitHubLookupStrategyOption.GitHubApiToken,
                apiToken = "token-123"
            )
        ).getOrThrow()

        assertEquals("os.kei.selected", packageName)
        assertEquals(asset.downloadUrl, source.scannedDownloadUrl)
        assertEquals(GitHubLookupStrategyOption.GitHubApiToken, source.scannedStrategy)
    }

    @Test
    fun `scanner falls back across apk assets when one manifest cannot be parsed`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.fallback"),
            assetNames = listOf("KeiOS-metadata.apk", "KeiOS-universal.apk"),
            manifestBytesByAsset = mapOf(
                "KeiOS-metadata.apk" to byteArrayOf(0x01, 0x02),
                "KeiOS-universal.apk" to BinaryManifestFixture.build("os.kei.fallback")
            ),
            readDelayMsByAsset = mapOf(
                "KeiOS-universal.apk" to 25L
            )
        )
        val scanner = GitHubApkPackageNameScanner(source)

        val result = scanner.scan(
            GitHubApkPackageNameScanRequest(
                repoUrl = "https://github.com/hosizoraru/KeiOS",
                lookupConfig = GitHubLookupConfig()
            )
        ).getOrThrow()

        assertEquals("KeiOS-universal.apk", result.assetName)
        assertEquals("os.kei.fallback", result.packageName)
        assertTrue(source.scannedAssetNames.contains("KeiOS-metadata.apk"))
        assertTrue(source.scannedAssetNames.contains("KeiOS-universal.apk"))
    }

    @Test
    fun `scanner keeps lookup strategy while scanning apk assets in parallel`() {
        val source = FakeScanSource(
            manifestBytes = BinaryManifestFixture.build("os.kei.parallel"),
            assetNames = listOf("KeiOS-arm64.apk", "KeiOS-x86.apk")
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

        assertEquals("os.kei.parallel", result.packageName)
        assertEquals(
            List(source.scannedStrategies.size) { GitHubLookupStrategyOption.GitHubApiToken },
            source.scannedStrategies
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
        private val manifestBytes: ByteArray,
        private val assetNames: List<String> = listOf("KeiOS-debug.apk"),
        private val manifestBytesByAsset: Map<String, ByteArray> = emptyMap(),
        private val readDelayMsByAsset: Map<String, Long> = emptyMap()
    ) : GitHubApkPackageNameScanSource {
        var releaseLoadCount = 0
        var scannedDownloadUrl = ""
        var scannedStrategy: GitHubLookupStrategyOption? = null
        val scannedAssetNames: MutableList<String> =
            Collections.synchronizedList(mutableListOf())
        val scannedStrategies: MutableList<GitHubLookupStrategyOption> =
            Collections.synchronizedList(mutableListOf())

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
                assetNames.mapIndexed { index, assetName ->
                    GitHubReleaseAssetFile(
                        name = assetName,
                        downloadUrl = "https://github.com/$owner/$repo/releases/download/${release.tag}/$assetName",
                        apiAssetUrl = "https://api.github.com/repos/$owner/$repo/releases/assets/${index + 1}",
                        sizeBytes = 1024L,
                        downloadCount = 1
                    )
                }
            )
        }

        override fun readAndroidManifestBytes(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<ByteArray> {
            scannedDownloadUrl = asset.downloadUrl
            scannedStrategy = lookupConfig.selectedStrategy
            scannedAssetNames += asset.name
            scannedStrategies += lookupConfig.selectedStrategy
            readDelayMsByAsset[asset.name]?.takeIf { it > 0L }?.let { delayMs ->
                Thread.sleep(delayMs)
            }
            return Result.success(manifestBytesByAsset[asset.name] ?: manifestBytes)
        }
    }
}
