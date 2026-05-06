package os.kei.ui.page.main.settings.page

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.ui.page.main.settings.state.SettingsPageViewModel
import os.kei.ui.page.main.settings.support.SettingsBatteryOptimizationController
import os.kei.ui.page.main.settings.support.SettingsPermissionKeepAliveController
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun BindSettingsPageEffects(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    scope: CoroutineScope,
    settingsPageViewModel: SettingsPageViewModel,
    batteryOptimizationController: SettingsBatteryOptimizationController,
    permissionKeepAliveController: SettingsPermissionKeepAliveController,
    notificationPermissionGranted: Boolean,
    shizukuStatus: String,
    cacheDiagnosticsEnabled: Boolean,
    logDebugEnabled: Boolean,
    shizukuRefreshToken: Int
) {
    val latestNotificationPermissionGranted = rememberUpdatedState(notificationPermissionGranted)
    val latestShizukuStatus = rememberUpdatedState(shizukuStatus)
    DisposableEffect(lifecycleOwner, batteryOptimizationController, permissionKeepAliveController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationController.refresh()
                scope.launch {
                    permissionKeepAliveController.refresh(
                        notificationPermissionGranted = latestNotificationPermissionGranted.value,
                        shizukuStatus = latestShizukuStatus.value
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(notificationPermissionGranted, shizukuStatus) {
        permissionKeepAliveController.refresh(
            notificationPermissionGranted = notificationPermissionGranted,
            shizukuStatus = shizukuStatus
        )
    }
    LaunchedEffect(context, cacheDiagnosticsEnabled) {
        settingsPageViewModel.bindCacheDiagnostics(
            context = context,
            enabled = cacheDiagnosticsEnabled
        )
    }
    LaunchedEffect(context, logDebugEnabled) {
        settingsPageViewModel.bindLogStats(
            context = context,
            logDebugEnabled = logDebugEnabled
        )
    }
    LaunchedEffect(shizukuRefreshToken) {
        if (shizukuRefreshToken <= 0) return@LaunchedEffect
        repeat(8) {
            permissionKeepAliveController.refresh(
                notificationPermissionGranted = latestNotificationPermissionGranted.value,
                shizukuStatus = latestShizukuStatus.value
            )
            delay(400.milliseconds)
        }
    }
}
