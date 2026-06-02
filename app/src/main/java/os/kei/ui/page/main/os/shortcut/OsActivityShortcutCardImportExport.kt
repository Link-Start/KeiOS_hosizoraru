package os.kei.ui.page.main.os.shortcut

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.KeiJson
import os.kei.core.json.encodeCompact
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.transfer.OS_ACTIVITY_CARD_EXPORT_SCHEMA
import os.kei.ui.page.main.os.transfer.OS_CARD_EXPORT_SCHEMA_VERSION
import os.kei.ui.page.main.os.transfer.OsActivityCardImportPayload
import os.kei.ui.page.main.os.transfer.OsCardImportRoot

internal object OsActivityShortcutCardImportExport {
    fun buildCardsExportJson(
        cards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        exportedAtMillis: Long,
    ): String {
        val normalized =
            cards.map { card ->
                card.copy(config = normalizeActivityShortcutConfig(card.config, defaults))
            }
        val items = OsActivityShortcutCardCodec.encodeCards(normalized).parseJsonArrayOrNull()
            ?: error("activity card export items are invalid")
        return buildJsonObject {
            put(OS_ACTIVITY_CARD_KEY_EXPORT_SCHEMA, OS_ACTIVITY_CARD_EXPORT_SCHEMA)
            put(OS_ACTIVITY_CARD_KEY_EXPORT_SCHEMA_VERSION, OS_CARD_EXPORT_SCHEMA_VERSION)
            put(OS_ACTIVITY_CARD_KEY_EXPORT_EXPORTED_AT, exportedAtMillis)
            put(OS_ACTIVITY_CARD_KEY_EXPORT_ITEM_COUNT, normalized.size)
            put(OS_ACTIVITY_CARD_KEY_EXPORT_ITEMS, items)
        }.encodeCompact(KeiJson.pretty)
    }

    fun parseCardsImport(
        root: OsCardImportRoot,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            defaultBuiltInActivityShortcutCards(builtInSampleDefaults),
    ): OsActivityCardImportPayload {
        if (root.items.isEmpty()) {
            return OsActivityCardImportPayload(
                cards = emptyList(),
                sourceCount = root.sourceCount,
                invalidCount = 0,
                duplicateCount = 0,
                fileKind = root.fileKind,
                schemaVersion = root.schemaVersion,
                isLegacyFormat = root.isLegacyFormat,
            )
        }
        val decoded =
            buildList {
                for (element in root.items) {
                    val item = element as? JsonObject ?: continue
                    OsActivityShortcutCardCodec.decodeCard(item, defaults)?.let(::add)
                }
            }
        val migrated =
            OsActivityShortcutCardMigration.migrateBuiltInSampleCards(
                cards = decoded,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                appendMissingBuiltIns = false,
            )
        val deduplicated = deduplicateImportedActivityShortcutCards(migrated)
        return OsActivityCardImportPayload(
            cards = deduplicated.cards,
            sourceCount = root.sourceCount,
            invalidCount = (root.sourceCount - decoded.size).coerceAtLeast(0),
            duplicateCount = deduplicated.duplicateCount,
            fileKind = root.fileKind,
            schemaVersion = root.schemaVersion,
            isLegacyFormat = root.isLegacyFormat,
        )
    }

    fun mergeImportedCards(
        existingCards: List<OsActivityShortcutCard>,
        importedCards: List<OsActivityShortcutCard>,
        defaults: OsGoogleSystemServiceConfig,
        builtInSampleDefaults: OsGoogleSystemServiceConfig,
        builtInActivityShortcutCards: List<OsActivityShortcutCard> =
            defaultBuiltInActivityShortcutCards(builtInSampleDefaults),
    ): OsActivityCardImportMergeResult =
        mergeImportedActivityShortcutCards(
            existingCards = existingCards,
            importedCards = importedCards,
            defaults = defaults,
            builtInSampleDefaults = builtInSampleDefaults,
            builtInActivityShortcutCards = builtInActivityShortcutCards,
        )
}
