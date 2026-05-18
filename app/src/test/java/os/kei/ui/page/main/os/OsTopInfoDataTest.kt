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
        val snapshot =
            buildTopInfoRowsSnapshot(
                mapOf(
                    SectionKind.SYSTEM to SectionState(loadedFresh = true),
                ),
            )

        assertEquals(TopInfoRowsStatus.EmptyFresh, snapshot.status)
        assertFalse(snapshot.hasRows)
        assertTrue(snapshot.loadedFresh)
    }

    @Test
    fun `top info snapshot distinguishes cached rows from fresh rows`() {
        val cached =
            buildTopInfoRowsSnapshot(
                mapOf(
                    SectionKind.SYSTEM to
                        SectionState(
                            rows = listOf(InfoRow("locked_apps", "1")),
                            loadedFresh = false,
                        ),
                ),
            )
        val fresh =
            buildTopInfoRowsSnapshot(
                mapOf(
                    SectionKind.SYSTEM to
                        SectionState(
                            rows = listOf(InfoRow("locked_apps", "1")),
                            loadedFresh = true,
                        ),
                ),
            )

        assertEquals(TopInfoRowsStatus.Cached, cached.status)
        assertEquals(TopInfoRowsStatus.Fresh, fresh.status)
        assertEquals(listOf(InfoRow("locked_apps", "1")), fresh.rows)
    }

    @Test
    fun `top info snapshot keeps cached rows visible while refreshing`() {
        val snapshot =
            buildTopInfoRowsSnapshot(
                mapOf(
                    SectionKind.SYSTEM to
                        SectionState(
                            rows = listOf(InfoRow("locked_apps", "1")),
                            loading = true,
                            loadedFresh = false,
                        ),
                ),
            )

        assertEquals(TopInfoRowsStatus.Refreshing, snapshot.status)
        assertTrue(snapshot.hasRows)
        assertFalse(snapshot.loadedFresh)
    }

    @Test
    fun `top info keeps low level rows and leaves noisy android rows in source section`() {
        val androidRows =
            listOf(
                InfoRow("runtime.abi.primary", "arm64-v8a"),
                InfoRow("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a"),
                InfoRow("persist.sys.zygote.last_pid", "1234"),
            )

        val snapshot =
            buildTopInfoRowsSnapshot(
                mapOf(
                    SectionKind.ANDROID to
                        SectionState(
                            rows = androidRows,
                            loadedFresh = true,
                        ),
                ),
            )

        assertEquals(TopInfoRowsStatus.Fresh, snapshot.status)
        assertTrue(InfoRow("runtime.abi.primary", "arm64-v8a") in snapshot.rows)
        assertFalse(InfoRow("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a") in snapshot.rows)
        assertFalse(snapshot.rows.any { it.key == "persist.sys.zygote.last_pid" })
        assertEquals(
            listOf(
                InfoRow("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a"),
                InfoRow("persist.sys.zygote.last_pid", "1234"),
            ),
            removeTopInfoRows(SectionKind.ANDROID, androidRows),
        )
    }
}
