package os.kei.feature.github.data.remote.fdroid

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.SharedHttpClient
import os.kei.core.io.executeCancellable
import os.kei.core.io.stringLimitedBlocking
import kotlin.time.Duration.Companion.seconds

/**
 * Reads a repository's package page for the full version history.
 *
 * One request of a few tens of kilobytes, against 58 MB for `index-v2.json` and against a package API
 * that answers with version numbers and nothing else. See [FdroidPackagePageParser] for why a page and
 * not an index, and for the rule that only structural markers are read.
 */
class FdroidPackagePageClient(
    private val client: OkHttpClient = defaultClient,
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
) {
    suspend fun fetchPackagePage(
        repoBaseUrl: String,
        packageName: String,
    ): Result<FdroidPackageSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val pageUrl = fdroidPackagePageUrl(repoBaseUrl, packageName)
                ?: error("no known package page for $repoBaseUrl")
            val request = Request.Builder()
                .url(pageUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,*/*")
                // The two prose fields the parser reads are rendered per locale; the /en/ path pins the
                // page and this pins the negotiation, so a device in another language still parses.
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.executeCancellable(request) { response ->
                check(response.isSuccessful) {
                    "F-Droid package page failed (HTTP ${response.code})"
                }
                val html = response.body.stringLimitedBlocking(MAX_PAGE_BYTES)
                val versions = FdroidPackagePageParser.parseVersions(html)
                check(versions.isNotEmpty()) {
                    "F-Droid package page listed no versions"
                }
                FdroidPackageSnapshot(
                    repoUrl = repoBaseUrl.trim().trimEnd('/'),
                    packageName = packageName.trim(),
                    suggestedVersionCode = FdroidPackagePageParser.parseSuggestedVersionCode(html),
                    versions = versions.sortedByDescending { version -> version.versionCode },
                )
            }
        }
    }

    private companion object {
        const val USER_AGENT = "KeiOS-App/1.0 (Android)"

        /**
         * Generous against the pages seen and still a hard stop.
         *
         * A package page runs to tens of kilobytes; a megabyte is room for an app with a long history and
         * many screenshots, while keeping a misrouted request from streaming an index into memory.
         */
        const val MAX_PAGE_BYTES = 4L * 1024L * 1024L
        val defaultClient: OkHttpClient = SharedHttpClient.base.newBuilder()
            .connectTimeout(12.seconds)
            .readTimeout(20.seconds)
            .callTimeout(28.seconds)
            .build()
    }
}
