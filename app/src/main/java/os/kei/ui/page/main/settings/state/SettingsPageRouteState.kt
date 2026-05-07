package os.kei.ui.page.main.settings.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import os.kei.ui.page.main.state.PageRouteState

@Immutable
internal data class SettingsPageRouteState(
    val cacheState: SettingsCacheUiState,
    val logState: SettingsLogUiState
) : PageRouteState

@Composable
internal fun rememberSettingsPageRouteState(
    cacheState: SettingsCacheUiState,
    logState: SettingsLogUiState
): SettingsPageRouteState {
    return remember(cacheState, logState) {
        SettingsPageRouteState(
            cacheState = cacheState,
            logState = logState
        )
    }
}
