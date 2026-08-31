package os.kei.memory

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import os.kei.core.log.AppLogger

/**
 * Answers the ITGSA fair-running-memory TRIM and KILL broadcasts, on every OEM that sends them.
 *
 * See [ItgsaFairMemory] for the transcribed contract and the source it came from. This class is the part
 * that touches the framework; the decisions live in `ItgsaFairMemoryParsing.kt` where they can be tested.
 *
 * ## Shape of the response
 *
 * TRIM releases caches through [AppMemoryRelease], the same path Android's own `onTrimMemory` uses, so the
 * OEM mechanism contributes a *trigger* rather than a second policy. KILL runs [onSaveState] first — the
 * process is going away regardless, and the only thing still in the app's control is whether reopening it
 * resumes — and then releases as well, because a KILL for a Java-heap exception only notifies the user and a
 * freed heap may still avoid the kill.
 *
 * ## Why a background thread, and why the reply is not deferred
 *
 * The receiver is registered with its own [HandlerThread], as the documentation's own sample does, because
 * `Debug.getPss()` alone can cost tens of milliseconds and the release runs several cache evictions. The
 * reply is sent from that same callback once the work returns rather than posted for later: the budget is
 * **3 seconds** ([ItgsaFairMemory.REPLY_TIMEOUT_MS]) and on the physical-memory path the system kills the
 * process before it notifies the user, so a reply that misses the window is a reply that never happened.
 *
 * ## Registered on every device, on purpose
 *
 * This started out gated behind Xiaomi's `ro.mi.os.*` / `ro.miui.*` system properties, on the assumption that
 * `itgsa` was a HyperOS namespace. **It is not.** ITGSA is the 金标联盟 — the Mobile Smart Terminal Ecosystem
 * Committee — and fair running memory is a *joint* standard its members ship, so the same broadcast arrives on
 * vivo, OPPO and Honor builds too. The gate meant this app declined to register on most of the alliance while
 * looking like it had adapted.
 *
 * The fix is not a longer property list. An enumeration of member OEMs is a list that goes stale the moment
 * the alliance admits another one, and being wrong there fails silently and identically. So registration is
 * unconditional and **the broadcast is the gate** — nothing sends these actions on a device that does not
 * implement the standard.
 *
 * What that costs is an **exported** receiver on every device, including ones where nothing will ever
 * broadcast. Any local app can then trigger it, so the defence moved from the registration to the handler:
 * see [MIN_RELEASE_INTERVAL_MS]. The worst a caller can buy is making this app drop caches it can rebuild
 * from disk, at most once every few seconds.
 */
object ItgsaFairMemoryReceiver : IBinder.DeathRecipient {
    private const val TAG = "ItgsaFairMemory"

    /**
     * Floor on how often a broadcast may actually cause a release.
     *
     * Registration is unconditional and the receiver is exported, so anything on the device can send these
     * actions. Releasing is cheap and safe but not free — it evicts the Coil memory cache and calls
     * [Debug.getPss] twice — so a caller in a loop could turn it into a re-decode treadmill. A few seconds is
     * far below any plausible cadence for a genuine memory warning and far above what an abuser needs to be
     * made useless.
     *
     * Deliberately does *not* rate-limit the **reply**: a suppressed release still answers the system inside
     * its 3-second window, because a missing reply is what gets the process killed.
     */
    private const val MIN_RELEASE_INTERVAL_MS = 4_000L

    /** `IBinder.FIRST_CALL_TRANSACTION`, as the documented reply transaction code. */
    private const val TRANSACTION_EXCEPTION_REPLY = IBinder.FIRST_CALL_TRANSACTION

    private val lock = Any()
    private var handlerThread: HandlerThread? = null
    private var registered = false
    private var remote: IBinder? = null
    private var lastReleaseAtMs: Long = 0L

    /**
     * Called on the receiver's background thread when a KILL arrives, before the caches are dropped.
     *
     * Must be fast and must not touch the UI. Anything held only in memory that the user would notice losing
     * belongs here; anything already written through to a store does not, which is most of this app.
     */
    private var onSaveState: (() -> Unit)? = null

    fun register(
        context: Context,
        onSaveState: () -> Unit,
    ) {
        synchronized(lock) {
            if (registered) return
            this.onSaveState = onSaveState
            val thread = HandlerThread(TAG).also(HandlerThread::start)
            handlerThread = thread
            val handler = Handler(thread.looper)
            val filter =
                IntentFilter().apply {
                    addAction(ItgsaFairMemory.ACTION_TRIM)
                    addAction(ItgsaFairMemory.ACTION_KILL)
                }
            val result =
                runCatching {
                    // The export flag has been mandatory since TIRAMISU (33) and minSdk is 35, so the
                    // unflagged overload was unreachable.
                    context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
                }
            result
                .onSuccess {
                    registered = true
                    AppLogger.i(TAG) { "ITGSA fair-memory receiver registered" }
                }.onFailure { error ->
                    thread.quitSafely()
                    handlerThread = null
                    AppLogger.w(TAG, "ITGSA fair-memory receiver registration failed", error)
                }
        }
    }

