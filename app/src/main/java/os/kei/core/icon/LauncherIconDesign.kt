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
            return entries.firstOrNull { it.storageId == normalized } ?: Android
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
        // Activity-alias components are registered under the base package (os.kei), not the
        // suffixed debug/benchmark package. Use MANIFEST_COMPONENT_PACKAGE for the ComponentName
        // package so the system can resolve the alias regardless of applicationIdSuffix.
        val componentPackage = BuildConfig.MANIFEST_COMPONENT_PACKAGE
        val targetComponent = design.componentName(componentPackage)
        runCatching {
            packageManager.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            LauncherIconDesign.entries
                .filter { it != design }
                .forEach { inactiveDesign ->
                    packageManager.setComponentEnabledSetting(
                        inactiveDesign.componentName(componentPackage),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
        }
    }
}

internal fun LauncherIconDesign.componentName(packageName: String): ComponentName = ComponentName(packageName, qualifiedAliasClassName())

internal fun LauncherIconDesign.qualifiedAliasClassName(): String = "${BuildConfig.MANIFEST_COMPONENT_PACKAGE}.$aliasClassName"
