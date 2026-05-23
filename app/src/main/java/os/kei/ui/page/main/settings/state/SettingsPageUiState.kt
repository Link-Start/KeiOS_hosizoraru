package os.kei.ui.page.main.settings.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.IntRect

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

    var themePopupAnchorBounds: IntRect?
        get() = chromeState().themePopupAnchorBounds
        set(value) {
            actions().onThemePopupAnchorBoundsChange(value)
        }

    var showLauncherIconDesignPopup: Boolean
        get() = chromeState().showLauncherIconDesignPopup
        set(value) {
            actions().onShowLauncherIconDesignPopupChange(value)
        }

    var launcherIconDesignPopupAnchorBounds: IntRect?
        get() = chromeState().launcherIconDesignPopupAnchorBounds
        set(value) {
            actions().onLauncherIconDesignPopupAnchorBoundsChange(value)
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
    val onThemePopupAnchorBoundsChange: (IntRect?) -> Unit,
    val onShowLauncherIconDesignPopupChange: (Boolean) -> Unit,
    val onLauncherIconDesignPopupAnchorBoundsChange: (IntRect?) -> Unit,
)
