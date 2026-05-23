package os.kei.ui.page.main.student.page.state

import androidx.compose.runtime.Immutable
import os.kei.ui.page.main.student.GuideBottomTab
import kotlin.math.abs

@Immutable
internal data class BaStudentGuidePageChromeState(
    val selectedBottomTabOrdinal: Int = GuideBottomTab.Archive.ordinal,
    val selectedVoiceLanguage: String = "",
)

@Immutable
internal data class BaStudentGuideVoiceUiState(
    val playingVoiceUrl: String = "",
    val isVoicePlaying: Boolean = false,
    val voicePlayProgress: Float = 0f,
)

internal fun BaStudentGuidePageChromeState.resetForNewSource(): BaStudentGuidePageChromeState = BaStudentGuidePageChromeState()

internal fun BaStudentGuideVoiceUiState.resetForNewSource(): BaStudentGuideVoiceUiState = BaStudentGuideVoiceUiState()

internal fun BaStudentGuideVoiceUiState.withProgress(nextProgress: Float): BaStudentGuideVoiceUiState {
    val coerced = nextProgress.coerceIn(0f, 1f)
    return if (abs(voicePlayProgress - coerced) < VOICE_PROGRESS_EMISSION_THRESHOLD) {
        this
    } else {
        copy(voicePlayProgress = coerced)
    }
}

private const val VOICE_PROGRESS_EMISSION_THRESHOLD = 0.0025f
