package os.kei.core.io

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * Where one HTTP call's wall clock went.
 *
 * A refresh that took two seconds per repository says nothing about what to do next: two seconds of
 * waiting for a server, two seconds of TLS handshakes, and two seconds sitting in our own call queue
 * are three different bugs with three different fixes. On an emulator over wifi four of these five
 * numbers are approximately zero, which is exactly why a green benchmark there proves nothing about
 * somebody's phone on mobile data.
 */
data class NetworkCallTiming(
    /**
     * From the call being handed to OkHttp to it actually starting work.
     *
     * This is our own doing: the per-host budget in [SharedHttpClient] and OkHttp's dispatcher queue.
     * If this is the large number, the answer is a concurrency setting, not the network.
     */
    val queuedMs: Long = 0L,
    val dnsMs: Long = 0L,
    /** TCP plus TLS. Zero when the connection was reused, which is the point of the pool. */
    val connectMs: Long = 0L,
    /** Request sent to first response byte: the server's own time, and where a rate limit shows up. */
    val waitingMs: Long = 0L,
    /** First byte to last: bandwidth, and the only one that scales with how much we asked for. */
    val bodyMs: Long = 0L,
    val bytes: Long = 0L,
    val connectionReused: Boolean = false,
    val protocol: String = "",
    val failed: Boolean = false,
)

/** The totals for a scope — one tracked item's refresh, typically. */
data class NetworkTimingSummary(
    val callCount: Int = 0,
    val queuedMs: Long = 0L,
    val dnsMs: Long = 0L,
    val connectMs: Long = 0L,
    val waitingMs: Long = 0L,
    val bodyMs: Long = 0L,
    val bytes: Long = 0L,
    val reusedConnectionCalls: Int = 0,
    val failedCalls: Int = 0,
    val protocol: String = "",
) {
    val totalMs: Long
        get() = queuedMs + dnsMs + connectMs + waitingMs + bodyMs

    val isEmpty: Boolean
        get() = callCount == 0

    /**
     * The phase that took the most of this scope's time, or blank when nothing was measured.
     *
     * Deliberately one word the rest of the app can key off rather than a sentence: the phrasing is
     * a product decision and belongs where the strings live.
     */
    val dominantPhase: String
        get() {
            if (isEmpty || totalMs <= 0L) return ""
            return listOf(
                NetworkPhase.QUEUED to queuedMs,
                NetworkPhase.DNS to dnsMs,
                NetworkPhase.CONNECT to connectMs,
                NetworkPhase.WAITING to waitingMs,
                NetworkPhase.BODY to bodyMs,
            ).maxBy { it.second }.first
        }
}

/** The phase names [NetworkTimingSummary.dominantPhase] can report. */
object NetworkPhase {
    const val QUEUED = "queued"
    const val DNS = "dns"
    const val CONNECT = "connect"
    const val WAITING = "waiting"
    const val BODY = "body"
}

/**
 * Collects the calls made inside one coroutine scope.
 *
 * Carried in the coroutine context rather than passed down, because the calls are made eight layers
 * below whoever wants the answer and threading a recorder through all of them would be its own kind
 * of damage. [OkHttpClient.executeCancellable] picks it up and tags the request with it, so a call
 * made outside any scope records nothing and costs nothing.
 */
class NetworkTimingScope : AbstractCoroutineContextElement(Key) {
    private val calls = AtomicInteger(0)
    private val queued = AtomicLong(0)
    private val dns = AtomicLong(0)
    private val connect = AtomicLong(0)
    private val waiting = AtomicLong(0)
    private val body = AtomicLong(0)
    private val byteCount = AtomicLong(0)
    private val reused = AtomicInteger(0)
    private val failed = AtomicInteger(0)
    @Volatile
    private var protocol: String = ""

    fun record(timing: NetworkCallTiming) {
        calls.incrementAndGet()
        queued.addAndGet(timing.queuedMs)
        dns.addAndGet(timing.dnsMs)
        connect.addAndGet(timing.connectMs)
        waiting.addAndGet(timing.waitingMs)
        body.addAndGet(timing.bodyMs)
        byteCount.addAndGet(timing.bytes)
        if (timing.connectionReused) reused.incrementAndGet()
        if (timing.failed) failed.incrementAndGet()
        if (timing.protocol.isNotBlank()) protocol = timing.protocol
    }

    fun summary(): NetworkTimingSummary = NetworkTimingSummary(
        callCount = calls.get(),
        queuedMs = queued.get(),
        dnsMs = dns.get(),
        connectMs = connect.get(),
        waitingMs = waiting.get(),
        bodyMs = body.get(),
        bytes = byteCount.get(),
        reusedConnectionCalls = reused.get(),
        failedCalls = failed.get(),
        protocol = protocol,
    )

    companion object Key : CoroutineContext.Key<NetworkTimingScope>
}

