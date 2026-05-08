package os.kei.core.platform

internal class TransientExternalLaunchGuard(
    private val transientWindowMs: Long = DEFAULT_TRANSIENT_WINDOW_MS
) {
    enum class Reason {
        NotificationPermission,
        LocalNetworkPermission,
        SystemPermission,
        SystemSettings,
        ExternalActivity,
        Installer,
        NotificationRoute
    }

    data class Snapshot(
        val active: Boolean,
        val reason: Reason?,
        val launchedAtMillis: Long
    )

    private var reason: Reason? = null
    private var launchedAtMillis: Long = 0L

    val snapshot: Snapshot
        get() = Snapshot(
            active = reason != null,
            reason = reason,
            launchedAtMillis = launchedAtMillis
        )

    fun markLaunching(reason: Reason, nowMillis: Long = System.currentTimeMillis()) {
        this.reason = reason
        launchedAtMillis = nowMillis
    }

    fun shouldDeferStopWork(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val activeReason = reason ?: return false
        if (activeReason == Reason.NotificationRoute) return false
        val age = (nowMillis - launchedAtMillis).coerceAtLeast(0L)
        return age <= transientWindowMs
    }

    fun clear() {
        reason = null
        launchedAtMillis = 0L
    }

    fun onResume() {
        clear()
    }

    private companion object {
        const val DEFAULT_TRANSIENT_WINDOW_MS = 15_000L
    }
}

