package os.kei.core.prefs

import os.kei.core.log.AppLogLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiPrefsDefaultSnapshotTest {
    @Test
    fun `non home background customization keeps legacy default rendering`() {
        val snapshot = UiPrefs.defaultSnapshot()

        assertEquals(NonHomeBackgroundContentScale.Crop, snapshot.nonHomeBackgroundContentScale)
        assertEquals(NonHomeBackgroundAlignment.Center, snapshot.nonHomeBackgroundAlignment)
        assertEquals(NonHomeBackgroundPageStyle.Standard, snapshot.nonHomeBackgroundPageStyle)
        assertEquals(0f, snapshot.nonHomeBackgroundScrim)
        assertFalse(snapshot.nonHomeBackgroundDepthEnabled)
        assertEquals(1f, snapshot.nonHomeBackgroundSaturation)
    }

    @Test
    fun `runtime defaults feed default snapshot log level`() {
        UiPrefs.configureRuntimeDefaults(
            buildType = "debug",
            defaultLogLevelId = AppLogLevel.Debug.storageId,
        )

        assertEquals(AppLogLevel.Debug, UiPrefs.defaultSnapshot().logLevel)

        UiPrefs.configureRuntimeDefaults(
            buildType = "release",
            defaultLogLevelId = AppLogLevel.Off.storageId,
        )
    }
}