/**
 * Process-wide gauge of how many instrumented calls are running at once.
 *
 * The reason this exists: the refresh path asked for sixteen concurrent repositories and got ten for
 * months, because a request held its caller's thread and nobody was counting. A number the batch
 * *requested* is a setting; this is what actually happened, and it is the one figure a report from a
 * real phone can carry that an emulator run cannot fake.
 *
 * Only scoped calls are counted, so a background download cannot inflate a refresh's reading.
 */
object NetworkCallGauge {
    private val inFlight = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    internal fun enter() {
        val now = inFlight.incrementAndGet()
        peak.updateAndGet { current -> maxOf(current, now) }
    }

    internal fun exit() {
        inFlight.decrementAndGet()
    }

    /** Start a fresh measurement. Returns the peak seen since the previous reset. */
    fun resetPeak(): Int {
        val previous = peak.get()
        peak.set(inFlight.get())
        return previous
    }

    fun peakConcurrentCalls(): Int = peak.get()
}

/**
 * Times every call that was issued inside a [NetworkTimingScope], and nothing else.
 *
 * OkHttp builds one of these per call, so the fields need no synchronisation between calls; the
 * callbacks themselves arrive on whichever thread the call is running on, and each field is written
 * once by that thread before [callEnd] reads them.
 */
internal class NetworkTimingEventListener(call: Call) : EventListener() {
    private val scope: NetworkTimingScope? =
        call.request().tag(NetworkTimingScope::class.java)

    private var callStartNs = 0L
    private var firstWorkNs = 0L
    private var dnsStartNs = 0L
    private var dnsMs = 0L
    private var connectStartNs = 0L
    private var connectMs = 0L
    private var requestSentNs = 0L
    private var waitingMs = 0L
    private var responseHeadersNs = 0L
    private var bodyMs = 0L
    private var bytes = 0L
    private var reused = true
    private var protocol = ""
    private var entered = false

    private fun markFirstWork(atNs: Long) {
        if (firstWorkNs == 0L) firstWorkNs = atNs
    }

    override fun callStart(call: Call) {
        if (scope == null) return
        callStartNs = System.nanoTime()
        NetworkCallGauge.enter()
        entered = true
    }

    override fun dnsStart(call: Call, domainName: String) {
        if (scope == null) return
        dnsStartNs = System.nanoTime()
        markFirstWork(dnsStartNs)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        if (scope == null || dnsStartNs == 0L) return
        dnsMs += elapsedMs(dnsStartNs)
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        if (scope == null) return
        connectStartNs = System.nanoTime()
        markFirstWork(connectStartNs)
        // A call that had to open a socket did not reuse one, whatever the pool says afterwards.
        reused = false
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) {
        if (scope == null || connectStartNs == 0L) return
        connectMs += elapsedMs(connectStartNs)
        protocol?.let { this.protocol = it.toString() }
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) {
        if (scope == null || connectStartNs == 0L) return
        connectMs += elapsedMs(connectStartNs)
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        // Folded into connectMs by connectEnd; TLS is part of the cost of not having a connection.
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        if (scope == null) return
        markFirstWork(System.nanoTime())
        if (protocol.isBlank()) protocol = connection.protocol().toString()
    }

    override fun requestHeadersStart(call: Call) {
        if (scope == null) return
        markFirstWork(System.nanoTime())
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        if (scope == null) return
        requestSentNs = System.nanoTime()
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        if (scope == null) return
        requestSentNs = System.nanoTime()
    }

    override fun responseHeadersStart(call: Call) {
        if (scope == null) return
        responseHeadersNs = System.nanoTime()
        if (requestSentNs != 0L) waitingMs += elapsedMs(requestSentNs, responseHeadersNs)
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        if (scope == null) return
        bytes += byteCount
        if (responseHeadersNs != 0L) bodyMs += elapsedMs(responseHeadersNs)
    }

    override fun callEnd(call: Call) {
        finish(failed = false)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        finish(failed = true)
    }

    override fun canceled(call: Call) {
        finish(failed = true)
    }

    private fun finish(failed: Boolean) {
        val target = scope ?: return
        if (!entered) return
        entered = false
        NetworkCallGauge.exit()
        val started = firstWorkNs.takeIf { it != 0L } ?: System.nanoTime()
        target.record(
            NetworkCallTiming(
                queuedMs = if (callStartNs == 0L) 0L else elapsedMs(callStartNs, started),
                dnsMs = dnsMs,
                connectMs = connectMs,
                waitingMs = waitingMs,
                bodyMs = bodyMs,
                bytes = bytes,
                connectionReused = reused,
                protocol = protocol,
                failed = failed,
            ),
        )
    }

    private fun elapsedMs(fromNs: Long, toNs: Long = System.nanoTime()): Long =
        ((toNs - fromNs) / 1_000_000L).coerceAtLeast(0L)

    companion object {
        val FACTORY = Factory { call -> NetworkTimingEventListener(call) }
    }
}
