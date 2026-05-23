@file:Suppress("FunctionName")

package os.kei.ui.page.main.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.RoundedRectangle
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FeedbackLiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    minHeight: Dp,
    singleLine: Boolean = false,
) {
    val isBody = minHeight > 120.dp
    val textStyle =
        TextStyle(
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = if (isBody) 14.sp else AppTypographyTokens.Body.fontSize,
            lineHeight = if (isBody) 20.sp else AppTypographyTokens.Body.lineHeight,
            textAlign = TextAlign.Start,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val placeholderStyle = textStyle.copy(color = feedbackSecondaryTextColor())
    FeedbackLiquidPanel(
        minHeight = minHeight,
        fixedHeight = isBody,
    ) {
        val fieldHeight = minHeight - 24.dp
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (isBody) {
                            Modifier.height(fieldHeight)
                        } else {
                            Modifier.heightIn(min = fieldHeight)
                        },
                    ),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopStart,
                ) {
                    if (value.isBlank()) {
                        BasicText(
                            text = label,
                            style = placeholderStyle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.TopStart,
                    ) {
                        innerTextField()
                    }
                }
            },
        )
    }
}

@Composable
internal fun FeedbackFieldLabel(text: String) {
    Text(
        text = text,
        color = feedbackSecondaryTextColor(),
        fontSize = AppTypographyTokens.Caption.fontSize,
        lineHeight = AppTypographyTokens.Caption.lineHeight,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun FeedbackLiquidPanel(
    minHeight: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    fixedHeight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedRectangle(18.dp)
    val borderColor =
        if (isDark) {
            Color(0xFF8ABEFF).copy(alpha = 0.24f)
        } else {
            Color(0xFFB5D7FF).copy(alpha = 0.82f)
        }
    val panelColor =
        if (isDark) {
            Color(0xFF121A24).copy(alpha = 0.78f)
        } else {
            Color.White.copy(alpha = 0.88f)
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (fixedHeight) {
                        Modifier.height(minHeight)
                    } else {
                        Modifier.heightIn(min = minHeight)
                    },
                ).background(panelColor, shape)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .padding(contentPadding),
        content = content,
    )
}
