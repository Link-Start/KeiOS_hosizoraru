package os.kei.ui.page.main.student.catalog.page

import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals

class BaGuideCatalogFavoriteTransferTest {
    @Test
    fun `legacy favorite array uses one fallback timestamp`() {
        val favorites =
            parseCatalogFavoritesExport(
                raw = """[1001, "1002", {"contentId": 1003}]""",
                fallbackFavoritedAtMs = 123_456L,
            )

        assertEquals(
            mapOf(
                1001L to 123_456L,
                1002L to 123_456L,
                1003L to 123_456L,
            ),
            favorites,
        )
    }

    @Test
    fun `export uses provided timestamp`() {
        val root =
            JSONObject(
                buildCatalogFavoritesExportJson(
                    favorites = mapOf(1001L to 1L),
                    nowMs = 99_999L,
                ),
            )

        assertEquals(99_999L, root.optLong("exportedAtMs"))
    }
}
