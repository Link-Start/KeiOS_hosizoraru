package os.kei.ui.page.main.os

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsTopInfoDataTest {
    @Test
    fun `top info snapshot reports not loaded when every source is empty and stale`() {
        val snapshot = buildTopInfoRowsSnapshot(emptyMap())

        assertEquals(TopInfoRowsStatus.NotLoaded, snapshot.status)
        assertFalse(snapshot.hasRows)
        assertFalse(snapshot.loadedFresh)
    }

    @Test
    fun `top info snapshot reports empty fresh after fresh empty source load`() {
        val snapshot = buildTopInfoRowsSnapshot(
            mapOf(
                SectionKind.SYSTEM to SectionState(loadedFresh = true)
            )
        )

        assertEquals(TopInfoRowsStatus.EmptyFresh, snapshot.status)
        assertFalse(snapshot.hasRows)
        assertTrue(snapshot.loadedFresh)
    }

    @Test
    fun `top info snapshot distinguishes cached rows from fresh rows`() {
        val cached = buildTopInfoRowsSnapshot(
            mapOf(
                SectionKind.SYSTEM to SectionState(
                    rows = listOf(InfoRow("locked_apps", "1")),
                    loadedFresh = false
                )
            )
        )
        val fresh = buildTopInfoRowsSnapshot(
            mapOf(
                SectionKind.SYSTEM to SectionState(
                    rows = listOf(InfoRow("locked_apps", "1")),
                    loadedFresh = true
                )
            )
        )

        assertEquals(TopInfoRowsStatus.Cached, cached.status)
        assertEquals(TopInfoRowsStatus.Fresh, fresh.status)
        assertEquals(listOf(InfoRow("locked_apps", "1")), fresh.rows)
    }

    @Test
    fun `top info snapshot keeps cached rows visible while refreshing`() {
        val snapshot = buildTopInfoRowsSnapshot(
            mapOf(
                SectionKind.SYSTEM to SectionState(
                    rows = listOf(InfoRow("locked_apps", "1")),
                    loading = true,
                    loadedFresh = false
                )
            )
        )

        assertEquals(TopInfoRowsStatus.Refreshing, snapshot.status)
        assertTrue(snapshot.hasRows)
        assertFalse(snapshot.loadedFresh)
    }
}
