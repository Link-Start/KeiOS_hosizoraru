package os.kei.feature.github

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.math.min

internal object GitHubBoundedRunner {
    fun <T, R> mapOrdered(
        items: List<T>,
        maxConcurrency: Int,
        threadName: String,
        block: (T) -> R
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
        if (concurrency <= 1) return items.map(block)
        val executor = Executors.newFixedThreadPool(concurrency) { runnable ->
            Thread(runnable, threadName).apply {
                isDaemon = true
            }
        }
        return try {
            val futures = try {
                executor.invokeAll(items.map { item -> Callable { block(item) } })
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            }
            futures
                .map { future ->
                    try {
                        future.get()
                    } catch (error: ExecutionException) {
                        throw error.cause ?: error
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw error
                    }
                }
        } finally {
            executor.shutdownNow()
        }
    }

    fun <T, R> firstSuccess(
        items: List<T>,
        maxConcurrency: Int,
        threadName: String,
        block: (T) -> Result<R>
    ): Result<R> {
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("No task was provided"))
        }
        val concurrency = min(items.size, maxConcurrency.coerceAtLeast(1))
        if (concurrency <= 1) {
            var lastError: Throwable? = null
            items.forEach { item ->
                block(item).fold(
                    onSuccess = { return Result.success(it) },
                    onFailure = { error -> lastError = error }
                )
            }
            return Result.failure(lastError ?: IllegalStateException("No task succeeded"))
        }

        val executor = Executors.newFixedThreadPool(concurrency) { runnable ->
            Thread(runnable, threadName).apply {
                isDaemon = true
            }
        }
        return try {
            val completionService = ExecutorCompletionService<Result<R>>(executor)
            items.forEach { item ->
                completionService.submit { block(item) }
            }
            var lastError: Throwable? = null
            repeat(items.size) {
                val result = try {
                    completionService.take().get()
                } catch (error: ExecutionException) {
                    Result.failure(error.cause ?: error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return Result.failure(error)
                }
                result.fold(
                    onSuccess = { return Result.success(it) },
                    onFailure = { error -> lastError = error }
                )
            }
            Result.failure(lastError ?: IllegalStateException("No task succeeded"))
        } finally {
            executor.shutdownNow()
        }
    }
}
