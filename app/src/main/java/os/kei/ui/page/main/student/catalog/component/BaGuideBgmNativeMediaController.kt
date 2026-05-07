package os.kei.ui.page.main.student.catalog.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import os.kei.ui.page.main.student.BaGuideBgmMediaSessionService
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import os.kei.ui.page.main.student.normalizeGuideMediaSource

internal class BaGuideBgmNativeMediaController(context: Context) {
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val lock = Any()
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()

    @Volatile
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun syncQueue(
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long,
        playWhenReady: Boolean? = null,
        restart: Boolean = false
    ) {
        val mediaItems = queue.toBaGuideBgmMediaItems(appContext)
        if (mediaItems.isEmpty()) return
        val targetMediaId = normalizeGuideMediaSource(selectedAudioUrl)
            .takeIf { it.isNotBlank() }
            ?: mediaItems.first().mediaId
        val targetIndex = mediaItems.indexOfFirst { it.mediaId == targetMediaId }
            .takeIf { it >= 0 }
            ?: 0
        val safeStartPositionMs = if (restart) 0L else startPositionMs.coerceAtLeast(0L)
        submit { mediaController ->
            val currentIds = (0 until mediaController.mediaItemCount)
                .map { index -> mediaController.getMediaItemAt(index).mediaId }
            val nextIds = mediaItems.map { item -> item.mediaId }
            val currentMediaId = mediaController.currentMediaItem?.mediaId.orEmpty()
            val preservedIndex = currentIds.indexOf(currentMediaId)
                .takeIf { index ->
                    playWhenReady == null &&
                            !restart &&
                            index >= 0 &&
                            currentMediaId in nextIds
                }
            val resolvedIndex = preservedIndex ?: targetIndex
            val resolvedStartPositionMs = if (preservedIndex != null) {
                mediaController.currentPosition.coerceAtLeast(0L)
            } else {
                safeStartPositionMs
            }
            if (
                currentIds != nextIds ||
                currentMediaId != mediaItems[resolvedIndex].mediaId ||
                restart ||
                mediaController.playbackState == Player.STATE_IDLE
            ) {
                mediaController.setMediaItems(mediaItems, resolvedIndex, resolvedStartPositionMs)
                mediaController.prepare()
            }
            mediaController.repeatMode = queueMode.toNativeRepeatMode()
            mediaController.volume = GuideBgmFavoritePlaybackStore.volume()
            when (playWhenReady) {
                true -> mediaController.play()
                false -> mediaController.pause()
                null -> Unit
            }
        }
    }

    fun pause() {
        submit { mediaController -> mediaController.pause() }
    }

    fun seekToProgress(progress: Float) {
        submit { mediaController ->
            val duration = mediaController.duration.coerceAtLeast(0L)
            if (duration <= 0L) return@submit
            val position = (duration * progress.coerceIn(0f, 1f))
                .toLong()
                .coerceIn(0L, duration)
            mediaController.seekTo(position)
        }
    }

    fun updateVolume(volume: Float) {
        val safeVolume = volume.coerceIn(0f, 1f)
        submit { mediaController -> mediaController.volume = safeVolume }
    }

    fun updateRepeatMode(queueMode: BaGuideBgmQueueMode) {
        submit { mediaController -> mediaController.repeatMode = queueMode.toNativeRepeatMode() }
    }

    fun runtimeState(): BaGuideBgmPlaybackRuntimeState {
        val mediaController = controller ?: return BaGuideBgmPlaybackRuntimeState(
            volume = GuideBgmFavoritePlaybackStore.volume()
        )
        return runCatching {
            val duration = mediaController.duration.coerceAtLeast(0L)
            val position = mediaController.currentPosition.coerceAtLeast(0L)
            BaGuideBgmPlaybackRuntimeState(
                positionMs = if (duration > 0L) position.coerceAtMost(duration) else position,
                durationMs = duration,
                isPlaying = mediaController.isPlaying,
                isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
                isEnded = mediaController.playbackState == Player.STATE_ENDED,
                volume = mediaController.volume.coerceIn(0f, 1f)
            )
        }.getOrDefault(
            BaGuideBgmPlaybackRuntimeState(volume = GuideBgmFavoritePlaybackStore.volume())
        )
    }

    fun currentAudioUrl(): String {
        val mediaController = controller ?: return ""
        return runCatching {
            normalizeGuideMediaSource(mediaController.currentMediaItem?.mediaId.orEmpty())
        }.getOrDefault("")
    }

    fun stopSession() {
        controller?.let { mediaController ->
            mainExecutor.execute {
                runCatching { mediaController.pause() }
                runCatching { mediaController.stop() }
                runCatching { mediaController.clearMediaItems() }
            }
        }
        disconnect()
        runCatching {
            appContext.stopService(Intent(appContext, BaGuideBgmMediaSessionService::class.java))
        }
    }

    fun disconnect() {
        val futureToCancel: ListenableFuture<MediaController>?
        val controllerToRelease: MediaController?
        synchronized(lock) {
            pendingCommands.clear()
            futureToCancel = controllerFuture
            controllerFuture = null
            controllerToRelease = controller
            controller = null
        }
        controllerToRelease?.let { mediaController ->
            mainExecutor.execute {
                runCatching { mediaController.release() }
            }
        }
        futureToCancel?.cancel(true)
    }

    private fun submit(command: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null) {
            mainExecutor.execute { runCatching { command(existing) } }
            return
        }
        synchronized(lock) {
            controller?.let { connected ->
                mainExecutor.execute { runCatching { command(connected) } }
                return
            }
            pendingCommands.add(command)
            ensureControllerLocked()
        }
    }

    private fun ensureControllerLocked() {
        if (controllerFuture != null) return
        val token = SessionToken(
            appContext,
            ComponentName(appContext, BaGuideBgmMediaSessionService::class.java)
        )
        controllerFuture = MediaController.Builder(appContext, token)
            .setApplicationLooper(appContext.mainLooper)
            .buildAsync()
            .also { future ->
                future.addListener(
                    {
                        val connected = runCatching { future.get() }.getOrNull()
                        if (connected == null) {
                            synchronized(lock) {
                                if (controllerFuture == future) controllerFuture = null
                                pendingCommands.clear()
                            }
                            return@addListener
                        }
                        val commands = synchronized(lock) {
                            if (controllerFuture == future) controllerFuture = null
                            controller = connected
                            buildList {
                                while (pendingCommands.isNotEmpty()) {
                                    add(pendingCommands.removeFirst())
                                }
                            }
                        }
                        commands.forEach { command ->
                            runCatching { command(connected) }
                        }
                    },
                    mainExecutor
                )
            }
    }

    private fun BaGuideBgmQueueMode.toNativeRepeatMode(): Int {
        return if (this == BaGuideBgmQueueMode.SingleLoop) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_ALL
        }
    }
}
