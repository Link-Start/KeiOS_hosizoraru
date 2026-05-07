package os.kei.ui.page.main.student

import android.app.PendingIntent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

internal const val BA_GUIDE_BGM_MEDIA_SESSION_ID = "ba_guide_bgm_media_session"

@OptIn(UnstableApi::class)
class BaGuideBgmMediaSessionService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val repeatModeListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            updateMediaButtonPreferences()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val sessionPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createGameKeeMediaSourceFactory(this))
            .build()
        sessionPlayer.addListener(repeatModeListener)
        player = sessionPlayer
        setMediaNotificationProvider(BaGuideBgmMediaNotificationProviderFactory.create(this))
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setId(BA_GUIDE_BGM_MEDIA_SESSION_ID)
            .setSessionActivity(createSessionActivity())
            .setMediaButtonPreferences(
                BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
                    context = this,
                    queueMode = BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(
                        sessionPlayer.repeatMode
                    )
                )
            )
            .setCallback(BaGuideBgmMediaSessionCallback(this))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.removeListener(repeatModeListener)
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun createSessionActivity(): PendingIntent {
        return BaGuideBgmPlaybackRouteIntentFactory.createPendingIntent(this)
    }

    private fun updateMediaButtonPreferences() {
        val session = mediaSession ?: return
        session.setMediaButtonPreferences(
            BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
                context = this,
                queueMode = BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(
                    session.player.repeatMode
                )
            )
        )
    }

    private class BaGuideBgmMediaSessionCallback(
        private val service: BaGuideBgmMediaSessionService
    ) : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    BaGuideBgmMediaButtonPreferences.availableSessionCommands(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    )
                )
                .setMediaButtonPreferences(
                    BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
                        context = service,
                        queueMode = BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(
                            session.player.repeatMode
                        )
                    )
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                BA_GUIDE_BGM_COMMAND_TOGGLE_REPEAT -> {
                    toggleRepeat(session)
                    successResult()
                }

                BA_GUIDE_BGM_COMMAND_STOP_PLAYBACK -> {
                    service.stopPlaybackAndDismiss()
                    successResult()
                }

                else -> Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
        }

        private fun toggleRepeat(session: MediaSession) {
            val currentMode = BaGuideBgmMediaButtonPreferences.queueModeFromNativeRepeatMode(
                session.player.repeatMode
            )
            val nextMode = BaGuideBgmMediaButtonPreferences.nextQueueMode(currentMode)
            session.player.repeatMode = BaGuideBgmMediaButtonPreferences.nativeRepeatMode(nextMode)
            val selectedAudioUrl = session.player.currentMediaItem
                ?.mediaId
                .orEmpty()
                .ifBlank { GuideBgmFavoritePlaybackStore.snapshot().selectedAudioUrl }
            GuideBgmFavoritePlaybackStore.saveSelection(
                audioUrl = selectedAudioUrl,
                queueModeName = nextMode.name
            )
            session.setMediaButtonPreferences(
                BaGuideBgmMediaButtonPreferences.mediaButtonPreferences(
                    context = service,
                    queueMode = nextMode
                )
            )
        }

        private fun successResult(): ListenableFuture<SessionResult> {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun stopPlaybackAndDismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopPlaybackAndDismissOnMain()
        } else {
            mainHandler.post { stopPlaybackAndDismissOnMain() }
        }
    }

    private fun stopPlaybackAndDismissOnMain() {
        player?.let { sessionPlayer ->
            runCatching { sessionPlayer.playWhenReady = false }
            runCatching { sessionPlayer.stop() }
            runCatching { sessionPlayer.clearMediaItems() }
        }
        runCatching { triggerNotificationUpdate() }
        runCatching { pauseAllPlayersAndStopSelf() }
    }
}
