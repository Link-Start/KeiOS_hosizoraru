package os.kei.ui.page.main.os.shortcut

import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig

internal data class OsActivityShortcutCardDeduplicateResult(
    val cards: List<OsActivityShortcutCard>,
    val duplicateCount: Int,
)

internal fun deduplicateImportedActivityShortcutCards(cards: List<OsActivityShortcutCard>): OsActivityShortcutCardDeduplicateResult {
    val deduplicated = mutableListOf<OsActivityShortcutCard>()
    var duplicateCount = 0
    cards.forEach { imported ->
        val targetIndex = deduplicated.indexOfMatchingActivityShortcut(imported)
        if (targetIndex < 0) {
            deduplicated += imported
        } else {
            val existing = deduplicated[targetIndex]
            deduplicated[targetIndex] =
                imported.copy(
                    id = existing.id,
                    isBuiltInSample = existing.isBuiltInSample || imported.isBuiltInSample,
                )
            duplicateCount += 1
        }
    }
    return OsActivityShortcutCardDeduplicateResult(
        cards = deduplicated,
        duplicateCount = duplicateCount,
    )
}

internal fun mergeImportedActivityShortcutCards(
    existingCards: List<OsActivityShortcutCard>,
    importedCards: List<OsActivityShortcutCard>,
    defaults: OsGoogleSystemServiceConfig,
    builtInSampleDefaults: OsGoogleSystemServiceConfig,
    builtInActivityShortcutCards: List<OsActivityShortcutCard>,
): OsActivityCardImportMergeResult {
    val mergedCards = existingCards.toMutableList()
    var addedCount = 0
    var updatedCount = 0
    var unchangedCount = 0
    importedCards.forEach { imported ->
        val targetIndex = mergedCards.indexOfMatchingActivityShortcut(imported)
        if (targetIndex < 0) {
            mergedCards += imported
            addedCount += 1
            return@forEach
        }
        val existing = mergedCards[targetIndex]
        val resolved =
            imported.copy(
                id = existing.id,
                isBuiltInSample = existing.isBuiltInSample || imported.isBuiltInSample,
            )
        if (osActivityShortcutCardsEquivalent(existing, resolved, defaults)) {
            unchangedCount += 1
        } else {
            mergedCards[targetIndex] = resolved
            updatedCount += 1
        }
    }
    return OsActivityCardImportMergeResult(
        cards =
            OsActivityShortcutCardMigration.migrateBuiltInSampleCards(
                cards = mergedCards,
                builtInSampleDefaults = builtInSampleDefaults,
                builtInActivityShortcutCards = builtInActivityShortcutCards,
                appendMissingBuiltIns = false,
            ),
        addedCount = addedCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
    )
}

private fun List<OsActivityShortcutCard>.indexOfMatchingActivityShortcut(target: OsActivityShortcutCard): Int {
    val targetIndexById = indexOfFirst { it.id == target.id }
    if (targetIndexById >= 0) return targetIndexById
    val targetKey = osActivityShortcutMergeKey(target)
    return indexOfFirst { osActivityShortcutMergeKey(it) == targetKey }
}
