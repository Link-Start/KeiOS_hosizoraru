package os.kei.ui.page.main.os.transfer

import org.json.JSONObject
import org.junit.Test
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.BUILTIN_EXTRA_DIM_CARD_ID
import os.kei.ui.page.main.os.shortcut.BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
import os.kei.ui.page.main.os.shortcut.BUILTIN_LANGUAGE_SETTINGS_CARD_ID
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.builtInActivityShortcutCard
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OsCardTransferServiceTest {
    private companion object {
        const val DeprecatedDefaultAppsCardId = "builtin-settings-default-apps"
        const val DeprecatedAppLanguageCardId = "builtin-settings-app-language"
        const val DeprecatedRunningServicesCardId = "builtin-settings-running-services"
    }

    @Test
    fun `parse root detects activity card export schema`() {
        val root =
            parseOsCardImportRoot(
                """
                {
                  "schema": "$OS_ACTIVITY_CARD_EXPORT_SCHEMA",
                  "itemCount": 1,
                  "items": [
                    {"id":"activity-1","packageName":"com.android.settings","className":"SettingsActivity"}
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(OsCardImportFileKind.Activity, root.fileKind)
        assertEquals(1, root.sourceCount)
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, root.schemaVersion)
        assertFalse(root.isLegacyFormat)
    }

    @Test
    fun `parse root detects shell card export schema`() {
        val root =
            parseOsCardImportRoot(
                """
                {
                  "schema": "$OS_SHELL_CARD_EXPORT_SCHEMA",
                  "itemCount": 1,
                  "items": [
                    {"id":"shell-1","command":"id","runOutput":"uid=0"}
                  ]
                }
                """.trimIndent(),
            )

        assertEquals(OsCardImportFileKind.Shell, root.fileKind)
        assertEquals(1, root.sourceCount)
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, root.schemaVersion)
        assertFalse(root.isLegacyFormat)
    }

    @Test
    fun `activity and bundle exports include schema version`() {
        val activityJson =
            JSONObject(
                OsCardTransferService.buildActivityCardsExportJson(
                    cards = emptyList(),
                    defaults = OsGoogleSystemServiceConfig(),
                ),
            )
        val bundleJson =
            JSONObject(
                OsCardTransferService.buildCardsBundleExportJson(
                    activityCards = emptyList(),
                    shellCards = emptyList(),
                    defaults = OsGoogleSystemServiceConfig(),
                ),
            )

        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, activityJson.optInt("schemaVersion"))
        assertEquals(OS_CARD_EXPORT_SCHEMA_VERSION, bundleJson.optInt("schemaVersion"))
        assertEquals(OS_CARD_BUNDLE_EXPORT_SCHEMA, bundleJson.optString("schema"))
    }

    @Test
    fun `bundle import preview combines activity and shell cards`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val sampleDefaults = defaults.copy(title = "Sample")
        val bundleRaw =
            OsCardTransferService.buildCardsBundleExportJson(
                activityCards =
                    listOf(
                        OsActivityShortcutCard(
                            id = "activity-1",
                            config =
                                defaults.copy(
                                    title = "Settings",
                                    packageName = "com.android.settings",
                                    className = "Settings",
                                ),
                        ),
                    ),
                shellCards =
                    listOf(
                        OsShellCommandCard(
                            id = "shell-1",
                            title = "List global",
                            command = "settings list global",
                        ),
                    ),
                defaults = defaults,
            )

        val preview =
            OsCardTransferService.buildBundleImportPreview(
                raw = bundleRaw,
                activityShortcutCards = emptyList(),
                shellCommandCards = emptyList(),
                defaults = defaults,
                builtInSampleDefaults = sampleDefaults,
            )

        assertTrue(preview.canImport)
        assertEquals(2, preview.fileItemCount)
        assertEquals(2, preview.validCount)
        assertEquals(2, preview.newCount)
        assertEquals(0, preview.updatedCount)
    }

    @Test
    fun `legacy array imports report legacy schema version`() {
        val root =
            parseOsCardImportRoot(
                """
                [
                  {"id":"shell-1","command":"id"}
                ]
                """.trimIndent(),
            )

        assertEquals(OS_CARD_LEGACY_SCHEMA_VERSION, root.schemaVersion)
        assertTrue(root.isLegacyFormat)
    }

    @Test
    fun `activity card preview preserves existing id for same launch target`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val sampleDefaults = defaults.copy(title = "Sample")
        val existing =
            listOf(
                OsActivityShortcutCard(
                    id = "activity-existing",
                    visible = true,
                    config =
                        defaults.copy(
                            title = "Old",
                            subtitle = "old-subtitle",
                            packageName = "com.android.settings",
                            className = "SettingsActivity",
                            intentAction = "android.intent.action.VIEW",
                        ),
                ),
            )
        val payload =
            OsActivityCardImportPayload(
                cards =
                    listOf(
                        OsActivityShortcutCard(
                            id = "activity-imported",
                            visible = false,
                            config =
                                defaults.copy(
                                    title = "Old",
                                    subtitle = "new-subtitle",
                                    packageName = "com.android.settings",
                                    className = "SettingsActivity",
                                    intentAction = "android.intent.action.VIEW",
                                ),
                        ),
                    ),
                sourceCount = 1,
                invalidCount = 0,
                duplicateCount = 0,
                fileKind = OsCardImportFileKind.Activity,
                schemaVersion = OS_CARD_EXPORT_SCHEMA_VERSION,
                isLegacyFormat = false,
            )

        val result =
            OsCardTransferService.previewActivityImport(
                payload = payload,
                existingCards = existing,
                defaults = defaults,
                builtInSampleDefaults = sampleDefaults,
            )

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals("activity-existing", result.cards.single().id)
        assertEquals(
            "new-subtitle",
            result.cards
                .single()
                .config.subtitle,
        )
    }

    @Test
    fun `built in activity migration appends missing cards`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val google =
            builtInActivityShortcutCard(
                id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
                title = "Google Settings",
                subtitle = "Google entry",
                appName = "Google Settings",
                packageName = "com.google.android.gms",
                className = "com.google.android.gms.app.settings.GoogleSettingsLink",
                intentAction = "android.intent.action.VIEW",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )
        val extraDim =
            builtInActivityShortcutCard(
                id = BUILTIN_EXTRA_DIM_CARD_ID,
                title = "Extra dim",
                subtitle = "Reduce bright colors",
                appName = "Android Settings",
                packageName = "com.android.settings",
                className = "com.android.settings.Settings\$ReduceBrightColorsSettingsActivity",
                intentAction = "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )

        val migrated =
            OsActivityShortcutCardStore.migrateBuiltInActivityShortcutCards(
                cards = listOf(google),
                builtInSampleDefaults = google.config,
                builtInActivityShortcutCards = listOf(google, extraDim),
                appendMissingBuiltIns = true,
            )

        assertEquals(
            listOf(BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID, BUILTIN_EXTRA_DIM_CARD_ID),
            migrated.map { it.id },
        )
        assertTrue(migrated.all { it.isBuiltInSample })
    }

    @Test
    fun `built in activity migration removes dropped built ins`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val dropped =
            listOf(
                DeprecatedDefaultAppsCardId,
                DeprecatedAppLanguageCardId,
                DeprecatedRunningServicesCardId,
            ).map { id ->
                builtInActivityShortcutCard(
                    id = id,
                    title = id,
                    subtitle = "Dropped",
                    appName = "Android Settings",
                    packageName = "com.android.settings",
                    className = "com.android.settings.Settings",
                    intentAction = "android.intent.action.MAIN",
                    defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                    defaults = defaults,
                )
            }
        val kept =
            builtInActivityShortcutCard(
                id = BUILTIN_EXTRA_DIM_CARD_ID,
                title = "Extra dim",
                subtitle = "Reduce bright colors",
                appName = "Android Settings",
                packageName = "com.android.settings",
                className = "com.android.settings.Settings\$ReduceBrightColorsSettingsActivity",
                intentAction = "android.settings.REDUCE_BRIGHT_COLORS_SETTINGS",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )

        val migrated =
            OsActivityShortcutCardStore.migrateBuiltInActivityShortcutCards(
                cards = dropped + kept,
                builtInSampleDefaults = kept.config,
                builtInActivityShortcutCards = listOf(kept),
            )

        assertEquals(listOf(BUILTIN_EXTRA_DIM_CARD_ID), migrated.map { it.id })
    }

    @Test
    fun `activity import preserves new built in card marker`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val languageSettings =
            builtInActivityShortcutCard(
                id = BUILTIN_LANGUAGE_SETTINGS_CARD_ID,
                title = "Language settings",
                subtitle = "LanguageSettings",
                appName = "Android Settings",
                packageName = "com.android.settings",
                className = "com.android.settings.LanguageSettings",
                intentAction = "android.settings.LANGUAGE_SETTINGS",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )
        val raw =
            OsCardTransferService.buildActivityCardsExportJson(
                cards = listOf(languageSettings),
                defaults = defaults,
            )

        val payload =
            OsCardTransferService.parseActivityImportPayload(
                raw = raw,
                defaults = defaults,
                builtInSampleDefaults = languageSettings.config,
                builtInActivityShortcutCards = listOf(languageSettings),
            )

        assertTrue(payload.cards.single().isBuiltInSample)
    }

    @Test
    fun `built in activity migration deduplicates upgraded Google settings card`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val google =
            builtInActivityShortcutCard(
                id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
                title = "Google Settings",
                subtitle = "Google entry",
                appName = "Google Settings",
                packageName = "com.google.android.gms",
                className = "com.google.android.gms.app.settings.GoogleSettingsLink",
                intentAction = "android.intent.action.VIEW",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )
        val legacy =
            OsActivityShortcutCard(
                id = "legacy-google-system-service",
                isBuiltInSample = false,
                config =
                    google.config.copy(
                        title = "Google Settings",
                        className = "com.google.android.gms.app.settings.GoogleSettingsActivity",
                    ),
            )

        val migrated =
            OsActivityShortcutCardStore.migrateBuiltInActivityShortcutCards(
                cards = listOf(legacy, google),
                builtInSampleDefaults = google.config,
                builtInActivityShortcutCards = listOf(google),
            )

        assertEquals(listOf(BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID), migrated.map { it.id })
        assertEquals(
            migrated.map { it.id }.distinct(),
            migrated.map { it.id },
        )
    }

    @Test
    fun `built in activity migration upgrades legacy Google settings card`() {
        val defaults = OsGoogleSystemServiceConfig(intentFlags = "FLAG_ACTIVITY_NEW_TASK")
        val google =
            builtInActivityShortcutCard(
                id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
                title = "Google Settings",
                subtitle = "Google entry",
                appName = "Google Settings",
                packageName = "com.google.android.gms",
                className = "com.google.android.gms.app.settings.GoogleSettingsLink",
                intentAction = "android.intent.action.VIEW",
                defaultIntentFlags = "FLAG_ACTIVITY_NEW_TASK",
                defaults = defaults,
            )
        val legacy =
            OsActivityShortcutCard(
                id = "legacy-google-system-service",
                isBuiltInSample = false,
                config =
                    google.config.copy(
                        title = "Google Settings",
                        className = "com.google.android.gms.app.settings.GoogleSettingsActivity",
                    ),
            )

        val migrated =
            OsActivityShortcutCardStore.migrateBuiltInActivityShortcutCards(
                cards = listOf(legacy),
                builtInSampleDefaults = google.config,
                builtInActivityShortcutCards = listOf(google),
            )

        assertEquals(BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID, migrated.single().id)
        assertEquals(
            "com.google.android.gms.app.settings.GoogleSettingsLink",
            migrated.single().config.className,
        )
        assertTrue(migrated.single().isBuiltInSample)
    }

    @Test
    fun `shell card preview merges by normalized command`() {
        val existing =
            listOf(
                OsShellCommandCard(
                    id = "shell-existing",
                    visible = true,
                    title = "Old",
                    command = "settings list global",
                ),
            )
        val payload =
            OsShellCardImportPayload(
                cards =
                    listOf(
                        OsShellCommandCard(
                            id = "shell-imported",
                            visible = false,
                            title = "New",
                            command = "settings   list   global",
                            runOutput = "ok",
                            updatedAtMillis = 2L,
                        ),
                    ),
                sourceCount = 1,
                invalidCount = 0,
                duplicateCount = 0,
                fileKind = OsCardImportFileKind.Shell,
                schemaVersion = OS_CARD_EXPORT_SCHEMA_VERSION,
                isLegacyFormat = false,
            )

        val result =
            OsCardTransferService.previewShellImport(
                payload = payload,
                existingCards = existing,
            )

        assertEquals(0, result.addedCount)
        assertEquals(1, result.updatedCount)
        assertEquals("shell-existing", result.cards.single().id)
        assertEquals("New", result.cards.single().title)
    }

    @Test
    fun `import summary keeps mcp key value shape`() {
        val payload =
            OsUnknownCardImportPayload(
                sourceCount = 2,
                invalidCount = 2,
                duplicateCount = 0,
                fileKind = OsCardImportFileKind.Unknown,
                isLegacyFormat = true,
            )

        val summary =
            OsCardTransferService.buildImportSummaryText(
                target = "shell",
                payload = payload,
                addedCount = 0,
                updatedCount = 0,
                unchangedCount = 0,
                mergedCount = 0,
                apply = false,
                applied = false,
            )

        assertTrue(summary.lines().contains("target=shell"))
        assertTrue(summary.lines().contains("fileKind=Unknown"))
        assertTrue(summary.lines().contains("schemaVersion=0"))
        assertTrue(summary.lines().contains("validCount=0"))
        assertTrue(summary.lines().contains("applied=false"))
    }
}
