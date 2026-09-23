package os.kei.ui.page.main.settings.page

import kotlinx.coroutines.Job

/**
 * Length of a settings category switch. The floor matches the main pager: an
 * adjacent switch used to run 200ms here against the main pager's 300ms, so the same gesture felt
 * abruptly quicker inside Settings than everywhere else.
 */
internal class SettingsTabJumpCoordinator {
    private var job: Job? = null

    fun launch(block: () -> Job) {
        job?.cancel()
        job = block()
    }
}
