package os.kei.core.io

import java.io.IOException
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
import okhttp3.mockwebserver.SocketPolicy
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
            val gauge = NetworkCallGauge()
            val scope = NetworkTimingScope(gauge)

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
                gauge.peakConcurrentCalls() > 1,
                "eight overlapping calls should not read as one at a time",
            )
        }
    }

    /**
     * A call killed while it was waiting must be charged for the waiting.
     *
     * Every phase but the first used to be committed only in the callback that ends it, so the calls
     * worth diagnosing — the ones that stalled — reported zero for the phase they stalled in, and the
     * cause pill then named whichever phase had managed to finish.
     */
    @Test
    fun `a call that dies mid-phase is charged for the phase it died in`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = SharedHttpClient.base.newBuilder()
                .readTimeout(400, TimeUnit.MILLISECONDS)
                .callTimeout(2, TimeUnit.SECONDS)
                .build()
            val scope = NetworkTimingScope()

            withContext(scope) {
                runCatching {
                    client.executeCancellable(
                        Request.Builder().url(server.url("/never-answers")).get().build(),
                    ) { response -> response.body.string() }
                }
            }

            val summary = scope.summary()
            assertEquals(1, summary.callCount)
            assertEquals(1, summary.failedCalls)
            assertTrue(
                summary.waitingMs >= 250L,
                "the request was sent and nothing came back; that is waiting: ${'$'}{summary.waitingMs}",
            )
            assertEquals(NetworkPhase.WAITING, summary.dominantPhase)
        }
    }

    /**
     * Cancellation used to close the call twice. OkHttp delivers `canceled` inline on the cancelling
     * thread and still follows it with `callFailed`, so both passed a plain-boolean guard: the scope
     * counted one call as two and the gauge's in-flight counter went permanently negative.
     */
    @Test
    fun `a cancelled call is recorded exactly once`() = runBlocking {
        MockWebServer().use { server ->
            repeat(4) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
            val client = SharedHttpClient.base.newBuilder()
                .callTimeout(300, TimeUnit.MILLISECONDS)
                .build()
            val gauge = NetworkCallGauge()
            val scope = NetworkTimingScope(gauge)

            withContext(scope) {
                (1..4).map { index ->
                    async {
                        runCatching {
                            client.executeCancellable(
                                Request.Builder().url(server.url("/cancelled/${'$'}index")).get().build(),
                            ) { response -> response.body.string() }
                        }
                    }
                }.awaitAll()
            }

            assertEquals(4, scope.summary().callCount, "four calls, not eight")
            assertTrue(
                gauge.peakConcurrentCalls() in 1..4,
                "a double exit would let the peak drift: ${'$'}{gauge.peakConcurrentCalls()}",
            )
        }
    }

    /**
     * Reuse has to mean reuse. The flag used to default to true and be cleared only when a socket
     * was opened, so a call that died before it ever tried to connect was counted as pool reuse —
     * and the reuse pill was hidden exactly when connections were the problem.
     */
    @Test
    fun `only a call that got a connection without opening one counts as reuse`() = runBlocking {
        MockWebServer().use { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("ok")) }
            val scope = NetworkTimingScope()

            withContext(scope) {
                repeat(2) { index ->
                    SharedHttpClient.base.executeCancellable(
                        Request.Builder().url(server.url("/pooled/$index")).get().build(),
                    ) { response -> response.body.string() }
                }
            }

            val summary = scope.summary()
            assertEquals(2, summary.callCount)
            assertEquals(
                1,
                summary.reusedConnectionCalls,
                "the first call opened the connection and the second took it from the pool",
            )
        }
    }

    @Test
    fun `a call that could not connect is not counted as reuse`() = runBlocking {
        val deadUrl = MockWebServer().let { server ->
            server.start()
            val url = server.url("/gone")
            server.shutdown()
            url
        }
        val scope = NetworkTimingScope()

        withContext(scope) {
            runCatching {
                SharedHttpClient.base.newBuilder()
                    .callTimeout(2, TimeUnit.SECONDS)
                    .build()
                    .executeCancellable(
                        Request.Builder().url(deadUrl).get().build(),
                    ) { response -> response.body.string() }
            }
        }

        val summary = scope.summary()
        assertEquals(1, summary.callCount)
        assertEquals(1, summary.failedCalls)
        assertEquals(0, summary.reusedConnectionCalls)
    }

    /** Two measurements running at once must not read each other's numbers. */
    @Test
    fun `overlapping measurements keep their own peaks`() = runBlocking {
        MockWebServer().use { server ->
            repeat(6) {
                server.enqueue(MockResponse().setBody("x").setHeadersDelay(150, TimeUnit.MILLISECONDS))
            }
            val busy = NetworkCallGauge()
            val quiet = NetworkCallGauge()

            withContext(NetworkTimingScope(busy)) {
                (1..5).map { index ->
                    async {
                        SharedHttpClient.base.executeCancellable(
                            Request.Builder().url(server.url("/busy/${'$'}index")).get().build(),
                        ) { response -> response.body.string() }
                    }
                }.awaitAll()
            }
            withContext(NetworkTimingScope(quiet)) {
                SharedHttpClient.base.executeCancellable(
                    Request.Builder().url(server.url("/quiet")).get().build(),
                ) { response -> response.body.string() }
            }

            assertTrue(busy.peakConcurrentCalls() > 1, "five at once: ${'$'}{busy.peakConcurrentCalls()}")
            assertEquals(1, quiet.peakConcurrentCalls(), "the quiet batch made one call and saw one")
        }
    }

    /**
     * Two terminal callbacks for one call must produce one record.
     *
     * OkHttp delivers `canceled` inline on whichever thread called `cancel()` and still follows it
     * with `callFailed`. Treating both as terminal counted one call as two and, worse, left the
     * gauge's in-flight count one below zero for the rest of the process — so every later peak read
     * low, silently and forever. Driven directly rather than through a race, because a race is not
     * something to assert on.
     */
    @Test
    fun `a call closed twice is recorded once`() {
        val gauge = NetworkCallGauge()
        val scope = NetworkTimingScope(gauge)
        val request = Request.Builder()
            .url("https://example.test/closed-twice")
            .get()
            .tag(NetworkTimingScope::class.java, scope)
            .build()
        val call = SharedHttpClient.base.newCall(request)
        val listener = NetworkTimingEventListener(call)

        listener.callStart(call)
        listener.callFailed(call, IOException("cancelled"))
        listener.callFailed(call, IOException("and again"))
        listener.callEnd(call)

        assertEquals(1, scope.summary().callCount, "one call, however many times it is closed")
        assertEquals(1, gauge.peakConcurrentCalls())

        // The counter must still read correctly afterwards; a double exit used to poison it.
        val second = SharedHttpClient.base.newCall(request)
        NetworkTimingEventListener(second).apply {
            callStart(second)
            callEnd(second)
        }
        assertEquals(1, gauge.peakConcurrentCalls(), "a leaked exit would let this read 0 or 2")
        assertEquals(2, scope.summary().callCount)
    }

    @Test
    fun `an unmeasured scope reports nothing rather than zeroes that look like facts`() {
        val summary = NetworkTimingScope().summary()

        assertTrue(summary.isEmpty)
        assertEquals("", summary.dominantPhase, "no calls means no dominant phase, not 'queued'")
    }
}
