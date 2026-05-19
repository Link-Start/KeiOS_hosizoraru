package os.kei.core.system

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import os.kei.core.ext.userMessage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

internal object PersistentShellCommandPolicy {
    private val settingsListRegex =
        Regex("""settings\s+list\s+(system|secure|global)(\s+2>/dev/null)?""")
    private val settingsProbePrintRegex =
        Regex("""printf\s+'__keios_settings_section=(system|secure|global)\\n'""")

    fun isEligible(command: String): Boolean {
        val normalized = command.trim()
        if (normalized.isBlank()) return false
        if (normalized.any { it == '\n' || it == '\r' }) return false
        if (normalized == "getprop") return true
        if (settingsListRegex.matches(normalized)) return true
        return isSettingsProbe(normalized)
    }

    private fun isSettingsProbe(command: String): Boolean {
        val parts = command.split(';').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size != SETTINGS_PROBE_SEGMENT_COUNT) return false
        return parts.withIndex().all { (index, part) ->
            if (index % 2 == 0) {
                settingsProbePrintRegex.matches(part)
            } else {
                settingsListRegex.matches(part)
            }
        }
    }

    private const val SETTINGS_PROBE_SEGMENT_COUNT = 6
}

internal class PersistentShellCommandExecutor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private data class ShellSession(
        val process: Process,
        val reader: BufferedReader,
        val writer: BufferedWriter
    )

    private data class ShellReadResult(
        val output: BoundedShellOutput,
        val exitCode: Int?,
        val reachedEndMarker: Boolean
    )

    private val commandMutex = Mutex()
    private val sessionLock = Any()
    private val markerCounter = AtomicLong(0)
    private var session: ShellSession? = null

    suspend fun executeAsync(request: AppCommandRequest): AppCommandResult = withContext(dispatcher) {
        val normalizedCommand = request.command.trim()
        if (normalizedCommand.isBlank()) {
            return@withContext AppCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
        commandMutex.withLock {
            runCommandLocked(
                command = normalizedCommand,
                timeoutMs = request.timeoutMs.coerceAtLeast(1L),
                maxOutputBytes = request.maxOutputBytes.coerceAtLeast(MIN_OUTPUT_BYTES)
            )
        }
    }

    fun close() {
        val current = synchronized(sessionLock) {
            session.also { session = null }
        }
        current?.closeSession()
    }

    private suspend fun runCommandLocked(
        command: String,
        timeoutMs: Long,
        maxOutputBytes: Int
    ): AppCommandResult {
        val current = currentSession()
        val marker = nextMarker()
        return try {
            writeCommand(current, command, marker)
            val readResult = withTimeoutOrNull(timeoutMs) {
                readUntilMarker(
                    session = current,
                    marker = marker,
                    maxOutputBytes = maxOutputBytes
                )
            }
            if (readResult == null) {
                closeCurrentSession(current)
                return AppCommandResult(
                    stdout = "",
                    stderr = "Command timed out",
                    exitCode = null,
                    timedOut = true,
                    cancelled = false
                )
            }
            if (!readResult.reachedEndMarker) {
                closeCurrentSession(current)
                return AppCommandResult(
                    stdout = readResult.output.text().trim(),
                    stderr = "Persistent shell closed before command finished",
                    exitCode = null,
                    timedOut = false,
                    cancelled = false,
                    stdoutTruncated = readResult.output.truncated
                )
            }
            AppCommandResult(
                stdout = readResult.output.text().trim(),
                stderr = "",
                exitCode = readResult.exitCode,
                timedOut = false,
                cancelled = false,
                stdoutTruncated = readResult.output.truncated
            )
        } catch (error: CancellationException) {
            closeCurrentSession(current)
            throw error
        } catch (error: Throwable) {
            closeCurrentSession(current)
            AppCommandResult(
                stdout = "",
                stderr = error.userMessage(),
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
    }

    private fun currentSession(): ShellSession {
        synchronized(sessionLock) {
            session?.takeIf { it.process.isAlive }?.let { return it }
        }
        val process = ProcessBuilder("sh")
            .redirectErrorStream(true)
            .start()
        val created = ShellSession(
            process = process,
            reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)),
            writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        )
        synchronized(sessionLock) {
            session?.closeSession()
            session = created
        }
        return created
    }

    private fun writeCommand(
        session: ShellSession,
        command: String,
        marker: String
    ) {
        val beginMarker = beginMarker(marker)
        val endMarker = endMarker(marker)
        session.writer.write("printf '%s\\n' '$beginMarker'\n")
        session.writer.write("( $command )\n")
        session.writer.write("__keios_exit=\$?\n")
        session.writer.write("printf '\\n%s:%s\\n' '$endMarker' \"\$__keios_exit\"\n")
        session.writer.flush()
    }

    private suspend fun readUntilMarker(
        session: ShellSession,
        marker: String,
        maxOutputBytes: Int
    ): ShellReadResult {
        val beginMarker = beginMarker(marker)
        val endMarkerPrefix = "${endMarker(marker)}:"
        val output = BoundedShellOutput(maxOutputBytes)
        var started = false
        while (true) {
            val line = runInterruptible { session.reader.readLine() }
                ?: return ShellReadResult(
                    output = output,
                    exitCode = null,
                    reachedEndMarker = false
                )
            when {
                line == beginMarker -> {
                    started = true
                }

                started && line.startsWith(endMarkerPrefix) -> {
                    val exitCode = line.substringAfter(endMarkerPrefix).trim().toIntOrNull()
                    return ShellReadResult(
                        output = output,
                        exitCode = exitCode,
                        reachedEndMarker = true
                    )
                }

                started -> output.appendLine(line)
            }
        }
    }

    private fun closeCurrentSession(current: ShellSession) {
        synchronized(sessionLock) {
            if (session === current) {
                session = null
            }
        }
        current.closeSession()
    }

    private fun ShellSession.closeSession() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { process.outputStream.close() }
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.destroy() }
        runCatching { process.destroyForcibly() }
    }

    private fun nextMarker(): String {
        return "__KEIOS_PERSISTENT_SHELL_${markerCounter.incrementAndGet()}_${System.nanoTime()}__"
    }

    private fun beginMarker(marker: String): String = "${marker}_BEGIN"

    private fun endMarker(marker: String): String = "${marker}_END"

    private class BoundedShellOutput(
        private val maxOutputBytes: Int
    ) {
        private val builder = StringBuilder()
        var truncated: Boolean = false
            private set
        private var bytes = 0

        fun appendLine(line: String) {
            append(if (builder.isEmpty()) line else "\n$line")
        }

        fun text(): String = builder.toString()

        private fun append(text: String) {
            if (text.isEmpty()) return
            val encoded = text.toByteArray(Charsets.UTF_8)
            val remaining = maxOutputBytes - bytes
            if (remaining <= 0) {
                truncated = true
                return
            }
            if (encoded.size <= remaining) {
                builder.append(text)
                bytes += encoded.size
                return
            }
            val accepted = encoded.copyOfRange(0, remaining)
                .toString(Charsets.UTF_8)
            builder.append(accepted)
            bytes = maxOutputBytes
            truncated = true
        }
    }

    private companion object {
        private const val MIN_OUTPUT_BYTES = 1_024
    }
}
