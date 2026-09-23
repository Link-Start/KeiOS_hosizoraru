package os.kei.ui.page.main.github.page

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import os.kei.feature.github.data.local.GitHubHistoryUnreadStoreSignals
import os.kei.feature.github.domain.GitHubHistoryUnreadService
import kotlin.test.assertEquals

/**
 * Reading history must clear the GitHub page's badge without anything else refreshing it.
 *
 * The page ViewModel is long-lived and the history pages are not: they move a bucket's watermark and
 * go away. The badge only followed if the ViewModel listened for the watermark signal, and when it did
 * not, the dock kept showing the count from before the history was read.
 *
 * The watermark itself lives in MMKV, which has no JVM build, so the store's write is stood in for by
 * the two things it does: the unread count the next load sees changes, and
 * `GitHubHistoryUnreadStoreSignals.notifyChanged()` is published, exactly as
 * `GitHubHistoryUnreadStore.markRead` does. The signal is read through the real service, the same flow
 * `GitHubPageRepository.historyUnreadSignalVersions()` hands the ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitHubHistoryUnreadSynchronizationTest {
    @Test
    fun aWatermarkChangeRefreshesTheLongLivedBadgeWithoutAnExplicitRefresh() =
        runTest {
            var unreadInStore = 3
            val badge =
                GitHubHistoryUnreadBadge(
                    scope = backgroundScope,
                    signalVersions = GitHubHistoryUnreadService().signalVersions(),
                    loadCount = { unreadInStore },
                )
            runCurrent()
            assertEquals(3, badge.count.value, "the badge starts from the stored count")

            // A history page marks its bucket read.
            unreadInStore = 0
            GitHubHistoryUnreadStoreSignals.notifyChanged()
            runCurrent()

            assertEquals(0, badge.count.value, "a watermark change must reach the page badge")

            // And a later change, not just the first one.
            unreadInStore = 2
            GitHubHistoryUnreadStoreSignals.notifyChanged()
            runCurrent()

            assertEquals(2, badge.count.value)
        }

    @Test
    fun aFailedLoadKeepsTheCountTheBadgeAlreadyShows() =
        runTest {
            var failLoad = false
            val badge =
                GitHubHistoryUnreadBadge(
                    scope = backgroundScope,
                    signalVersions = GitHubHistoryUnreadService().signalVersions(),
                    loadCount = { if (failLoad) error("history store unavailable") else 4 },
                )
            runCurrent()

            failLoad = true
            GitHubHistoryUnreadStoreSignals.notifyChanged()
            runCurrent()

            assertEquals(4, badge.count.value)
        }
}
