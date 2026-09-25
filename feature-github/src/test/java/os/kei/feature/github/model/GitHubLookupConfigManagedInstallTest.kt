package os.kei.feature.github.model

import os.kei.core.download.segmented.SegmentedDownloadSpeedProfile
import os.kei.feature.github.install.managedInstallDownloadSpeedProfile
import org.junit.Test
import kotlin.test.assertEquals

class GitHubLookupConfigManagedInstallTest {
    @Test
    fun `boost flag selects managed download profile`() {
        val cases = listOf(
            false to SegmentedDownloadSpeedProfile.Balanced,
            true to SegmentedDownloadSpeedProfile.ForegroundBoost,
        )

        cases.forEach { (boostEnabled, expected) ->
            val config = GitHubLookupConfig().copy(foregroundManagedDownloadBoostEnabled = boostEnabled)
            assertEquals(
                expected,
                config.managedInstallDownloadSpeedProfile(),
                "foregroundManagedDownloadBoostEnabled=$boostEnabled",
            )
        }
    }
}
