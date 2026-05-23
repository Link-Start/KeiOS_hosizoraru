package os.kei.ui.page.main.github.sheet

import org.junit.Test
import os.kei.feature.github.data.remote.GITHUB_ACTIONS_APK_ARTIFACT_CONTENT_TYPE
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.ui.page.main.github.GitHubDecisionLevel
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubManagedInstallConfirmSheetStateTest {
    @Test
    fun `managed install state derives update and abi match`() {
        val state =
            deriveGitHubManagedInstallConfirmSheetState(
                GitHubManagedInstallConfirmSheetInput(
                    requestKey = "request",
                    request = request(assetName = "keios-arm64-v8a.apk"),
                    info =
                        GitHubApkManifestInfo(
                            assetName = "keios-arm64-v8a.apk",
                            appLabel = "KeiOS Remote",
                            packageName = "os.kei",
                            versionName = "1.9.0",
                            versionCode = "90",
                            nativeAbis = listOf("arm64-v8a"),
                        ),
                    installedInfo =
                        GitHubInstalledPackageInfo(
                            packageName = "os.kei",
                            appLabel = "KeiOS",
                            versionName = "1.8.0",
                            versionCode = 80,
                        ),
                    supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
                ),
            )

        assertEquals("request", state.requestKey)
        assertEquals("KeiOS Remote", state.title)
        assertEquals(InstallVersionDecision.Update, state.versionDecision)
        assertEquals(InstallAbiDecision.Match, state.abiDecision)
        assertTrue(state.preferredForDevice)
        assertTrue(state.likelyCompatible)
        assertEquals(GitHubDecisionLevel.Good, state.trustSignal.level)
    }

    @Test
    fun `managed install state allows actions artifact archive fallback`() {
        val state =
            deriveGitHubManagedInstallConfirmSheetState(
                GitHubManagedInstallConfirmSheetInput(
                    requestKey = "archive",
                    request =
                        request(
                            assetName = "app-release",
                            contentType = GITHUB_ACTIONS_APK_ARTIFACT_CONTENT_TYPE,
                        ),
                    supportedAbis = listOf("arm64-v8a"),
                ),
            )

        assertTrue(state.canConfirmWithoutManifest)
        assertFalse(state.preferredForDevice)
        assertEquals("KeiOS", state.title)
    }

    private fun request(
        assetName: String,
        contentType: String = "",
    ): GitHubManagedInstallConfirmRequest =
        GitHubManagedInstallConfirmRequest(
            item =
                GitHubTrackedApp(
                    repoUrl = "https://github.com/hosizoraru/KeiOS",
                    owner = "hosizoraru",
                    repo = "KeiOS",
                    packageName = "os.kei",
                    appLabel = "KeiOS",
                ),
            asset =
                GitHubReleaseAssetFile(
                    name = assetName,
                    downloadUrl = "https://example.com/$assetName",
                    sizeBytes = 1024L,
                    downloadCount = 1,
                    contentType = contentType,
                ),
        )
}
