package os.kei.ui.page.main.about.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import os.kei.core.shizuku.ShizukuApiUtils
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
internal data class AboutPageSectionExpansionState(
    val appExpanded: Boolean = true,
    val releaseExpanded: Boolean = true,
    val runtimeExpanded: Boolean = false,
    val permissionExpanded: Boolean = false,
    val componentExpanded: Boolean = false,
    val buildExpanded: Boolean = false,
    val uiFrameworkExpanded: Boolean = false,
    val githubExpanded: Boolean = false,
    val networkExpanded: Boolean = false,
    val mediaExpanded: Boolean = false,
    val projectLicenseExpanded: Boolean = false,
    val licenseExpanded: Boolean = false,
    val componentLabExpanded: Boolean = true,
)

@Immutable
internal data class AboutPageColorPalette(
    val accent: Color,
    val subtitleColor: Color,
    val readyColor: Color,
    val notReadyColor: Color,
    val infoCardColor: Color,
    val releaseCardColor: Color,
    val buildCardColor: Color,
    val uiFrameworkCardColor: Color,
    val networkServiceCardColor: Color,
    val mediaStorageCardColor: Color,
    val projectLicenseCardColor: Color,
    val licenseCardColor: Color,
    val githubCardColor: Color,
    val runtimeCardColor: Color,
    val componentLabCardColor: Color,
)

@Composable
internal fun rememberAboutPageColorPalette(shizukuStatus: String): AboutPageColorPalette {
    val accent = MiuixTheme.colorScheme.primary
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val readyColor = Color(0xFF2E7D32)
    val notReadyColor = Color(0xFFC62828)
    val shizukuReady = ShizukuApiUtils.isCommandReadyStatusText(shizukuStatus)
    val runtimeCardColor =
        if (shizukuReady) {
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
            releaseCardColor = Color(0x2222C55E),
            buildCardColor = Color(0x223B82F6),
            uiFrameworkCardColor = Color(0x2233A1F4),
            networkServiceCardColor = Color(0x2222C55E),
            mediaStorageCardColor = Color(0x2260A5FA),
            projectLicenseCardColor = Color(0x2243A047),
            licenseCardColor = Color(0x2243A047),
            githubCardColor = Color(0x2248A6FF),
            runtimeCardColor = runtimeCardColor,
            componentLabCardColor = Color(0x223B82F6),
        )
    }
}
