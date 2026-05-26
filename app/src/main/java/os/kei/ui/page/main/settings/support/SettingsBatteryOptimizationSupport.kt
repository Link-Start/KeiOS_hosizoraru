package os.kei.ui.page.main.settings.support

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import os.kei.core.system.HyperOsSettingsIntents

@Immutable
internal data class SettingsBatteryOptimizationSnapshot(
    val ignoringBatteryOptimizations: Boolean = false,
    val requestActionAvailable: Boolean = false,
)

@Stable
internal class SettingsBatteryOptimizationController(
    private val appContext: Context,
) {
    fun loadSnapshot(): SettingsBatteryOptimizationSnapshot {
        val ignoringBatteryOptimizations = isIgnoringBatteryOptimizations(appContext)
        val requestActionAvailable =
            buildBatteryOptimizationIntent(
                context = appContext,
                alreadyIgnored = ignoringBatteryOptimizations,
            ) != null
        return SettingsBatteryOptimizationSnapshot(
            ignoringBatteryOptimizations = ignoringBatteryOptimizations,
            requestActionAvailable = requestActionAvailable,
        )
    }

    fun openBatteryOptimizationSettings(snapshot: SettingsBatteryOptimizationSnapshot): Boolean {
        val intent =
            buildBatteryOptimizationIntent(
                context = appContext,
                alreadyIgnored = snapshot.ignoringBatteryOptimizations,
            ) ?: return false
        return runCatching {
            appContext.startActivity(intent)
        }.isSuccess
    }
}

@Composable
internal fun rememberSettingsBatteryOptimizationController(context: Context): SettingsBatteryOptimizationController {
    val appContext = context.applicationContext
    return remember(appContext) {
        SettingsBatteryOptimizationController(appContext)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java) ?: return false
    return runCatching {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)
}

private fun buildBatteryOptimizationIntent(
    context: Context,
    alreadyIgnored: Boolean,
): Intent? =
    HyperOsSettingsIntents.buildBatteryOptimizationIntent(
        context = context,
        alreadyIgnored = alreadyIgnored,
    )
