package os.kei.feature.github

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

internal object GitHubExecution {
    suspend fun <T, R> mapOrderedBounded(
        items: List<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend (T) -> R
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
        if (concurrency <= 1) {
            return items.map { item -> block(item) }
        }
        val nextIndex = AtomicInteger(0)
        val results = Array<Any?>(items.size) { MissingResult }
        return coroutineScope {
            List(concurrency) {
                async(dispatcher) {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= items.size) break
                        results[index] = block(items[index])
                        yield()
                    }
                }
            }.awaitAll()
            @Suppress("UNCHECKED_CAST")
            results.map { value ->
                check(value !== MissingResult) { "Bounded map result was not produced" }
                value as R
            }
        }
    }

    suspend fun <T, R> firstSuccessBounded(
        items: List<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend (T) -> Result<R>
    ): Result<R> {
        if (items.isEmpty()) {
            return Result.failure(IllegalStateException("No task was provided"))
        }
        val concurrency = items.size.coerceAtMost(maxConcurrency.coerceAtLeast(1))
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

        return supervisorScope {
            val nextIndex = AtomicInteger(0)
            val channel = Channel<Result<R>>(capacity = concurrency)
            val jobs = List(concurrency) {
                async(dispatcher) {
                    while (true) {
                        val index = nextIndex.getAndIncrement()
                        if (index >= items.size) break
                        val result = try {
                            block(items[index])
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            Result.failure(error)
                        }
                        channel.send(result)
                        yield()
                    }
                }
            }
            var lastError: Throwable? = null
            try {
                repeat(items.size) {
                    val result = channel.receive()
                    result.fold(
                        onSuccess = { value ->
                            jobs.forEach { job -> job.cancel() }
                            channel.cancel()
                            return@supervisorScope Result.success(value)
                        },
                        onFailure = { error -> lastError = error }
                    )
                }
                Result.failure(lastError ?: IllegalStateException("No task succeeded"))
            } finally {
                channel.cancel()
            }
        }
    }

    private object MissingResult

    suspend fun <T> retryOnce(
        delayMillis: Long = DEFAULT_RETRY_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean,
        block: suspend () -> T
    ): Result<T> {
        return runCatching {
            try {
                block()
            } catch (firstError: Throwable) {
                if (firstError is CancellationException || !shouldRetry(firstError)) {
                    throw firstError
                }
                delay(delayMillis.milliseconds)
                block()
            }
        }
    }

    fun <T, R> mapOrderedBoundedBlocking(
        items: List<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: (T) -> R
    ): List<R> {
        return runBlocking(dispatcher) {
            mapOrderedBounded(
                items = items,
                maxConcurrency = maxConcurrency,
                dispatcher = dispatcher
            ) { item ->
                block(item)
            }
        }
    }

    fun <T, R> firstSuccessBoundedBlocking(
        items: List<T>,
        maxConcurrency: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: (T) -> Result<R>
    ): Result<R> {
        return runBlocking(dispatcher) {
            firstSuccessBounded(
                items = items,
                maxConcurrency = maxConcurrency,
                dispatcher = dispatcher
            ) { item ->
                block(item)
            }
        }
    }

    fun <T> runBlockingIo(block: suspend () -> T): T {
        return runBlocking(Dispatchers.IO) {
            block()
        }
    }

    fun <T> retryOnceBlocking(
        delayMillis: Long = DEFAULT_RETRY_DELAY_MS,
        shouldRetry: (Throwable) -> Boolean,
        block: () -> T
    ): Result<T> {
        return runBlocking(Dispatchers.IO) {
            retryOnce(
                delayMillis = delayMillis,
                shouldRetry = shouldRetry
            ) {
                block()
            }
        }
    }

    private const val DEFAULT_RETRY_DELAY_MS = 220L
}

internal class GitHubSingleFlight<K, V> {
    private val inFlight = ConcurrentHashMap<K, Deferred<Result<V>>>()

    suspend fun run(
        key: K,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        block: suspend () -> Result<V>
    ): Result<V> {
        return coroutineScope {
            val candidate = async(dispatcher, start = CoroutineStart.LAZY) {
                try {
                    block()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    Result.failure(error)
                }
            }
            val active = inFlight.putIfAbsent(key, candidate)
            val selected = active ?: candidate.also { it.start() }
            if (active != null) {
                candidate.cancel()
            }
            try {
                selected.await()
            } finally {
                if (active == null) {
                    inFlight.remove(key, candidate)
                }
            }
        }
    }
}
