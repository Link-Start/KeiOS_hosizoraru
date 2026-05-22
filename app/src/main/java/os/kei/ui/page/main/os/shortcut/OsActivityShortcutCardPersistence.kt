package os.kei.ui.page.main.os.shortcut

import com.tencent.mmkv.MMKV
import os.kei.core.prefs.KeiMmkv
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsShortcutCardStore

internal class OsActivityShortcutCardPersistence {
    private val store: MMKV by lazy { KeiMmkv.byId(OS_ACTIVITY_SHORTCUT_CARD_KV_ID) }
    private val legacyStore: MMKV by lazy { KeiMmkv.byId(OS_ACTIVITY_SHORTCUT_CARD_LEGACY_KV_ID) }

    fun loadCards(
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
    ): List<OsActivityShortcutCard> {
        val persistedRaw = store.decodeString(OS_ACTIVITY_SHORTCUT_CARD_KEY_CARDS).orEmpty().trim()
        if (persistedRaw.isNotBlank()) {
            decodeAndPersistMigratedCards(
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
            decodeAndPersistMigratedCards(
                raw = legacyRaw,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                saveMigrated = true,
                forceSave = true,
            )?.let { return it }
        }

        val migrated =
            OsActivityShortcutCardLoadSupport.initialMigratedCards(
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            )
        saveCards(cards = migrated, defaults = defaults)
        return migrated
    }

    fun saveCards(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
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

    private fun decodeAndPersistMigratedCards(
        raw: String,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard>,
        saveMigrated: Boolean,
        forceSave: Boolean,
    ): List<OsActivityShortcutCard>? {
        val persisted =
            OsActivityShortcutCardLoadSupport.loadPersistedCards(
                raw = raw,
                defaults = defaults,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
            ) ?: return null
        val migrated = persisted.migrated
        if (saveMigrated && (forceSave || migrated != persisted.decoded)) {
            saveCards(cards = migrated, defaults = defaults)
        }
        return migrated
    }
}
