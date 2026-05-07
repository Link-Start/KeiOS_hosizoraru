package os.kei.ui.page.main.student.catalog.component

import org.junit.Test
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BaGuideBgmPlaybackQueueTest {
    @Test
    fun `queue selection clamps current track into visible playlist`() {
        val selection = resolveBaGuideBgmPlaybackQueueSelection(
            nextQueue = listOf(track("a"), track("b"), track("c")),
            currentSelectedAudioUrl = "outside"
        )

        assertEquals("a", selection.selectedAudioUrl)
        assertEquals(listOf("a", "b", "c"), selection.queue.map { it.audioUrl })
    }

    @Test
    fun `queue offset cycles inside visible playlist`() {
        val queue = listOf(track("a"), track("b"), track("c"))

        assertEquals(
            "c",
            selectBaGuideBgmPlaybackQueueOffset(
                queue = queue,
                selectedAudioUrl = "b",
                offset = 1
            )?.audioUrl
        )
        assertEquals(
            "a",
            selectBaGuideBgmPlaybackQueueOffset(
                queue = queue,
                selectedAudioUrl = "b",
                offset = -1
            )?.audioUrl
        )
    }

    @Test
    fun `empty queue keeps selection and disables offset selection`() {
        val selection = resolveBaGuideBgmPlaybackQueueSelection(
            nextQueue = emptyList(),
            currentSelectedAudioUrl = "outside"
        )

        assertEquals("outside", selection.selectedAudioUrl)
        assertNull(
            selectBaGuideBgmPlaybackQueueOffset(
                queue = selection.queue,
                selectedAudioUrl = selection.selectedAudioUrl,
                offset = 1
            )
        )
    }

    @Test
    fun `duplicate queue entries keep first occurrence`() {
        val selection = resolveBaGuideBgmPlaybackQueueSelection(
            nextQueue = listOf(track("a"), track("a"), track("b")),
            currentSelectedAudioUrl = "b"
        )

        assertEquals(listOf("a", "b"), selection.queue.map { it.audioUrl })
        assertEquals("b", selection.selectedAudioUrl)
    }

    @Test
    fun `system media previous and next stay inside active queue`() {
        val queue = listOf(track("a"), track("b"), track("c"))

        assertEquals(
            "a",
            selectBaGuideBgmPlaybackQueueOffset(
                queue = queue,
                selectedAudioUrl = "c",
                offset = 1
            )?.audioUrl
        )
        assertEquals(
            "c",
            selectBaGuideBgmPlaybackQueueOffset(
                queue = queue,
                selectedAudioUrl = "a",
                offset = -1
            )?.audioUrl
        )
    }

    private fun track(id: String): GuideBgmFavoriteItem {
        return GuideBgmFavoriteItem(
            audioUrl = id,
            title = "Track $id",
            studentTitle = "Student $id",
            studentImageUrl = "",
            imageUrl = "",
            sourceUrl = "https://www.gamekee.com/ba/tj/$id.html",
            note = "",
            favoritedAtMs = 1L
        )
    }
}
