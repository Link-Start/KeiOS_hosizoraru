@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import os.kei.feature.github.model.InstalledAppItem

private const val GITHUB_APP_ICON_PRELOAD_LIMIT = 96

@Composable
internal fun BindGitHubAppIconPreloadEffect(
    active: Boolean,
    trackedPackages: List<String>,
    appList: List<InstalledAppItem>,
    selectedPackageName: String,
    pickerExpanded: Boolean,
    appPickerFilteredPackages: List<String>,
    requestAppIcons: (List<String>) -> Unit,
) {
    val packageNames =
        remember(
            active,
            trackedPackages,
            appList,
            selectedPackageName,
            pickerExpanded,
            appPickerFilteredPackages,
        ) {
            buildGitHubAppIconPreloadPackages(
                trackedPackages = trackedPackages,
                appList = appList,
                selectedPackageName = selectedPackageName,
                pickerExpanded = pickerExpanded,
                appPickerFilteredPackages = appPickerFilteredPackages,
            )
        }
    LaunchedEffect(active, packageNames) {
        if (!active) return@LaunchedEffect
        requestAppIcons(packageNames)
    }
}

internal fun buildGitHubAppIconPreloadPackages(
    trackedPackages: List<String>,
    appList: List<InstalledAppItem>,
    selectedPackageName: String,
    pickerExpanded: Boolean,
    appPickerFilteredPackages: List<String>,
): List<String> {
    val packages = linkedSetOf<String>()

    fun addPackage(packageName: String) {
        val normalized = packageName.trim()
        if (normalized.isNotBlank()) {
            packages.add(normalized)
        }
    }
    trackedPackages.take(GITHUB_APP_ICON_PRELOAD_LIMIT).forEach(::addPackage)
    addPackage(selectedPackageName)
    if (pickerExpanded) {
        appPickerFilteredPackages
            .take(GITHUB_APP_ICON_PRELOAD_LIMIT)
            .forEach(::addPackage)
    } else {
        appList
            .asSequence()
            .map { it.packageName }
            .take(12)
            .forEach(::addPackage)
    }
    return packages.toList()
}
