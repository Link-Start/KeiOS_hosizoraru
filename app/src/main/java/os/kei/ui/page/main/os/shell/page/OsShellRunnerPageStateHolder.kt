package os.kei.ui.page.main.os.shell.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntRect
import os.kei.ui.page.main.os.shell.OsShellRunnerPageChromeState

@Stable
internal class OsShellRunnerPageStateHolder(
    private val chromeState: () -> OsShellRunnerPageChromeState,
    private val actions: () -> OsShellRunnerPageChromeActions,
) {
    val startupFocusRequestToken: Int
        get() = chromeState().startupFocusRequestToken

    val startupFocusApplied: Boolean
        get() = chromeState().startupFocusApplied

    var showSaveSheet: Boolean
        get() = chromeState().showSaveSheet
        set(value) {
            actions().onShowSaveSheetChange(value)
        }

    var showBehaviorSettingsSheet: Boolean
        get() = chromeState().showBehaviorSettingsSheet
        set(value) {
            actions().onShowBehaviorSettingsSheetChange(value)
        }

    var showOutputSettingsSheet: Boolean
        get() = chromeState().showOutputSettingsSheet
        set(value) {
            actions().onShowOutputSettingsSheetChange(value)
        }

    val showDangerousCommandConfirm: Boolean
        get() = chromeState().showDangerousCommandConfirm

    val pendingDangerousCommand: String
        get() = chromeState().pendingDangerousCommand

    var saveTitleInput: String
        get() = chromeState().saveTitleInput
        set(value) {
            actions().onSaveTitleInputChange(value)
        }

    var saveSubtitleInput: String
        get() = chromeState().saveSubtitleInput
        set(value) {
            actions().onSaveSubtitleInputChange(value)
        }

    val saveInitialSubtitleInput: String
        get() = chromeState().saveInitialSubtitleInput

    val timeoutDropdownExpanded: Boolean
        get() = chromeState().timeoutDropdownExpanded

    val timeoutDropdownAnchorBounds: IntRect?
        get() = chromeState().timeoutDropdownAnchorBounds

    val outputLimitDropdownExpanded: Boolean
        get() = chromeState().outputLimitDropdownExpanded

    val outputLimitDropdownAnchorBounds: IntRect?
        get() = chromeState().outputLimitDropdownAnchorBounds

    fun requestStartupFocus() {
        actions().onRequestStartupFocus()
    }

    fun openSaveSheet(suggestedSubtitle: String) {
        actions().onOpenSaveSheet(suggestedSubtitle)
    }

    fun resetSaveSheetInputs() {
        actions().onResetSaveSheetInputs()
    }

    fun openDangerousCommandConfirm(command: String) {
        actions().onOpenDangerousCommandConfirm(command)
    }

    fun dismissDangerousCommandConfirm() {
        actions().onDismissDangerousCommandConfirm()
    }

    fun updateTimeoutDropdownExpanded(expanded: Boolean) {
        actions().onTimeoutDropdownExpandedChange(expanded)
    }

    fun updateTimeoutDropdownAnchorBounds(bounds: IntRect?) {
        actions().onTimeoutDropdownAnchorBoundsChange(bounds)
    }

    fun updateOutputLimitDropdownExpanded(expanded: Boolean) {
        actions().onOutputLimitDropdownExpandedChange(expanded)
    }

    fun updateOutputLimitDropdownAnchorBounds(bounds: IntRect?) {
        actions().onOutputLimitDropdownAnchorBoundsChange(bounds)
    }

    fun consumeCloseCleanupRequest(): Boolean = actions().onConsumeCloseCleanupRequest()
}

@Stable
internal data class OsShellRunnerPageChromeActions(
    val onRequestStartupFocus: () -> Unit,
    val onOpenSaveSheet: (String) -> Unit,
    val onShowSaveSheetChange: (Boolean) -> Unit,
    val onShowBehaviorSettingsSheetChange: (Boolean) -> Unit,
    val onShowOutputSettingsSheetChange: (Boolean) -> Unit,
    val onSaveTitleInputChange: (String) -> Unit,
    val onSaveSubtitleInputChange: (String) -> Unit,
    val onResetSaveSheetInputs: () -> Unit,
    val onOpenDangerousCommandConfirm: (String) -> Unit,
    val onDismissDangerousCommandConfirm: () -> Unit,
    val onTimeoutDropdownExpandedChange: (Boolean) -> Unit,
    val onTimeoutDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    val onOutputLimitDropdownExpandedChange: (Boolean) -> Unit,
    val onOutputLimitDropdownAnchorBoundsChange: (IntRect?) -> Unit,
    val onConsumeCloseCleanupRequest: () -> Boolean,
)

@Composable
internal fun rememberOsShellRunnerPageStateHolder(
    chromeState: OsShellRunnerPageChromeState,
    chromeActions: OsShellRunnerPageChromeActions,
): OsShellRunnerPageStateHolder {
    val currentChromeState = rememberUpdatedState(chromeState)
    val currentChromeActions = rememberUpdatedState(chromeActions)
    return remember {
        OsShellRunnerPageStateHolder(
            chromeState = { currentChromeState.value },
            actions = { currentChromeActions.value },
        )
    }
}
