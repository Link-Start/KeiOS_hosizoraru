package os.kei.core.system

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class AppCommandRequest(
    val command: String,
    val timeoutMs: Long = AppCommandExecutor.DEFAULT_TIMEOUT_MS,
    val maxOutputBytes: Int = AppCommandExecutor.DEFAULT_MAX_OUTPUT_BYTES
)

data class AppCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val cancelled: Boolean = false,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false
) {
    val succeeded: Boolean
        get() = exitCode == 0 && !timedOut && !cancelled

    fun combinedOutput(): String {
        return stdout.ifBlank { stderr }
    }
}

object AppCommandExecutor {
    const val DEFAULT_TIMEOUT_MS = 5_000L
    const val DEFAULT_MAX_OUTPUT_BYTES = 512 * 1024
    private val streamThreadIndex = AtomicInteger(0)
    private val streamExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(
            runnable,
            "KeiOS-AppCommandStream-${streamThreadIndex.incrementAndGet()}"
        ).apply {
            isDaemon = true
        }
    }

    fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES
    ): AppCommandResult {
        return execute(
            AppCommandRequest(
                command = command,
                timeoutMs = timeoutMs,
                maxOutputBytes = maxOutputBytes
            )
        )
    }

    fun execute(request: AppCommandRequest): AppCommandResult {
        val normalizedCommand = request.command.trim()
        if (normalizedCommand.isBlank()) {
            return AppCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }

        var process: Process? = null
        val maxOutputBytes = request.maxOutputBytes.coerceAtLeast(MIN_OUTPUT_BYTES)
        return try {
            val startedProcess = ProcessBuilder("sh", "-c", normalizedCommand).start()
            process = startedProcess
            val stdoutFuture = streamExecutor.submit(
                Callable { startedProcess.inputStream.captureText(maxOutputBytes) }
            )
            val stderrFuture = streamExecutor.submit(
                Callable { startedProcess.errorStream.captureText(maxOutputBytes) }
            )
            val completed = startedProcess.waitFor(
                request.timeoutMs.coerceAtLeast(1L),
                TimeUnit.MILLISECONDS
            )
            if (!completed) {
                startedProcess.destroyForcibly()
                startedProcess.waitFor(250L, TimeUnit.MILLISECONDS)
            }
            val stdout = stdoutFuture.awaitCapture(completed)
            val stderr = stderrFuture.awaitCapture(completed)
            AppCommandResult(
                stdout = stdout.text.trim(),
                stderr = stderr.text.trim(),
                exitCode = if (completed) startedProcess.exitValue() else null,
                timedOut = !completed,
                cancelled = false,
                stdoutTruncated = stdout.truncated,
                stderrTruncated = stderr.truncated
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            runCatching { process?.destroyForcibly() }
            AppCommandResult(
                stdout = "",
                stderr = error.message.orEmpty(),
                exitCode = null,
                timedOut = false,
                cancelled = true
            )
        } catch (error: Exception) {
            runCatching { process?.destroyForcibly() }
            AppCommandResult(
                stdout = "",
                stderr = error.message.orEmpty(),
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        } finally {
            process = null
        }
    }

    private fun InputStream.captureText(maxOutputBytes: Int): AppCommandStreamCapture {
        return runCatching {
            use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val output = ByteArrayOutputStream()
                var capturedBytes = 0
                var truncated = false
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    val remaining = maxOutputBytes - capturedBytes
                    if (remaining > 0) {
                        val accepted = minOf(read, remaining)
                        output.write(buffer, 0, accepted)
                        capturedBytes += accepted
                    }
                    if (read > remaining) {
                        truncated = true
                    }
                }
                AppCommandStreamCapture(
                    text = output.toByteArray().toString(Charsets.UTF_8),
                    truncated = truncated
                )
            }
        }.getOrDefault(AppCommandStreamCapture())
    }

    private fun java.util.concurrent.Future<AppCommandStreamCapture>.awaitCapture(
        processCompleted: Boolean
    ): AppCommandStreamCapture {
        val timeoutMs = if (processCompleted) 2_000L else 250L
        return runCatching { get(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault(AppCommandStreamCapture())
    }

    private data class AppCommandStreamCapture(
        val text: String = "",
        val truncated: Boolean = false
    )

    private const val MIN_OUTPUT_BYTES = 1_024
}
