package os.kei.core.install

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import os.kei.core.system.AppCommandResult
import os.kei.core.system.ShizukuApiUtils
import java.io.InputStream
import java.util.concurrent.TimeUnit

interface ShizukuProcessGateway {
    suspend fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): AppCommandResult

    suspend fun streamInput(
        command: String,
        input: InputStream,
        sizeBytes: Long,
        timeoutMs: Long = DEFAULT_STREAM_TIMEOUT_MS,
        onBytesWritten: suspend (Long) -> Unit = {}
    ): AppCommandResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 8_000L
        const val DEFAULT_STREAM_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

class DefaultShizukuProcessGateway(
    private val shizukuApiUtils: ShizukuApiUtils = ShizukuApiUtils(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ShizukuProcessGateway {
    override suspend fun execute(
        command: String,
        timeoutMs: Long
    ): AppCommandResult = withContext(ioDispatcher) {
        shizukuApiUtils.execCommandResult(command = command, timeoutMs = timeoutMs)
    }

    override suspend fun streamInput(
        command: String,
        input: InputStream,
        sizeBytes: Long,
        timeoutMs: Long,
        onBytesWritten: suspend (Long) -> Unit
    ): AppCommandResult = withContext(ioDispatcher) {
        val process = shizukuApiUtils.openShellCommandProcess(command)
            ?: return@withContext AppCommandResult(
                stdout = "",
                stderr = shizukuApiUtils.currentStatus(),
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader =
            startStreamCollector("KeiOS-ShizukuInstallStdout", process.inputStream, stdout)
        val stderrReader =
            startStreamCollector("KeiOS-ShizukuInstallStderr", process.errorStream, stderr)
        try {
            input.use { source ->
                process.outputStream.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read.toLong()
                        onBytesWritten(
                            if (sizeBytes > 0L) written.coerceAtMost(sizeBytes) else written
                        )
                    }
                    output.flush()
                }
            }
            val completed = process.waitFor(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            if (!completed) {
                runCatching { process.destroy() }
                runCatching { process.destroyForcibly() }
            }
            stdoutReader.join(if (completed) STREAM_JOIN_MS else STREAM_TIMEOUT_JOIN_MS)
            stderrReader.join(if (completed) STREAM_JOIN_MS else STREAM_TIMEOUT_JOIN_MS)
            AppCommandResult(
                stdout = stdout.toString().trim(),
                stderr = stderr.toString().trim(),
                exitCode = if (completed) process.exitValue() else null,
                timedOut = !completed,
                cancelled = false
            )
        } catch (error: CancellationException) {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            throw error
        } catch (error: Exception) {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            AppCommandResult(
                stdout = stdout.toString().trim(),
                stderr = error.message.orEmpty().ifBlank { stderr.toString().trim() },
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        } finally {
            runCatching { stdoutReader.interrupt() }
            runCatching { stderrReader.interrupt() }
        }
    }

    private fun startStreamCollector(
        name: String,
        input: InputStream,
        sink: StringBuilder
    ): Thread {
        return Thread(
            {
                runCatching {
                    input.bufferedReader().useLines { lines ->
                        lines.forEach { line -> sink.appendLine(line) }
                    }
                }
            },
            name
        ).apply {
            isDaemon = true
            start()
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 16 * 1024
        const val STREAM_JOIN_MS = 1_000L
        const val STREAM_TIMEOUT_JOIN_MS = 250L
    }
}
