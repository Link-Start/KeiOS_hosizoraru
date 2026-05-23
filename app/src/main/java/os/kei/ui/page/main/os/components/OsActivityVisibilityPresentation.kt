package os.kei.ui.page.main.os.components

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

@Immutable
internal data class OsActivityVisibilityPresentationState(
    val builtInItems: List<OsActivityVisibilityItem> = emptyList(),
    val customItems: List<OsActivityVisibilityItem> = emptyList(),
    val emptySearchActive: Boolean = false,
)

internal fun deriveOsActivityVisibilityPresentationState(
    cards: List<OsActivityShortcutCard>,
    defaultCardTitle: String,
    query: String,
): OsActivityVisibilityPresentationState {
    val normalizedQuery = query.trim()
    val filteredItems =
        cards
            .asSequence()
            .map { card ->
                OsActivityVisibilityItem(
                    id = card.id,
                    title = card.config.title.ifBlank { defaultCardTitle },
                    packageName = card.config.packageName,
                    className = card.config.className,
                    builtInSample = card.isBuiltInSample,
                    visible = card.visible,
                )
            }.filter { item ->
                item.matchesActivityVisibilityQuery(normalizedQuery)
            }.toList()
    return OsActivityVisibilityPresentationState(
        builtInItems = filteredItems.filter { it.builtInSample },
        customItems = filteredItems.filterNot { it.builtInSample },
        emptySearchActive = normalizedQuery.isNotBlank() && filteredItems.isEmpty(),
    )
}

private fun OsActivityVisibilityItem.matchesActivityVisibilityQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        packageName.contains(query, ignoreCase = true) ||
        className.contains(query, ignoreCase = true)
}
