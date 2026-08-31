package os.kei.feature.github.domain

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import os.kei.core.io.BoundedContentTextReadTooLargeException
import os.kei.feature.github.model.GitHubRefreshFailureDiagnostics

internal object GitHubRefreshFailureClassifier {
    fun from(
        error: Throwable,
        responseType: String = "",
    ): GitHubRefreshFailureDiagnostics {
        val causes = error.causes()
        val boundedError = causes
            .asSequence()
            .filterIsInstance<BoundedContentTextReadTooLargeException>()
            .firstOrNull()
        if (boundedError != null) {
            return GitHubRefreshFailureDiagnostics(
                category = CATEGORY_RESPONSE_TOO_LARGE,
                responseType = responseType,
                limitBytes = boundedError.maxBytes,
                declaredBytes = boundedError.declaredBytes ?: -1L,
                observedBytes = boundedError.observedBytes,
                limitStage = boundedError.stage.name,
            )
        }

        val message = causes.joinToString(" ") { cause -> cause.message.orEmpty() }.lowercase()
        val category = when {
            causes.any { it is CancellationException } -> CATEGORY_CANCELLED
            causes.any { it is SocketTimeoutException } || "timed out" in message || "timeout" in message ->
                CATEGORY_TIMEOUT
            "rate limit" in message || "http 429" in message -> CATEGORY_RATE_LIMITED
            HTTP_ERROR_PATTERN.containsMatchIn(message) -> CATEGORY_HTTP_ERROR
            causes.any { it is SerializationException } -> CATEGORY_PARSE_ERROR
            causes.any { it is UnknownHostException || it is IOException } -> CATEGORY_NETWORK_ERROR
            else -> CATEGORY_UNKNOWN
        }
        return GitHubRefreshFailureDiagnostics(
            category = category,
            responseType = responseType,
        )
    }

    fun timeout(responseType: String = ""): GitHubRefreshFailureDiagnostics =
        GitHubRefreshFailureDiagnostics(
            category = CATEGORY_TIMEOUT,
            responseType = responseType,
        )

    fun cancelled(responseType: String = ""): GitHubRefreshFailureDiagnostics =
        GitHubRefreshFailureDiagnostics(
            category = CATEGORY_CANCELLED,
            responseType = responseType,
        )

    fun network(responseType: String = ""): GitHubRefreshFailureDiagnostics =
        GitHubRefreshFailureDiagnostics(
            category = CATEGORY_NETWORK_ERROR,
            responseType = responseType,
        )

    /**
     * Every exception reachable from this one, through causes *and* suppressed exceptions.
     *
     * Suppressed matter because an aggregating wrapper can only carry one cause, and the one it keeps
     * is not always the one worth classifying. `FdroidBatchPackageSnapshotProvider` is the case that
     * forced this: when both the package API and the repository-index fallback fail it reports a
     * combined message and keeps the *repository* error as the cause, so a
     * [BoundedContentTextReadTooLargeException] from the API half survived only as text and every
     * oversized F-Droid response classified as `unknown`. The category and the whole
     * limit/declared/observed/stage row it drives were unreachable in the UI as a result.
     *
     * Breadth-first so a shallow suppressed exception is not lost behind a deep cause chain, and
     * bounded by [MAX_INSPECTED_CAUSES] because this runs per failed item on a refresh.
     */
    private fun Throwable.causes(): List<Throwable> =
        buildList {
            val visited = java.util.Collections.newSetFromMap(
                java.util.IdentityHashMap<Throwable, Boolean>(),
            )
            val pending = ArrayDeque<Throwable>()
            pending.addLast(this@causes)
            while (pending.isNotEmpty() && size < MAX_INSPECTED_CAUSES) {
                val current = pending.removeFirst()
                if (!visited.add(current)) continue
                add(current)
                current.cause?.let(pending::addLast)
                current.suppressed.forEach(pending::addLast)
            }
        }

    private val HTTP_ERROR_PATTERN = Regex("\\bhttp\\s+[45]\\d{2}\\b")

    /** A cap on the graph walk, not on depth: causes and suppressed exceptions both count. */
    private const val MAX_INSPECTED_CAUSES = 16

    const val CATEGORY_RESPONSE_TOO_LARGE = "response_too_large"
    const val CATEGORY_TIMEOUT = "timeout"
    const val CATEGORY_RATE_LIMITED = "rate_limited"
    const val CATEGORY_HTTP_ERROR = "http_error"
    const val CATEGORY_NETWORK_ERROR = "network_error"
    const val CATEGORY_PARSE_ERROR = "parse_error"
    const val CATEGORY_CANCELLED = "cancelled"
    const val CATEGORY_UNKNOWN = "unknown"
}
