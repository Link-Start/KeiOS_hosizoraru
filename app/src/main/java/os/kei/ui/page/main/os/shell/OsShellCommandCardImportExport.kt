package os.kei.ui.page.main.os.shell

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.KeiJson
import os.kei.core.json.encodeCompact
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.ui.page.main.os.transfer.OS_CARD_EXPORT_SCHEMA_VERSION
import os.kei.ui.page.main.os.transfer.OS_SHELL_CARD_EXPORT_SCHEMA
import os.kei.ui.page.main.os.transfer.OsCardImportRoot
import os.kei.ui.page.main.os.transfer.OsShellCardImportPayload

internal object OsShellCommandCardImportExport {
    fun buildCardsExportJson(
        cards: List<OsShellCommandCard>,
        exportedAtMillis: Long,
    ): String {
        val normalized = cards.mapNotNull(OsShellCommandCardCodec::normalizeCard)
        val items = OsShellCommandCardCodec.encodeCards(normalized).parseJsonArrayOrNull()
            ?: error("shell card export items are invalid")
        return buildJsonObject {
            put(OS_SHELL_CARD_KEY_EXPORT_SCHEMA, OS_SHELL_CARD_EXPORT_SCHEMA)
            put(OS_SHELL_CARD_KEY_EXPORT_SCHEMA_VERSION, OS_CARD_EXPORT_SCHEMA_VERSION)
            put(OS_SHELL_CARD_KEY_EXPORT_EXPORTED_AT, exportedAtMillis)
            put(OS_SHELL_CARD_KEY_EXPORT_ITEM_COUNT, normalized.size)
            put(OS_SHELL_CARD_KEY_EXPORT_ITEMS, items)
        }.encodeCompact(KeiJson.pretty)
    }

    fun parseCardsImport(root: OsCardImportRoot): OsShellCardImportPayload {
        if (root.items.isEmpty()) {
            return OsShellCardImportPayload(
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
                    OsShellCommandCardCodec.decodeCard(item)?.let(::add)
                }
            }
        val deduplicated = mutableListOf<OsShellCommandCard>()
        var duplicateCount = 0
        decoded.forEach { imported ->
            val targetIndexById = deduplicated.indexOfFirst { it.id == imported.id }
            val targetIndex =
                if (targetIndexById >= 0) {
                    targetIndexById
                } else {
                    deduplicated.indexOfFirst {
                        OsShellCommandCardCodec.mergeKeyFor(it) == OsShellCommandCardCodec.mergeKeyFor(imported)
                    }
                }
            if (targetIndex < 0) {
                deduplicated += imported
            } else {
                val existing = deduplicated[targetIndex]
                deduplicated[targetIndex] =
                    imported.copy(
                        id = existing.id,
                        createdAtMillis =
                            existing.createdAtMillis.takeIf { it > 0L }
                                ?: imported.createdAtMillis,
                        updatedAtMillis = maxOf(existing.updatedAtMillis, imported.updatedAtMillis),
                    )
                duplicateCount += 1
            }
        }
        return OsShellCardImportPayload(
            cards = deduplicated,
            sourceCount = root.sourceCount,
            invalidCount = (root.sourceCount - decoded.size).coerceAtLeast(0),
            duplicateCount = duplicateCount,
            fileKind = root.fileKind,
            schemaVersion = root.schemaVersion,
            isLegacyFormat = root.isLegacyFormat,
        )
    }

    fun mergeImportedCards(
        existingCards: List<OsShellCommandCard>,
        importedCards: List<OsShellCommandCard>,
    ): OsShellCardImportMergeResult {
        val mergedCards = existingCards.toMutableList()
        var addedCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        importedCards.forEach { imported ->
            val targetIndexById = mergedCards.indexOfFirst { it.id == imported.id }
            val targetIndex =
                if (targetIndexById >= 0) {
                    targetIndexById
                } else {
                    mergedCards.indexOfFirst {
                        OsShellCommandCardCodec.mergeKeyFor(it) == OsShellCommandCardCodec.mergeKeyFor(imported)
                    }
                }
            if (targetIndex < 0) {
                mergedCards += imported
                addedCount += 1
                return@forEach
            }
            val existing = mergedCards[targetIndex]
            val resolved =
                imported.copy(
                    id = existing.id,
                    createdAtMillis = existing.createdAtMillis.takeIf { it > 0L } ?: imported.createdAtMillis,
                    updatedAtMillis = maxOf(existing.updatedAtMillis, imported.updatedAtMillis),
                )
            if (OsShellCommandCardCodec.cardsEquivalent(existing, resolved)) {
                unchangedCount += 1
            } else {
                mergedCards[targetIndex] = resolved
                updatedCount += 1
            }
        }
        return OsShellCardImportMergeResult(
            cards = mergedCards,
            addedCount = addedCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
        )
    }
}
