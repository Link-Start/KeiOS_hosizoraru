package os.kei.feature.github.engine.apk

import org.junit.Test
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubApkCandidateSelectionEngineTest {
    @Test
    fun `inspection plan prioritizes package affinity and installable abi`() {
        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = listOf(
                asset("other-arm64-v8a-release.apk"),
                asset("keios-universal-release.apk"),
                asset("keios-debug-arm64-v8a.apk"),
                asset("keios-metadata.apk"),
                asset("notes.txt"),
            ),
            expectedPackageName = "os.kei.keios",
        )

        assertEquals(
            listOf(
                "keios-universal-release.apk",
                "other-arm64-v8a-release.apk",
                "keios-debug-arm64-v8a.apk",
            ),
            planned.map { it.name },
        )
    }

    @Test
    fun `package arm64 release listed after the inspection cap is planned first`() {
        // KeiOS and Mithka release naming: a dozen unrelated APKs, then every variant of the target.
        val target = "KeiOS-arm64-v8a-release.apk"
        val assets = buildList {
            repeat(12) { index -> add(asset("other-package-$index.apk")) }
            add(asset("notes.txt"))
            add(asset("KeiOS-metadata.apk"))
            add(asset("KeiOS-debug-arm64-v8a.apk"))
            add(asset("KeiOS-benchmark-arm64-v8a.apk"))
            add(asset("KeiOS-x86_64-release.apk"))
            add(asset("KeiOS-armeabi-v7a-release.apk"))
            add(asset("KeiOS-universal-release.apk"))
            add(asset(target))
        }
        assertTrue(
            assets.filter { it.name.endsWith(".apk") }.indexOfFirst { it.name == target } >=
                GitHubApkCandidateSelectionEngine.DEFAULT_MAX_INSPECTION_CANDIDATES,
            "premise: taking APKs in repository order would never inspect the target",
        )

        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = assets,
            expectedPackageName = "os.kei.keios",
        )

        assertEquals(target, planned.first().name, planned.map { it.name }.toString())
    }

    @Test
    fun `mithka corpus prefers arm64 release before legacy abi`() {
        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = listOf(
                asset("mithka-0.3.0+26071104-armeabi-v7a-release.apk"),
                asset("mithka-0.3.0+26071104-arm64-v8a-release.apk"),
            ),
            expectedPackageName = "ad.neko.mithka",
        )

        assertEquals(
            "mithka-0.3.0+26071104-arm64-v8a-release.apk",
            planned.first().name,
        )
    }

    @Test
    fun `equal priority assets retain repository order`() {
        val assets = listOf(asset("app-one.apk"), asset("app-two.apk"), asset("app-three.apk"))

        assertEquals(
            assets,
            GitHubApkCandidateSelectionEngine.planInspection(
                assets = assets,
                expectedPackageName = "",
            ),
        )
    }

    @Test
    fun `inspection plan enforces bounded candidate count`() {
        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = (1..20).map { index -> asset("app-$index.apk") },
            expectedPackageName = "demo.app",
        )

        assertEquals(
            GitHubApkCandidateSelectionEngine.DEFAULT_MAX_INSPECTION_CANDIDATES,
            planned.size,
        )
    }

    @Test
    fun `hma corpus inspects release zip when release has no top level apk`() {
        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = listOf(
                asset("HMA-OSS-ZYGISK-oss-164-debug.zip"),
                asset("HMA-OSS-ZYGISK-oss-164-release.zip"),
                asset("translators.json"),
            ),
            expectedPackageName = "org.frknkrc44.hma_oss",
        )

        assertEquals(
            listOf(
                "HMA-OSS-ZYGISK-oss-164-release.zip",
                "HMA-OSS-ZYGISK-oss-164-debug.zip",
            ),
            planned.map { it.name },
        )
    }

    @Test
    fun `top level apk prevents unrelated zip inspection`() {
        val planned = GitHubApkCandidateSelectionEngine.planInspection(
            assets = listOf(
                asset("app-release.apk"),
                asset("source.zip"),
            ),
            expectedPackageName = "demo.app",
        )

        assertEquals(listOf("app-release.apk"), planned.map { it.name })
    }

    @Test
    fun `manifest selection prefers exact package over earlier valid fallback`() {
        val selection = GitHubApkCandidateSelectionEngine.selectInspected(
            inspected = listOf(
                inspected("other.apk", "other.app", "9.0.0", "90"),
                inspected("target.apk", "demo.target", "2.0.0", "20"),
            ),
            expectedPackageName = "demo.target",
        )

        assertEquals("target.apk", selection?.candidate?.asset?.name)
        assertTrue(selection?.packageMatched == true)
    }

    @Test
    fun `manifest selection skips versionless results and keeps first valid fallback`() {
        val selection = GitHubApkCandidateSelectionEngine.selectInspected(
            inspected = listOf(
                inspected("empty.apk", "demo.empty", "", ""),
                inspected("fallback.apk", "other.app", "1.0.0", "10"),
            ),
            expectedPackageName = "demo.target",
        )

        assertEquals("fallback.apk", selection?.candidate?.asset?.name)
        assertFalse(selection?.packageMatched == true)
    }

    private fun inspected(
        assetName: String,
        packageName: String,
        versionName: String,
        versionCode: String,
    ): GitHubInspectedApkCandidate {
        return GitHubInspectedApkCandidate(
            asset = asset(assetName),
            manifest = GitHubApkManifestInfo(
                assetName = assetName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
            ),
        )
    }

    private fun asset(name: String): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = name,
            downloadUrl = "https://example.test/$name",
            sizeBytes = 1_024L,
            downloadCount = 1,
        )
    }
}
