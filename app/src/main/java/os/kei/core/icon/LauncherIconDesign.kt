package os.kei.core.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import os.kei.BuildConfig

enum class LauncherIconDesign(
    val storageId: String,
    val aliasClassName: String,
) {
    Apple("apple", "LauncherAppleDesigns"),
    Android("android", "LauncherAndroidDesigns"),
    ;

    companion object {
        fun fromStorageId(raw: String?): LauncherIconDesign {
            val normalized = raw.orEmpty().trim()
            return entries.firstOrNull { it.storageId == normalized } ?: Apple
        }
    }
}

object LauncherIconController {
    fun applyDesign(
        context: Context,
        design: LauncherIconDesign,
    ) {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val packageName = appContext.packageName
        val targetComponent = design.componentName(packageName)
        packageManager.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        LauncherIconDesign.entries
            .filter { it != design }
            .forEach { inactiveDesign ->
                packageManager.setComponentEnabledSetting(
                    inactiveDesign.componentName(packageName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
    }
}

internal fun LauncherIconDesign.componentName(packageName: String): ComponentName = ComponentName(packageName, qualifiedAliasClassName())

internal fun LauncherIconDesign.qualifiedAliasClassName(): String = "${BuildConfig::class.java.packageName}.$aliasClassName"
