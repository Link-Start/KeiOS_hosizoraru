package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import kotlin.time.Duration.Companion.milliseconds

internal data class BaGuideBgmPlaybackUiState(
    val favorites: List<GuideBgmFavoriteItem> = emptyList(),
    val queue: List<GuideBgmFavoriteItem> = emptyList(),
    val selectedAudioUrl: String = "",
    val queueModeName: String = BaGuideBgmQueueMode.Continuous.name,
    val runtimeState: BaGuideBgmPlaybackRuntimeState = BaGuideBgmPlaybackRuntimeState(),
    val nativeMediaNotificationEnabled: Boolean = false
) {
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
}

internal class BaGuideBgmPlaybackCoordinator(
    private val context: Context,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private val lightweightBackend = BaGuideBgmLightweightPlaybackBackend(context)
    private val nativeBackend = BaGuideBgmNativePlaybackBackend(
        BaGuideBgmNativeMediaController(context)
    )
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _uiState = MutableStateFlow(BaGuideBgmPlaybackUiState())
    private var runtimePollingJob: Job? = null

    val uiState: StateFlow<BaGuideBgmPlaybackUiState> = _uiState.asStateFlow()

    val favorites: List<GuideBgmFavoriteItem>
        get() = _uiState.value.favorites

    val queue: List<GuideBgmFavoriteItem>
        get() = _uiState.value.queue

    val selectedAudioUrl: String
        get() = _uiState.value.selectedAudioUrl

    val queueModeName: String
        get() = _uiState.value.queueModeName

    val runtimeState: BaGuideBgmPlaybackRuntimeState
        get() = _uiState.value.runtimeState

    val nativeMediaNotificationEnabled: Boolean
        get() = _uiState.value.nativeMediaNotificationEnabled

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
        get() = _uiState.value.queueMode

    val selectedFavorite: GuideBgmFavoriteItem?
        get() = _uiState.value.selectedFavorite

    val selectedQueueFavorite: GuideBgmFavoriteItem?
        get() = _uiState.value.selectedQueueFavorite

    fun updateFavorites(nextFavorites: List<GuideBgmFavoriteItem>) {
        val favorites = nextFavorites
            .filter { it.audioUrl.isNotBlank() }
            .distinctBy { it.audioUrl }
        _uiState.update { state ->
            state.copy(
                favorites = favorites,
                selectedAudioUrl = state.selectedAudioUrl.ifBlank {
                    favorites.firstOrNull()?.audioUrl.orEmpty()
                }
            )
        }
        syncActiveQueue()
    }

    fun updateQueue(nextQueue: List<GuideBgmFavoriteItem>) {
        val selection = resolveBaGuideBgmPlaybackQueueSelection(
            nextQueue = nextQueue,
            currentSelectedAudioUrl = selectedAudioUrl
        )
        _uiState.update { state ->
            state.copy(
                queue = selection.queue,
                selectedAudioUrl = selection.selectedAudioUrl
            )
        }
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
        _uiState.update { state ->
            state.copy(
                nativeMediaNotificationEnabled = enabled,
                runtimeState = previousState.copy(isPlaying = previousState.isPlaying && enabled)
            )
        }
    }

    fun restoreSnapshot() {
        val snapshot = GuideBgmFavoritePlaybackStore.snapshot()
        val queueModeName = snapshot.queueModeName
            .takeIf { saved -> BaGuideBgmQueueMode.entries.any { it.name == saved } }
            ?: BaGuideBgmQueueMode.Continuous.name
        _uiState.update { state ->
            state.copy(
                selectedAudioUrl = snapshot.selectedAudioUrl,
                queueModeName = queueModeName,
                runtimeState = BaGuideBgmPlaybackRuntimeState(volume = snapshot.volume)
            )
        }
    }

    fun select(audioUrl: String) {
        _uiState.update { state -> state.copy(selectedAudioUrl = audioUrl) }
        persistSelection()
    }

    fun selectOffset(offset: Int, startPlayback: Boolean = true, restart: Boolean = true): Boolean {
        val favorite = selectBaGuideBgmPlaybackQueueOffset(
            queue = queue,
            selectedAudioUrl = selectedAudioUrl,
            offset = offset
        ) ?: return false
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl) }
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
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl) }
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
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl) }
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
        val nextState = activeBackend.pause(favorite)
        setRuntimeState(nextState)
        saveProgress(favorite, nextState)
        return nextState
    }

    fun seek(favorite: GuideBgmFavoriteItem, progress: Float): BaGuideBgmPlaybackRuntimeState {
        val nextState = activeBackend.seek(
            favorite = favorite,
            queueMode = queueMode,
            progress = progress
        )
        setRuntimeState(nextState)
        saveProgress(favorite, nextState)
        return nextState
    }

    fun updateVolume(favorite: GuideBgmFavoriteItem, volume: Float): BaGuideBgmPlaybackRuntimeState {
        val nextState = activeBackend.updateVolume(favorite, volume)
        setRuntimeState(nextState)
        return nextState
    }

    fun toggleQueueMode() {
        val favorite = selectedFavorite
        val nextMode = if (queueMode == BaGuideBgmQueueMode.Continuous) {
            BaGuideBgmQueueMode.SingleLoop
        } else {
            BaGuideBgmQueueMode.Continuous
        }
        _uiState.update { state -> state.copy(queueModeName = nextMode.name) }
        persistSelection()
        if (favorite != null) {
            activeBackend.applyQueueMode(favorite, nextMode)
        }
    }

    fun refreshRuntime(favorite: GuideBgmFavoriteItem? = selectedFavorite) {
        if (nativeMediaNotificationEnabled) {
            val currentAudioUrl = activeBackend.currentAudioUrl()
            if (currentAudioUrl.isNotBlank() && queue.any { it.audioUrl == currentAudioUrl }) {
                _uiState.update { state -> state.copy(selectedAudioUrl = currentAudioUrl) }
                persistSelection()
            }
            val nativeQueueMode = activeBackend.currentQueueMode()
            if (nativeQueueMode != null && queueModeName != nativeQueueMode.name) {
                _uiState.update { state -> state.copy(queueModeName = nativeQueueMode.name) }
                persistSelection()
            }
        }
        val resolvedFavorite = selectedFavorite ?: favorite
        val nextRuntimeState = activeBackend.runtimeState(resolvedFavorite)
        setRuntimeState(nextRuntimeState)
        if (resolvedFavorite != null) saveProgress(resolvedFavorite, nextRuntimeState)
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

    fun startRuntimePolling() {
        if (runtimePollingJob?.isActive == true) return
        runtimePollingJob = scope.launch {
            while (isActive) {
                refreshRuntime()
                advanceIfEnded()
                delay(resolveRuntimePollingDelayMs(_uiState.value.runtimeState).milliseconds)
            }
        }
    }

    fun stopRuntimePolling() {
        runtimePollingJob?.cancel()
        runtimePollingJob = null
    }

    fun dispose() {
        stopRuntimePolling()
        nativeBackend.disconnect()
        scope.cancel()
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

    private fun setRuntimeState(nextState: BaGuideBgmPlaybackRuntimeState) {
        _uiState.update { state -> state.copy(runtimeState = nextState) }
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

private fun resolveRuntimePollingDelayMs(
    runtimeState: BaGuideBgmPlaybackRuntimeState
): Long {
    return when {
        runtimeState.isPlaying || runtimeState.isBuffering -> BGM_RUNTIME_ACTIVE_POLL_MS
        runtimeState.isEnded -> BGM_RUNTIME_ENDED_POLL_MS
        else -> BGM_RUNTIME_IDLE_POLL_MS
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
    DisposableEffect(coordinator) {
        coordinator.startRuntimePolling()
        onDispose { coordinator.dispose() }
    }
    return coordinator
}

private const val BGM_RUNTIME_ACTIVE_POLL_MS = 500L
private const val BGM_RUNTIME_ENDED_POLL_MS = 300L
private const val BGM_RUNTIME_IDLE_POLL_MS = 1_500L
