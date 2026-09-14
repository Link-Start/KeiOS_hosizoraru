package os.kei.core.io

import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

/**
 * What a slow refresh on somebody's phone is actually made of.
 *
 * The history page could say a repository took two seconds and never which two seconds those were.
 * Queueing behind our own concurrency budget, a DNS lookup, a TLS handshake, a server taking its
 * time, and a body coming down a slow radio are five different problems, and on an emulator over
 * wifi four of them round to zero — which is why a benchmark there proves nothing about a real
 * device.
 */
class NetworkCallTimingTest {
    @Test
    fun `a scope collects the calls made inside it and nothing else`() = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody("hello").setBodyDelay(0, TimeUnit.MILLISECONDS)) }
            val client = SharedHttpClient.base
            val scope = NetworkTimingScope()

            withContext(scope) {
                repeat(2) { index ->
                    client.executeCancellable(
                        Request.Builder().url(server.url("/scoped/$index")).get().build(),
                    ) { response -> response.body.string() }
                }
            }
            // Outside any scope: the same client, the same server, nothing recorded.
            client.executeCancellable(
                Request.Builder().url(server.url("/unscoped")).get().build(),
            ) { response -> response.body.string() }

            val summary = scope.summary()
            assertEquals(2, summary.callCount, "only the calls inside the scope")
            assertTrue(summary.bytes >= 10, "two five-byte bodies, at least: ${summary.bytes}")
            assertTrue(summary.protocol.isNotBlank(), "the protocol is worth knowing on a real radio")
        }
    }

    /**
     * The server holds every response for a beat. That time belongs to `waiting` — the server's own
     * — and must not be charged to the body, which is the number that would send somebody hunting
     * for a bandwidth problem they do not have.
     */
    @Test
    fun `time spent waiting for a server is not charged to the download`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody("x").setHeadersDelay(220, TimeUnit.MILLISECONDS),
            )
            val scope = NetworkTimingScope()

            withContext(scope) {
                SharedHttpClient.base.executeCancellable(
                    Request.Builder().url(server.url("/slow-server")).get().build(),
                ) { response -> response.body.string() }
            }

            val summary = scope.summary()
            assertTrue(summary.waitingMs >= 150L, "server delay landed in waiting: ${summary.waitingMs}")
            assertTrue(summary.bodyMs < summary.waitingMs, "body ${summary.bodyMs} vs waiting ${summary.waitingMs}")
            assertEquals(NetworkPhase.WAITING, summary.dominantPhase)
        }
    }

    /**
     * The gauge that would have caught the bug this instrument was built after: the refresh asked
     * for more concurrency than it ever got, for months, because nothing counted what was really in
     * the air.
     */
    @Test
    fun `the gauge reports calls that were actually in flight, not the number asked for`() = runBlocking {
        MockWebServer().use { server ->
            val calls = 8
            repeat(calls) {
                server.enqueue(MockResponse().setBody("x").setHeadersDelay(120, TimeUnit.MILLISECONDS))
            }
            NetworkCallGauge.resetPeak()
            val scope = NetworkTimingScope()

            withContext(scope) {
                (1..calls).map { index ->
                    async {
                        SharedHttpClient.base.executeCancellable(
                            Request.Builder().url(server.url("/parallel/$index")).get().build(),
                        ) { response -> response.body.string() }
                    }
                }.awaitAll()
            }

            assertEquals(calls, scope.summary().callCount)
            assertTrue(
                NetworkCallGauge.peakConcurrentCalls() > 1,
                "eight overlapping calls should not read as one at a time",
            )
        }
    }

    @Test
    fun `an unmeasured scope reports nothing rather than zeroes that look like facts`() {
        val summary = NetworkTimingScope().summary()

        assertTrue(summary.isEmpty)
        assertEquals("", summary.dominantPhase, "no calls means no dominant phase, not 'queued'")
    }
}
