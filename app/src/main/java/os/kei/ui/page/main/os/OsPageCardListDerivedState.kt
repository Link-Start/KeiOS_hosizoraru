package os.kei.ui.page.main.os

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard

@Immutable
internal data class OsPageCardListDerivedState(
    val activityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    val visibleActivityShortcutCards: List<OsActivityShortcutCard> = emptyList(),
    val shellCommandCards: List<OsShellCommandCard> = emptyList(),
    val visibleShellCommandCards: List<OsShellCommandCard> = emptyList(),
) {
    companion object {
        val Empty = OsPageCardListDerivedState()
    }
}

internal fun deriveOsPageCardListState(persistentState: OsPagePersistentState): OsPageCardListDerivedState {
    val activityShortcutCards = osActivityShortcutCardsForUi(persistentState)
    val shellCommandCards = persistentState.shellCommandCards
    return OsPageCardListDerivedState(
        activityShortcutCards = activityShortcutCards,
        visibleActivityShortcutCards = activityShortcutCards.filter { it.visible },
        shellCommandCards = shellCommandCards,
        visibleShellCommandCards = shellCommandCards.filter { it.visible },
    )
}
