package os.kei.ui.page.main.student

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers

internal interface BaGuideBgmPlaybackClock {
    fun nowMs(): Long
}

internal object BaGuideBgmSystemPlaybackClock : BaGuideBgmPlaybackClock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

internal class BaGuideBgmPlaybackRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.media,
    private val clock: BaGuideBgmPlaybackClock = BaGuideBgmSystemPlaybackClock,
) {
    @Volatile
    private var cachedSnapshot: GuideBgmFavoritePlaybackSnapshot = GuideBgmFavoritePlaybackSnapshot.Empty

    suspend fun loadSnapshot(): GuideBgmFavoritePlaybackSnapshot =
        withContext(ioDispatcher) {
            GuideBgmFavoritePlaybackStore.snapshot()
        }.also { snapshot ->
            cachedSnapshot = snapshot
        }

    fun volume(): Float = cachedSnapshot.volume.coerceIn(0f, 1f)

    fun selectedAudioUrl(): String = cachedSnapshot.selectedAudioUrl

    suspend fun saveSelection(
        audioUrl: String,
        queueModeName: String,
    ) {
        cachedSnapshot =
            cachedSnapshot.copy(
                selectedAudioUrl = normalizeGuideMediaSource(audioUrl),
                queueModeName = queueModeName.trim(),
            )
        withContext(ioDispatcher) {
            GuideBgmFavoritePlaybackStore.saveSelection(audioUrl, queueModeName)
        }
    }

    suspend fun saveVolume(value: Float) {
        cachedSnapshot = cachedSnapshot.copy(volume = value.coerceIn(0f, 1f))
        withContext(ioDispatcher) {
            GuideBgmFavoritePlaybackStore.saveVolume(value)
        }
    }

    suspend fun saveProgress(
        audioUrl: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        nowMs: Long = clock.nowMs(),
    ) {
        updateCachedProgress(
            audioUrl = audioUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
            nowMs = nowMs,
        )
        withContext(ioDispatcher) {
            GuideBgmFavoritePlaybackStore.saveProgress(
                audioUrl = audioUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                nowMs = nowMs,
            )
        }
    }

    private fun updateCachedProgress(
        audioUrl: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        nowMs: Long,
    ) {
        val normalizedAudioUrl = normalizeGuideMediaSource(audioUrl)
        if (normalizedAudioUrl.isBlank()) return
        val safeDuration = durationMs.coerceAtLeast(0L)
        val safePosition =
            if (safeDuration > 0L) {
                positionMs.coerceIn(0L, safeDuration)
            } else {
                positionMs.coerceAtLeast(0L)
            }
        val previous = cachedSnapshot.progressFor(normalizedAudioUrl)
        val progress =
            GuideBgmFavoritePlaybackProgress(
                audioUrl = normalizedAudioUrl,
                positionMs = safePosition,
                durationMs = safeDuration,
                updatedAtMs = nowMs.coerceAtLeast(1L),
                lastPlayedAtMs =
                    if (isPlaying) {
                        nowMs.coerceAtLeast(1L)
                    } else {
                        previous?.lastPlayedAtMs ?: 0L
                    },
            )
        cachedSnapshot =
            cachedSnapshot.copy(
                progressByAudioUrl = cachedSnapshot.progressByAudioUrl + (normalizedAudioUrl to progress),
            )
    }
}
