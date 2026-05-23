package os.kei.ui.page.main.student.page.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import os.kei.ui.page.main.student.GuideBottomTab

internal class BaStudentGuideChromeController {
    private val mutablePageChromeState = MutableStateFlow(BaStudentGuidePageChromeState())
    private val mutableVoiceUiState = MutableStateFlow(BaStudentGuideVoiceUiState())

    val pageChromeState: StateFlow<BaStudentGuidePageChromeState> =
        mutablePageChromeState.asStateFlow()
    val voiceUiState: StateFlow<BaStudentGuideVoiceUiState> =
        mutableVoiceUiState.asStateFlow()

    fun coerceSelectedBottomTab(bottomTabs: List<GuideBottomTab>) {
        val selectedOrdinal = mutablePageChromeState.value.selectedBottomTabOrdinal
        if (bottomTabs.any { it.ordinal == selectedOrdinal }) return
        mutablePageChromeState.update { state ->
            state.copy(
                selectedBottomTabOrdinal =
                    bottomTabs
                        .firstOrNull()
                        ?.ordinal
                        ?: GuideBottomTab.Archive.ordinal,
            )
        }
    }

    fun updateSelectedBottomTab(tab: GuideBottomTab) {
        mutablePageChromeState.update { state ->
            if (state.selectedBottomTabOrdinal == tab.ordinal) {
                state
            } else {
                state.copy(selectedBottomTabOrdinal = tab.ordinal)
            }
        }
    }

    fun updateSelectedVoiceLanguage(language: String) {
        mutablePageChromeState.update { state ->
            if (state.selectedVoiceLanguage == language) {
                state
            } else {
                state.copy(selectedVoiceLanguage = language)
            }
        }
    }

    fun updatePlayingVoiceUrl(url: String) {
        mutableVoiceUiState.update { state ->
            if (state.playingVoiceUrl == url) {
                state
            } else {
                state.copy(playingVoiceUrl = url)
            }
        }
    }

    fun updateIsVoicePlaying(isPlaying: Boolean) {
        mutableVoiceUiState.update { state ->
            if (state.isVoicePlaying == isPlaying) {
                state
            } else {
                state.copy(isVoicePlaying = isPlaying)
            }
        }
    }

    fun updateVoicePlayProgress(progress: Float) {
        mutableVoiceUiState.update { state ->
            state.withProgress(progress)
        }
    }

    fun resetForNewSource() {
        mutablePageChromeState.update { state -> state.resetForNewSource() }
        mutableVoiceUiState.update { state -> state.resetForNewSource() }
    }
}
