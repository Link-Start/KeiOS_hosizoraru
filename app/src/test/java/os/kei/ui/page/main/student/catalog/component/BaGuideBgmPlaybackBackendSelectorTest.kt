package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import kotlin.test.assertEquals

class BaGuideBgmPlaybackBackendSelectorTest {
    @Test
    fun `backend mode follows native media notification switch`() {
        assertEquals(
            BaGuideBgmPlaybackBackendMode.Lightweight,
            resolveBaGuideBgmPlaybackBackendMode(nativeMediaNotificationEnabled = false)
        )
        assertEquals(
            BaGuideBgmPlaybackBackendMode.NativeMedia,
            resolveBaGuideBgmPlaybackBackendMode(nativeMediaNotificationEnabled = true)
        )
    }
}
