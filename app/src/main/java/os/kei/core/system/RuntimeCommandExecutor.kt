package os.kei.core.system

data class RuntimeCommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val cancelled: Boolean = false
) {
    val succeeded: Boolean
        get() = exitCode == 0 && !timedOut && !cancelled
}

object RuntimeCommandExecutor {
    private const val DEFAULT_TIMEOUT_MS = 5_000L

    fun execute(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): RuntimeCommandResult {
        val result = AppCommandExecutor.execute(command = command, timeoutMs = timeoutMs)
        return RuntimeCommandResult(
            stdout = result.stdout,
            stderr = result.stderr,
            exitCode = result.exitCode,
            timedOut = result.timedOut,
            cancelled = result.cancelled
        )
    }
}
