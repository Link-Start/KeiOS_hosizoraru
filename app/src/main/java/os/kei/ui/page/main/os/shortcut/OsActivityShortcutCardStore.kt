package os.kei.ui.page.main.os.shortcut

import androidx.compose.runtime.Immutable
import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsShortcutCardStore
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportError
import os.kei.ui.page.main.os.transfer.OsCardImportException
import os.kei.ui.page.main.os.transfer.OsCardImportRoot
import os.kei.ui.page.main.os.transfer.parseOsCardImportRoot

@Immutable
internal data class OsActivityCardImportMergeResult(
    val cards: List<OsActivityShortcutCard>,
    val addedCount: Int,
    val updatedCount: Int,
    val unchangedCount: Int,
)

internal object OsActivityShortcutCardStore {
    private val store: MMKV by lazy { KeiMmkv.byId(OS_ACTIVITY_SHORTCUT_CARD_KV_ID) }
    private val legacyStore: MMKV by lazy { KeiMmkv.byId(OS_ACTIVITY_SHORTCUT_CARD_LEGACY_KV_ID) }

    fun loadCards(
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
    ): List<OsActivityShortcutCard> {
        val persistedRaw = store.decodeString(OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS).orEmpty().trim()
        if (persistedRaw.isNotBlank()) {
            decodeAndMigratePersistedCards(
                raw = persistedRaw,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                saveMigrated = true,
                forceSave = false,
            )?.let { return it }
        }

        val legacyRaw = legacyStore.decodeString(OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS).orEmpty().trim()
        if (legacyRaw.isNotBlank()) {
            decodeAndMigratePersistedCards(
                raw = legacyRaw,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                saveMigrated = true,
                forceSave = true,
            )?.let { return it }
        }

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
        val migrated =
            OsActivityShortcutCardMigration.migrateBuiltInSampleCards(
                cards = listOf(initialCard),
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                appendMissingBuiltIns = true,
            )
        saveCards(cards = migrated, defaults = defaults)
        return migrated
    }

    fun saveCards(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
    ) {
        val normalized =
            cards.map { card ->
                card.copy(config = normalizeActivityShortcutConfig(card.config, defaults))
            }
        store.encode(OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS, OsActivityShortcutCardCodec.encodeCards(normalized))
        legacyStore.removeValueForKey(OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS)
        normalized.firstOrNull()?.let { first ->
            OsShortcutCardStore.saveGoogleSystemServiceConfig(first.config, defaults)
        }
    }

    fun buildCardsExportJson(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        exportedAtMillis: Long = System.currentTimeMillis(),
    ): String =
        OsActivityShortcutCardImportExport.buildCardsExportJson(
            cards = cards,
            defaults = defaults,
            exportedAtMillis = exportedAtMillis,
        )

    fun parseCardsImport(
        root: OsCardImportRoot,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
    ): OsActivityCardImportPayload =
        OsActivityShortcutCardImportExport.parseCardsImport(
            root = root,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )

    fun previewImportedCards(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
    ): OsActivityCardImportMergeResult =
        OsActivityShortcutCardImportExport.mergeImportedCards(
            existingCards = existingCards,
            importedCards = payload.cards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )

    fun applyImportedCards(
        payload: OsActivityCardImportPayload,
        existingCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
    ): OsActivityCardImportMergeResult {
        val result =
            previewImportedCards(
                payload = payload,
                existingCards = existingCards,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        saveCards(cards = result.cards, defaults = defaults)
        return result
    }

    fun importCardsFromJsonMerged(
        raw: String,
        defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig(),
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
    ): OsActivityCardImportMergeResult {
        val payload =
            parseCardsImport(
                root = parseOsCardImportRoot(raw),
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        if (payload.cards.isEmpty()) {
            throw OsCardImportException(OsCardImportError.NoValidActivityCards)
        }
        return applyImportedCards(
            payload = payload,
            existingCards =
                loadCards(
                    defaults = defaults,
                    builtInSampleDefaults = builtInSampleDefaults,
                    builtInActivityShortcutCards = builtInActivityShortcutCards,
                ),
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )
    }

    internal fun migrateBuiltInActivityShortcutCards(
        cards: List<OsActivityShortcutCard>,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            googleSettingsBuiltInCard(
                builtInSampleDefaults,
            ),
        appendMissingBuiltIns: Boolean = false,
    ): List<OsActivityShortcutCard> =
        OsActivityShortcutCardMigration.migrateBuiltInActivityShortcutCards(
            cards = cards,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
            appendMissingBuiltIns = appendMissingBuiltIns,
        )

    private fun decodeAndMigratePersistedCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        saveMigrated: Boolean,
        forceSave: Boolean,
    ): List<OsActivityShortcutCard>? {
        val decoded = OsActivityShortcutCardCodec.decodeCards(raw = raw, defaults = defaults)
        if (decoded.isEmpty()) return null
        val migrated =
            OsActivityShortcutCardMigration.migrateBuiltInActivityShortcutCards(
                cards = decoded,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                appendMissingBuiltIns = true,
            )
        if (saveMigrated && (forceSave || migrated != decoded)) {
            saveCards(cards = migrated, defaults = defaults)
        }
        return migrated
    }
}
