package os.kei.core.io

import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

suspend fun <T> OkHttpClient.executeCancellable(
    request: Request,
    block: (Response) -> T,
): T {
    return newCall(request).executeCancellable(block)
}

suspend fun OkHttpClient.executeCancellable(request: Request): Response {
    return newCall(request).executeCancellable()
}

/**
 * Run this call without a coroutine thread waiting on it.
 *
 * The obvious implementation is `execute()` inside `suspendCancellableCoroutine`, and it was the
 * implementation here. It reads as suspending and is not: the calling thread sits inside a blocking
 * socket read for the whole round trip, so the number of requests that can be in flight is exactly
 * the number of threads the caller's dispatcher will hand out. Measured on the GitHub refresh path,
 * which runs on a ten-thread dispatcher: asking for 16, 24 or 32 concurrent repositories produced
 * ten in flight and the same wall clock every time.
 *
 * `enqueue` costs no thread while the request is in the air. What bounds concurrency is then
 * OkHttp's own dispatcher, which is built for the job and is configured in `SharedHttpClient`.
 *
 * [block] still runs on OkHttp's callback thread and may read the body there, which is what that
 * pool is for. The response is closed either way.
 */
suspend fun <T> Call.executeCancellable(
    block: (Response) -> T,
): T {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use(block)
                    } catch (error: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                        return
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            },
        )
    }
}

/**
 * The same, handing back an unread [Response] the caller must close.
 *
 * Separate from the [block] form because the body has to outlive the callback, so this one cannot
 * use `response.use`. A caller that abandons the result leaks a connection until the pool evicts it.
 */
suspend fun Call.executeCancellable(): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) {
                        continuation.resume(response)
                    } else {
                        response.close()
                    }
                }
            },
        )
    }
}

suspend inline fun <T> cancellableResult(
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}

inline fun <T> resultPreservingCancellation(
    block: () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        Result.failure(error)
    }
}
