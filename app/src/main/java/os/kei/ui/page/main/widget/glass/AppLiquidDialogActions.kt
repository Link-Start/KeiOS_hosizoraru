package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppLiquidDialogActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    containerColor: Color? = null,
    textColor: Color = containerColor ?: MiuixTheme.colorScheme.primary,
    leadingIcon: ImageVector? = null,
    iconTint: Color = textColor,
    variant: GlassVariant = if (containerColor != null) {
        GlassVariant.SheetPrimaryAction
    } else {
        GlassVariant.SheetAction
    }
) {
    AppStandaloneLiquidTextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        buttonModifier = buttonModifier,
        textColor = textColor,
        containerColor = containerColor,
        leadingIcon = leadingIcon,
        iconTint = iconTint,
        enabled = enabled,
        variant = variant,
        minHeight = 40.dp,
        horizontalPadding = 12.dp,
        verticalPadding = 8.dp,
        textMaxLines = 1,
        textOverflow = TextOverflow.Ellipsis,
        textSoftWrap = false
    )
}
