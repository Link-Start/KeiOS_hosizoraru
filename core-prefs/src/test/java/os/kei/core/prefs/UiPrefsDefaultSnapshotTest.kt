package os.kei.core.prefs

import os.kei.core.log.AppLogLevel
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UiPrefsDefaultSnapshotTest {
    @Test
    fun `home hdr highlight starts disabled`() {
        assertFalse(UiPrefs.defaultSnapshot().homeIconHdrEnabled)
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
