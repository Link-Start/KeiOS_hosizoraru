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
        // ComponentName.packageName must match the installed applicationId, while the alias
        // class name is resolved from the manifest namespace.
        val targetComponent = design.componentName(appContext.packageName)
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
                        inactiveDesign.componentName(appContext.packageName),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
        }
    }
}

internal data class LauncherIconComponentSpec(
    val packageName: String,
    val className: String,
)

internal fun LauncherIconDesign.componentName(packageName: String): ComponentName =
    componentSpec(packageName).let { spec ->
        ComponentName(spec.packageName, spec.className)
    }

internal fun LauncherIconDesign.componentSpec(packageName: String): LauncherIconComponentSpec =
    LauncherIconComponentSpec(
        packageName = packageName,
        className = qualifiedAliasClassName(),
    )

internal fun LauncherIconDesign.qualifiedAliasClassName(): String = "${BuildConfig.MANIFEST_COMPONENT_PACKAGE}.$aliasClassName"
