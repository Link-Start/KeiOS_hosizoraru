package os.kei.core.io

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Test

/**
 * A request must not occupy the thread that started it.
 *
 * This is the property the GitHub refresh path depends on and did not have. `executeCancellable`
 * used `execute()`, so a call sat in a blocking socket read for its whole round trip and the number
 * of requests that could be in the air was exactly the caller's thread count. The refresh runs on a
 * ten-thread dispatcher, so asking for sixteen, twenty-four or thirty-two concurrent repositories
 * produced ten and the same wall clock every time — measured, not inferred.
 *
 * Wall clock is the wrong thing to assert about on CI, so this asserts the shape instead: the server
 * refuses to answer anybody until it is holding more requests at once than the caller has threads.
 * On the blocking implementation that is a deadlock and the latch times out.
 */
class OkHttpCallConcurrencyTest {
    @Test
    fun `more requests fly at once than the calling dispatcher has threads`() = runBlocking {
        val threads = 4
        val requests = 12
        val allArrived = CountDownLatch(requests)
        val peak = AtomicInteger(0)
        val live = AtomicInteger(0)

        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    peak.updateAndGet { maxOf(it, live.incrementAndGet()) }
                    allArrived.countDown()
                    // Nobody is answered until everybody has arrived. A call that owns its caller's
                    // thread can never get here more than `threads` at a time.
                    val everybodyArrived = allArrived.await(20, TimeUnit.SECONDS)
                    live.decrementAndGet()
                    return MockResponse()
                        .setResponseCode(if (everybodyArrived) 200 else 504)
                        .setBody("ok")
                }
            }

            @Suppress("DEPRECATION")
            val caller = Dispatchers.IO.limitedParallelism(threads)
            val client = SharedHttpClient.base
            val codes = (1..requests).map { index ->
                async(caller) {
                    client.executeCancellable(
                        Request.Builder().url(server.url("/$index")).get().build(),
                    ) { response -> response.code }
                }
            }.awaitAll()

            assertEquals(List(requests) { 200 }, codes, "every request was answered")
            assertTrue(
                peak.get() >= requests,
                "only ${peak.get()} of $requests requests were ever in the air at once",
            )
        }
    }
}
