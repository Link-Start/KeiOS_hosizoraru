package os.kei.feature.github.data.local

import org.junit.Test
import kotlin.test.assertTrue

class GitHubTrackStoreSignalsTest {
    @Test
    fun `notifyChanged keeps versions monotonic when timestamps match`() {
        val previous = GitHubTrackStoreSignals.version.value

        GitHubTrackStoreSignals.notifyChanged(previous)
        val first = GitHubTrackStoreSignals.version.value
        GitHubTrackStoreSignals.notifyChanged(previous)
        val second = GitHubTrackStoreSignals.version.value

        assertTrue(first > previous)
        assertTrue(second > first)
    }
}
