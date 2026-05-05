package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.chrome.AppChromeTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppFloatingLiquidActionButton(
    backdrop: Backdrop?,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AppChromeTokens.floatingBottomBarOuterHeight,
    iconSize: Dp = 27.dp,
    iconTint: Color = MiuixTheme.colorScheme.primary,
    enabled: Boolean = true,
    iconModifier: Modifier = Modifier
) {
    AppLiquidIconButton(
        backdrop = backdrop,
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        width = size,
        height = size,
        variant = GlassVariant.Bar,
        iconTint = iconTint,
        iconModifier = iconModifier.then(Modifier.size(iconSize)),
        enabled = enabled
    )
}
