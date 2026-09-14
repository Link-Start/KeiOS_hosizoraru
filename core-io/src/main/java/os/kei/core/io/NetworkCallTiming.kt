package os.kei.core.io

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Request

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
    /**
     * The phases summed across every call in the scope.
     *
     * Not the scope's wall clock: an item that ran two calls at once spent both their waits in the
     * same seconds. Treat it as a breakdown of effort, not of elapsed time.
     */
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
 * How many instrumented calls were in the air at once, for one measurement.
 *
 * The reason this exists: the refresh path asked for sixteen concurrent repositories and got ten for
 * months, because a request held its caller's thread and nobody was counting. A number a batch
 * *requested* is a setting; this is what happened.
 *
 * An instance per measurement rather than a process-wide singleton, because refreshes overlap — a
 * background tick, a card the user tapped, a package-install broadcast — and a shared counter would
 * have each of them resetting and reading the others' work, then showing the result as a fact.
 */
class NetworkCallGauge {
    private val inFlight = AtomicInteger(0)
    private val peak = AtomicInteger(0)

    internal fun enter() {
        val now = inFlight.incrementAndGet()
        peak.updateAndGet { current -> maxOf(current, now) }
    }

    internal fun exit() {
        inFlight.decrementAndGet()
    }

    fun peakConcurrentCalls(): Int = peak.get()
}

/**
 * Collects the calls made inside one coroutine scope.
 *
 * Carried in the coroutine context rather than passed down, because the calls are made eight layers
 * below whoever wants the answer and threading a recorder through all of them would be its own kind
 * of damage. [OkHttpClient.executeCancellable] picks it up and tags the request with it, so a call
 * made outside any scope records nothing and costs nothing.
 */
class NetworkTimingScope(
    /**
     * Shared by every scope in one batch, so the peak belongs to that batch and nobody else.
     * Null when the caller wants per-call phases without a concurrency reading.
     */
    val gauge: NetworkCallGauge? = null,
) : AbstractCoroutineContextElement(Key) {
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
 * Times every call that was issued inside a [NetworkTimingScope], and nothing else.
 *
 * OkHttp builds one of these per call. The accumulators are written on the call's own threads and
 * read once in [finish], which is reached from `callEnd` or `callFailed` — both delivered by the
 * call itself, after it is done. `canceled` is deliberately not one of them: OkHttp delivers it
 * inline on whichever thread called `cancel()` and still follows it with `callFailed`, so treating
 * it as terminal both raced the accumulators and closed the same call twice, which left the gauge's
 * in-flight count permanently below zero.
 */
internal class NetworkTimingEventListener(call: Call) : EventListener() {
    private val scope: NetworkTimingScope? =
        call.request().tag(NetworkTimingScope::class.java)

    private val finished = AtomicBoolean(false)

    private var callStartNs = 0L
    private var firstWorkNs = 0L
    private var dnsStartNs = 0L
    private var dnsEnded = false
    private var dnsMs = 0L

    /**
     * The first of possibly several attempts. `fastFallback` races an IPv6 plan and an IPv4 plan for
     * one call, so connect callbacks interleave and summing them would double count. "How long until
     * this call had a connection" is one well-defined interval whatever the plans did.
     */
    private var connectFirstStartNs = 0L
    private var connectionAcquiredNs = 0L
    private var connectMs = 0L

    private var requestSentNs = 0L
    private var waitingMs = 0L
    private var responseHeadersNs = 0L
    private var bodyEnded = false
    private var bodyMs = 0L
    private var bytes = 0L
    private var protocol = ""

    private fun markFirstWork(atNs: Long) {
        if (firstWorkNs == 0L) firstWorkNs = atNs
    }

    override fun callStart(call: Call) {
        if (scope == null) return
        callStartNs = System.nanoTime()
        scope.gauge?.enter()
    }

    override fun dnsStart(call: Call, domainName: String) {
        if (scope == null) return
        dnsStartNs = System.nanoTime()
        markFirstWork(dnsStartNs)
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        if (scope == null || dnsStartNs == 0L) return
        dnsMs += elapsedMs(dnsStartNs)
        dnsEnded = true
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        if (scope == null) return
        val now = System.nanoTime()
        if (connectFirstStartNs == 0L) connectFirstStartNs = now
        markFirstWork(now)
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        if (scope == null) return
        val now = System.nanoTime()
        markFirstWork(now)
        if (connectionAcquiredNs == 0L) connectionAcquiredNs = now
        if (connectFirstStartNs != 0L && connectMs == 0L) {
            connectMs = elapsedMs(connectFirstStartNs, now)
        }
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
        bodyEnded = true
    }

    override fun callEnd(call: Call) {
        finish(failed = false)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        finish(failed = true)
    }

    /**
     * Close out whichever phase the call was standing in when it ended.
     *
     * Every phase but the first is committed in the callback that ends it, so a call killed mid-phase
     * used to report zero for the one phase it was actually stuck in — and the cause pill would then
     * name whichever phase had managed to finish. That is exactly backwards: the calls worth
     * diagnosing are the ones that did not complete.
     */
    private fun finish(failed: Boolean) {
        val target = scope ?: return
        if (!finished.compareAndSet(false, true)) return
        target.gauge?.exit()
        val now = System.nanoTime()
        if (dnsStartNs != 0L && !dnsEnded) dnsMs += elapsedMs(dnsStartNs, now)
        if (connectFirstStartNs != 0L && connectionAcquiredNs == 0L) {
            connectMs += elapsedMs(connectFirstStartNs, now)
        }
        if (requestSentNs != 0L && responseHeadersNs == 0L) waitingMs += elapsedMs(requestSentNs, now)
        if (responseHeadersNs != 0L && !bodyEnded) bodyMs += elapsedMs(responseHeadersNs, now)
        val started = firstWorkNs.takeIf { it != 0L } ?: now
        target.record(
            NetworkCallTiming(
                queuedMs = if (callStartNs == 0L) 0L else elapsedMs(callStartNs, started),
                dnsMs = dnsMs,
                connectMs = connectMs,
                waitingMs = waitingMs,
                bodyMs = bodyMs,
                bytes = bytes,
                // Reuse means this call got a connection without opening one. A call that died before
                // any connect attempt reused nothing, and counting it as reuse hid the pill precisely
                // when connections were the problem.
                connectionReused = connectionAcquiredNs != 0L && connectFirstStartNs == 0L,
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
