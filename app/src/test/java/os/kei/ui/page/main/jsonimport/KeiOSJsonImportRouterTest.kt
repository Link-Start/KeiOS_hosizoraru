package os.kei.ui.page.main.jsonimport

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeiOSJsonImportRouterTest {
    @Test
    fun `detects every github tracked schema and marks only a future one as high version`() {
        val v2Item = """{"repoUrl":"https://github.com/hosizoraru/KeiOS","owner":"hosizoraru","repo":"KeiOS"}"""
        listOf(
            Triple("github tracked v2", 2 to v2Item, false),
            Triple("legacy github tracked v3 after schema bump", 3 to "", false),
            Triple("current github tracked v4", 4 to "", false),
            Triple("future github tracked v5", 5 to "", true),
        ).forEach { (name, schema, highVersion) ->
            val (version, items) = schema
            val header = KeiOSJsonImportRouter.inspect(
                """
                {
                  "format": "keios.github.tracked/v$version",
                  "schemaVersion": $version,
                  "items": [$items]
                }
                """.trimIndent()
            )

            assertEquals(KeiOSJsonImportKind.GitHubTracked, header.kind, name)
            assertEquals(version, header.version, name)
            assertEquals(highVersion, header.highVersion, name)
        }
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
