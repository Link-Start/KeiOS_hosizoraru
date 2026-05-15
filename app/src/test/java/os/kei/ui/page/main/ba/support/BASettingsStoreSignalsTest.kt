package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals

class BASettingsStoreSignalsTest {
    @Test
    fun `runtime changes do not wake home overview signal`() {
        val homeBefore = BASettingsStoreSignals.homeOverviewVersion.value

        BASettingsStoreSignals.notifyChanged(
            atMillis = homeBefore + 1_000L,
            notifyHomeOverview = false
        )

        assertEquals(homeBefore + 1_000L, BASettingsStoreSignals.version.value)
        assertEquals(homeBefore, BASettingsStoreSignals.homeOverviewVersion.value)
    }

    @Test
    fun `structural changes wake both ba and home overview signals`() {
        val targetVersion = BASettingsStoreSignals.version.value + 1_000L

        BASettingsStoreSignals.notifyChanged(
            atMillis = targetVersion,
            notifyHomeOverview = true
        )

        assertEquals(targetVersion, BASettingsStoreSignals.version.value)
        assertEquals(targetVersion, BASettingsStoreSignals.homeOverviewVersion.value)
    }
}
