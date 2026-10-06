package os.kei.ui.page.main.settings.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

@Stable
internal class SettingsPageUiState(
    private val chromeState: () -> SettingsPageChromeState,
    private val actions: () -> SettingsPageUiActions,
) {
    var showThemeModePopup: Boolean
        get() = chromeState().showThemeModePopup
        set(value) {
            actions().onShowThemeModePopupChange(value)
        }

    var showLauncherIconDesignPopup: Boolean
        get() = chromeState().showLauncherIconDesignPopup
        set(value) {
            actions().onShowLauncherIconDesignPopupChange(value)
        }

}

@Composable
internal fun rememberSettingsPageUiState(
    chromeState: SettingsPageChromeState,
    actions: SettingsPageUiActions,
): SettingsPageUiState {
    val currentChromeState = rememberUpdatedState(chromeState)
    val currentActions = rememberUpdatedState(actions)
    return remember {
        SettingsPageUiState(
            chromeState = { currentChromeState.value },
            actions = { currentActions.value },
        )
    }
}

@Stable
internal data class SettingsPageUiActions(
    val onShowThemeModePopupChange: (Boolean) -> Unit,
    val onShowLauncherIconDesignPopupChange: (Boolean) -> Unit,
)
