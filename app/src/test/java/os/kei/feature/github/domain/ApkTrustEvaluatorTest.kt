package os.kei.feature.github.domain

import org.junit.Test
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubApkSignatureInfo
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApkTrustEvaluatorTest {
    @Test
    fun `normal release apk is trusted`() {
        val signal = evaluate(
            manifestInfo = manifest(
                packageName = "os.kei.demo",
                versionCode = "2",
                nativeAbis = listOf("arm64-v8a")
            ),
            installedInfo = installed(
                packageName = "os.kei.demo",
                versionCode = 1,
                signatures = listOf("aa")
            ),
            localArchiveInfo = archive(
                packageName = "os.kei.demo",
                versionCode = 2,
                signatures = listOf("aa")
            )
        )

        assertEquals(GitHubDecisionLevel.Good, signal.level)
        assertTrue(GitHubApkTrustReason.PackageMatched in signal.reasons)
        assertTrue(GitHubApkTrustReason.SignatureMatched in signal.reasons)
        assertTrue(GitHubApkTrustReason.VersionUpgrade in signal.reasons)
    }

    @Test
    fun `package mismatch is high risk`() {
        val signal = evaluate(
            manifestInfo = manifest(packageName = "os.kei.other"),
            expectedPackageName = "os.kei.demo"
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.PackageMismatch in signal.reasons)
    }

    @Test
    fun `signature mismatch is high risk`() {
        val signal = evaluate(
            manifestInfo = manifest(
                packageName = "os.kei.demo",
                signature = "bb"
            ),
            installedInfo = installed(
                packageName = "os.kei.demo",
                signatures = listOf("aa")
            )
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.SignatureMismatch in signal.reasons)
    }

    @Test
    fun `missing candidate signature asks for review`() {
        val signal = evaluate(
            manifestInfo = manifest(packageName = "os.kei.demo"),
            installedInfo = installed(
                packageName = "os.kei.demo",
                signatures = listOf("aa")
            )
        )

        assertEquals(GitHubDecisionLevel.Review, signal.level)
        assertTrue(GitHubApkTrustReason.SignatureUnknown in signal.reasons)
    }

    @Test
    fun `version downgrade is high risk`() {
        val signal = evaluate(
            manifestInfo = manifest(packageName = "os.kei.demo", versionCode = "10"),
            installedInfo = installed(packageName = "os.kei.demo", versionCode = 12)
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.VersionDowngrade in signal.reasons)
    }

    @Test
    fun `abi mismatch is high risk`() {
        val signal = evaluate(
            asset = asset("demo-x86.apk"),
            manifestInfo = manifest(nativeAbis = listOf("x86"))
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.IncompatibleAbi in signal.reasons)
    }

    @Test
    fun `min sdk above device is high risk`() {
        val signal = evaluate(
            manifestInfo = manifest(minSdk = "40"),
            deviceSdkInt = 35
        )

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.MinSdkTooHigh in signal.reasons)
    }

    @Test
    fun `debug test only sensitive permission and exported component ask for review`() {
        val signal = evaluate(
            asset = asset("demo-debug.apk"),
            manifestInfo = manifest(
                permissions = listOf("android.permission.REQUEST_INSTALL_PACKAGES"),
                nodes = listOf(
                    GitHubApkManifestNode(
                        tagName = "receiver",
                        displayName = "BootReceiver",
                        attributes = mapOf("exported" to "true")
                    ),
                    GitHubApkManifestNode(
                        tagName = "application",
                        displayName = "app",
                        attributes = mapOf("testOnly" to "true")
                    )
                )
            )
        )

        assertEquals(GitHubDecisionLevel.Review, signal.level)
        assertTrue(GitHubApkTrustReason.DebugBuild in signal.reasons)
        assertTrue(GitHubApkTrustReason.TestOnly in signal.reasons)
        assertTrue(GitHubApkTrustReason.SensitivePermission in signal.reasons)
        assertTrue(GitHubApkTrustReason.ExportedComponent in signal.reasons)
    }

    @Test
    fun `source archive is high risk`() {
        val signal = evaluate(asset = asset("source.zip"))

        assertEquals(GitHubDecisionLevel.Risk, signal.level)
        assertTrue(GitHubApkTrustReason.SourceArchive in signal.reasons)
    }

    private fun evaluate(
        asset: GitHubReleaseAssetFile = asset("demo-arm64-v8a-release.apk"),
        manifestInfo: GitHubApkManifestInfo? = null,
        installedInfo: GitHubInstalledPackageInfo? = null,
        expectedPackageName: String = "",
        localArchiveInfo: LocalApkArchiveInfo? = null,
        deviceSdkInt: Int = 35
    ) = ApkTrustEvaluator.evaluate(
        ApkTrustEvaluationInput(
            asset = asset,
            supportedAbis = listOf("arm64-v8a"),
            manifestInfo = manifestInfo,
            installedInfo = installedInfo,
            expectedPackageName = expectedPackageName,
            localArchiveInfo = localArchiveInfo,
            deviceSdkInt = deviceSdkInt
        )
    )

    private fun asset(name: String): GitHubReleaseAssetFile {
        return GitHubReleaseAssetFile(
            name = name,
            downloadUrl = "https://example.invalid/$name",
            sizeBytes = 4,
            downloadCount = 0
        )
    }

    private fun manifest(
        packageName: String = "os.kei.demo",
        versionCode: String = "",
        minSdk: String = "",
        nativeAbis: List<String> = emptyList(),
        permissions: List<String> = emptyList(),
        nodes: List<GitHubApkManifestNode> = emptyList(),
        signature: String = ""
    ): GitHubApkManifestInfo {
        return GitHubApkManifestInfo(
            assetName = "demo.apk",
            packageName = packageName,
            versionCode = versionCode,
            minSdk = minSdk,
            nativeAbis = nativeAbis,
            permissions = permissions,
            manifestNodes = nodes,
            signatureInfo = signature.takeIf { it.isNotBlank() }?.let {
                GitHubApkSignatureInfo(entryName = "META-INF/CERT.RSA", sha256 = it)
            }
        )
    }

    private fun installed(
        packageName: String,
        versionCode: Long = -1,
        signatures: List<String> = emptyList()
    ): GitHubInstalledPackageInfo {
        return GitHubInstalledPackageInfo(
            packageName = packageName,
            versionCode = versionCode,
            signatureSha256 = signatures
        )
    }

    private fun archive(
        packageName: String,
        versionCode: Long,
        signatures: List<String>
    ): LocalApkArchiveInfo {
        return LocalApkArchiveInfo(
            packageName = packageName,
            versionName = "1.0",
            versionCode = versionCode,
            minSdk = 35,
            targetSdk = 36,
            signatureSha256 = signatures,
            debuggable = false,
            testOnly = false
        )
    }
}
