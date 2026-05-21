package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import os.kei.core.ext.showToast
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoriteStore
import os.kei.ui.page.main.student.GuideBottomTab
import os.kei.ui.page.main.student.catalog.BaGuideCatalogEntry
import os.kei.ui.page.main.student.page.state.GuideDetailTabRequestStore

internal data class BaGuideStudentBgmActions(
    val openStudentGuide: (BaGuideCatalogEntry) -> Unit,
    val openFavoriteGuide: (GuideBgmFavoriteItem) -> Unit,
    val playEntry: (BaGuideCatalogEntry) -> Unit,
    val toggleEntryFavorite: (BaGuideCatalogEntry) -> Unit,
    val togglePlayback: (GuideBgmFavoriteItem) -> Unit,
    val selectQueueOffset: (offset: Int, startPlayback: Boolean, restart: Boolean) -> Unit
)

@Composable
internal fun rememberBaGuideStudentBgmActions(
    context: Context,
    lookupCoordinator: BaGuideStudentBgmLookupCoordinator,
    lookupStates: Map<Long, BaGuideStudentBgmLookupState>,
    favoriteByNormalizedSourceUrl: Map<String, GuideBgmFavoriteItem>,
    selectedAudioUrl: String,
    playbackCoordinator: BaGuideBgmPlaybackCoordinator,
    setNowPlayingVisible: (Boolean) -> Unit,
    onOpenGuide: (String) -> Unit,
    bgmMissingText: String,
    bgmResolveFailedText: String,
    favoriteAddedText: String,
    favoriteRemovedText: String
): BaGuideStudentBgmActions {
    val currentSetNowPlayingVisible = rememberUpdatedState(setNowPlayingVisible)
    val currentOnOpenGuide = rememberUpdatedState(onOpenGuide)
    return remember(
        context,
        lookupCoordinator,
        lookupStates,
        favoriteByNormalizedSourceUrl,
        selectedAudioUrl,
        playbackCoordinator,
        bgmMissingText,
        bgmResolveFailedText,
        favoriteAddedText,
        favoriteRemovedText
    ) {
        fun openStudentGuide(entry: BaGuideCatalogEntry) {
            GuideDetailTabRequestStore.request(entry.detailUrl, GuideBottomTab.Gallery)
            currentOnOpenGuide.value(entry.detailUrl)
        }

        fun openFavoriteGuide(favorite: GuideBgmFavoriteItem) {
            GuideDetailTabRequestStore.request(favorite.sourceUrl, GuideBottomTab.Gallery)
            currentOnOpenGuide.value(favorite.sourceUrl)
        }

        fun favoriteForEntry(entry: BaGuideCatalogEntry): GuideBgmFavoriteItem? {
            return favoriteForStudentBgmEntry(
                entry = entry,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
            )
        }

        fun stateWithFavoriteFallback(
            entry: BaGuideCatalogEntry,
            lookupState: BaGuideStudentBgmLookupState
        ): BaGuideStudentBgmLookupState {
            return studentBgmStateWithFavoriteFallback(
                entry = entry,
                lookupState = lookupState,
                favoriteByNormalizedSourceUrl = favoriteByNormalizedSourceUrl
            )
        }

        fun resolveEntry(
            entry: BaGuideCatalogEntry,
            allowNetwork: Boolean,
            onResolved: (BaGuideStudentBgmResolvedItem?) -> Unit
        ) {
            lookupCoordinator.resolveEntry(
                entry = entry,
                allowNetwork = allowNetwork,
                onResolved = onResolved
            )
        }

        fun startPlayback(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
            playbackCoordinator.play(favorite, restart = restart)
            currentSetNowPlayingVisible.value(true)
        }

        fun togglePlayback(favorite: GuideBgmFavoriteItem) {
            playbackCoordinator.toggle(favorite)
            currentSetNowPlayingVisible.value(true)
        }

        fun playEntry(entry: BaGuideCatalogEntry) {
            val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
            stateWithFavoriteFallback(entry, lookupState).readyFavoriteOrNull()?.let { favorite ->
                if (lookupState !is BaGuideStudentBgmLookupState.Ready) {
                    lookupCoordinator.markReadyFromFavorite(
                        entry = entry,
                        item = BaGuideStudentBgmResolvedItem(
                            favorite = favorite,
                            fromCache = false,
                            fromFavorite = true
                        )
                    )
                }
                if (selectedAudioUrl == favorite.audioUrl) {
                    togglePlayback(favorite)
                } else {
                    startPlayback(favorite)
                }
                return
            }
            resolveEntry(entry = entry, allowNetwork = true) { resolved ->
                val favorite = resolved?.favorite
                if (favorite == null) {
                    context.showToast(bgmMissingText)
                } else if (selectedAudioUrl == favorite.audioUrl) {
                    togglePlayback(favorite)
                } else {
                    startPlayback(favorite)
                }
            }
        }

        fun toggleEntryFavorite(entry: BaGuideCatalogEntry) {
            val lookupState = lookupStates[entry.contentId] ?: BaGuideStudentBgmLookupState.Idle
            if (lookupState !is BaGuideStudentBgmLookupState.Ready) {
                val savedFavorite = favoriteForEntry(entry)
                if (savedFavorite != null) {
                    GuideBgmFavoriteStore.removeFavorite(savedFavorite.audioUrl)
                    context.showToast(favoriteRemovedText)
                    return
                }
            }
            resolveEntry(entry = entry, allowNetwork = true) { resolved ->
                val favorite = resolved?.favorite
                if (favorite == null) {
                    context.showToast(bgmResolveFailedText)
                    return@resolveEntry
                }
                val added = GuideBgmFavoriteStore.toggleFavorite(favorite)
                context.showToast(
                    if (added) favoriteAddedText else favoriteRemovedText
                )
            }
        }

        BaGuideStudentBgmActions(
            openStudentGuide = ::openStudentGuide,
            openFavoriteGuide = ::openFavoriteGuide,
            playEntry = ::playEntry,
            toggleEntryFavorite = ::toggleEntryFavorite,
            togglePlayback = ::togglePlayback,
            selectQueueOffset = { offset, startPlayback, restart ->
                playbackCoordinator.selectOffset(
                    offset = offset,
                    startPlayback = startPlayback,
                    restart = restart
                )
            }
        )
    }
}
