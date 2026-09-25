package os.kei.ui.page.main.student.catalog.state

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.testBgmFavorite

/**
 * The undo offer on removing a BGM favourite from the favourites list.
 *
 * Replaces a source scan that looked for the capture, the delete and the `delay` by text. These run the
 * removal itself: the item has to be looked up *before* the delete (afterwards nothing holds it), it has to
 * come back where it was, and the offer has to go away on its own rather than sit on the page until the tab
 * changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BaGuideBgmFavoriteUndoControllerTest {
    @Test
    fun `removing from the favourites list offers the removed item back`() =
        runTest {
            val store = FakeBgmFavorites(Newest, Middle, Oldest)
            val undo = undoController(store)

            undo.remove(Middle.audioUrl, offerUndo = true, onRemoved = {})
            runCurrent()

            assertEquals(listOf(Newest, Oldest), store.favorites, "the removal must still delete")
            // Null here is the capture happening after the delete: by then the list no longer has it.
            assertSame(Middle, undo.pending.value, "the offer must carry the item that was removed")
        }

    @Test
    fun `undo puts the item back in its place`() =
        runTest {
            val store = FakeBgmFavorites(Newest, Middle, Oldest)
            val undo = undoController(store)
            undo.remove(Middle.audioUrl, offerUndo = true, onRemoved = {})
            runCurrent()

            undo.restore()
            runCurrent()

            // Its place comes from its original favourited time, so a restore that rebuilt or re-stamped the
            // item would put it at the top instead.
            assertEquals(listOf(Newest, Middle, Oldest), store.favorites)
            assertSame(Middle, store.restored.single(), "restore the captured item itself, not a rebuild")
            assertNull(undo.pending.value, "taking the offer clears it")
        }

    @Test
    fun `the offer expires on its own`() =
        runTest {
            val store = FakeBgmFavorites(Newest, Middle, Oldest)
            val undo = undoController(store)
            undo.remove(Middle.audioUrl, offerUndo = true, onRemoved = {})
            runCurrent()

            advanceTimeBy(BGM_FAVORITE_UNDO_WINDOW_MS - 1)
            runCurrent()
            assertSame(Middle, undo.pending.value, "still inside the window")

            advanceTimeBy(2)
            runCurrent()
            assertNull(undo.pending.value, "the offer must lapse without anyone clearing it")

            undo.restore()
            runCurrent()
            assertEquals(listOf(Newest, Oldest), store.favorites, "an expired offer restores nothing")
        }

    private fun TestScope.undoController(store: FakeBgmFavorites) =
        BaGuideBgmFavoriteUndoController(
            scope = backgroundScope,
            currentFavorites = { store.favorites },
            removeFavorite = store::remove,
            restoreFavorite = store::restore,
            onFavoritesChanged = {},
        )
}

/** Keeps the order `GuideBgmFavoriteStore` keeps: newest favourite first. */
private class FakeBgmFavorites(
    vararg initial: GuideBgmFavoriteItem,
) {
    var favorites: List<GuideBgmFavoriteItem> = initial.sortedByDescending { it.favoritedAtMs }
        private set
    val restored = mutableListOf<GuideBgmFavoriteItem>()

    suspend fun remove(audioUrl: String) {
        favorites = favorites.filterNot { it.audioUrl == audioUrl }
    }

    suspend fun restore(item: GuideBgmFavoriteItem) {
        restored += item
        favorites = (favorites + item).sortedByDescending { it.favoritedAtMs }
    }
}

private fun favorite(
    name: String,
    favoritedAtMs: Long,
) = testBgmFavorite(
    audioUrl = "https://example.invalid/bgm/$name.ogg",
    sourceUrl = "https://example.invalid/student/$name",
    title = name,
    studentTitle = "Student $name",
    favoritedAtMs = favoritedAtMs,
)

private val Newest = favorite("newest", favoritedAtMs = 3_000L)
private val Middle = favorite("middle", favoritedAtMs = 2_000L)
private val Oldest = favorite("oldest", favoritedAtMs = 1_000L)
