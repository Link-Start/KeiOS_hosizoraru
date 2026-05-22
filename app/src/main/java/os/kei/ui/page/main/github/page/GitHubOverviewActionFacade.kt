package os.kei.ui.page.main.github.page

import kotlinx.coroutines.launch
import os.kei.ui.page.main.github.page.action.GitHubPageActionEnvironment
import os.kei.ui.page.main.github.section.GitHubOverviewEntry
import os.kei.ui.page.main.github.section.defaultGitHubOverviewEntries
import os.kei.ui.page.main.github.section.orDefaultGitHubOverviewEntries

internal class GitHubOverviewActionFacade(
    private val env: GitHubPageActionEnvironment,
) {
    private val state: GitHubPageState
        get() = env.state

    fun setOverviewExpanded(value: Boolean) {
        state.overviewExpanded = value
        env.scope.launch {
            env.repository.saveOverviewExpanded(value)
        }
    }

    fun openOverviewEntrySheet() {
        state.showOverviewEntrySheet = true
        state.overviewExpanded = true
        env.scope.launch {
            env.repository.saveOverviewExpanded(true)
        }
    }

    fun closeOverviewEntrySheet() {
        state.showOverviewEntrySheet = false
    }

    fun setOverviewEntryVisible(
        entry: GitHubOverviewEntry,
        visible: Boolean,
    ) {
        val current = state.overviewVisibleEntries.orDefaultGitHubOverviewEntries()
        val next =
            if (visible) {
                current + entry
            } else {
                (current - entry).ifEmpty { setOf(entry) }
            }
        state.overviewVisibleEntries = next
        env.scope.launch {
            env.repository.saveOverviewVisibleEntries(next)
        }
    }

    fun resetOverviewEntries() {
        val defaults = defaultGitHubOverviewEntries()
        state.overviewVisibleEntries = defaults
        env.scope.launch {
            env.repository.saveOverviewVisibleEntries(defaults)
        }
    }
}
