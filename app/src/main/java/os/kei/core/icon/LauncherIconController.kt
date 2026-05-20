package os.kei.core.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ComponentEnabledSetting
import os.kei.BuildConfig
import os.kei.core.prefs.LauncherIconDesign

object LauncherIconController {
    fun applyDesign(
        context: Context,
        design: LauncherIconDesign,
    ) {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val settings = design.componentStateSpecs(appContext.packageName)
        runCatching {
            packageManager.setComponentEnabledSettings(
                settings.map { spec ->
                    ComponentEnabledSetting(
                        spec.component.componentName(),
                        spec.enabledState,
                        PackageManager.DONT_KILL_APP,
                    )
                },
            )
        }.onFailure {
            settings.forEach { spec ->
                packageManager.setComponentEnabledSetting(
                    spec.component.componentName(),
                    spec.enabledState,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

internal data class LauncherIconComponentStateSpec(
    val component: LauncherIconComponentSpec,
    val enabledState: Int,
)

internal fun LauncherIconDesign.componentStateSpecs(packageName: String): List<LauncherIconComponentStateSpec> =
    buildList {
        LauncherIconDesign.entries
            .filter { it != this@componentStateSpecs }
            .forEach { inactiveDesign ->
                add(
                    LauncherIconComponentStateSpec(
                        component = inactiveDesign.componentSpec(packageName),
                        enabledState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    ),
                )
            }
        add(
            LauncherIconComponentStateSpec(
                component = componentSpec(packageName),
                enabledState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            ),
        )
    }

internal data class LauncherIconComponentSpec(
    val packageName: String,
    val className: String,
)

internal fun LauncherIconDesign.componentName(packageName: String): ComponentName =
    componentSpec(packageName).let { spec ->
        spec.componentName()
    }

internal fun LauncherIconComponentSpec.componentName(): ComponentName = ComponentName(packageName, className)

internal fun LauncherIconDesign.componentSpec(packageName: String): LauncherIconComponentSpec =
    LauncherIconComponentSpec(
        packageName = packageName,
        className = qualifiedAliasClassName(),
    )

internal fun LauncherIconDesign.qualifiedAliasClassName(): String = "${BuildConfig.MANIFEST_COMPONENT_PACKAGE}.$aliasClassName"
