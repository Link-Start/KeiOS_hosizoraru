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
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.student.BaGuideBgmPlaybackClock
import os.kei.ui.page.main.student.BaGuideBgmPlaybackRepository
import os.kei.ui.page.main.student.BaGuideBgmSystemPlaybackClock
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackProgress
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackSnapshot
import os.kei.ui.page.main.student.normalizeGuideMediaSource
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

internal data class BaGuideBgmPlaybackUiState(
    val favorites: List<GuideBgmFavoriteItem> = emptyList(),
    val queue: List<GuideBgmFavoriteItem> = emptyList(),
    val selectedAudioUrl: String = "",
    val queueModeName: String = BaGuideBgmQueueMode.Continuous.name,
    val nativeMediaNotificationEnabled: Boolean = false,
    val selectedFavorite: GuideBgmFavoriteItem? = null,
    val selectedQueueFavorite: GuideBgmFavoriteItem? = null,
) {
    val queueMode: BaGuideBgmQueueMode
        get() =
            BaGuideBgmQueueMode.entries.firstOrNull { it.name == queueModeName }
                ?: BaGuideBgmQueueMode.Continuous
}

internal fun BaGuideBgmPlaybackUiState.withResolvedSelection(): BaGuideBgmPlaybackUiState {
    val nextSelectedQueueFavorite = queue.firstOrNull { it.audioUrl == selectedAudioUrl }
    val nextSelectedFavorite =
        nextSelectedQueueFavorite
            ?: favorites.firstOrNull { it.audioUrl == selectedAudioUrl }
            ?: queue.firstOrNull()
            ?: favorites.firstOrNull()
    if (
        selectedFavorite == nextSelectedFavorite &&
        selectedQueueFavorite == nextSelectedQueueFavorite
    ) {
        return this
    }
    return copy(
        selectedFavorite = nextSelectedFavorite,
        selectedQueueFavorite = nextSelectedQueueFavorite,
    )
}

