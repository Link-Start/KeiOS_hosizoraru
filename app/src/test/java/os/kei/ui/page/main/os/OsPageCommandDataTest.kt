package os.kei.ui.page.main.os

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OsPageCommandDataTest {
    @Test
    fun `settings snapshot parser assigns rows to tagged sections`() {
        val snapshot = parseSettingsSectionSnapshot(
            """
            __keios_settings_section=system
            screen_brightness=80
            __keios_settings_section=secure
            lock_screen_locking_enabled=1
            malformed
            __keios_settings_section=global
            adb_enabled=0
            """.trimIndent()
        )

        assertEquals(
            listOf(InfoRow("screen_brightness", "80")),
            snapshot.rowsFor(SectionKind.SYSTEM)
        )
        assertEquals(
            listOf(InfoRow("lock_screen_locking_enabled", "1")),
            snapshot.rowsFor(SectionKind.SECURE)
        )
        assertEquals(
            listOf(InfoRow("adb_enabled", "0")),
            snapshot.rowsFor(SectionKind.GLOBAL)
        )
        assertNull(snapshot.rowsFor(SectionKind.ANDROID))
    }

    @Test
    fun `settings snapshot without rows is treated as empty`() {
        val snapshot = parseSettingsSectionSnapshot(
            """
            __keios_settings_section=system
            __keios_settings_section=secure
            __keios_settings_section=global
            """.trimIndent()
        )

        assertFalse(snapshot.hasRows)
        assertNull(snapshot.rowsFor(SectionKind.SYSTEM))
    }
}
