package os.kei.feature.github.data.local

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import os.kei.core.io.NetworkPhase
import os.kei.core.io.NetworkTimingSummary

/**
 * The network profile rides along with every slow item of every retained refresh, so it has to be
 * cheap when there is nothing to say and complete when there is.
 */
class GitHubRefreshNetworkTimingJsonTest {
    @Test
    fun `an item that made no calls stores nothing`() {
        assertNull(
            networkTimingToJson(NetworkTimingSummary()),
            "an item answered from cache made no calls; a row of zeroes would read like a measurement",
        )
        assertTrue(networkTimingFromJson(null).isEmpty, "and a record written before this existed reads back empty")
    }

    @Test
    fun `a measured item round-trips`() {
        val summary = NetworkTimingSummary(
            callCount = 3,
            queuedMs = 40L,
            dnsMs = 12L,
            connectMs = 180L,
            waitingMs = 640L,
            bodyMs = 95L,
            bytes = 331_004L,
            reusedConnectionCalls = 2,
            failedCalls = 1,
            protocol = "h2",
        )

        assertEquals(summary, networkTimingFromJson(networkTimingToJson(summary)))
    }

    /**
     * The phase names are written into the export and read by whoever receives a user's report, so
     * they are part of the contract rather than an implementation detail.
     */
    @Test
    fun `the dominant phase is the one that took the time`() {
        val serverBound = NetworkTimingSummary(callCount = 1, waitingMs = 900L, bodyMs = 40L)
        val queueBound = NetworkTimingSummary(callCount = 1, queuedMs = 700L, waitingMs = 120L)
        val bandwidthBound = NetworkTimingSummary(callCount = 1, waitingMs = 90L, bodyMs = 1_400L)

        assertEquals(NetworkPhase.WAITING, serverBound.dominantPhase)
        assertEquals(NetworkPhase.QUEUED, queueBound.dominantPhase)
        assertEquals(NetworkPhase.BODY, bandwidthBound.dominantPhase)
    }

    /** A build that learns a new phase name must not relabel what is already on disk. */
    @Test
    fun `a field this build has no name for is dropped, not guessed at`() {
        val fromTheFuture = buildJsonObject {
            put("calls", 2)
            put("waiting", 300L)
            put("somethingNew", 999L)
        }

        val restored = networkTimingFromJson(fromTheFuture)

        assertEquals(2, restored.callCount)
        assertEquals(300L, restored.waitingMs)
        assertEquals(0L, restored.queuedMs)
    }
}
