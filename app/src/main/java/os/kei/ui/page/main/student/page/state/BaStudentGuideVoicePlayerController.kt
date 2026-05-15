package os.kei.ui.page.main.student.page.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import os.kei.ui.page.main.student.createGameKeeMediaSourceFactory

@Stable
internal class BaStudentGuideVoicePlayerController(
    private val appContext: Context
) {
    var player: ExoPlayer? by mutableStateOf(null)
        private set

    fun getOrCreate(): ExoPlayer {
        player?.let { return it }
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(createGameKeeMediaSourceFactory(appContext))
            .build()
            .also { player = it }
    }

    fun pause() {
        runCatching { player?.pause() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
    }
}

@Composable
internal fun rememberBaStudentGuideVoicePlayerController(
    sourceUrl: String
): BaStudentGuideVoicePlayerController {
    val appContext = LocalContext.current.applicationContext
    return remember(appContext, sourceUrl) {
        BaStudentGuideVoicePlayerController(appContext)
    }
}
