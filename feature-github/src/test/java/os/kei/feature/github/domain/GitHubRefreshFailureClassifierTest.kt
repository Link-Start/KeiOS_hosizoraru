package os.kei.feature.github.domain

import java.io.IOException
import kotlin.test.assertEquals
import org.junit.Test
import os.kei.core.io.BoundedContentReadLimitStage
import os.kei.core.io.BoundedContentTextReadTooLargeException

/**
 * The classifier has to find the informative exception, not merely the nearest one.
 *
 * `response_too_large` and the limit/declared/observed/stage row it drives were unreachable in the
 * refresh history for as long as this walked `cause` alone: `FdroidBatchPackageSnapshotProvider`
 * reports a combined message when both its halves fail and can only keep one of them as the cause,
 * and the one it keeps is the repository fallback's. The bounded-read exception from the other half
 * survived as text, which no classifier reads, so an oversized F-Droid response came out `unknown`.
 *
 * Measured before the fix, on the API 37 AVD against a loopback server declaring 99999999 bytes: the
 * failure message said `content text exceeds 8388608 bytes (stage=DeclaredLength, observed=99999999,
 * declared=99999999)` while the category pill said "Unknown failure" and no size row was drawn.
 */
class GitHubRefreshFailureClassifierTest {
    @Test
    fun aBoundedReadOnTheCauseChainIsStillClassified() {
        val diagnostics =
            GitHubRefreshFailureClassifier.from(
                IllegalStateException("wrapped", oversized()),
            )

        assertEquals(GitHubRefreshFailureClassifier.CATEGORY_RESPONSE_TOO_LARGE, diagnostics.category)
        assertEquals(8L * 1024L * 1024L, diagnostics.limitBytes)
        assertEquals(99_999_999L, diagnostics.declaredBytes)
        assertEquals(BoundedContentReadLimitStage.DeclaredLength.name, diagnostics.limitStage)
    }

    /** The F-Droid shape exactly: a different error as the cause, the interesting one suppressed. */
    @Test
    fun aBoundedReadReachableOnlyThroughSuppressedIsClassified() {
        val aggregate =
            IllegalStateException(
                "package API failed; repository index fallback failed",
                IllegalStateException("Expected '{' in JSON stream"),
            ).apply { addSuppressed(oversized()) }

        val diagnostics = GitHubRefreshFailureClassifier.from(aggregate)

        assertEquals(GitHubRefreshFailureClassifier.CATEGORY_RESPONSE_TOO_LARGE, diagnostics.category)
        assertEquals(99_999_999L, diagnostics.observedBytes)
    }

    /** Widening the walk must not start reclassifying the failures that already worked. */
    @Test
    fun anOrdinaryNetworkFailureIsUnchanged() {
        val diagnostics =
            GitHubRefreshFailureClassifier.from(
                IllegalStateException("refresh failed", IOException("connection reset")),
            )

        assertEquals(GitHubRefreshFailureClassifier.CATEGORY_NETWORK_ERROR, diagnostics.category)
        assertEquals(-1L, diagnostics.limitBytes)
    }

    /**
     * A graph, not a chain, so it can now contain a loop. Left unbounded this hangs a refresh thread.
     */
    @Test
    fun aSuppressedCycleTerminates() {
        val first = IllegalStateException("first")
        val second = IllegalStateException("second")
        first.addSuppressed(second)
        second.addSuppressed(first)

        val diagnostics = GitHubRefreshFailureClassifier.from(first)

        assertEquals(GitHubRefreshFailureClassifier.CATEGORY_UNKNOWN, diagnostics.category)
    }

    private fun oversized(): BoundedContentTextReadTooLargeException =
        BoundedContentTextReadTooLargeException(
            maxBytes = 8L * 1024L * 1024L,
            observedBytes = 99_999_999L,
            declaredBytes = 99_999_999L,
            stage = BoundedContentReadLimitStage.DeclaredLength,
        )
}
