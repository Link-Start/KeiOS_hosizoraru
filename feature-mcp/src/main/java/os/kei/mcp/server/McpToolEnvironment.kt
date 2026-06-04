package os.kei.mcp.server

import android.content.Context
import os.kei.core.shizuku.ShizukuApiUtils
import java.util.Locale

class McpToolEnvironment(
    val appContext: Context,
    val shizukuApiUtils: ShizukuApiUtils,
    val appVersionName: String,
    val appVersionCode: Long,
    val appPackageName: String,
    val appLabel: String,
    private val stateProvider: () -> McpServerUiState?,
    private val toolCallLogger: (
        name: String,
        profile: McpToolExecutionProfile,
        elapsedMs: Long,
        success: Boolean,
        error: String?
    ) -> Unit
) {
    fun currentState(): McpServerUiState? = stateProvider()

    fun currentLocale(): Locale {
        val configuration = appContext.resources.configuration
        return configuration.locales[0] ?: Locale.getDefault()
    }

    fun recordToolCall(
        name: String,
        profile: McpToolExecutionProfile,
        elapsedMs: Long,
        success: Boolean,
        error: String?
    ) {
        toolCallLogger(name, profile, elapsedMs, success, error)
    }
}
