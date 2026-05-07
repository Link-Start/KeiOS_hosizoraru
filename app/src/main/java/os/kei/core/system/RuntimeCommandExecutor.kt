package os.kei.core.system

import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class RuntimeCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean
) {
    val succeeded: Boolean
        get() = exitCode == 0 && !timedOut
}

object RuntimeCommandExecutor {
    private const val DEFAULT_TIMEOUT_MS = 5_000L

    private val streamExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "KeiOS-RuntimeCommandStream").apply {
            isDaemon = true
        }
    }

    fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RuntimeCommandResult {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) {
            return RuntimeCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false
            )
        }

        var process: Process? = null
        return runCatching {
            val startedProcess = ProcessBuilder("sh", "-c", normalizedCommand).start()
            process = startedProcess
            val stdoutFuture = streamExecutor.submit(Callable { startedProcess.inputStream.readTextSafely() })
            val stderrFuture = streamExecutor.submit(Callable { startedProcess.errorStream.readTextSafely() })
            val completed = startedProcess.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            if (!completed) {
                startedProcess.destroyForcibly()
                startedProcess.waitFor(250L, TimeUnit.MILLISECONDS)
            }
            RuntimeCommandResult(
                stdout = stdoutFuture.awaitText().trim(),
                stderr = stderrFuture.awaitText().trim(),
                exitCode = if (completed) startedProcess.exitValue() else null,
                timedOut = !completed
            )
        }.getOrElse { error ->
            runCatching { process?.destroyForcibly() }
            RuntimeCommandResult(
                stdout = "",
                stderr = error.message.orEmpty(),
                exitCode = null,
                timedOut = false
            )
        }
    }

    private fun InputStream.readTextSafely(): String {
        return runCatching {
            bufferedReader().use { reader -> reader.readText() }
        }.getOrDefault("")
    }

    private fun java.util.concurrent.Future<String>.awaitText(): String {
        return runCatching { get(250L, TimeUnit.MILLISECONDS) }
            .getOrDefault("")
    }
}