internal class BaGuideBgmPlaybackCoordinator(
    private val context: Context,
    private val playbackRepository: BaGuideBgmPlaybackRepository = BaGuideBgmPlaybackRepository(),
    private val clock: BaGuideBgmPlaybackClock = BaGuideBgmSystemPlaybackClock,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) {
    private val lightweightBackend = BaGuideBgmLightweightPlaybackBackend(context, playbackRepository)
    private val nativeBackend =
        BaGuideBgmNativePlaybackBackend(
            BaGuideBgmNativeMediaController(
                context = context,
                volumeProvider = playbackRepository::volume,
            ),
        )
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val _uiState = MutableStateFlow(BaGuideBgmPlaybackUiState())
    private val _runtimeState = MutableStateFlow(BaGuideBgmPlaybackRuntimeState())
    private var runtimePollingJob: Job? = null
    private var lastProgressPersistAudioUrl = ""
    private var lastProgressPersistPositionMs = Long.MIN_VALUE
    private var lastProgressPersistPlaying: Boolean? = null
    private var lastProgressPersistAtMs = 0L
    private var playbackSnapshot = GuideBgmFavoritePlaybackSnapshot.Empty

    val uiState: StateFlow<BaGuideBgmPlaybackUiState> = _uiState.asStateFlow()
    val sessionState: StateFlow<BaGuideBgmPlaybackUiState> = _uiState.asStateFlow()
    val runtimeStateFlow: StateFlow<BaGuideBgmPlaybackRuntimeState> = _runtimeState.asStateFlow()

    val favorites: List<GuideBgmFavoriteItem>
        get() = _uiState.value.favorites

    val queue: List<GuideBgmFavoriteItem>
        get() = _uiState.value.queue

    val selectedAudioUrl: String
        get() = _uiState.value.selectedAudioUrl

    val queueModeName: String
        get() = _uiState.value.queueModeName

    val runtimeState: BaGuideBgmPlaybackRuntimeState
        get() = _runtimeState.value

    val nativeMediaNotificationEnabled: Boolean
        get() = _uiState.value.nativeMediaNotificationEnabled

    internal val activeBackendMode: BaGuideBgmPlaybackBackendMode
        get() = activeBackend.mode

    val keepsPlaybackAfterPageStop: Boolean
        get() = nativeMediaNotificationEnabled

    private val activeBackend: BaGuideBgmPlaybackBackend
        get() =
            when (resolveBaGuideBgmPlaybackBackendMode(nativeMediaNotificationEnabled)) {
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
        val favorites =
            nextFavorites
                .filter { it.audioUrl.isNotBlank() }
                .distinctBy { it.audioUrl }
        val currentState = _uiState.value
        val selectedAudioUrl =
            currentState.selectedAudioUrl.ifBlank {
                favorites.firstOrNull()?.audioUrl.orEmpty()
            }
        if (currentState.favorites == favorites && currentState.selectedAudioUrl == selectedAudioUrl) {
            return
        }
        _uiState.update { state ->
            state
                .copy(
                    favorites = favorites,
                    selectedAudioUrl = selectedAudioUrl,
                ).withResolvedSelection()
        }
        syncActiveQueue()
    }

    fun updateQueue(nextQueue: List<GuideBgmFavoriteItem>) {
        val currentState = _uiState.value
        val selection =
            resolveBaGuideBgmPlaybackQueueSelection(
                nextQueue = nextQueue,
                currentSelectedAudioUrl = currentState.selectedAudioUrl,
                currentSelectedFavorite = currentState.selectedQueueFavorite ?: currentState.selectedFavorite,
            )
        if (currentState.queue == selection.queue &&
            currentState.selectedAudioUrl == selection.selectedAudioUrl
        ) {
            return
        }
        _uiState.update { state ->
            state
                .copy(
                    queue = selection.queue,
                    selectedAudioUrl = selection.selectedAudioUrl,
                ).withResolvedSelection()
        }
        syncActiveQueue()
    }

    fun updateNativeMediaNotificationEnabled(enabled: Boolean) {
        if (nativeMediaNotificationEnabled == enabled) return
        val favorite = selectedFavorite
        val previousBackend = activeBackend
        val previousState = previousBackend.runtimeState(favorite)
        if (favorite != null) saveProgress(favorite, previousState, force = true)
        if (enabled) {
            favorite?.let { selected ->
                lightweightBackend.pause(selected)
                nativeBackend.prepare(
                    favorite = selected,
                    queue = activePlaybackQueue(selected),
                    queueMode = queueMode,
                    startPositionMs = previousState.positionMs,
                )
                if (previousState.isPlaying) {
                    nativeBackend.play(
                        favorite = selected,
                        queue = activePlaybackQueue(selected),
                        queueMode = queueMode,
                        startPositionMs = previousState.positionMs,
                        restart = false,
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
                    startPositionMs = previousState.positionMs,
                )
            }
        }
        _uiState.update { state ->
            state.copy(
                nativeMediaNotificationEnabled = enabled,
            )
        }
        setRuntimeState(previousState.copy(isPlaying = previousState.isPlaying && enabled), force = true)
    }

    fun restoreSnapshot() {
        scope.launch {
            val snapshot = playbackRepository.loadSnapshot()
            playbackSnapshot = snapshot
            val queueModeName =
                snapshot.queueModeName
                    .takeIf { saved -> BaGuideBgmQueueMode.entries.any { it.name == saved } }
                    ?: BaGuideBgmQueueMode.Continuous.name
            _uiState.update { state ->
                state
                    .copy(
                        selectedAudioUrl = snapshot.selectedAudioUrl,
                        queueModeName = queueModeName,
                    ).withResolvedSelection()
            }
            setRuntimeState(BaGuideBgmPlaybackRuntimeState(volume = snapshot.volume), force = true)
        }
    }

    fun select(audioUrl: String) {
        _uiState.update { state -> state.copy(selectedAudioUrl = audioUrl).withResolvedSelection() }
        persistSelection()
    }

    fun selectOffset(
        offset: Int,
        startPlayback: Boolean = true,
        restart: Boolean = true,
    ): Boolean {
        val favorite =
            selectBaGuideBgmPlaybackQueueOffset(
                queue = queue,
                selectedAudioUrl = selectedAudioUrl,
                offset = offset,
            ) ?: return false
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl).withResolvedSelection() }
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
            startPositionMs = resumePosition(favorite),
        )
        persistSelection(favorite.audioUrl)
    }

    fun play(
        favorite: GuideBgmFavoriteItem,
        restart: Boolean = false,
    ) {
        val previousRuntimeState = activeBackend.runtimeState(favorite)
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl).withResolvedSelection() }
        persistSelection()
        activeBackend.play(
            favorite = favorite,
            queue = activePlaybackQueue(favorite),
            queueMode = queueMode,
            startPositionMs = if (restart) 0L else resumePosition(favorite),
            restart = restart,
        )
        publishOptimisticPlaybackStart(previousRuntimeState, restart = restart)
    }

    fun toggle(favorite: GuideBgmFavoriteItem) {
        val previousRuntimeState = activeBackend.runtimeState(favorite)
        _uiState.update { state -> state.copy(selectedAudioUrl = favorite.audioUrl).withResolvedSelection() }
        persistSelection()
        activeBackend.toggle(
            favorite = favorite,
            queue = activePlaybackQueue(favorite),
            queueMode = queueMode,
            startPositionMs = resumePosition(favorite),
        )
        if (previousRuntimeState.isPlaying) {
            val pausedRuntimeState =
                previousRuntimeState.copy(
                    isPlaying = false,
                    isBuffering = false,
                )
            setRuntimeState(pausedRuntimeState, force = true)
            saveProgress(favorite, pausedRuntimeState, force = true)
        } else {
            publishOptimisticPlaybackStart(previousRuntimeState, restart = false)
        }
    }

    fun pause(favorite: GuideBgmFavoriteItem): BaGuideBgmPlaybackRuntimeState {
        val nextState = activeBackend.pause(favorite)
        setRuntimeState(nextState, force = true)
        saveProgress(favorite, nextState, force = true)
        return nextState
    }

    fun seek(
        favorite: GuideBgmFavoriteItem,
        progress: Float,
    ): BaGuideBgmPlaybackRuntimeState {
        val nextState =
            activeBackend.seek(
                favorite = favorite,
                queueMode = queueMode,
                progress = progress,
            )
        setRuntimeState(nextState, force = true)
        saveProgress(favorite, nextState, force = true)
        return nextState
    }

    fun updateVolume(
        favorite: GuideBgmFavoriteItem,
        volume: Float,
    ): BaGuideBgmPlaybackRuntimeState {
        val nextState = activeBackend.updateVolume(favorite, volume)
        setRuntimeState(nextState, force = true)
        persistVolume(nextState.volume)
        return nextState
    }

    fun toggleQueueMode() {
        val favorite = selectedFavorite
        val nextMode =
            if (queueMode == BaGuideBgmQueueMode.Continuous) {
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
                _uiState.update { state -> state.copy(selectedAudioUrl = currentAudioUrl).withResolvedSelection() }
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
        runtimePollingJob =
            scope.launch {
                while (isActive) {
                    refreshRuntime()
                    advanceIfEnded()
                    delay(resolveRuntimePollingDelayMs(_runtimeState.value).milliseconds)
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
            startPositionMs = resumePosition(favorite),
        )
    }

    private fun persistSelection(audioUrl: String = selectedAudioUrl) {
        val normalizedAudioUrl = normalizeGuideMediaSource(audioUrl)
        playbackSnapshot =
            playbackSnapshot.copy(
                selectedAudioUrl = normalizedAudioUrl,
                queueModeName = queueModeName,
            )
        scope.launch(AppDispatchers.media) {
            playbackRepository.saveSelection(normalizedAudioUrl, queueModeName)
        }
    }

    private fun setRuntimeState(
        nextState: BaGuideBgmPlaybackRuntimeState,
        force: Boolean = false,
    ) {
        if (!force && !shouldPublishRuntimeState(_runtimeState.value, nextState)) {
            return
        }
        _runtimeState.value = nextState
    }

    private fun publishOptimisticPlaybackStart(
        previousRuntimeState: BaGuideBgmPlaybackRuntimeState,
        restart: Boolean,
    ) {
        setRuntimeState(
            previousRuntimeState.copy(
                positionMs = if (restart) 0L else previousRuntimeState.positionMs,
                isPlaying = true,
                isBuffering = previousRuntimeState.durationMs <= 0L || previousRuntimeState.isBuffering,
                isEnded = false,
            ),
            force = true,
        )
    }

    private fun shouldPublishRuntimeState(
        currentState: BaGuideBgmPlaybackRuntimeState,
        nextState: BaGuideBgmPlaybackRuntimeState,
    ): Boolean {
        if (currentState.isPlaying != nextState.isPlaying) return true
        if (currentState.isBuffering != nextState.isBuffering) return true
        if (currentState.isEnded != nextState.isEnded) return true
        if (currentState.durationMs != nextState.durationMs) return true
        if (currentState.volume != nextState.volume) return true
        return abs(currentState.positionMs - nextState.positionMs) >= BGM_RUNTIME_UI_POSITION_DELTA_MS
    }

    private fun resumePosition(favorite: GuideBgmFavoriteItem): Long =
        playbackSnapshot
            .progressFor(favorite.audioUrl)
            ?.resumePositionMs
            ?: 0L

    private fun saveProgress(
        favorite: GuideBgmFavoriteItem,
        state: BaGuideBgmPlaybackRuntimeState,
        force: Boolean = false,
    ) {
        if (state.durationMs > 0L || state.positionMs > 0L || state.isPlaying) {
            val now = clock.nowMs()
            val shouldPersist =
                force ||
                    favorite.audioUrl != lastProgressPersistAudioUrl ||
                    abs(state.positionMs - lastProgressPersistPositionMs) >= BGM_PROGRESS_SAVE_POSITION_DELTA_MS ||
                    lastProgressPersistPlaying != state.isPlaying ||
                    state.isEnded ||
                    now - lastProgressPersistAtMs >= BGM_PROGRESS_SAVE_INTERVAL_MS
            if (!shouldPersist) return
            val normalizedAudioUrl = normalizeGuideMediaSource(favorite.audioUrl)
            if (normalizedAudioUrl.isBlank()) return
            playbackSnapshot =
                playbackSnapshot.copy(
                    progressByAudioUrl =
                        playbackSnapshot.progressByAudioUrl +
                            (
                                normalizedAudioUrl to
                                    GuideBgmFavoritePlaybackProgress(
                                        audioUrl = normalizedAudioUrl,
                                        positionMs = state.positionMs,
                                        durationMs = state.durationMs,
                                        updatedAtMs = now.coerceAtLeast(1L),
                                        lastPlayedAtMs =
                                            if (state.isPlaying) {
                                                now.coerceAtLeast(1L)
                                            } else {
                                                playbackSnapshot.progressFor(normalizedAudioUrl)?.lastPlayedAtMs ?: 0L
                                            },
                                    )
                            ),
                )
            scope.launch(AppDispatchers.media) {
                playbackRepository.saveProgress(
                    audioUrl = favorite.audioUrl,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    isPlaying = state.isPlaying,
                    nowMs = now,
                )
            }
            lastProgressPersistAudioUrl = favorite.audioUrl
            lastProgressPersistPositionMs = state.positionMs
            lastProgressPersistPlaying = state.isPlaying
            lastProgressPersistAtMs = now
        }
    }

    private fun persistVolume(volume: Float) {
        scope.launch(AppDispatchers.media) {
            playbackRepository.saveVolume(volume)
        }
    }
}

private fun resolveRuntimePollingDelayMs(runtimeState: BaGuideBgmPlaybackRuntimeState): Long =
    when {
        runtimeState.isPlaying || runtimeState.isBuffering -> BGM_RUNTIME_ACTIVE_POLL_MS
        runtimeState.isEnded -> BGM_RUNTIME_ENDED_POLL_MS
        else -> BGM_RUNTIME_IDLE_POLL_MS
    }

@Composable
internal fun rememberBaGuideBgmPlaybackCoordinator(
    context: Context,
    favorites: List<GuideBgmFavoriteItem>,
    nativeMediaNotificationEnabled: Boolean,
): BaGuideBgmPlaybackCoordinator {
    val coordinator =
        remember(context) {
            BaGuideBgmPlaybackCoordinator(context)
        }
    LaunchedEffect(coordinator) {
        coordinator.restoreSnapshot()
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
private const val BGM_RUNTIME_IDLE_POLL_MS = 5_000L
private const val BGM_RUNTIME_UI_POSITION_DELTA_MS = 900L
private const val BGM_PROGRESS_SAVE_POSITION_DELTA_MS = 2_000L
private const val BGM_PROGRESS_SAVE_INTERVAL_MS = 5_000L
