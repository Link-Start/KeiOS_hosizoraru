package os.kei.feature.github.data.remote

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitHubTrackedRepoDeviceSnapshotTest {
    @Test
    fun `device snapshot resource is available for regression tests`() {
        val items = GitHubTrackedRepoDeviceSnapshot.items

        assertEquals(24, items.size)
        assertTrue(items.none { it.id == "xororz/local-dream" })
        assertTrue(items.any { it.id == "monogram-android/monogram" })
        assertTrue(items.any { it.id == "CeuiLiSA/Pixiv-Shaft" })
        assertTrue(items.any { it.id == "jay3-yy/BiliPai" })
    }
}
