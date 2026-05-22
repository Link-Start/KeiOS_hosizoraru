package os.kei.ui.page.main.os.shortcut

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsShortcutCardStore

internal data class OsActivityShortcutPersistedCards(
    val decoded: List<OsActivityShortcutCard>,
    val migrated: List<OsActivityShortcutCard>,
)

internal object OsActivityShortcutCardLoadSupport {
    fun loadFromPersistedRaw(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): List<OsActivityShortcutCard>? =
        loadPersistedCards(
            raw = raw,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )?.migrated

    fun loadPersistedCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): OsActivityShortcutPersistedCards? {
        val decoded = OsActivityShortcutCardCodec.decodeCards(raw = raw, defaults = defaults)
        if (decoded.isEmpty()) return null
        return OsActivityShortcutPersistedCards(
            decoded = decoded,
            migrated =
                OsActivityShortcutCardMigration.migrateBuiltInActivityShortcutCards(
                    cards = decoded,
                    builtInSampleDefaults = builtInSampleDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                    appendMissingBuiltIns = true,
                ),
        )
    }

    fun initialMigratedCards(
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): List<OsActivityShortcutCard> {
        val legacy =
            normalizeActivityShortcutConfig(
                OsShortcutCardStore.loadGoogleSystemServiceConfig(defaults),
                defaults,
            )
        val defaultLegacy = normalizeActivityShortcutConfig(defaults, defaults)
        val initialCard =
            if (legacy != defaultLegacy) {
                OsActivityShortcutCard(
                    id = LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID,
                    visible = true,
                    isBuiltInSample = false,
                    config = legacy,
                )
            } else {
                OsActivityShortcutCard(
                    id = BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID,
                    visible = true,
                    isBuiltInSample = true,
                    config = normalizeActivityShortcutConfig(builtInSampleDefaults, defaults),
                )
            }
        return OsActivityShortcutCardMigration.migrateBuiltInSampleCards(
            cards = listOf(initialCard),
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
            appendMissingBuiltIns = true,
        )
    }

    fun decodeAndMigratePersistedCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): List<OsActivityShortcutCard>? =
        loadPersistedCards(
            raw = raw,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )?.migrated
}
