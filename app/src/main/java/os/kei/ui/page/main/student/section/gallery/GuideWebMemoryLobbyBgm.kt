@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.section.gallery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.ui.page.main.student.configureGuideMediaAudioBehavior
import os.kei.ui.page.main.student.createGameKeeMediaSourceFactory

/** Music has its own lifetime; chrome, actions, camera and WebView retries do not replace it. */
internal class GuideWebMemoryLobbyBgmController(private val player: Player, audioUrl: String) {
    private var lastRetryToken = 0

    init {
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(MediaItem.fromUri(audioUrl))
        player.prepare()
    }

    fun update(playing: Boolean, muted: Boolean, resumed: Boolean, retryToken: Int) {
        player.volume = if (muted) 0f else 1f
        // Muting also releases audio focus and avoids decoding inaudible music.
        player.playWhenReady = playing && resumed && !muted
        if (retryToken != lastRetryToken && player.playbackState == Player.STATE_IDLE) player.prepare()
        lastRetryToken = retryToken
    }

    fun release() = player.release()
}

@Composable
internal fun GuideWebMemoryLobbyBgm(audioUrl: String, playing: Boolean, muted: Boolean, retryToken: Int) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var resumed by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var controller by remember(audioUrl) { mutableStateOf<GuideWebMemoryLobbyBgmController?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(audioUrl, context) {
        if (audioUrl.isBlank()) return@DisposableEffect onDispose {}
        val player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(createGameKeeMediaSourceFactory(context.applicationContext))
            .build().apply { configureGuideMediaAudioBehavior() }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                context.showToast(R.string.guide_gallery_dynamic_lobby_bgm_failed)
            }
        }
        player.addListener(listener)
        val created = GuideWebMemoryLobbyBgmController(player, audioUrl)
        controller = created
        onDispose {
            player.removeListener(listener)
            created.release()
            controller = null
        }
    }
    LaunchedEffect(controller, playing, muted, resumed, retryToken) {
        controller?.update(playing, muted, resumed, retryToken)
    }
}
