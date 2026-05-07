package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import kotlin.time.Duration.Companion.milliseconds

internal class BaGuideBgmPlaybackCoordinator(
    private val context: Context
) {
    private val lightweightBackend = BaGuideBgmLightweightPlaybackBackend(context)
    private val nativeBackend = BaGuideBgmNativePlaybackBackend(
        BaGuideBgmNativeMediaController(context)
    )

    var favorites by mutableStateOf<List<GuideBgmFavoriteItem>>(emptyList())
        private set
    var queue by mutableStateOf<List<GuideBgmFavoriteItem>>(emptyList())
        private set
    var selectedAudioUrl by mutableStateOf("")
        private set
    var queueModeName by mutableStateOf(BaGuideBgmQueueMode.Continuous.name)
        private set
    var runtimeState by mutableStateOf(BaGuideBgmPlaybackRuntimeState())
        private set
    var nativeMediaNotificationEnabled by mutableStateOf(false)
        private set

    internal val activeBackendMode: BaGuideBgmPlaybackBackendMode
        get() = activeBackend.mode

    val keepsPlaybackAfterPageStop: Boolean
        get() = nativeMediaNotificationEnabled

    private val activeBackend: BaGuideBgmPlaybackBackend
        get() = when (resolveBaGuideBgmPlaybackBackendMode(nativeMediaNotificationEnabled)) {
            BaGuideBgmPlaybackBackendMode.NativeMedia -> nativeBackend
            BaGuideBgmPlaybackBackendMode.Lightweight -> lightweightBackend
        }

    val queueMode: BaGuideBgmQueueMode
        get() = BaGuideBgmQueueMode.entries.firstOrNull { it.name == queueModeName }
            ?: BaGuideBgmQueueMode.Continuous

    val selectedFavorite: GuideBgmFavoriteItem?
        get() = queue.firstOrNull { it.audioUrl == selectedAudioUrl }
            ?: favorites.firstOrNull { it.audioUrl == selectedAudioUrl }
            ?: queue.firstOrNull()
            ?: favorites.firstOrNull()

    val selectedQueueFavorite: GuideBgmFavoriteItem?
        get() = queue.firstOrNull { it.audioUrl == selectedAudioUrl }

    fun updateFavorites(nextFavorites: List<GuideBgmFavoriteItem>) {
        favorites = nextFavorites
            .filter { it.audioUrl.isNotBlank() }
            .distinctBy { it.audioUrl }
        if (selectedAudioUrl.isBlank()) {
            selectedAudioUrl = favorites.firstOrNull()?.audioUrl.orEmpty()
        }
        syncActiveQueue()
    }

    fun updateQueue(nextQueue: List<GuideBgmFavoriteItem>) {
        val selection = resolveBaGuideBgmPlaybackQueueSelection(
            nextQueue = nextQueue,
            currentSelectedAudioUrl = selectedAudioUrl
        )
        queue = selection.queue
        selectedAudioUrl = selection.selectedAudioUrl
        syncActiveQueue()
    }

    fun updateNativeMediaNotificationEnabled(enabled: Boolean) {
        if (nativeMediaNotificationEnabled == enabled) return
        val favorite = selectedFavorite
        val previousBackend = activeBackend
        val previousState = previousBackend.runtimeState(favorite)
        if (favorite != null) saveProgress(favorite, previousState)
        if (enabled) {
            favorite?.let { selected ->
                lightweightBackend.pause(selected)
                nativeBackend.prepare(
                    favorite = selected,
                    queue = activePlaybackQueue(selected),
                    queueMode = queueMode,
                    startPositionMs = previousState.positionMs
                )
                if (previousState.isPlaying) {
                    nativeBackend.play(
                        favorite = selected,
                        queue = activePlaybackQueue(selected),
                        queueMode = queueMode,
                        startPositionMs = previousState.positionMs,
                        restart = false
                    )
                }
            }
        } else {
            nativeBackend.stopSession()
            favorite?.let { selected ->
                lightweightBackend.prepare(
                    favorite = selected,
                    queue = activePlaybackQueue(selected),
                    queueMode = queueMode,
                    startPositionMs = previousState.positionMs
                )
            }
        }
        nativeMediaNotificationEnabled = enabled
        runtimeState = previousState.copy(isPlaying = previousState.isPlaying && enabled)
    }

    fun restoreSnapshot() {
        val snapshot = GuideBgmFavoritePlaybackStore.snapshot()
        selectedAudioUrl = snapshot.selectedAudioUrl
        queueModeName = snapshot.queueModeName
            .takeIf { saved -> BaGuideBgmQueueMode.entries.any { it.name == saved } }
            ?: BaGuideBgmQueueMode.Continuous.name
        runtimeState = BaGuideBgmPlaybackRuntimeState(volume = snapshot.volume)
    }

    fun select(audioUrl: String) {
        selectedAudioUrl = audioUrl
        persistSelection()
    }

    fun selectOffset(offset: Int, startPlayback: Boolean = true, restart: Boolean = true): Boolean {
        val favorite = selectBaGuideBgmPlaybackQueueOffset(
            queue = queue,
            selectedAudioUrl = selectedAudioUrl,
            offset = offset
        ) ?: return false
        selectedAudioUrl = favorite.audioUrl
        if (startPlayback) {
            play(favorite, restart = restart)
        } else {
            persistSelection()
        }
        return true
    }

    fun prepareSelected() {
        val favorite = selectedQueueFavorite ?: selectedFavorite ?: return
        activeBackend.prepare(
            favorite = favorite,
            queue = activePlaybackQueue(favorite),
            queueMode = queueMode,
            startPositionMs = resumePosition(favorite)
        )
        persistSelection(favorite.audioUrl)
    }

    fun play(favorite: GuideBgmFavoriteItem, restart: Boolean = false) {
        selectedAudioUrl = favorite.audioUrl
        persistSelection()
        activeBackend.play(
            favorite = favorite,
            queue = activePlaybackQueue(favorite),
            queueMode = queueMode,
            startPositionMs = if (restart) 0L else resumePosition(favorite),
            restart = restart
        )
        refreshRuntime(favorite)
    }

    fun toggle(favorite: GuideBgmFavoriteItem) {
        selectedAudioUrl = favorite.audioUrl
        persistSelection()
        activeBackend.toggle(
            favorite = favorite,
            queue = activePlaybackQueue(favorite),
            queueMode = queueMode,
            startPositionMs = resumePosition(favorite)
        )
        refreshRuntime(favorite)
    }

    fun pause(favorite: GuideBgmFavoriteItem): BaGuideBgmPlaybackRuntimeState {
        runtimeState = activeBackend.pause(favorite)
        saveProgress(favorite, runtimeState)
        return runtimeState
    }

    fun seek(favorite: GuideBgmFavoriteItem, progress: Float): BaGuideBgmPlaybackRuntimeState {
        runtimeState = activeBackend.seek(
            favorite = favorite,
            queueMode = queueMode,
            progress = progress
        )
        saveProgress(favorite, runtimeState)
        return runtimeState
    }

    fun updateVolume(favorite: GuideBgmFavoriteItem, volume: Float): BaGuideBgmPlaybackRuntimeState {
        runtimeState = activeBackend.updateVolume(favorite, volume)
        return runtimeState
    }

    fun toggleQueueMode() {
        val favorite = selectedFavorite
        val nextMode = if (queueMode == BaGuideBgmQueueMode.Continuous) {
            BaGuideBgmQueueMode.SingleLoop
        } else {
            BaGuideBgmQueueMode.Continuous
        }
        queueModeName = nextMode.name
        persistSelection()
        if (favorite != null) {
            activeBackend.applyQueueMode(favorite, nextMode)
        }
    }

    fun refreshRuntime(favorite: GuideBgmFavoriteItem? = selectedFavorite) {
        if (nativeMediaNotificationEnabled) {
            val currentAudioUrl = activeBackend.currentAudioUrl()
            if (currentAudioUrl.isNotBlank() && queue.any { it.audioUrl == currentAudioUrl }) {
                selectedAudioUrl = currentAudioUrl
                persistSelection()
            }
            val nativeQueueMode = activeBackend.currentQueueMode()
            if (nativeQueueMode != null && queueModeName != nativeQueueMode.name) {
                queueModeName = nativeQueueMode.name
                persistSelection()
            }
        }
        val resolvedFavorite = selectedFavorite ?: favorite
        runtimeState = activeBackend.runtimeState(resolvedFavorite)
        if (resolvedFavorite != null) saveProgress(resolvedFavorite, runtimeState)
    }

    fun advanceIfEnded(): Boolean {
        if (
            runtimeState.isEnded &&
            queueMode == BaGuideBgmQueueMode.Continuous &&
            queue.size > 1
        ) {
            selectOffset(offset = 1, startPlayback = true, restart = true)
            return true
        }
        return false
    }

    fun dispose() {
        nativeBackend.disconnect()
    }

    private fun activePlaybackQueue(favorite: GuideBgmFavoriteItem): List<GuideBgmFavoriteItem> {
        val baseQueue = queue.takeIf { it.isNotEmpty() } ?: favorites
        if (baseQueue.any { it.audioUrl == favorite.audioUrl }) return baseQueue
        return listOf(favorite) + baseQueue
    }

    private fun syncActiveQueue() {
        if (!nativeMediaNotificationEnabled) return
        val favorite = selectedFavorite ?: return
        activeBackend.updateQueue(
            queue = activePlaybackQueue(favorite),
            selectedAudioUrl = favorite.audioUrl,
            queueMode = queueMode,
            startPositionMs = resumePosition(favorite)
        )
    }

    private fun persistSelection(audioUrl: String = selectedAudioUrl) {
        GuideBgmFavoritePlaybackStore.saveSelection(audioUrl, queueModeName)
    }

    private fun resumePosition(favorite: GuideBgmFavoriteItem): Long {
        return GuideBgmFavoritePlaybackStore
            .progressFor(favorite.audioUrl)
            ?.resumePositionMs
            ?: 0L
    }

    private fun saveProgress(
        favorite: GuideBgmFavoriteItem,
        state: BaGuideBgmPlaybackRuntimeState
    ) {
        if (state.durationMs > 0L || state.positionMs > 0L || state.isPlaying) {
            GuideBgmFavoritePlaybackStore.saveProgress(
                audioUrl = favorite.audioUrl,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying
            )
        }
    }
}

@Composable
internal fun rememberBaGuideBgmPlaybackCoordinator(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    nativeMediaNotificationEnabled: Boolean
): BaGuideBgmPlaybackCoordinator {
    val coordinator = remember(context) {
        BaGuideBgmPlaybackCoordinator(context).apply { restoreSnapshot() }
    }
    LaunchedEffect(coordinator, nativeMediaNotificationEnabled) {
        coordinator.updateNativeMediaNotificationEnabled(nativeMediaNotificationEnabled)
    }
    LaunchedEffect(coordinator, favorites) {
        coordinator.updateFavorites(favorites)
    }
    LaunchedEffect(coordinator) {
        while (true) {
            coordinator.refreshRuntime()
            coordinator.advanceIfEnded()
            delay(500L.milliseconds)
        }
    }
    DisposableEffect(coordinator) {
        onDispose { coordinator.dispose() }
    }
    return coordinator
}
