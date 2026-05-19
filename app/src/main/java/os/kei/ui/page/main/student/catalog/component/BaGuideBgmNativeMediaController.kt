package os.kei.ui.page.main.student.catalog.component

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.BaGuideBgmMediaButtonPreferences
import os.kei.ui.page.main.student.BaGuideBgmMediaSessionService
import os.kei.ui.page.main.student.GuideBgmFavoriteItem
import os.kei.ui.page.main.student.GuideBgmFavoritePlaybackStore
import os.kei.ui.page.main.student.normalizeGuideMediaSource

internal class BaGuideBgmNativeMediaController(
    context: Context,
    artworkDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val appContext = context.applicationContext
    private val mainExecutor = appContext.mainExecutor
    private val artworkScope = CoroutineScope(SupervisorJob() + artworkDispatcher)
    private val lock = Any()
    private val pendingCommands = ArrayDeque<(MediaController) -> Unit>()

    @Volatile
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var artworkHydrationJob: Job? = null
    private var artworkHydrationGeneration = 0L

    fun syncQueue(
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String,
        queueMode: BaGuideBgmQueueMode,
        startPositionMs: Long,
        playWhenReady: Boolean? = null,
        restart: Boolean = false
    ) {
        val artworkGeneration = nextArtworkHydrationGeneration()
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
            mediaController.repeatMode =
                BaGuideBgmMediaButtonPreferences.nativeRepeatMode(queueMode)
            mediaController.volume = GuideBgmFavoritePlaybackStore.volume()
            when (playWhenReady) {
                true -> mediaController.play()
                false -> mediaController.pause()
                null -> Unit
            }
        }
        scheduleArtworkHydration(
            generation = artworkGeneration,
            queue = queue,
            selectedAudioUrl = selectedAudioUrl
        )
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
        submit { mediaController ->
            mediaController.repeatMode =
                BaGuideBgmMediaButtonPreferences.nativeRepeatMode(queueMode)
        }
    }

    fun currentQueueMode(): BaGuideBgmQueueMode? {
        val mediaController = controller ?: return null
        return runCatching {
            BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(mediaController.repeatMode)
        }.getOrNull()
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
        cancelArtworkHydration()
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

    private fun nextArtworkHydrationGeneration(): Long {
        return synchronized(lock) {
            artworkHydrationJob?.cancel()
            artworkHydrationJob = null
            artworkHydrationGeneration += 1L
            artworkHydrationGeneration
        }
    }

    private fun cancelArtworkHydration() {
        synchronized(lock) {
            artworkHydrationGeneration += 1L
            artworkHydrationJob?.cancel()
            artworkHydrationJob = null
        }
    }

    private fun scheduleArtworkHydration(
        generation: Long,
        queue: List<GuideBgmFavoriteItem>,
        selectedAudioUrl: String
    ) {
        val targets = queue.orderedArtworkHydrationTargets(selectedAudioUrl)
        if (targets.isEmpty()) return
        val job = artworkScope.launch {
            targets.forEach { favorite ->
                if (!isArtworkHydrationCurrent(generation)) return@launch
                val mediaId = normalizeGuideMediaSource(favorite.audioUrl)
                val artworkData =
                    BaGuideBgmMediaArtworkPayloadResolver.resolve(appContext, favorite)
                        ?: return@forEach
                if (!isArtworkHydrationCurrent(generation)) return@launch
                submit { mediaController ->
                    if (!isArtworkHydrationCurrent(generation)) return@submit
                    mediaController.replaceArtworkMetadata(mediaId, artworkData)
                }
            }
        }
        synchronized(lock) {
            if (artworkHydrationGeneration == generation) {
                artworkHydrationJob = job
            } else {
                job.cancel()
            }
        }
    }

    private fun isArtworkHydrationCurrent(generation: Long): Boolean {
        return synchronized(lock) {
            artworkHydrationGeneration == generation
        }
    }

    private fun MediaController.replaceArtworkMetadata(
        mediaId: String,
        artworkData: ByteArray
    ) {
        val index = (0 until mediaItemCount)
            .firstOrNull { itemIndex -> getMediaItemAt(itemIndex).mediaId == mediaId }
            ?: return
        val currentItem = getMediaItemAt(index)
        if (currentItem.mediaMetadata.artworkData?.contentEquals(artworkData) == true) return
        replaceMediaItem(index, currentItem.withArtworkData(artworkData))
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

}

private const val MAX_NATIVE_ARTWORK_HYDRATION_TARGETS = 8

internal fun List<GuideBgmFavoriteItem>.orderedArtworkHydrationTargets(
    selectedAudioUrl: String,
    maxCount: Int = MAX_NATIVE_ARTWORK_HYDRATION_TARGETS
): List<GuideBgmFavoriteItem> {
    if (isEmpty() || maxCount <= 0) return emptyList()
    val selectedMediaId = normalizeGuideMediaSource(selectedAudioUrl)
    val selectedIndex = indexOfFirst { favorite ->
        normalizeGuideMediaSource(favorite.audioUrl) == selectedMediaId
    }.takeIf { it >= 0 } ?: 0
    return indices
        .asSequence()
        .map { offset -> (selectedIndex + offset) % size }
        .map { index -> this[index] }
        .distinctBy { favorite -> normalizeGuideMediaSource(favorite.audioUrl) }
        .take(maxCount)
        .toList()
}

private fun MediaItem.withArtworkData(artworkData: ByteArray): MediaItem {
    val metadata = mediaMetadata.buildUpon()
        .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        .build()
    return buildUpon()
        .setMediaMetadata(metadata)
        .build()
}
