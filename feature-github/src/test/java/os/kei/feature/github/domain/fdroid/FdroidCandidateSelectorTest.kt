package os.kei.feature.github.domain.fdroid

import org.junit.Test
import os.kei.feature.github.data.remote.fdroid.FdroidPackageSnapshot
import os.kei.feature.github.data.remote.fdroid.FdroidVersionSnapshot
import os.kei.feature.github.model.FdroidTrackedAppConfig
import os.kei.feature.github.model.FdroidVersionSelectionMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FdroidCandidateSelectorTest {
    @Test
    fun `suggested mode uses suggested version when compatible`() {
        val snapshot = packageSnapshot(
            suggestedVersionCode = 200,
            versions = listOf(
                version(versionCode = 300, versionName = "3.0.0"),
                version(versionCode = 200, versionName = "2.0.0"),
                version(versionCode = 100, versionName = "1.0.0")
            )
        )

        val selected = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(),
            deviceSdk = 37
        )

        assertEquals(200L, selected?.versionCode)
        assertEquals("2.0.0", selected?.versionName)
    }

    @Test
    fun `suggested mode falls back to highest compatible version`() {
        val snapshot = packageSnapshot(
            suggestedVersionCode = 300,
            versions = listOf(
                version(versionCode = 300, minSdk = 38),
                version(versionCode = 200, minSdk = 35),
                version(versionCode = 100, minSdk = 21)
            )
        )

        val selected = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(),
            deviceSdk = 37
        )

        assertEquals(200L, selected?.versionCode)
    }

    @Test
    fun `version name regex mode filters versions before selecting highest compatible`() {
        val snapshot = packageSnapshot(
            versions = listOf(
                version(versionCode = 400, versionName = "4.0.0-beta"),
                version(versionCode = 300, versionName = "3.0.0"),
                version(versionCode = 200, versionName = "2.0.0")
            )
        )

        val selected = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(
                selectionMode = FdroidVersionSelectionMode.VersionNameRegex,
                versionNameRegex = """^\d+\.\d+\.\d+$"""
            ),
            deviceSdk = 37
        )

        assertEquals(300L, selected?.versionCode)
    }

    @Test
    fun `apk regex filters candidate package files`() {
        val snapshot = packageSnapshot(
            versions = listOf(
                version(versionCode = 300, apkName = "demo-arm64-v8a.apk"),
                version(versionCode = 200, apkName = "demo-universal.apk")
            )
        )

        val selected = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(apkNameRegex = "universal"),
            deviceSdk = 37
        )

        assertEquals(200L, selected?.versionCode)
    }

    @Test
    fun `selector returns null when regex is invalid or no compatible version exists`() {
        val snapshot = packageSnapshot(
            versions = listOf(
                version(versionCode = 300, minSdk = 39),
                version(versionCode = 200, minSdk = 38)
            )
        )

        val invalidRegexSelection = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(
                selectionMode = FdroidVersionSelectionMode.VersionNameRegex,
                versionNameRegex = "["
            ),
            deviceSdk = 37
        )
        val incompatibleSelection = FdroidCandidateSelector.select(
            snapshot = snapshot,
            config = FdroidTrackedAppConfig(),
            deviceSdk = 37
        )

        assertNull(invalidRegexSelection)
        assertNull(incompatibleSelection)
    }

    private fun packageSnapshot(
        suggestedVersionCode: Long? = null,
        versions: List<FdroidVersionSnapshot>
    ): FdroidPackageSnapshot {
        return FdroidPackageSnapshot(
            repoUrl = "https://f-droid.org/repo",
            packageName = "org.fdroid.fdroid",
            suggestedVersionCode = suggestedVersionCode,
            versions = versions
        )
    }

    private fun version(
        versionCode: Long,
        versionName: String = versionCode.toString(),
        apkName: String = "demo-$versionCode.apk",
        minSdk: Int = 21
    ) = fdroidVersionFixture(versionCode, versionName, apkName = apkName, minSdk = minSdk)
}
