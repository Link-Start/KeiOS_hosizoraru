package os.kei.feature.github.data.local

import org.junit.Test
import kotlin.test.assertTrue

/**
 * Both store signals are StateFlows keyed on a version: two writes in the same millisecond must
 * still produce two distinct versions, or the second change is conflated away and never reaches
 * the page.
 */
class GitHubTrackStoreSignalsTest {
    @Test
    fun `notifyChanged keeps versions monotonic when timestamps match`() {
        val signals = listOf(
            "track store" to ({ GitHubTrackStoreSignals.version.value } to GitHubTrackStoreSignals::notifyChanged),
            "history unread" to
                ({ GitHubHistoryUnreadStoreSignals.version.value } to GitHubHistoryUnreadStoreSignals::notifyChanged),
        )

        signals.forEach { (label, signal) ->
            val (version, notifyChanged) = signal
            val previous = version()

            notifyChanged(previous)
            val first = version()
            notifyChanged(previous)
            val second = version()

            assertTrue(first > previous, label)
            assertTrue(second > first, label)
        }
    }
}
