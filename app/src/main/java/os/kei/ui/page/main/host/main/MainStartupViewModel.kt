package os.kei.ui.page.main.host.main

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger
import os.kei.core.prefs.UiPrefsSnapshot
import os.kei.feature.home.model.HomeOverviewSnapshot

/** Only local, first-frame data belongs here; network and permission checks keep their own lifecycles. */
@Immutable
internal data class MainStartupSnapshot(
    val preferences: UiPrefsSnapshot,
    val homeOverview: HomeOverviewSnapshot,
)

internal class MainStartupViewModel(
    load: suspend () -> MainStartupSnapshot,
    fallback: () -> MainStartupSnapshot,
) : ViewModel() {
    val snapshot: StateFlow<MainStartupSnapshot?>
        field = MutableStateFlow<MainStartupSnapshot?>(null)

    init {
        viewModelScope.launch {
            snapshot.value = try {
                load()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.w("MainStartup", "Initial local snapshot unavailable", error)
                fallback()
            }
        }
    }
}
