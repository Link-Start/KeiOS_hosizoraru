package os.kei.ui.page.main.os.components

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.isBuiltInShellCommandCard

@Immutable
internal data class OsShellVisibilityPresentationState(
    val showShellRunner: Boolean = true,
    val builtInCards: List<OsShellCommandCard> = emptyList(),
    val customCards: List<OsShellCommandCard> = emptyList(),
    val emptySearchActive: Boolean = false,
)

internal fun deriveOsShellVisibilityPresentationState(
    cards: List<OsShellCommandCard>,
    shellRunnerTitle: String,
    query: String,
): OsShellVisibilityPresentationState {
    val normalizedQuery = query.trim()
    val showShellRunner =
        normalizedQuery.isBlank() ||
            shellRunnerTitle.contains(normalizedQuery, ignoreCase = true)
    val filteredCards =
        cards
            .asSequence()
            .filter { card -> card.matchesShellVisibilityQuery(normalizedQuery) }
            .toList()
    val builtInCards = ArrayList<OsShellCommandCard>()
    val customCards = ArrayList<OsShellCommandCard>()
    filteredCards.forEach { card ->
        if (isBuiltInShellCommandCard(card)) {
            builtInCards += card
        } else {
            customCards += card
        }
    }
    return OsShellVisibilityPresentationState(
        showShellRunner = showShellRunner,
        builtInCards = builtInCards,
        customCards = customCards,
        emptySearchActive = !showShellRunner && filteredCards.isEmpty(),
    )
}

private fun OsShellCommandCard.matchesShellVisibilityQuery(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        subtitle.contains(query, ignoreCase = true) ||
        command.contains(query, ignoreCase = true)
}
