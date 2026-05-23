package os.kei.ui.page.main.feedback

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun feedbackCardContainerColor(): Color =
    if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.64f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }

@Composable
internal fun feedbackDraftCardContainerColor(): Color =
    if (isSystemInDarkTheme()) {
        Color(0xFF101824).copy(alpha = 0.72f)
    } else {
        Color(0xFFF8FBFF).copy(alpha = 0.88f)
    }

@Composable
internal fun feedbackSecondaryTextColor(): Color =
    if (isSystemInDarkTheme()) {
        MiuixTheme.colorScheme.onBackgroundVariant.copy(alpha = 0.84f)
    } else {
        Color(0xFF65718A).copy(alpha = 0.94f)
    }
