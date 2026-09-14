package os.kei.feature.github.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * What the device was connected through, recorded alongside a refresh.
 *
 * A refresh that took nine seconds means one thing on wifi and another on a cellular connection the
 * carrier is shaping, and without this the two are the same row in the history. It is also the
 * cheapest possible answer to the reason this instrumentation exists at all: an emulator is always
 * on an unmetered, zero-latency link, so a number measured there and a number measured on somebody's
 * commute are not the same kind of number and should not look alike in a report.
 */
object GitHubRefreshNetworkKind {
    const val WIFI = "wifi"
    const val CELLULAR = "cellular"
    const val ETHERNET = "ethernet"
    const val OTHER = "other"
    const val NONE = "none"
    const val UNKNOWN = ""

    /** Best-effort: any failure reads as unknown rather than as a claim. */
    fun of(context: Context): GitHubRefreshNetworkState =
        runCatching {
            val manager = context.applicationContext
                .getSystemService(ConnectivityManager::class.java)
                ?: return@runCatching GitHubRefreshNetworkState()
            val network = manager.activeNetwork
                ?: return@runCatching GitHubRefreshNetworkState(kind = NONE)
            val capabilities = manager.getNetworkCapabilities(network)
                ?: return@runCatching GitHubRefreshNetworkState()
            val kind = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ETHERNET
                else -> OTHER
            }
            GitHubRefreshNetworkState(
                kind = kind,
                metered = !capabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
                ),
            )
        }.getOrDefault(GitHubRefreshNetworkState())
}

data class GitHubRefreshNetworkState(
    val kind: String = GitHubRefreshNetworkKind.UNKNOWN,
    val metered: Boolean = false,
)
