@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

private const val GITHUB_APP_ICON_PRELOAD_LIMIT = 96

@Composable
internal fun BindGitHubAppIconPreloadEffect(
    active: Boolean,
    trackedPackages: List<String>,
    installedPackages: List<String>,
    selectedPackageName: String,
    pickerExpanded: Boolean,
    appPickerFilteredPackages: List<String>,
    requestAppIcons: (List<String>) -> Unit,
) {
    val packageNames =
        remember(
            active,
            trackedPackages,
            installedPackages,
            selectedPackageName,
            pickerExpanded,
            appPickerFilteredPackages,
        ) {
            buildGitHubAppIconPreloadPackages(
                trackedPackages = trackedPackages,
                installedPackages = installedPackages,
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
    installedPackages: List<String>,
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
        installedPackages.forEach(::addPackage)
    }
    return packages.toList()
}
