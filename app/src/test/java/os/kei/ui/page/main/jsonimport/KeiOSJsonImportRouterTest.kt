package os.kei.ui.page.main.jsonimport

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeiOSJsonImportRouterTest {
    @Test
    fun `detects github tracked v2`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            {
              "format": "keios.github.tracked/v2",
              "schemaVersion": 2,
              "items": [
                {"repoUrl":"https://github.com/hosizoraru/KeiOS","owner":"hosizoraru","repo":"KeiOS"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.GitHubTracked, header.kind)
        assertEquals(2, header.version)
        assertFalse(header.highVersion)
    }

    @Test
    fun `detects newer github tracked as compatible high version`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            {
              "format": "keios.github.tracked/v3",
              "schemaVersion": 3,
              "items": []
            }
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.GitHubTracked, header.kind)
        assertTrue(header.highVersion)
    }

    @Test
    fun `detects os cards bundle`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            {
              "schema": "keios.os.cards.bundle.v1",
              "schemaVersion": 1,
              "activity": {"items":[]},
              "shell": {"items":[]}
            }
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.OsCardsBundle, header.kind)
        assertEquals(1, header.version)
    }

    @Test
    fun `detects ba all favorites`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            {
              "type": "keios.ba.catalog_all_favorites",
              "version": 1,
              "catalogFavorites": [{"contentId": 1}],
              "bgmFavorites": [{"audioUrl": "https://example.com/a.mp3"}]
            }
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.BaAllFavorites, header.kind)
        assertEquals(1, header.version)
    }

    @Test
    fun `detects read only mcp logs`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            {
              "schema": "keios.mcp.logs.v1",
              "logs": []
            }
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.McpLogs, header.kind)
        assertTrue(header.readOnly)
    }

    @Test
    fun `legacy shell array remains importable`() {
        val header = KeiOSJsonImportRouter.inspect(
            """
            [
              {"id": "shell-1", "command": "settings list global"}
            ]
            """.trimIndent()
        )

        assertEquals(KeiOSJsonImportKind.OsShellCards, header.kind)
        assertTrue(header.legacyFormat)
    }
}
