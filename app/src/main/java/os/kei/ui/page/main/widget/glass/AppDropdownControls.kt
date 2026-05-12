package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.sheet.SnapshotPopupPlacement
import os.kei.ui.page.main.widget.sheet.SnapshotWindowListPopup
import os.kei.ui.page.main.widget.sheet.capturePopupAnchor
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DropdownNeutralTint = Color(0xFF3B82F6)
private val DropdownSelectorMinWidth = 152.dp
private val DropdownSelectorScreenHorizontalMargin = 18.dp
private val DropdownSelectorHorizontalChrome = 78.dp

private fun dropdownAnchorTint(
    textColor: Color,
    variant: GlassVariant
): Color {
    return if (textColor.alpha <= 0f) {
        when (variant) {
            GlassVariant.SheetDangerAction -> Color(0xFFE25B6A)
            else -> DropdownNeutralTint
        }
    } else {
        textColor
    }
}

@Composable
fun AppDropdownAnchorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    variant: GlassVariant = GlassVariant.SheetAction,
    enabled: Boolean = true,
    textColor: Color = MiuixTheme.colorScheme.primary,
    minHeight: Dp = AppInteractiveTokens.compactAppLiquidTextButtonMinHeight,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
    textMaxLines: Int = 1,
    textOverflow: TextOverflow = TextOverflow.Ellipsis,
    textSoftWrap: Boolean = false,
    textSize: TextUnit = AppTypographyTokens.Body.fontSize,
    textLineHeight: TextUnit = AppTypographyTokens.Body.lineHeight,
    textFontWeight: FontWeight = AppTypographyTokens.BodyEmphasis.fontWeight
) {
    val accentColor = dropdownAnchorTint(textColor = textColor, variant = variant)
    if (backdrop != null) {
        AppLiquidTextButton(
            backdrop = backdrop,
            text = text,
            onClick = onClick,
            modifier = modifier,
            textColor = textColor,
            containerColor = accentColor,
            enabled = enabled,
            variant = variant,
            minHeight = minHeight,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            textMaxLines = textMaxLines,
            textOverflow = textOverflow,
            textSoftWrap = textSoftWrap,
            textSize = textSize,
            textLineHeight = textLineHeight,
            textFontWeight = textFontWeight
        )
    } else {
        AppStandaloneLiquidTextButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            textColor = textColor,
            containerColor = accentColor,
            enabled = enabled,
            variant = variant,
            minHeight = minHeight,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            textMaxLines = textMaxLines,
            textOverflow = textOverflow,
            textSoftWrap = textSoftWrap,
            textSize = textSize,
            textLineHeight = textLineHeight,
            textFontWeight = textFontWeight
        )
    }
}

