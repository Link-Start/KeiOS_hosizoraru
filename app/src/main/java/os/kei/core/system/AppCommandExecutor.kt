package os.kei.core.system

import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class AppCommandRequest(
    val command: String,
    val timeoutMs: Long = AppCommandExecutor.DEFAULT_TIMEOUT_MS
)

data class AppCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val cancelled: Boolean = false
) {
    val succeeded: Boolean
        get() = exitCode == 0 && !timedOut && !cancelled

    fun combinedOutput(): String {
        return stdout.ifBlank { stderr }
    }
}

object AppCommandExecutor {
    const val DEFAULT_TIMEOUT_MS = 5_000L

    fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): AppCommandResult {
        return execute(AppCommandRequest(command = command, timeoutMs = timeoutMs))
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
        val streamExecutor = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "KeiOS-AppCommandStream").apply {
                isDaemon = true
            }
        }
        return try {
            val startedProcess = ProcessBuilder("sh", "-c", normalizedCommand).start()
            process = startedProcess
            val stdoutFuture = streamExecutor.submit(Callable { startedProcess.inputStream.readTextSafely() })
            val stderrFuture = streamExecutor.submit(Callable { startedProcess.errorStream.readTextSafely() })
            val completed = startedProcess.waitFor(
                request.timeoutMs.coerceAtLeast(1L),
                TimeUnit.MILLISECONDS
            )
            if (!completed) {
                startedProcess.destroyForcibly()
                startedProcess.waitFor(250L, TimeUnit.MILLISECONDS)
            }
            AppCommandResult(
                stdout = stdoutFuture.awaitText(completed).trim(),
                stderr = stderrFuture.awaitText(completed).trim(),
                exitCode = if (completed) startedProcess.exitValue() else null,
                timedOut = !completed,
                cancelled = false
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
            streamExecutor.shutdownNow()
        }
    }

    private fun InputStream.readTextSafely(): String {
        return runCatching {
            bufferedReader().use { reader -> reader.readText() }
        }.getOrDefault("")
    }

    private fun java.util.concurrent.Future<String>.awaitText(processCompleted: Boolean): String {
        val timeoutMs = if (processCompleted) 2_000L else 250L
        return runCatching { get(timeoutMs, TimeUnit.MILLISECONDS) }
            .getOrDefault("")
    }
}
