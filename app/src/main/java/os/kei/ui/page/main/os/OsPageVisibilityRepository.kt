package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardDataSource
import os.kei.ui.page.main.os.shell.OsShellCommandCardStoreDataSource
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore

internal class OsPageVisibilityRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
    private val shellCommandCards: OsShellCommandCardDataSource = OsShellCommandCardStoreDataSource,
) {
    fun updatedVisibleCards(
        currentVisibleCards: Set<OsSectionCard>,
        card: OsSectionCard,
        visible: Boolean,
    ): Set<OsSectionCard> =
        currentVisibleCards
            .toMutableSet()
            .apply {
                if (visible) add(card) else remove(card)
            }.toSet()

    suspend fun persistSectionCardVisibility(
        card: OsSectionCard,
        visible: Boolean,
        visibleCards: Set<OsSectionCard>,
    ): Boolean =
        withContext(ioDispatcher) {
            OsCardVisibilityStore.saveVisibleCards(visibleCards)
            val hiddenSection = sectionKindByCard(card).takeIf { !visible }
            if (hiddenSection != null) {
                OsInfoCache.clear(hiddenSection)
            }
            OsInfoCache.readSnapshot(visibleSectionKinds(visibleCards)).hasPersistedCache
        }

    suspend fun setActivityCardVisible(
        cards: List<OsActivityShortcutCard>,
        cardId: String,
        visible: Boolean,
        defaults: OsGoogleSystemServiceConfig,
    ): List<OsActivityShortcutCard> =
        withContext(ioDispatcher) {
            val updatedCards =
                cards.map { card ->
                    if (card.id == cardId) card.copy(visible = visible) else card
                }
            OsActivityShortcutCardStore.saveCards(
                cards = updatedCards,
                defaults = defaults,
            )
            updatedCards
        }

    suspend fun setShellCommandCardVisible(
        cardId: String,
        visible: Boolean,
        builtInShellCommandCards: List<OsShellCommandCard>,
    ): List<OsShellCommandCard> =
        withContext(ioDispatcher) {
            shellCommandCards.setCardVisible(cardId = cardId, visible = visible)
            shellCommandCards.loadCards(builtInShellCommandCards)
        }
}
