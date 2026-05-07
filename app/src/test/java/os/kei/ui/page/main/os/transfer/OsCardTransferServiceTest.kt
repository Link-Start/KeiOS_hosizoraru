package os.kei.ui.page.main.os.transfer

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsCardTransferServiceTest {
    @Test
    fun `parse root detects activity card export schema`() {
        val root = parseOsCardImportRoot(
            """
            {
              "schema": "$OS_ACTIVITY_CARD_EXPORT_SCHEMA",
              "itemCount": 1,
              "items": [
                {"id":"activity-1","packageName":"com.android.settings","className":"SettingsActivity"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(OsCardImportFileKind.Activity, root.fileKind)
        assertEquals(1, root.sourceCount)
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, root.schemaVersion)
        assertFalse(root.isLegacyFormat)
    }

    @Test
    fun `parse root detects shell card export schema`() {
        val root = parseOsCardImportRoot(
            """
            {
              "schema": "$OS_SHELL_CARD_EXPORT_SCHEMA",
              "itemCount": 1,
              "items": [
                {"id":"shell-1","command":"id","runOutput":"uid=0"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(OsCardImportFileKind.Shell, root.fileKind)
        assertEquals(1, root.sourceCount)
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, root.schemaVersion)
        assertFalse(root.isLegacyFormat)
    }

    @Test
    fun `activity and bundle exports include schema version`() {
        val activityJson = JSONObject(
            OsCardTransferService.buildActivityCardsExportJson(
                cards = emptyList(),
                defaults = OsGoogleSystemServiceConfig(),
            )
        )
        val bundleJson = JSONObject(
            OsCardTransferService.buildCardsBundleExportJson(
                activityCards = emptyList(),
                shellCards = emptyList(),
                defaults = OsGoogleSystemServiceConfig(),
            )
        )

        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, activityJson.optInt("schemaVersion"))
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, bundleJson.optInt("schemaVersion"))
        assertEquals(OS_CARD_BUNDLE_EXPORT_SCHEMA, bundleJson.optString("schema"))
    }

    @Test
    fun `legacy array imports report legacy schema version`() {
        val root = parseOsCardImportRoot(
            """
            [
              {"id":"shell-1","command":"id"}
            ]
            """.trimIndent()
        )

        assertEquals(OS_CARD_LEGACY_SCHEMA_VERSION, root.schemaVersion)
        assertTrue(root.isLegacyFormat)
    }

    @Test
    fun `activity card preview preserves existing id for same launch target`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val sampleDefaults = defaults.copy(title = "Sample")
        val existing = listOf(
            OsActivityShortcutCard(
                id = "activity-existing",
                visible = true,
                config = defaults.copy(
                    title = "Old",
                    subtitle = "old-subtitle",
                    packageName = "com.android.settings",
                    className = "SettingsActivity",
                    intentAction = "android.intent.action.VIEW"
                )
            )
        )
        val payload = OsActivityCardImportPayload(
            cards = listOf(
                OsActivityShortcutCard(
                    id = "activity-imported",
                    visible = false,
                    config = defaults.copy(
                        title = "Old",
                        subtitle = "new-subtitle",
                        packageName = "com.android.settings",
                        className = "SettingsActivity",
                        intentAction = "android.intent.action.VIEW"
                    )
                )
            ),
            sourceCount = 1,
            invalidCount = 0,
            duplicateCount = 0,
            fileKind = OsCardImportFileKind.Activity,
            schemaVersion = OS_CARD_EXPORT_SCHEMA_VERSION,
            isLegacyFormat = false
        )

        val result = OsCardTransferService.previewActivityImport(
            payload = payload,
            existingCards = existing,
            defaults = defaults,
            builtInSampleDefaults = sampleDefaults
        )

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals("activity-existing", result.cards.single().id)
        assertEquals("new-subtitle", result.cards.single().config.subtitle)
    }

    @Test
    fun `shell card preview merges by normalized command`() {
        val existing = listOf(
            OsShellCommandCard(
                id = "shell-existing",
                visible = true,
                title = "Old",
                command = "settings list global"
            )
        )
        val payload = OsShellCardImportPayload(
            cards = listOf(
                OsShellCommandCard(
                    id = "shell-imported",
                    visible = false,
                    title = "New",
                    command = "settings   list   global",
                    runOutput = "ok",
                    updatedAtMillis = 2L
                )
            ),
            sourceCount = 1,
            invalidCount = 0,
            duplicateCount = 0,
            fileKind = OsCardImportFileKind.Shell,
            schemaVersion = OS_CARD_EXPORT_SCHEMA_VERSION,
            isLegacyFormat = false
        )

        val result = OsCardTransferService.previewShellImport(
            payload = payload,
            existingCards = existing
        )

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals("shell-existing", result.cards.single().id)
        assertEquals("New", result.cards.single().title)
    }

    @Test
    fun `import summary keeps mcp key value shape`() {
        val payload = OsUnknownCardImportPayload(
            sourceCount = 2,
            invalidCount = 2,
            duplicateCount = 0,
            fileKind = OsCardImportFileKind.Unknown,
            isLegacyFormat = true
        )

        val summary = OsCardTransferService.buildImportSummaryText(
            target = "shell",
            payload = payload,
            addedCount = 0,
            updatedCount = 0,
            unchangedCount = 0,
            mergedCount = 0,
            apply = false,
            applied = false
        )

        assertTrue(summary.lines().contains("target=shell"))
        assertTrue(summary.lines().contains("fileKind=Unknown"))
        assertTrue(summary.lines().contains("schemaVersion=0"))
        assertTrue(summary.lines().contains("validCount=0"))
        assertTrue(summary.lines().contains("applied=false"))
    }
}
