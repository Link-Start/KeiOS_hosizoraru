package os.kei.ui.page.main.about.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import os.kei.core.privilege.PrivilegedShell
import top.yukonga.miuix.kmp.theme.MiuixTheme
import os.kei.core.privilege.PrivilegeStatus

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
    val acknowledgementsExpanded: Boolean = true,
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
internal fun rememberAboutPageColorPalette(privilegeStatus: PrivilegeStatus): AboutPageColorPalette {
    val accent = MiuixTheme.colorScheme.primary
    val subtitleColor = MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.90f)
    val readyColor = Color(0xFF2E7D32)
    val notReadyColor = Color(0xFFC62828)
    val privilegeReady = privilegeStatus.isCommandReady
    val cardSurface = MiuixTheme.colorScheme.surfaceContainer
    val runtimeTint = if (privilegeReady) Color(0x2222C55E) else Color(0x22EF4444)
    return remember(privilegeStatus, accent, subtitleColor, cardSurface) {
        AboutPageColorPalette(
            accent = accent,
            subtitleColor = subtitleColor,
            readyColor = readyColor,
            notReadyColor = notReadyColor,
            infoCardColor = aboutCardColor(Color(0x223B82F6), cardSurface),
            releaseCardColor = aboutCardColor(Color(0x2222C55E), cardSurface),
            buildCardColor = aboutCardColor(Color(0x223B82F6), cardSurface),
            uiFrameworkCardColor = aboutCardColor(Color(0x2233A1F4), cardSurface),
            networkServiceCardColor = aboutCardColor(Color(0x2222C55E), cardSurface),
            mediaStorageCardColor = aboutCardColor(Color(0x2260A5FA), cardSurface),
            projectLicenseCardColor = aboutCardColor(Color(0x2243A047), cardSurface),
            licenseCardColor = aboutCardColor(Color(0x2243A047), cardSurface),
            githubCardColor = aboutCardColor(Color(0x2248A6FF), cardSurface),
            runtimeCardColor = aboutCardColor(runtimeTint, cardSurface),
            componentLabCardColor = aboutCardColor(Color(0x223B82F6), cardSurface),
        )
    }
}

/**
 * An About card's accent sitting on a material, rather than being one.
 *
 * Each tint is `0x22` — 13% alpha and nothing else — which reads as a coloured card only because an
 * opaque page used to be behind it. Over the non-Home background the wallpaper *is* the card: measured in
 * light theme with the image at its 35% default, `onBackgroundVariant` labels fell to **2.09–2.23:1**
 * against 3.04:1 on a plain page, and the illustration was plainly legible through the card body.
 *
 * Settings has the same kind of card and did not have the problem, because it fills with
 * `surfaceContainer` at 64% and only then tints. Compositing over the same surface at the same alpha
 * keeps About's per-card hues while giving both routes one answer for what a content-layer card is.
 */
internal fun aboutCardColor(
    tint: Color,
    surface: Color,
): Color = tint.compositeOver(surface.copy(alpha = ABOUT_CARD_SURFACE_ALPHA))

/** Matches `SettingsPage`'s `enabledCardColor`, so the two routes' cards read alike. */
private const val ABOUT_CARD_SURFACE_ALPHA = 0.64f
