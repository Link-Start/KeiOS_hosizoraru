@file:Suppress("FunctionName")

package os.kei.ui.page.main.os.shell

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppTextInputContent
import os.kei.ui.page.main.widget.glass.AppTextInputContentStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ShellCommandInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    minHeight: Dp = 136.dp,
    focusRequestToken: Int = 0,
    /**
     * Take the whole pane rather than [minHeight].
     *
     * Only meaningful inside a bounded parent. The editor is then as tall as the split, so a long command
     * scrolls inside the field instead of growing the card, and the whole pane is a tap target for the
     * caret rather than the first four lines of it.
     */
    fillHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val textStyle =
        TextStyle(
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = AppTypographyTokens.Body.fontSize,
            lineHeight = AppTypographyTokens.Body.lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    val promptStyle = textStyle.copy(color = MiuixTheme.colorScheme.primary)
    val placeholderStyle = textStyle.copy(color = MiuixTheme.colorScheme.onBackgroundVariant)
    val inputContentStyle =
        remember(textStyle, placeholderStyle, promptStyle.color) {
            AppTextInputContentStyle(
                textStyle = textStyle,
                placeholderColor = placeholderStyle.color,
                cursorColor = promptStyle.color,
                leadingContentGap = 8.dp,
                placeholderMaxLines = 1,
                wrapFieldContentHeight = false,
            )
        }

    ShellLiquidPanelSurface(
        modifier =
            modifier
                .fillMaxWidth(),
        minHeight = minHeight,
    ) {
        AppTextInputContent(
            value = value,
            onValueChange = onValueChange,
            label = label,
            style = inputContentStyle,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
            fieldModifier =
                if (fillHeight) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.heightIn(min = minHeight - 24.dp)
                },
            enabled = true,
            readOnly = false,
            singleLine = false,
            minLines = 1,
            maxLines = Int.MAX_VALUE,
            keyboardOptions = KeyboardOptions.Default,
            keyboardActions = KeyboardActions.Default,
            focusRequester = focusRequester,
            leadingContent = {
                BasicText(
                    text = "$",
                    style = promptStyle,
                    maxLines = 1,
                )
            },
        )
    }
}
