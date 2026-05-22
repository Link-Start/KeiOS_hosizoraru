package os.kei.ui.page.main.os.shell.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class OsShellRunnerPageStateHolder(
    private val startupFocusRequestTokenState: MutableIntState,
    private val startupFocusAppliedState: MutableState<Boolean>,
    private val showSaveSheetState: MutableState<Boolean>,
    private val showBehaviorSettingsSheetState: MutableState<Boolean>,
    private val showOutputSettingsSheetState: MutableState<Boolean>,
    private val showDangerousCommandConfirmState: MutableState<Boolean>,
    private val pendingDangerousCommandState: MutableState<String>,
    private val saveTitleInputState: MutableState<String>,
    private val saveSubtitleInputState: MutableState<String>,
    private val saveInitialSubtitleInputState: MutableState<String>,
) {
    var startupFocusRequestToken: Int
        get() = startupFocusRequestTokenState.intValue
        private set(value) {
            startupFocusRequestTokenState.intValue = value
        }

    var startupFocusApplied: Boolean
        get() = startupFocusAppliedState.value
        private set(value) {
            startupFocusAppliedState.value = value
        }

    var showSaveSheet: Boolean
        get() = showSaveSheetState.value
        set(value) {
            showSaveSheetState.value = value
        }

    var showBehaviorSettingsSheet: Boolean
        get() = showBehaviorSettingsSheetState.value
        set(value) {
            showBehaviorSettingsSheetState.value = value
        }

    var showOutputSettingsSheet: Boolean
        get() = showOutputSettingsSheetState.value
        set(value) {
            showOutputSettingsSheetState.value = value
        }

    var showDangerousCommandConfirm: Boolean
        get() = showDangerousCommandConfirmState.value
        set(value) {
            showDangerousCommandConfirmState.value = value
        }

    var pendingDangerousCommand: String
        get() = pendingDangerousCommandState.value
        set(value) {
            pendingDangerousCommandState.value = value
        }

    var saveTitleInput: String
        get() = saveTitleInputState.value
        set(value) {
            saveTitleInputState.value = value
        }

    var saveSubtitleInput: String
        get() = saveSubtitleInputState.value
        set(value) {
            saveSubtitleInputState.value = value
        }

    var saveInitialSubtitleInput: String
        get() = saveInitialSubtitleInputState.value
        set(value) {
            saveInitialSubtitleInputState.value = value
        }

    var closeCleanupApplied by mutableStateOf(false)

    fun requestStartupFocus() {
        startupFocusRequestToken += 1
        startupFocusApplied = true
    }

    fun openSaveSheet(suggestedSubtitle: String) {
        saveTitleInput = ""
        saveSubtitleInput = suggestedSubtitle
        saveInitialSubtitleInput = suggestedSubtitle
        showSaveSheet = true
    }

    fun resetSaveSheetInputs() {
        saveTitleInput = ""
        saveSubtitleInput = ""
        saveInitialSubtitleInput = ""
    }

    fun openDangerousCommandConfirm(command: String) {
        pendingDangerousCommand = command
        showDangerousCommandConfirm = true
    }

    fun dismissDangerousCommandConfirm() {
        showDangerousCommandConfirm = false
        pendingDangerousCommand = ""
    }
}

@Composable
internal fun rememberOsShellRunnerPageStateHolder(): OsShellRunnerPageStateHolder {
    val startupFocusRequestTokenState = rememberSaveable { mutableIntStateOf(0) }
    val startupFocusAppliedState = rememberSaveable { mutableStateOf(false) }
    val showSaveSheetState = rememberSaveable { mutableStateOf(false) }
    val showBehaviorSettingsSheetState = rememberSaveable { mutableStateOf(false) }
    val showOutputSettingsSheetState = rememberSaveable { mutableStateOf(false) }
    val showDangerousCommandConfirmState = rememberSaveable { mutableStateOf(false) }
    val pendingDangerousCommandState = rememberSaveable { mutableStateOf("") }
    val saveTitleInputState = rememberSaveable { mutableStateOf("") }
    val saveSubtitleInputState = rememberSaveable { mutableStateOf("") }
    val saveInitialSubtitleInputState = rememberSaveable { mutableStateOf("") }
    return remember(
        startupFocusRequestTokenState,
        startupFocusAppliedState,
        showSaveSheetState,
        showBehaviorSettingsSheetState,
        showOutputSettingsSheetState,
        showDangerousCommandConfirmState,
        pendingDangerousCommandState,
        saveTitleInputState,
        saveSubtitleInputState,
        saveInitialSubtitleInputState,
    ) {
        OsShellRunnerPageStateHolder(
            startupFocusRequestTokenState = startupFocusRequestTokenState,
            startupFocusAppliedState = startupFocusAppliedState,
            showSaveSheetState = showSaveSheetState,
            showBehaviorSettingsSheetState = showBehaviorSettingsSheetState,
            showOutputSettingsSheetState = showOutputSettingsSheetState,
            showDangerousCommandConfirmState = showDangerousCommandConfirmState,
            pendingDangerousCommandState = pendingDangerousCommandState,
            saveTitleInputState = saveTitleInputState,
            saveSubtitleInputState = saveSubtitleInputState,
            saveInitialSubtitleInputState = saveInitialSubtitleInputState,
        )
    }
}
