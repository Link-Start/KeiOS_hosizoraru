package os.kei.core.system

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import os.kei.core.log.AppLogger
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.lang.reflect.Method
import java.util.Locale

class ShizukuApiUtils(
    private val requestCode: Int = DEFAULT_REQUEST_CODE,
    private val commandDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private enum class CommandIdentity(val label: String) {
        ROOT("root"),
        SHELL("shell"),
        UNSUPPORTED("unsupported");

        val canRunCommand: Boolean
            get() = this == ROOT || this == SHELL

        companion object {
            fun fromUid(uid: Int?): CommandIdentity = when (uid) {
                0 -> ROOT
                2000 -> SHELL
                else -> UNSUPPORTED
            }
        }
    }

    private data class RuntimeState(
        val binderAlive: Boolean,
        val preV11: Boolean,
        val permissionGranted: Boolean,
        val serviceUid: Int?,
        val commandIdentity: CommandIdentity
    ) {
        val commandReady: Boolean =
            binderAlive && !preV11 && permissionGranted && commandIdentity.canRunCommand

        val statusText: String
            get() = when {
                !binderAlive -> "Shizuku service unavailable (start Shizuku app first)"
                preV11 -> "Shizuku pre-v11 is unsupported"
                !permissionGranted -> "Shizuku permission: not granted"
                commandIdentity.canRunCommand -> "Shizuku permission: granted (${commandIdentity.label})"
                else -> {
                    val uidText = serviceUid?.toString() ?: "unknown"
                    "Shizuku command unavailable: unsupported service uid $uidText"
                }
            }
    }

    private data class CachedRuntimeState(
        val state: RuntimeState,
        val capturedAtNanos: Long
    ) {
        fun isFresh(nowNanos: Long): Boolean {
            val age = nowNanos - capturedAtNanos
            return age in 0..RUNTIME_STATE_CACHE_TTL_NANOS
        }
    }

    private data class InteractiveCommandRewriteResult(
        val command: String,
        val adaptedTopOnce: Boolean = false
    )

    private data class UiDumpRewriteResult(
        val command: String,
        val redirectedPath: String?
    )


    private var statusCallback: ((String) -> Unit)? = null
    private var cachedNewProcessMethod: Method? = null
    @Volatile
    private var cachedRuntimeState: CachedRuntimeState? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        invalidateRuntimeStateCache()
        publishStatus(currentStatus())
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        invalidateRuntimeStateCache()
        publishStatus("Shizuku service disconnected")
        cachedNewProcessMethod = null
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { code, grantResult ->
        if (code != requestCode) return@OnRequestPermissionResultListener
        invalidateRuntimeStateCache()
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            publishStatus(currentStatus())
        } else {
            publishStatus("Shizuku permission: denied")
        }
    }

    fun attach(onStatusChanged: (String) -> Unit) {
        statusCallback = onStatusChanged
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        }.onFailure {
            publishStatus("Shizuku init failed: ${it.javaClass.simpleName}")
        }
        publishStatus(currentStatus())
    }

    fun detach() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        }
        statusCallback = null
    }

    fun requestPermissionIfNeeded() {
        runCatching {
            when {
                !Shizuku.pingBinder() -> publishStatus("Shizuku service unavailable (start Shizuku app first)")
                Shizuku.isPreV11() -> publishStatus("Shizuku pre-v11 is unsupported")
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    publishStatus(currentStatus())
                }

                Shizuku.shouldShowRequestPermissionRationale() -> {
                    publishStatus("Shizuku permission denied permanently")
                }

                else -> {
                    publishStatus("Requesting Shizuku permission...")
                    Shizuku.requestPermission(requestCode)
                }
            }
        }.onFailure {
            publishStatus("Shizuku request failed: ${it.javaClass.simpleName}")
        }
    }

    fun currentStatus(): String {
        return resolveRuntimeState(forceRefresh = true).statusText
    }

    fun canUseCommand(): Boolean {
        return resolveRuntimeState().commandReady
    }

    fun execCommandResult(command: String, timeoutMs: Long = 2000L): AppCommandResult {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) {
            return AppCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
        val state = resolveRuntimeState()
        if (!state.commandReady) {
            return AppCommandResult(
                stdout = "",
                stderr = state.statusText,
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
        val process = createShellProcess(normalizedCommand)
            ?: return AppCommandResult(
                stdout = "",
                stderr = "Shizuku process unavailable",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        return runCatching {
            executeProcess(process = process, timeoutMs = timeoutMs)
        }.onFailure {
            AppLogger.w(
                TAG,
                "execCommand failed: ${it.javaClass.simpleName}${it.message?.let { msg -> ": $msg" }.orEmpty()}"
            )
        }.getOrElse { error ->
            AppCommandResult(
                stdout = "",
                stderr = error.message.orEmpty().ifBlank { error.javaClass.simpleName },
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
    }

    fun execCommand(command: String, timeoutMs: Long = 2000L): String? {
        val result = execCommandResult(command = command, timeoutMs = timeoutMs)
        if (result.exitCode == null && !result.timedOut && !result.cancelled) return null
        return result.combinedOutput().ifBlank { null }
    }

    suspend fun execCommandCancellableResult(
        command: String,
        timeoutMs: Long = 2000L
    ): AppCommandResult {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) {
            return AppCommandResult(
                stdout = "",
                stderr = "",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
        val state = resolveRuntimeState()
        if (!state.commandReady) {
            return AppCommandResult(
                stdout = "",
                stderr = state.statusText,
                exitCode = null,
                timedOut = false,
                cancelled = false
            )
        }
        val process = createShellProcess(normalizedCommand)
            ?: return AppCommandResult(
                stdout = "",
                stderr = "Shizuku process unavailable",
                exitCode = null,
                timedOut = false,
                cancelled = false
            )

        return executeProcessCancellable(process = process, timeoutMs = timeoutMs)
    }

    suspend fun execCommandCancellable(command: String, timeoutMs: Long = 2000L): String? {
        val result = execCommandCancellableResult(command = command, timeoutMs = timeoutMs)
        if (result.exitCode == null && !result.timedOut && !result.cancelled) return null
        return result.combinedOutput().ifBlank { null }
    }

    private fun createShellProcess(command: String): Process? {
        val interactiveRewrite = rewriteInteractiveShellCommand(command)
        if (interactiveRewrite.adaptedTopOnce) {
            publishStatus("top command adapted: run once with -n 1")
        }
        val resolved = rewriteUiAutomatorDumpCommand(interactiveRewrite.command)
        if (!resolved.redirectedPath.isNullOrBlank()) {
            publishStatus("UI dump redirected: ${resolved.redirectedPath}")
        }
        val processMethod = resolveNewProcessMethod() ?: run {
            publishStatus("Shizuku process API unavailable")
            AppLogger.w(TAG, "createShellProcess skipped: Shizuku newProcess method unavailable")
            return null
        }
        return runCatching {
            processMethod.invoke(
                null,
                arrayOf("sh", "-c", resolved.command),
                null,
                null
            ) as? Process ?: error("Shizuku newProcess did not return Process")
        }.onFailure {
            AppLogger.w(
                TAG,
                "createShellProcess failed: ${it.javaClass.simpleName}${it.message?.let { msg -> ": $msg" }.orEmpty()}"
            )
        }.getOrNull()
    }

    private fun rewriteInteractiveShellCommand(command: String): InteractiveCommandRewriteResult {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return InteractiveCommandRewriteResult(command = command)
        val normalized = trimmed.lowercase(Locale.ROOT)
        if (!normalized.startsWith("top")) {
            return InteractiveCommandRewriteResult(command = command)
        }
        val hasIterationCount = Regex("""(^|\s)-n(\s*\d+)?(\s|$)""").containsMatchIn(trimmed)
        if (hasIterationCount) {
            return InteractiveCommandRewriteResult(command = command)
        }
        return InteractiveCommandRewriteResult(
            command = "$trimmed -n 1",
            adaptedTopOnce = true
        )
    }

    private fun executeProcess(process: Process, timeoutMs: Long): AppCommandResult {
        val stdout = BoundedCommandOutputSink(AppCommandExecutor.DEFAULT_MAX_OUTPUT_BYTES)
        val stderr = BoundedCommandOutputSink(AppCommandExecutor.DEFAULT_MAX_OUTPUT_BYTES)
        val stdoutReader = startStreamCollector(
            name = "KeiOS-ShizukuStdout",
            stream = process.inputStream,
            sink = stdout
        )
        val stderrReader = startStreamCollector(
            name = "KeiOS-ShizukuStderr",
            stream = process.errorStream,
            sink = stderr
        )

        var waitThrowable: Throwable? = null
        val waiter = Thread(
            {
                runCatching { process.waitFor() }
                    .onFailure { throwable -> waitThrowable = throwable }
            },
            "KeiOS-ShizukuWait"
        ).apply {
            isDaemon = true
            start()
        }

        waiter.join(timeoutMs)
        if (waiter.isAlive) {
            runCatching { process.destroy() }
            runCatching {
                waiter.join(300)
                if (waiter.isAlive) {
                    process.destroyForcibly()
                    waiter.join(300)
                }
            }
            stdoutReader.join(300)
            stderrReader.join(300)
            return AppCommandResult(
                stdout = stdout.text().trim(),
                stderr = stderr.text().trim(),
                exitCode = null,
                timedOut = true,
                cancelled = false,
                stdoutTruncated = stdout.truncated,
                stderrTruncated = stderr.truncated
            )
        }
        waitThrowable?.let { throw it }
        stdoutReader.join(600)
        stderrReader.join(600)
        return AppCommandResult(
            stdout = stdout.text().trim(),
            stderr = stderr.text().trim(),
            exitCode = process.exitValue(),
            timedOut = false,
            cancelled = false,
            stdoutTruncated = stdout.truncated,
            stderrTruncated = stderr.truncated
        )
    }

    private suspend fun executeProcessCancellable(
        process: Process,
        timeoutMs: Long
    ): AppCommandResult = withContext(commandDispatcher) {
        val stdout = BoundedCommandOutputSink(AppCommandExecutor.DEFAULT_MAX_OUTPUT_BYTES)
        val stderr = BoundedCommandOutputSink(AppCommandExecutor.DEFAULT_MAX_OUTPUT_BYTES)
        val stdoutReader = startStreamCollector(
            name = "KeiOS-ShizukuStdout",
            stream = process.inputStream,
            sink = stdout
        )
        val stderrReader = startStreamCollector(
            name = "KeiOS-ShizukuStderr",
            stream = process.errorStream,
            sink = stderr
        )

        val exitCode = runCatching {
            withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
                runInterruptible { process.waitFor() }
            }
        }.onFailure {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            throw it
        }.getOrNull()

        if (exitCode == null) {
            runCatching { process.destroy() }
            runCatching { process.destroyForcibly() }
            stdoutReader.join(300)
            stderrReader.join(300)
            return@withContext AppCommandResult(
                stdout = stdout.text().trim(),
                stderr = stderr.text().trim(),
                exitCode = null,
                timedOut = true,
                cancelled = false,
                stdoutTruncated = stdout.truncated,
                stderrTruncated = stderr.truncated
            )
        }

        stdoutReader.join(600)
        stderrReader.join(600)
        AppCommandResult(
            stdout = stdout.text().trim(),
            stderr = stderr.text().trim(),
            exitCode = exitCode,
            timedOut = false,
            cancelled = false,
            stdoutTruncated = stdout.truncated,
            stderrTruncated = stderr.truncated
        )
    }

    private fun startStreamCollector(
        name: String,
        stream: InputStream,
        sink: BoundedCommandOutputSink
    ): Thread {
        return Thread(
            {
                runCatching {
                    stream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            sink.append(buffer, read)
                        }
                    }
                }
            },
            name
        ).apply {
            isDaemon = true
            start()
        }
    }

    private class BoundedCommandOutputSink(
        private val maxBytes: Int
    ) {
        private val output = ByteArrayOutputStream()
        private var capturedBytes = 0
        @Volatile
        var truncated: Boolean = false
            private set

        @Synchronized
        fun append(buffer: ByteArray, length: Int) {
            val remaining = maxBytes - capturedBytes
            if (remaining > 0) {
                val accepted = minOf(length, remaining)
                output.write(buffer, 0, accepted)
                capturedBytes += accepted
            }
            if (length > remaining) {
                truncated = true
            }
        }

        @Synchronized
        fun text(): String {
            return output.toByteArray().toString(Charsets.UTF_8)
        }
    }

    private fun resolveNewProcessMethod(): Method? {
        cachedNewProcessMethod?.let { return it }
        val resolved = runCatching {
            val parameterTypes = arrayOf(
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            Shizuku::class.java.declaredMethods.firstOrNull { method ->
                method.parameterTypes.contentEquals(parameterTypes) &&
                    Process::class.java.isAssignableFrom(method.returnType)
            }?.apply {
                isAccessible = true
            }
        }.onFailure {
            AppLogger.w(TAG, "resolveNewProcessMethod failed: ${it.javaClass.simpleName}")
        }.getOrNull()
        cachedNewProcessMethod = resolved
        return resolved
    }

    private fun rewriteUiAutomatorDumpCommand(command: String): UiDumpRewriteResult {
        val normalized = command.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return UiDumpRewriteResult(command = command, redirectedPath = null)
        if (!normalized.contains("uiautomator dump")) {
            return UiDumpRewriteResult(command = command, redirectedPath = null)
        }
        val tokens = command.trim().split(Regex("\\s+"))
        val uiIndex = tokens.indexOfFirst { it.equals("uiautomator", ignoreCase = true) }
        val dumpIndex = uiIndex + 1
        if (uiIndex < 0 || dumpIndex >= tokens.size || !tokens[dumpIndex].equals("dump", ignoreCase = true)) {
            return UiDumpRewriteResult(command = command, redirectedPath = null)
        }

        val options = mutableListOf<String>()
        var rawOutputPath: String? = null
        for (i in (dumpIndex + 1) until tokens.size) {
            val token = tokens[i]
            if (token.startsWith("-")) {
                options += token
                continue
            }
            rawOutputPath = token
            break
        }

        val requestedName = rawOutputPath
            ?.trim('"', '\'')
            ?.substringAfterLast('/')
            ?.ifBlank { null }
            ?: "window_dump.xml"
        val safeName = sanitizeUiDumpFileName(requestedName)
        val targetDir = AppBuildEnv.uiDumpShellDirectory()
        val targetPath = "$targetDir/$safeName"
        val optionText = if (options.isEmpty()) "" else options.joinToString(prefix = " ", separator = " ")
        val rewritten = "mkdir -p \"$targetDir\" && uiautomator dump$optionText \"$targetPath\""
        return UiDumpRewriteResult(command = rewritten, redirectedPath = targetPath)
    }

    private fun sanitizeUiDumpFileName(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[^A-Za-z0-9._-]"""), "_")
            .trim('_')
        val withExt = if (cleaned.lowercase(Locale.ROOT).endsWith(".xml")) cleaned else "${cleaned}.xml"
        return withExt.ifBlank { "window_dump.xml" }.take(64)
    }

    fun detailedRows(): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        val state = resolveRuntimeState()

        rows += "Shizuku Binder Alive" to state.binderAlive.toString()
        rows += "Shizuku Permission Granted" to state.permissionGranted.toString()
        rows += "Shizuku Activated" to state.commandReady.toString()
        rows += "Shizuku Command Identity" to state.commandIdentity.label
        rows += "Shizuku Pre-v11" to state.preV11.toString()
        rows += "Shizuku Permission Rationale" to runCatching { Shizuku.shouldShowRequestPermissionRationale().toString() }.getOrDefault("unknown")
        state.serviceUid?.let { rows += "Shizuku Service UID" to it.toString() }

        reflectAny("getVersion")?.let { rows += "Shizuku Service Version" to it.toString() }
        reflectAny("getServerPatchVersion")?.let { rows += "Shizuku Server Patch Version" to it.toString() }
        reflectAny("getSELinuxContext")?.let { rows += "Shizuku SELinux Context" to it.toString() }
        reflectAny("getLatestServiceVersion")?.let { rows += "Shizuku Latest Service Version" to it.toString() }

        if (state.commandReady) {
            execCommand("id")?.let { rows += "Shizuku id" to it.lineSequence().firstOrNull().orEmpty() }
            execCommand("whoami")?.let { rows += "Shizuku whoami" to it.lineSequence().firstOrNull().orEmpty() }
            execCommand("uname -a")?.let { rows += "Shizuku uname" to it.lineSequence().firstOrNull().orEmpty() }
            execCommand("getenforce")?.let { rows += "Shizuku getenforce" to it.lineSequence().firstOrNull().orEmpty() }
            execCommand("ps -A | wc -l")?.let { rows += "Shizuku process count" to it.lineSequence().firstOrNull().orEmpty() }
        }

        return rows.filter { it.first.isNotBlank() && it.second.isNotBlank() }
    }

    private fun resolveRuntimeState(forceRefresh: Boolean = false): RuntimeState {
        val now = System.nanoTime()
        if (!forceRefresh) {
            cachedRuntimeState?.takeIf { it.isFresh(now) }?.let { return it.state }
        }
        val state = readRuntimeState()
        cachedRuntimeState = CachedRuntimeState(state = state, capturedAtNanos = now)
        return state
    }

    private fun readRuntimeState(): RuntimeState {
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return RuntimeState(
                binderAlive = false,
                preV11 = false,
                permissionGranted = false,
                serviceUid = null,
                commandIdentity = CommandIdentity.UNSUPPORTED
            )
        }

        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(true)
        if (preV11) {
            return RuntimeState(
                binderAlive = true,
                preV11 = true,
                permissionGranted = false,
                serviceUid = null,
                commandIdentity = CommandIdentity.UNSUPPORTED
            )
        }

        val permissionGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val serviceUid = if (permissionGranted) {
            runCatching { Shizuku.getUid() }
                .onFailure {
                    AppLogger.w(TAG, "resolveRuntimeState getUid failed: ${it.javaClass.simpleName}")
                }
                .getOrNull()
        } else {
            null
        }
        val identity = CommandIdentity.fromUid(serviceUid)

        return RuntimeState(
            binderAlive = true,
            preV11 = false,
            permissionGranted = permissionGranted,
            serviceUid = serviceUid,
            commandIdentity = identity
        )
    }

    private fun invalidateRuntimeStateCache() {
        cachedRuntimeState = null
    }

    private fun reflectAny(methodName: String): Any? {
        return runCatching {
            val method = Shizuku::class.java.methods.firstOrNull {
                it.name == methodName && it.parameterTypes.isEmpty()
            } ?: return null
            method.invoke(null)
        }.getOrNull()
    }

    private fun publishStatus(message: String) {
        val callback = statusCallback ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback(message)
        } else {
            mainHandler.post {
                statusCallback?.invoke(message)
            }
        }
    }

    companion object {
        private const val TAG = "ShizukuApiUtils"
        private const val RUNTIME_STATE_CACHE_TTL_NANOS = 750_000_000L
        const val DEFAULT_REQUEST_CODE = 1001
        const val API_VERSION = "13.1.5"
    }
}
