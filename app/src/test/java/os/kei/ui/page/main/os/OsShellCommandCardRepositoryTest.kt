package os.kei.ui.page.main.os

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCardCodec
import os.kei.ui.page.main.os.shell.OsShellCommandCardDataSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OsShellCommandCardRepositoryTest {
    @Test
    fun `run result save reloads shell cards with built-in context`() =
        runTest {
            val stored = shellCard(id = "custom", command = "settings list global")
            val builtIn = shellCard(id = "builtin-new", command = "settings get global animator_duration_scale")
            val dataSource = FakeShellCommandCardDataSource(listOf(stored))
            val repository =
                OsPageShellCommandRepository(
                    ioDispatcher = Dispatchers.Unconfined,
                    shellCommandCards = dataSource,
                )

            val result =
                repository.saveRunResult(
                    cardId = "custom",
                    output = "ok",
                    builtInShellCommandCards = listOf(builtIn),
                )

            assertEquals("ok", dataSource.cards.single { it.id == "custom" }.runOutput)
            assertEquals(listOf(listOf(builtIn)), dataSource.loadBuiltInRequests)
            assertEquals(listOf("custom", "builtin-new"), result.map { it.id })
        }

    @Test
    fun `edit and delete reload shell cards with built-in context`() =
        runTest {
            val stored = shellCard(id = "custom", command = "settings list global")
            val builtIn = shellCard(id = "builtin-new", command = "settings list secure")
            val dataSource = FakeShellCommandCardDataSource(listOf(stored))
            val repository =
                OsPageCardRepository(
                    ioDispatcher = Dispatchers.Unconfined,
                    shellCommandCards = dataSource,
                )

            val edited =
                repository.saveShellCommandCardEdit(
                    cardId = "custom",
                    title = "Edited",
                    subtitle = "Subtitle",
                    command = "settings list system",
                    builtInShellCommandCards = listOf(builtIn),
                )

            assertEquals("Edited", edited?.first { it.id == "custom" }?.title)
            assertEquals(listOf(builtIn), dataSource.loadBuiltInRequests.single())

            dataSource.loadBuiltInRequests.clear()
            val deleted =
                repository.deleteShellCommandCard(
                    cardId = "custom",
                    builtInShellCommandCards = listOf(builtIn),
                )

            assertEquals(listOf("builtin-new"), deleted.map { it.id })
            assertEquals(listOf(builtIn), dataSource.loadBuiltInRequests.single())
        }

    @Test
    fun `visibility update reloads shell cards with built-in context`() =
        runTest {
            val stored = shellCard(id = "custom", command = "settings list global")
            val builtIn = shellCard(id = "builtin-new", command = "settings list secure")
            val dataSource = FakeShellCommandCardDataSource(listOf(stored))
            val repository =
                OsPageVisibilityRepository(
                    ioDispatcher = Dispatchers.Unconfined,
                    shellCommandCards = dataSource,
                )

            val result =
                repository.setShellCommandCardVisible(
                    cardId = "custom",
                    visible = false,
                    builtInShellCommandCards = listOf(builtIn),
                )

            assertEquals(false, result.first { it.id == "custom" }.visible)
            assertEquals(listOf("custom", "builtin-new"), result.map { it.id })
            assertEquals(listOf(builtIn), dataSource.loadBuiltInRequests.single())
        }

    @Test
    fun `edit returns null when target shell card is missing`() =
        runTest {
            val builtIn = shellCard(id = "builtin-new", command = "settings list secure")
            val dataSource = FakeShellCommandCardDataSource(emptyList())
            val repository =
                OsPageCardRepository(
                    ioDispatcher = Dispatchers.Unconfined,
                    shellCommandCards = dataSource,
                )

            val result =
                repository.saveShellCommandCardEdit(
                    cardId = "missing",
                    title = "Missing",
                    subtitle = "",
                    command = "settings list system",
                    builtInShellCommandCards = listOf(builtIn),
                )

            assertNull(result)
            assertEquals(emptyList(), dataSource.loadBuiltInRequests)
        }

    private class FakeShellCommandCardDataSource(
        initialCards: List<OsShellCommandCard>,
    ) : OsShellCommandCardDataSource {
        var cards: List<OsShellCommandCard> = initialCards
            private set
        val loadBuiltInRequests = mutableListOf<List<OsShellCommandCard>>()

        override fun loadCards(builtInShellCommandCards: List<OsShellCommandCard>): List<OsShellCommandCard> {
            loadBuiltInRequests += builtInShellCommandCards
            cards =
                cards +
                builtInShellCommandCards.filterNot { builtIn ->
                    cards.any { card ->
                        card.id == builtIn.id ||
                            OsShellCommandCardCodec.mergeKeyFor(card) == OsShellCommandCardCodec.mergeKeyFor(builtIn)
                    }
                }
            return cards
        }

        override fun updateCard(
            cardId: String,
            title: String,
            subtitle: String,
            command: String,
        ): OsShellCommandCard? {
            val existing = cards.firstOrNull { it.id == cardId } ?: return null
            val updated =
                existing.copy(
                    title = title.trim(),
                    subtitle = subtitle.trim(),
                    command = command.trim(),
                )
            cards = cards.map { card -> if (card.id == cardId) updated else card }
            return updated
        }

        override fun setCardVisible(
            cardId: String,
            visible: Boolean,
        ): List<OsShellCommandCard> {
            cards = cards.map { card -> if (card.id == cardId) card.copy(visible = visible) else card }
            return cards
        }

        override fun deleteCard(cardId: String): List<OsShellCommandCard> {
            cards = cards.filterNot { card -> card.id == cardId }
            return cards
        }

        override fun updateCardRunResult(
            cardId: String,
            runOutput: String,
            runAtMillis: Long,
        ): OsShellCommandCard? {
            val existing = cards.firstOrNull { it.id == cardId } ?: return null
            val updated =
                existing.copy(
                    runOutput = runOutput,
                    lastRunAtMillis = runAtMillis,
                )
            cards = cards.map { card -> if (card.id == cardId) updated else card }
            return updated
        }
    }

    private fun shellCard(
        id: String,
        command: String,
        visible: Boolean = true,
    ): OsShellCommandCard =
        OsShellCommandCard(
            id = id,
            visible = visible,
            title = id,
            command = command,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
}
