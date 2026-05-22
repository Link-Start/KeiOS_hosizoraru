package os.kei.ui.page.main.student

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

internal class GuideGalleryAudioPlaybackRepository(
    private val context: Context,
    private val scopeKey: String,
    private val audioUrl: String,
) {
    fun existingPlayer(): ExoPlayer? =
        GuideBgmPlayerStore.getExisting(
            scopeKey = scopeKey,
            audioUrl = audioUrl,
        )

    fun isLoopEnabled(): Boolean =
        audioUrl.isNotBlank() &&
            GuideBgmLoopStore.isEnabled(
                scopeKey = scopeKey,
                audioUrl = audioUrl,
            )

    fun setLoopEnabled(enabled: Boolean) {
        GuideBgmLoopStore.setEnabled(
            scopeKey = scopeKey,
            audioUrl = audioUrl,
            enabled = enabled,
        )
    }

    fun getOrCreatePlayer(): ExoPlayer? =
        GuideBgmPlayerStore.getOrCreate(
            context = context,
            scopeKey = scopeKey,
            audioUrl = audioUrl,
        )

    fun pauseScopeExcept() {
        GuideBgmPlayerStore.pauseScopeExcept(
            scopeKey = scopeKey,
            audioUrl = audioUrl,
        )
    }
}
