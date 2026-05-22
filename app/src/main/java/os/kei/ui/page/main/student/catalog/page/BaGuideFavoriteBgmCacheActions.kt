@file:Suppress("ktlint:standard:filename")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.catalog.state.BaGuideFavoriteBgmOfflineCacheUiState

internal data class BaGuideFavoriteBgmOfflineCacheState(
    val offlineAudioUrls: Set<String> = emptySet(),
    val onToggleFavoriteCache: (GuideBgmFavoriteItem) -> Unit = {},
)

@Composable
internal fun rememberBaGuideFavoriteBgmOfflineCacheState(
    uiState: BaGuideFavoriteBgmOfflineCacheUiState,
    onToggleFavoriteCache: (GuideBgmFavoriteItem) -> Unit,
): BaGuideFavoriteBgmOfflineCacheState {
    val offlineAudioUrls = uiState.offlineAudioUrls
    return remember(offlineAudioUrls, onToggleFavoriteCache) {
        BaGuideFavoriteBgmOfflineCacheState(
            offlineAudioUrls = offlineAudioUrls,
            onToggleFavoriteCache = onToggleFavoriteCache,
        )
    }
}
