package os.kei.core.shortcut

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.MainActivity
import os.kei.R
import os.kei.core.background.AppForegroundInfoHandler
import os.kei.core.background.AppShortcutGitHubRefreshResult
import os.kei.core.log.AppLogger
import os.kei.core.platform.LocalNetworkPermissionCompat
import os.kei.core.system.ShizukuApiUtils
import os.kei.mcp.server.LocalMcpService
import os.kei.mcp.server.McpServerManager
import os.kei.mcp.server.McpServerRuntimeRegistry
import os.kei.ui.page.main.ba.BaApIslandShortcutNotificationCoordinator

internal object AppShortcutActionHandler {
    private const val TAG = "AppShortcutAction"

    suspend fun handle(context: Context, request: AppShortcutActionRequest) {
        when {
            request.mcpServerAction == MainActivity.MCP_SERVER_ACTION_TOGGLE -> {
                toggleMcpServer(context)
            }

            request.shortcutAction == MainActivity.SHORTCUT_ACTION_BA_AP_ISLAND -> {
                sendBaApIsland(context)
            }

            request.shortcutAction == MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED -> {
                refreshGitHubTracked(context)
            }
        }
    }

    private suspend fun sendBaApIsland(context: Context) {
        val sent = BaApIslandShortcutNotificationCoordinator.send(context)
        if (!sent) {
            context.toast(R.string.ba_toast_notification_permission_required)
        }
    }

    private suspend fun refreshGitHubTracked(context: Context) {
        val result = AppForegroundInfoHandler.handleGitHubShortcutRefresh(context)
        when (result) {
            AppShortcutGitHubRefreshResult.NoTrackedItems -> {
                context.toast(R.string.github_toast_no_checkable_item)
            }

            AppShortcutGitHubRefreshResult.Completed -> Unit
        }
    }

    private suspend fun toggleMcpServer(context: Context) {
        val runningManager = McpServerRuntimeRegistry.currentManager()
        if (runningManager != null) {
            runningManager.stop()
            context.toast(R.string.mcp_toast_service_stopped)
            return
        }

        val manager = createMcpServerManager(context)
        val state = manager.uiState.value
        if (state.allowExternal && !LocalNetworkPermissionCompat.hasPermission(context)) {
            context.toast(R.string.mcp_toast_local_network_permission_requested)
            return
        }
        val result = manager.start(
            port = state.port,
            allowExternal = state.allowExternal
        )
        result.onSuccess {
            context.toast(R.string.mcp_toast_service_started)
        }.onFailure { error ->
            AppLogger.w(TAG, "MCP shortcut start failed", error)
            context.toast(
                R.string.mcp_toast_start_failed,
                error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            )
        }
    }

    private fun createMcpServerManager(context: Context): McpServerManager {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val appLabel = runCatching {
            packageManager.getApplicationLabel(appContext.applicationInfo).toString()
        }.getOrDefault(appContext.getString(R.string.app_name))
        val packageInfo = runCatching {
            packageManager.getPackageInfoCompat(appContext.packageName)
        }.getOrNull()
        val service = LocalMcpService(
            appContext = appContext,
            shizukuApiUtils = ShizukuApiUtils(),
            appVersionName = packageInfo?.versionName ?: "unknown",
            appVersionCode = packageInfo?.longVersionCode ?: -1L,
            appPackageName = appContext.packageName,
            appLabel = appLabel
        )
        return McpServerManager(
            appContext = appContext,
            localMcpService = service
        )
    }

    private suspend fun Context.toast(resId: Int) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(this@toast, getString(resId), Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun Context.toast(resId: Int, vararg args: Any) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(this@toast, getString(resId, *args), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo {
    return getPackageInfo(packageName, 0)
}
