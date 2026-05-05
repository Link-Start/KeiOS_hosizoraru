package os.kei.ui.page.main.about.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Stable
internal class AboutPageSectionExpansionState {
    var appExpanded by mutableStateOf(true)
    var runtimeExpanded by mutableStateOf(false)
    var permissionExpanded by mutableStateOf(false)
    var componentExpanded by mutableStateOf(false)
    var buildExpanded by mutableStateOf(false)
    var uiFrameworkExpanded by mutableStateOf(false)
    var githubExpanded by mutableStateOf(false)
    var networkExpanded by mutableStateOf(false)
    var mediaExpanded by mutableStateOf(false)
    var projectLicenseExpanded by mutableStateOf(false)
    var licenseExpanded by mutableStateOf(false)
    var componentLabExpanded by mutableStateOf(true)
}

internal data class AboutPageColorPalette(
    val accent: Color,
    val subtitleColor: Color,
    val readyColor: Color,
    val notReadyColor: Color,
    val infoCardColor: Color,
    val buildCardColor: Color,
    val uiFrameworkCardColor: Color,
    val networkServiceCardColor: Color,
    val mediaStorageCardColor: Color,
    val projectLicenseCardColor: Color,
    val licenseCardColor: Color,
    val githubCardColor: Color,
    val runtimeCardColor: Color,
    val componentLabCardColor: Color
)

@Composable
internal fun rememberAboutPageSectionExpansionState(): AboutPageSectionExpansionState {
    val appExpanded = rememberSaveable { mutableStateOf(true) }
    val runtimeExpanded = rememberSaveable { mutableStateOf(false) }
    val permissionExpanded = rememberSaveable { mutableStateOf(false) }
    val componentExpanded = rememberSaveable { mutableStateOf(false) }
    val buildExpanded = rememberSaveable { mutableStateOf(false) }
    val uiFrameworkExpanded = rememberSaveable { mutableStateOf(false) }
    val githubExpanded = rememberSaveable { mutableStateOf(false) }
    val networkExpanded = rememberSaveable { mutableStateOf(false) }
    val mediaExpanded = rememberSaveable { mutableStateOf(false) }
    val projectLicenseExpanded = rememberSaveable { mutableStateOf(false) }
    val licenseExpanded = rememberSaveable { mutableStateOf(false) }
    val componentLabExpanded = rememberSaveable { mutableStateOf(true) }
    return remember {
        AboutPageSectionExpansionState().apply {
            this.appExpanded = appExpanded.value
            this.runtimeExpanded = runtimeExpanded.value
            this.permissionExpanded = permissionExpanded.value
            this.componentExpanded = componentExpanded.value
            this.buildExpanded = buildExpanded.value
            this.uiFrameworkExpanded = uiFrameworkExpanded.value
            this.githubExpanded = githubExpanded.value
            this.networkExpanded = networkExpanded.value
            this.mediaExpanded = mediaExpanded.value
            this.projectLicenseExpanded = projectLicenseExpanded.value
            this.licenseExpanded = licenseExpanded.value
            this.componentLabExpanded = componentLabExpanded.value
        }
    }.also { state ->
        appExpanded.value = state.appExpanded
        runtimeExpanded.value = state.runtimeExpanded
        permissionExpanded.value = state.permissionExpanded
        componentExpanded.value = state.componentExpanded
        buildExpanded.value = state.buildExpanded
        uiFrameworkExpanded.value = state.uiFrameworkExpanded
        githubExpanded.value = state.githubExpanded
        networkExpanded.value = state.networkExpanded
        mediaExpanded.value = state.mediaExpanded
        projectLicenseExpanded.value = state.projectLicenseExpanded
        licenseExpanded.value = state.licenseExpanded
        componentLabExpanded.value = state.componentLabExpanded
    }
}

@Composable
internal fun rememberAboutPageColorPalette(shizukuStatus: String): AboutPageColorPalette {
    val accent = MiuixTheme.colorScheme.primary
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val readyColor = Color(0xFF2E7D32)
    val notReadyColor = Color(0xFFC62828)
    val runtimeCardColor = if (shizukuStatus.contains("granted", ignoreCase = true)) {
        Color(0x2222C55E)
    } else {
        Color(0x22EF4444)
    }
    return remember(shizukuStatus, accent, subtitleColor) {
        AboutPageColorPalette(
            accent = accent,
            subtitleColor = subtitleColor,
            readyColor = readyColor,
            notReadyColor = notReadyColor,
            infoCardColor = Color(0x223B82F6),
            buildCardColor = Color(0x223B82F6),
            uiFrameworkCardColor = Color(0x2233A1F4),
            networkServiceCardColor = Color(0x2222C55E),
            mediaStorageCardColor = Color(0x2260A5FA),
            projectLicenseCardColor = Color(0x2243A047),
            licenseCardColor = Color(0x2243A047),
            githubCardColor = Color(0x2248A6FF),
            runtimeCardColor = runtimeCardColor,
            componentLabCardColor = Color(0x223B82F6)
        )
    }
}