    override fun binderDied() {
        synchronized(lock) {
            runCatching { remote?.unlinkToDeath(this, 0) }
            remote = null
        }
    }

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val notification = parseItgsaFairMemoryNotification(intent.action, intent.extras)
                if (notification == null) {
                    // Logged rather than dropped in silence so that reachability can be told apart from
                    // correctness. A broadcast the parser rejects and a broadcast that never arrived look
                    // identical otherwise, which would make the local-broadcast smoke check in
                    // docs/planning/itgsa-fair-memory.md prove nothing.
                    AppLogger.i(TAG) { "ignored ${intent.action}: not a fair-memory notification" }
                    return
                }
                val callback =
                    intent.extras
                        ?.getBundle(ItgsaFairMemory.KEY_COMMON)
                        ?.getBinder(ItgsaFairMemory.KEY_CALLBACK)
                handle(context, notification, callback)
            }
        }

    private fun handle(
        context: Context,
        notification: ItgsaFairMemoryNotification,
        callback: IBinder?,
    ) {
        val startedAtMs = System.currentTimeMillis()
        AppLogger.i(TAG) {
            "received ${if (notification.kill) "KILL" else "TRIM"} type=${notification.notifyType} " +
                "reason='${notification.reason}' pss=${notification.pssKb}/${notification.pssLimitKb} " +
                "heap=${notification.heapUsedKb}/${notification.heapCapacityKb} " +
                "usage=${notification.usageFraction}"
        }

        // Saving comes first on a KILL: the release is worth doing but the save is the part that cannot be
        // redone once the process is gone.
        val saved =
            if (notification.kill) {
                runCatching { onSaveState?.invoke() }
                    .onFailure { error -> AppLogger.w(TAG, "state save failed", error) }
                    .isSuccess
            } else {
                true
            }

        // A KILL is never suppressed: the process is going away and this is the last chance to act on it.
        val runRelease =
            notification.kill ||
                synchronized(lock) {
                    shouldRunItgsaRelease(
                        nowMs = startedAtMs,
                        lastReleaseAtMs = lastReleaseAtMs,
                        minIntervalMs = MIN_RELEASE_INTERVAL_MS,
                    ).also { allowed -> if (allowed) lastReleaseAtMs = startedAtMs }
                }

        val freedKb =
            if (runRelease) {
                runCatching { AppMemoryRelease.release(context, releaseLevelFor(notification)) }
                    .onFailure { error -> AppLogger.w(TAG, "release failed", error) }
                    .getOrNull()
            } else {
                AppLogger.i(TAG) { "release suppressed, last one was under ${MIN_RELEASE_INTERVAL_MS}ms ago" }
                null
            }

        val elapsedMs = System.currentTimeMillis() - startedAtMs
        // Reported even when inside the budget, because "we replied at 2.9s" is the warning that the next
        // cache added here will push it over.
        if (elapsedMs >= ItgsaFairMemory.REPLY_TIMEOUT_MS) {
            AppLogger.w(TAG, "handled in ${elapsedMs}ms, past the ${ItgsaFairMemory.REPLY_TIMEOUT_MS}ms budget")
        }

        if (callback == null) {
            AppLogger.w(TAG, "no callback binder in the notification; nothing to reply to")
            return
        }
        reply(
            callback = callback,
            notifyType = notification.notifyType,
            notifyId = notification.notifyId,
            result = if (saved) ItgsaFairMemory.RESULT_HANDLED else ItgsaFairMemory.RESULT_NOT_HANDLED,
            message = "freedKb=$freedKb elapsedMs=$elapsedMs",
        )
    }

    /**
     * Writes the documented reply parcel: `notifyType`, `notifyId`, `result`, `extra`, in that order.
     *
     * A raw `transact` rather than a generated stub because the system side exposes no AIDL to compile
     * against — so the *order* here is the contract, and `ItgsaFairMemoryReplyTest` pins it by reading the
     * parcel back.
     */
    private fun reply(
        callback: IBinder,
        notifyType: Int,
        notifyId: Int,
        result: Int,
        message: String,
    ) {
        synchronized(lock) {
            if (remote !== callback) {
                runCatching { remote?.unlinkToDeath(this, 0) }
                remote = callback
                runCatching { callback.linkToDeath(this, 0) }
                    .onFailure { error ->
                        remote = null
                        AppLogger.w(TAG, "callback binder already dead", error)
                        return
                    }
            }
        }
        val data = Parcel.obtain()
        val replyParcel = Parcel.obtain()
        try {
            writeItgsaFairMemoryReply(
                data = data,
                notifyType = notifyType,
                notifyId = notifyId,
                result = result,
                extra = Bundle().apply { putString(ItgsaFairMemory.REPLY_KEY_MESSAGE, message) },
            )
            callback.transact(TRANSACTION_EXCEPTION_REPLY, data, replyParcel, IBinder.FLAG_ONEWAY)
            AppLogger.i(TAG) { "replied type=$notifyType id=$notifyId result=$result" }
        } catch (error: Exception) {
            AppLogger.w(TAG, "reply failed", error)
        } finally {
            replyParcel.recycle()
            data.recycle()
        }
    }

}

/**
 * The reply parcel's field order, extracted so a test can write it and read it back.
 *
 * There is no AIDL to compile against, so nothing but this ordering makes the reply parse on the system side.
 */
internal fun writeItgsaFairMemoryReply(
    data: Parcel,
    notifyType: Int,
    notifyId: Int,
    result: Int,
    extra: Bundle,
) {
    data.writeInt(notifyType)
    data.writeInt(notifyId)
    data.writeInt(result)
    data.writeBundle(extra)
}

/**
 * Whether a release should actually run, given when the last one did.
 *
 * Extracted so the rate limit is testable without a broadcast. Returns `true` for the first call of a session
 * — `lastReleaseAtMs` of zero — and treats a clock that went backwards as "long enough ago", since the
 * alternative is suppressing every release until the clock catches up.
 */
internal fun shouldRunItgsaRelease(
    nowMs: Long,
    lastReleaseAtMs: Long,
    minIntervalMs: Long,
): Boolean {
    if (lastReleaseAtMs <= 0L) return true
    val elapsedMs = nowMs - lastReleaseAtMs
    if (elapsedMs < 0L) return true
    return elapsedMs >= minIntervalMs
}
