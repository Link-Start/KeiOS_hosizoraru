package os.kei.core.io

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Application-wide shared [OkHttpClient] singleton.
 *
 * Previously the codebase created 13+ independent OkHttpClient instances, each with its own
 * connection pool, thread pool, and DNS cache. This wastes memory and prevents HTTP/2 connection
 * coalescing across repositories that hit the same host (github.com, api.github.com).
 *
 * All feature-level clients should derive from [base] via [OkHttpClient.newBuilder] so they share
 * the underlying connection pool and dispatcher while still being able to customize timeouts,
 * interceptors, or redirect policy per use case.
 *
 * Usage:
 * ```kotlin
 * private val client: OkHttpClient by lazy {
 *     SharedHttpClient.base.newBuilder()
 *         .callTimeout(20.seconds.toJavaDuration())
 *         .build()
 * }
 * ```
 */
object SharedHttpClient {
    /**
     * Shared connection pool: up to 8 idle connections kept alive for 90 seconds. This covers
     * concurrent GitHub API + HTML + asset download paths without over-provisioning.
     */
    private val connectionPool = ConnectionPool(
        maxIdleConnections = 8,
        keepAliveDuration = 90L,
        timeUnit = TimeUnit.SECONDS
    )

    /**
     * Base client with conservative defaults. Feature clients should call [OkHttpClient.newBuilder]
     * to customize timeouts without duplicating the pool/dispatcher.
     */
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(10.seconds)
            .readTimeout(15.seconds)
            .writeTimeout(10.seconds)
            .callTimeout(30.seconds)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .fastFallback(true)
            .build()
    }
}
