package os.kei.ui.page.main.github.page

import os.kei.ui.page.main.github.section.GitHubOverviewEntry
import os.kei.ui.page.main.github.section.GitHubOverviewUiStateStore
import os.kei.ui.page.main.github.section.defaultGitHubOverviewEntries
import os.kei.ui.page.main.github.section.orDefaultGitHubOverviewEntries

internal class GitHubOverviewActionFacade(
    private val state: GitHubPageState,
) {
    fun setOverviewExpanded(value: Boolean) {
        state.overviewExpanded = value
        GitHubOverviewUiStateStore.setExpanded(value)
    }

    fun openOverviewEntrySheet() {
        state.showOverviewEntrySheet = true
        state.overviewExpanded = true
        GitHubOverviewUiStateStore.setExpanded(true)
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
        GitHubOverviewUiStateStore.setVisibleEntries(next)
    }

    fun resetOverviewEntries() {
        val defaults = defaultGitHubOverviewEntries()
        state.overviewVisibleEntries = defaults
        GitHubOverviewUiStateStore.setVisibleEntries(defaults)
    }
}
