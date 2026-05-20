package os.kei.feature.github.domain

import kotlinx.coroutines.runBlocking
import org.junit.Test
import os.kei.feature.github.data.apk.AndroidBinaryXmlPackageNameParser
import os.kei.feature.github.data.apk.BinaryManifestFixture
import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.GitHubVersionUtils
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubReleaseVersionSignals
import os.kei.feature.github.model.GitHubVersionCandidateSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class GitHubPreciseApkVersionResolverTest {
    @Test
    fun `resolver prefers apk manifest package matching tracked package`() {
        val source = FakePreciseSource(
            assets = listOf(
                asset("other.apk"),
                asset("demo-arm64.apk"),
                asset("demo-universal.apk")
            ),
            manifests = mapOf(
                "other.apk" to manifest("other.app", "9.9.9", 999L),
                "demo-arm64.apk" to manifest("demo.app", "2.0.0", 20L),
                "demo-universal.apk" to manifest("demo.app", "2.0.1", 21L)
            )
        )

        val result = runBlocking {
            GitHubPreciseApkVersionResolver(source).resolve(
            GitHubPreciseApkVersionRequest(
                owner = "demo",
                repo = "app",
                release = release("v2.0.0", "Demo Release"),
                packageName = "demo.app",
                lookupConfig = GitHubLookupConfig()
            )
            )
        }.getOrThrow()

        assertEquals("demo-arm64.apk", result.assetName)
        assertEquals("demo.app", result.packageName)
        assertEquals("2.0.0", result.versionName)
        assertEquals("20", result.versionCode)
        assertEquals("Demo Release · v2.0.0", result.releaseLabel())
    }

    @Test
    fun `resolver inspects enough apk assets for multi package releases`() {
        val assets = (1..14).map { index -> asset("demo-$index.apk") }
        val source = FakePreciseSource(
            assets = assets,
            manifests = assets.withIndex().associate { (index, asset) ->
                asset.name to manifest(
                    packageName = if (index == 10) {
                        "demo.target"
                    } else {
                        "other.${asset.name.removeSuffix(".apk").replace('-', '.')}"
                    },
                    versionName = "1.0.${index + 1}",
                    versionCode = index + 1L
                )
            }
        )

        runBlocking {
            GitHubPreciseApkVersionResolver(source).resolve(
            GitHubPreciseApkVersionRequest(
                owner = "demo",
                repo = "app",
                release = release("v1.0.0"),
                packageName = "demo.target",
                lookupConfig = GitHubLookupConfig()
            )
            )
        }.getOrThrow().also { result ->
            assertEquals("demo-11.apk", result.assetName)
            assertEquals("demo.target", result.packageName)
        }

        assertEquals(12, source.inspectCount)
    }

    private fun release(
        tag: String,
        title: String = tag
    ): GitHubReleaseVersionSignals {
        return GitHubReleaseVersionSignals(
            displayVersion = title,
            rawTag = tag,
            rawName = title,
            link = GitHubVersionUtils.buildReleaseTagUrl("demo", "app", tag),
            versionCandidates = GitHubVersionUtils.buildVersionCandidates(
                GitHubVersionCandidateSource.Tag to tag,
                GitHubVersionCandidateSource.Title to title
            ),
            source = GitHubReleaseSignalSource.GitHubApi,
            channel = GitHubReleaseChannel.STABLE
        )
    }

    private fun asset(name: String): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = name,
            downloadUrl = "https://github.com/demo/app/releases/download/v1/$name",
            sizeBytes = 1024L,
            downloadCount = 1
        )
    }

    private fun manifest(
        packageName: String,
        versionName: String,
        versionCode: Long
    ): GitHubApkManifestInfo {
        return AndroidBinaryXmlPackageNameParser.parseManifestInfo(
            BinaryManifestFixture.build(
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode
            )
        ).getOrThrow()
    }

    private class FakePreciseSource(
        private val assets: List<GitHubReleaseAssetFile>,
        private val manifests: Map<String, GitHubApkManifestInfo>
    ) : GitHubPreciseApkVersionSource {
        private val inspectCounter = AtomicInteger(0)

        val inspectCount: Int
            get() = inspectCounter.get()

        override suspend fun loadReleaseAssetBundle(
            owner: String,
            repo: String,
            rawTag: String,
            releaseUrl: String,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubReleaseAssetBundle> {
            return Result.success(
                GitHubReleaseAssetBundle(
                    releaseName = "Demo Release",
                    tagName = rawTag,
                    htmlUrl = releaseUrl,
                    assets = assets
                )
            )
        }

        override suspend fun inspectApk(
            asset: GitHubReleaseAssetFile,
            lookupConfig: GitHubLookupConfig
        ): Result<GitHubApkManifestInfo> {
            inspectCounter.incrementAndGet()
            return Result.success(requireNotNull(manifests[asset.name]))
        }
    }
}
