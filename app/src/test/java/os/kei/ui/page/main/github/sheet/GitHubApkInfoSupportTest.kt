package os.kei.ui.page.main.github.sheet

import org.junit.Test
import os.kei.R
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestMetadata
import os.kei.feature.github.model.GitHubApkManifestNode
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.GitHubStatusPalette
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubApkInfoSupportTest {
    @Test
    fun `difference signals prefer remote version and target api when newer`() {
        val signals =
            buildApkDifferenceSignals(
                info =
                    GitHubApkManifestInfo(
                        assetName = "demo.apk",
                        versionCode = "12",
                        targetSdk = "36",
                    ),
                installedInfo =
                    GitHubInstalledPackageInfo(
                        packageName = "os.kei.demo",
                        versionCode = 10,
                        targetSdk = 35,
                    ),
                strings = testStrings,
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
        val node =
            GitHubApkManifestNode(
                tagName = "receiver",
                displayName = "os.kei.demo.BootReceiver",
                attributes = mapOf("exported" to "true"),
            )

        assertTrue(node.isExportedComponent())
        assertTrue(node.riskPills().any { it.color == GitHubStatusPalette.Error })
    }

    @Test
    fun `query filtering searches node attributes`() {
        val nodes =
            listOf(
                GitHubApkManifestNode(
                    tagName = "provider",
                    displayName = "DemoProvider",
                    attributes = mapOf("authorities" to "os.kei.demo.provider"),
                ),
            )

        assertEquals(nodes, nodes.filterNodesByQuery("provider"))
        assertEquals(emptyList(), nodes.filterNodesByQuery("missing"))
    }

    @Test
    fun `apk info sheet derivation filters lists and manifest nodes`() {
        val exportedReceiver =
            GitHubApkManifestNode(
                tagName = "receiver",
                displayName = "BootReceiver",
                attributes = mapOf("exported" to "true"),
            )
        val info =
            GitHubApkManifestInfo(
                assetName = "demo.apk",
                nativeAbis = listOf("arm64-v8a", "x86_64"),
                permissions = listOf("android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS"),
                features = listOf("android.hardware.camera", "android.hardware.bluetooth"),
                metadata =
                    listOf(
                        GitHubApkManifestMetadata("channel", "github"),
                        GitHubApkManifestMetadata("flavor", "benchmark"),
                    ),
                manifestNodes =
                    listOf(
                        exportedReceiver,
                        GitHubApkManifestNode(
                            tagName = "activity",
                            displayName = "MainActivity",
                        ),
                    ),
            )

        val state =
            deriveGitHubApkInfoSheetState(
                GitHubApkInfoSheetInput(
                    assetKey = "demo.apk",
                    info = info,
                    query = "camera",
                ),
            )

        assertEquals("camera", state.normalizedQuery)
        assertEquals(emptyList(), state.nativeAbiValues)
        assertEquals(listOf("android.permission.CAMERA"), state.permissionValues)
        assertEquals(GitHubStatusPalette.Error, state.permissionColors["android.permission.CAMERA"])
        assertEquals(listOf("android.hardware.camera"), state.featureValues)
        assertEquals(emptyList(), state.metadataValues)
        assertEquals(emptyList(), state.manifestNodes)
        assertEquals(emptyMap(), state.manifestNodeGroups)

        val receiverState =
            deriveGitHubApkInfoSheetState(
                GitHubApkInfoSheetInput(
                    assetKey = "demo.apk",
                    info = info,
                    query = "boot",
                ),
            )

        assertEquals(listOf(exportedReceiver), receiverState.manifestNodes)
        assertEquals(listOf(exportedReceiver), receiverState.manifestNodeGroups[R.string.github_apk_info_group_exported])
    }

    @Test
    fun `apk info sheet derivation keeps all values for blank query`() {
        val info =
            GitHubApkManifestInfo(
                assetName = "demo.apk",
                nativeAbis = listOf("arm64-v8a"),
                permissions = listOf("android.permission.CAMERA"),
                features = listOf("android.hardware.camera"),
                metadata = listOf(GitHubApkManifestMetadata("channel", "github")),
                manifestNodes =
                    listOf(
                        GitHubApkManifestNode(
                            tagName = "activity",
                            displayName = "MainActivity",
                        ),
                    ),
            )

        val state =
            deriveGitHubApkInfoSheetState(
                GitHubApkInfoSheetInput(
                    assetKey = "demo.apk",
                    info = info,
                    query = "  ",
                ),
            )

        assertEquals(listOf("arm64-v8a"), state.nativeAbiValues)
        assertEquals(listOf("android.permission.CAMERA"), state.permissionValues)
        assertEquals(listOf("android.hardware.camera"), state.featureValues)
        assertEquals(listOf("channel: github"), state.metadataValues)
        assertEquals(info.manifestNodes, state.manifestNodes)
        assertEquals(info.manifestNodes, state.manifestNodeGroups[R.string.github_apk_info_group_components])
    }

    private companion object {
        val testStrings =
            ApkDifferenceStrings(
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
                abiMismatch = "abi mismatch",
            )
    }
}
