package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaGuideBgmPlaybackUiStateTest {
    @Test
    fun `resolved selection prefers queue item`() {
        val queueFavorite = favorite("same-url", "queue")
        val state =
            BaGuideBgmPlaybackUiState(
                favorites = listOf(favorite("same-url", "favorite")),
                queue = listOf(queueFavorite),
                selectedAudioUrl = "same-url",
            ).withResolvedSelection()

        assertEquals(queueFavorite, state.selectedFavorite)
        assertEquals(queueFavorite, state.selectedQueueFavorite)
    }

    @Test
    fun `resolved selection falls back to first available favorite`() {
        val fallbackFavorite = favorite("fallback-url", "fallback")
        val state =
            BaGuideBgmPlaybackUiState(
                favorites = listOf(fallbackFavorite),
                selectedAudioUrl = "missing-url",
            ).withResolvedSelection()

        assertEquals(fallbackFavorite, state.selectedFavorite)
        assertNull(state.selectedQueueFavorite)
    }

    private fun favorite(
        audioUrl: String,
        title: String,
    ): GuideBgmFavoriteItem =
        GuideBgmFavoriteItem(
            audioUrl = audioUrl,
            title = title,
            studentTitle = title,
            studentImageUrl = "",
            imageUrl = "",
            sourceUrl = "",
            note = "",
            favoritedAtMs = 1L,
        )
}
