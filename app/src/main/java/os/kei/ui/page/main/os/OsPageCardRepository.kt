package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardStore
import os.kei.ui.page.main.os.shortcut.OsActivityCardEditMode
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCardStore
import os.kei.ui.page.main.os.shortcut.newOsActivityShortcutCardId
import os.kei.ui.page.main.os.shortcut.normalizeActivityShortcutConfig

internal class OsPageCardRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.osOperations,
) {
    suspend fun saveShellCommandCardEdit(
        cardId: String,
        title: String,
        subtitle: String,
        command: String,
    ): List<OsShellCommandCard>? =
        withContext(ioDispatcher) {
            OsShellCommandCardStore.updateCard(
                cardId = cardId,
                title = title,
                subtitle = subtitle,
                command = command,
            ) ?: return@withContext null
            OsShellCommandCardStore.loadCards()
        }

    suspend fun deleteShellCommandCard(cardId: String): List<OsShellCommandCard> =
        withContext(ioDispatcher) {
            OsShellCommandCardStore.deleteCard(cardId)
        }

    suspend fun saveActivityShortcutCard(
        cards: List<OsActivityShortcutCard>,
        editMode: OsActivityCardEditMode,
        editingCardId: String?,
        draft: OsGoogleSystemServiceConfig,
        defaults: OsGoogleSystemServiceConfig,
    ): List<OsActivityShortcutCard> =
        withContext(ioDispatcher) {
            val normalized = normalizeActivityShortcutConfig(draft, defaults)
            val updatedCards =
                if (editMode == OsActivityCardEditMode.Add || editingCardId.isNullOrBlank()) {
                    cards +
                        OsActivityShortcutCard(
                            id = newOsActivityShortcutCardId(),
                            visible = true,
                            isBuiltInSample = false,
                            config = normalized,
                        )
                } else {
                    cards.map { card ->
                        if (card.id == editingCardId) card.copy(config = normalized) else card
                    }
                }
            OsActivityShortcutCardStore.saveCards(
                cards = updatedCards,
                defaults = defaults,
            )
            updatedCards
        }

    suspend fun deleteActivityShortcutCard(
        cards: List<OsActivityShortcutCard>,
        cardId: String,
        defaults: OsGoogleSystemServiceConfig,
    ): List<OsActivityShortcutCard> =
        withContext(ioDispatcher) {
            val updatedCards = cards.filterNot { card -> card.id == cardId }
            OsActivityShortcutCardStore.saveCards(
                cards = updatedCards,
                defaults = defaults,
            )
            updatedCards
        }
}