@Composable
fun AppDropdownSelector(
    selectedText: String,
    options: List<String>,
    selectedIndex: Int,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onSelectedIndexChange: (Int) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    variant: GlassVariant = GlassVariant.SheetAction,
    textColor: Color = MiuixTheme.colorScheme.primary,
    minHeight: Dp = AppInteractiveTokens.compactAppLiquidTextButtonMinHeight,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 6.dp,
    anchorFillMaxWidth: Boolean = false,
    anchorTextMaxLines: Int = 1,
    anchorTextOverflow: TextOverflow = TextOverflow.Ellipsis,
    anchorTextSoftWrap: Boolean = false,
    anchorTextSize: TextUnit = AppTypographyTokens.Body.fontSize,
    anchorTextLineHeight: TextUnit = AppTypographyTokens.Body.lineHeight,
    anchorTextFontWeight: FontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
    dropdownItemTextMaxLines: Int = 1,
    popupMaxWidth: Dp? = 280.dp,
    popupMatchAnchorWidth: Boolean = false,
    anchorAlignment: Alignment = Alignment.CenterStart,
    alignment: PopupPositionProvider.Align = PopupPositionProvider.Align.BottomEnd,
    placement: SnapshotPopupPlacement = SnapshotPopupPlacement.ButtonEnd,
    enabled: Boolean = true
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val maxScreenWidth =
        (configuration.screenWidthDp.dp - DropdownSelectorScreenHorizontalMargin * 2)
            .coerceAtLeast(DropdownSelectorMinWidth)
    val resolvedMaxWidth = (popupMaxWidth ?: maxScreenWidth)
        .coerceAtMost(maxScreenWidth)
        .coerceAtLeast(DropdownSelectorMinWidth)
    val anchorWidth = remember(anchorBounds, density) {
        anchorBounds?.let { bounds ->
            with(density) { bounds.width.toDp() }
        } ?: 0.dp
    }
    val optionTextStyle = TextStyle(
        fontSize = if (dropdownItemTextMaxLines == 1) {
            AppTypographyTokens.Body.fontSize
        } else {
            AppTypographyTokens.Supporting.fontSize
        },
        lineHeight = if (dropdownItemTextMaxLines == 1) {
            AppTypographyTokens.Body.lineHeight
        } else {
            AppTypographyTokens.Supporting.lineHeight
        },
        fontWeight = FontWeight.Medium
    )
    val measuredOptionWidth = remember(
        options,
        dropdownItemTextMaxLines,
        optionTextStyle,
        density,
        textMeasurer
    ) {
        val textWidthPx = options.maxOfOrNull { option ->
            textMeasurer.measure(
                text = AnnotatedString(option),
                style = optionTextStyle,
                maxLines = dropdownItemTextMaxLines,
                overflow = if (dropdownItemTextMaxLines == 1) {
                    TextOverflow.Ellipsis
                } else {
                    TextOverflow.Clip
                }
            ).size.width
        } ?: 0
        with(density) { textWidthPx.toDp() } + DropdownSelectorHorizontalChrome
    }
    val resolvedPopupWidth = maxOf(
        DropdownSelectorMinWidth,
        measuredOptionWidth,
        if (popupMatchAnchorWidth) anchorWidth else 0.dp
    ).coerceAtMost(resolvedMaxWidth)

    Box(
        modifier = modifier.capturePopupAnchor { onAnchorBoundsChange(it) },
        contentAlignment = anchorAlignment
    ) {
        AppDropdownAnchorButton(
            text = selectedText,
            onClick = { onExpandedChange(!expanded) },
            modifier = if (anchorFillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            backdrop = backdrop,
            variant = variant,
            enabled = enabled && options.isNotEmpty(),
            textColor = textColor,
            minHeight = minHeight,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            textMaxLines = anchorTextMaxLines,
            textOverflow = anchorTextOverflow,
            textSoftWrap = anchorTextSoftWrap,
            textSize = anchorTextSize,
            textLineHeight = anchorTextLineHeight,
            textFontWeight = anchorTextFontWeight
        )
        if (expanded && enabled && options.isNotEmpty()) {
            SnapshotWindowListPopup(
                show = true,
                alignment = alignment,
                anchorBounds = anchorBounds,
                placement = placement,
                onDismissRequest = { onExpandedChange(false) },
                enableWindowDim = false,
                minWidth = resolvedPopupWidth,
                maxWidth = resolvedPopupWidth,
                matchAnchorWidth = false
            ) {
                val accentColor = dropdownAnchorTint(textColor = textColor, variant = variant)
                AppLiquidGlassDropdownColumn(
                    modifier = Modifier.fillMaxWidth(),
                    minWidth = resolvedPopupWidth,
                    maxWidth = resolvedPopupWidth,
                    accentColor = accentColor,
                    initialScrollItemIndex = selectedIndex,
                    backdrop = backdrop
                ) {
                    DropdownSelectorChoiceList(
                        options = options,
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = onSelectedIndexChange,
                        onExpandedChange = onExpandedChange,
                        accentColor = accentColor,
                        variant = variant,
                        textMaxLines = dropdownItemTextMaxLines
                    )
                }
            }
        }
    }
}

@Composable
private fun DropdownSelectorChoiceList(
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    accentColor: Color,
    variant: GlassVariant,
    textMaxLines: Int
) {
    LiquidGlassDropdownSingleChoiceList(
        options = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { selected ->
            onSelectedIndexChange(selected)
            onExpandedChange(false)
        },
        accentColor = accentColor,
        variant = variant,
        modifier = Modifier.fillMaxWidth(),
        textMaxLines = textMaxLines
    )
}
