package os.kei.ui.page.main.student.catalog.component

import android.content.Context
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore

internal enum class BaGuideBgmPlaybackBackendMode {
    Lightweight,
    NativeMedia
}

internal fun resolveBaGuideBgmPlaybackBackendMode(
    nativeMediaNotificationEnabled: Boolean
): BaGuideBgmPlaybackBackendMode {
    return if (nativeMediaNotificationEnabled) {
        BaGuideBgmPlaybackBackendMode.NativeMedia
    } else {
        BaGuideBgmPlaybackBackendMode.Lightweight
    }
}

internal interface BaGuideBgmPlaybackBackend {
    val mode: BaGuideBgmPlaybackBackendMode

    fun updateQueue(
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    )

    fun prepare(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    )

    fun play(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long,
        restart: Boolean
    )

    fun toggle(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    )

    fun pause(favorite: GuideBgmFavoriteItem): BaGuideBgmPlaybackRuntimeState

    fun seek(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode,
        progress: Float
    ): BaGuideBgmPlaybackRuntimeState

    fun updateVolume(
        favorite: GuideBgmFavoriteItem,
        volume: Float
    ): BaGuideBgmPlaybackRuntimeState

    fun applyQueueMode(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode
    )

    fun runtimeState(favorite: GuideBgmFavoriteItem?): BaGuideBgmPlaybackRuntimeState

    fun currentAudioUrl(): String = ""

    fun currentQueueMode(): BaGuideBgmQueueMode? = null

    fun disconnect() = Unit

    fun stopSession() = disconnect()
}

internal class BaGuideBgmLightweightPlaybackBackend(
    private val context: Context
) : BaGuideBgmPlaybackBackend {
    override val mode: BaGuideBgmPlaybackBackendMode = BaGuideBgmPlaybackBackendMode.Lightweight

    override fun updateQueue(
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) = Unit

    override fun prepare(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) {
        prepareFavoriteBgmPlayback(context, favorite, queueMode, startPositionMs)
    }

    override fun play(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long,
        restart: Boolean
    ) {
        playFavoriteBgm(context, favorite, queueMode, startPositionMs, restart)
    }

    override fun toggle(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) {
        toggleFavoriteBgmPlayback(context, favorite, queueMode, startPositionMs)
    }

    override fun pause(favorite: GuideBgmFavoriteItem): BaGuideBgmPlaybackRuntimeState {
        return pauseFavoriteBgmPlayback(context, favorite)
    }

    override fun seek(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode,
        progress: Float
    ): BaGuideBgmPlaybackRuntimeState {
        return seekFavoriteBgmPlayback(context, favorite, queueMode, progress)
    }

    override fun updateVolume(
        favorite: GuideBgmFavoriteItem,
        volume: Float
    ): BaGuideBgmPlaybackRuntimeState {
        return updateFavoriteBgmVolume(context, favorite, volume)
    }

    override fun applyQueueMode(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode
    ) {
        applyFavoriteBgmQueueMode(context, favorite, queueMode)
    }

    override fun runtimeState(favorite: GuideBgmFavoriteItem?): BaGuideBgmPlaybackRuntimeState {
        return favorite
            ?.let { favoriteBgmRuntimeState(context, it) }
            ?: BaGuideBgmPlaybackRuntimeState(volume = GuideBgmFavoritePlaybackStore.volume())
    }
}

internal class BaGuideBgmNativePlaybackBackend(
    private val controller: BaGuideBgmNativeMediaController
) : BaGuideBgmPlaybackBackend {
    override val mode: BaGuideBgmPlaybackBackendMode = BaGuideBgmPlaybackBackendMode.NativeMedia

    override fun updateQueue(
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) {
        controller.syncQueue(queue, selectedAudioUrl, queueMode, startPositionMs)
    }

    override fun prepare(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) {
        controller.syncQueue(queue, favorite.audioUrl, queueMode, startPositionMs)
    }

    override fun play(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long,
        restart: Boolean
    ) {
        controller.syncQueue(
            queue = queue,
            selectedAudioUrl = favorite.audioUrl,
            queueMode = queueMode,
            startPositionMs = startPositionMs,
            playWhenReady = true,
            restart = restart
        )
    }

    override fun toggle(
        favorite: GuideBgmFavoriteItem,
        queue: List<GuideBgmFavoriteItem>,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long
    ) {
        val current = controller.runtimeState()
        if (current.isPlaying) {
            controller.pause()
        } else {
            play(favorite, queue, queueMode, startPositionMs, restart = false)
        }
    }

    override fun pause(favorite: GuideBgmFavoriteItem): BaGuideBgmPlaybackRuntimeState {
        controller.pause()
        return controller.runtimeState().copy(isPlaying = false)
    }

    override fun seek(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode,
        progress: Float
    ): BaGuideBgmPlaybackRuntimeState {
        controller.seekToProgress(progress)
        return controller.runtimeState()
    }

    override fun updateVolume(
        favorite: GuideBgmFavoriteItem,
        volume: Float
    ): BaGuideBgmPlaybackRuntimeState {
        val safeVolume = volume.coerceIn(0f, 1f)
        GuideBgmFavoritePlaybackStore.saveVolume(safeVolume)
        controller.updateVolume(safeVolume)
        return controller.runtimeState().copy(volume = safeVolume)
    }

    override fun applyQueueMode(
        favorite: GuideBgmFavoriteItem,
        queueMode: BaGuideBgmQueueMode
    ) {
        controller.updateRepeatMode(queueMode)
    }

    override fun runtimeState(favorite: GuideBgmFavoriteItem?): BaGuideBgmPlaybackRuntimeState {
        return controller.runtimeState()
    }

    override fun currentAudioUrl(): String {
        return controller.currentAudioUrl()
    }

    override fun currentQueueMode(): BaGuideBgmQueueMode? {
        return controller.currentQueueMode()
    }

    override fun disconnect() {
        controller.disconnect()
    }

    override fun stopSession() {
        controller.stopSession()
    }
}
