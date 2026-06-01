package os.kei.ui.page.main.settings.page

import kotlinx.coroutines.Job

internal fun settingsPagerSwitchDurationMillis(distance: Int): Int = (100 * distance.coerceAtLeast(1) + 100).coerceIn(180, 420)

internal class SettingsTabJumpCoordinator {
    private var job: Job? = null

    fun launch(block: () -> Job) {
        job?.cancel()
        job = block()
    }
}
