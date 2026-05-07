package os.kei.ui.page.main.student.catalog.component

import os.kei.ui.page.main.student.GuideBgmFavoriteItem

internal data class BaGuideBgmPlaybackQueueSelection(
    val queue: List<GuideBgmFavoriteItem>,
    val selectedAudioUrl: String
)

internal fun resolveBaGuideBgmPlaybackQueueSelection(
    nextQueue: List<GuideBgmFavoriteItem>,
    currentSelectedAudioUrl: String
): BaGuideBgmPlaybackQueueSelection {
    val queue = nextQueue
        .filter { it.audioUrl.isNotBlank() }
        .distinctBy { it.audioUrl }
    val selected = when {
        queue.isEmpty() -> currentSelectedAudioUrl
        currentSelectedAudioUrl.isBlank() -> queue.first().audioUrl
        queue.any { it.audioUrl == currentSelectedAudioUrl } -> currentSelectedAudioUrl
        else -> queue.first().audioUrl
    }
    return BaGuideBgmPlaybackQueueSelection(
        queue = queue,
        selectedAudioUrl = selected
    )
}

internal fun selectBaGuideBgmPlaybackQueueOffset(
    queue: List<GuideBgmFavoriteItem>,
    selectedAudioUrl: String,
    offset: Int
): GuideBgmFavoriteItem? {
    val activeQueue = queue
        .filter { it.audioUrl.isNotBlank() }
        .distinctBy { it.audioUrl }
    if (activeQueue.isEmpty()) return null
    val currentIndex = activeQueue.indexOfFirst { it.audioUrl == selectedAudioUrl }
        .takeIf { it >= 0 }
        ?: 0
    val nextIndex = (currentIndex + offset + activeQueue.size) % activeQueue.size
    return activeQueue.getOrNull(nextIndex)
}
