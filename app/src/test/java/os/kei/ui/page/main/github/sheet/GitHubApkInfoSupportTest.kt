package os.kei.ui.page.main.github.sheet

import org.junit.Test
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkInfoSupportTest {
    @Test
    fun `difference signals prefer remote version and target api when newer`() {
        val signals = buildApkDifferenceSignals(
            info = GitHubApkManifestInfo(
                assetName = "demo.apk",
                versionCode = "12",
                targetSdk = "36"
            ),
            installedInfo = GitHubInstalledPackageInfo(
                packageName = "os.kei.demo",
                versionCode = 10,
                targetSdk = 35
            ),
            strings = testStrings
        )

        assertTrue(signals.any { it.label == "remote newer" && it.color == GitHubStatusPalette.Update })
        assertTrue(signals.any { it.label == "target higher" && it.color == GitHubStatusPalette.Update })
    }

    @Test
    fun `permission risk highlights sensitive package install permission`() {
        val color = permissionRiskColor("android.permission.REQUEST_INSTALL_PACKAGES")

        assertEquals(GitHubStatusPalette.Error, color)
    }

    @Test
    fun `exported component without permission is detected`() {
        val node = GitHubApkManifestNode(
            tagName = "receiver",
            displayName = "os.kei.demo.BootReceiver",
            attributes = mapOf("exported" to "true")
        )

        assertTrue(node.isExportedComponent())
        assertTrue(node.riskPills().any { it.color == GitHubStatusPalette.Error })
    }

    @Test
    fun `query filtering searches node attributes`() {
        val nodes = listOf(
            GitHubApkManifestNode(
                tagName = "provider",
                displayName = "DemoProvider",
                attributes = mapOf("authorities" to "os.kei.demo.provider")
            )
        )

        assertEquals(nodes, nodes.filterNodesByQuery("provider"))
        assertEquals(emptyList(), nodes.filterNodesByQuery("missing"))
    }

    private companion object {
        val testStrings = ApkDifferenceStrings(
            notInstalled = "not installed",
            remoteVersionHigher = "remote newer",
            localVersionHigher = "local newer",
            sameVersion = "same version",
            versionManual = "manual",
            targetHigher = "target higher",
            targetSame = "target same",
            targetLower = "target lower",
            abiUniversal = "universal",
            abiMatch = "abi match",
            abiMismatch = "abi mismatch"
        )
    }
}
