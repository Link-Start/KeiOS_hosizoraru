package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.GuideBgmFavoriteItem

/**
 * Removal of a BGM favourite, and the offer to take it back.
 *
 * Only the *favourites list* offers the undo. Removing on the student BGM tab empties a heart and leaves the
 * row in place, so tapping again restores it; removing here deletes the row from the only screen that shows
 * it, and finding the track again means going back through the catalog to the right student. Apple treats an
 * undo affordance as the alternative to confirming a destructive action rather than a complement to it, and
 * this is reversible enough to take that branch.
 *
 * Its own class, rather than inline in [BaGuideCatalogViewModel], so the removal, the restore and the
 * expiry can be run off-device: the ViewModel builds its repositories itself over MMKV singletons, which a
 * JVM test cannot open.
 */
internal class BaGuideBgmFavoriteUndoController(
    private val scope: CoroutineScope,
    private val currentFavorites: () -> List<GuideBgmFavoriteItem>,
    private val removeFavorite: suspend (audioUrl: String) -> Unit,
    /** Adds [GuideBgmFavoriteItem] back. The store's toggle adds when absent, so it is the removal's inverse. */
    private val restoreFavorite: suspend (GuideBgmFavoriteItem) -> Unit,
    /** Runs after every removal and every restore, before anything else reacts to it. */
    private val onFavoritesChanged: suspend () -> Unit,
    private val undoWindowMs: Long = BGM_FAVORITE_UNDO_WINDOW_MS,
) {
    private val mutablePending = MutableStateFlow<GuideBgmFavoriteItem?>(null)

    /** The favourite most recently removed from the favourites list, while it is still being offered back. */
    val pending: StateFlow<GuideBgmFavoriteItem?> = mutablePending.asStateFlow()
    private var pendingJob: Job? = null

    fun remove(
        normalizedAudioUrl: String,
        offerUndo: Boolean,
        onRemoved: suspend () -> Unit,
    ) {
        // Captured before the removal, because afterwards the item is gone from the only list that holds
        // it and there is nothing left to rebuild it from.
        val removed =
            if (offerUndo) {
                currentFavorites().firstOrNull { item -> item.audioUrl.trim() == normalizedAudioUrl }
            } else {
                null
            }
        scope.launch {
            removeFavorite(normalizedAudioUrl)
            onFavoritesChanged()
            if (removed != null) offer(removed)
            onRemoved()
        }
    }

    /** Puts the pending item back and clears the offer. A no-op if the offer already expired. */
    fun restore() {
        val item = mutablePending.value ?: return
        clear()
        scope.launch {
            restoreFavorite(item)
            onFavoritesChanged()
        }
    }

    fun clear() {
        pendingJob?.cancel()
        pendingJob = null
        mutablePending.value = null
    }

    private fun offer(item: GuideBgmFavoriteItem) {
        pendingJob?.cancel()
        mutablePending.value = item
        pendingJob =
            scope.launch {
                delay(undoWindowMs)
                if (mutablePending.value?.audioUrl == item.audioUrl) {
                    mutablePending.value = null
                }
            }
    }
}

/**
 * How long the favourites list keeps offering the last removal back.
 *
 * Long enough to notice the row vanish, read which track it was and reach for Undo, and short enough that
 * the card is not still sitting there when attention has moved on. Matches the order of a system snackbar's
 * long duration rather than being a fresh guess.
 */
internal const val BGM_FAVORITE_UNDO_WINDOW_MS = 8_000L
