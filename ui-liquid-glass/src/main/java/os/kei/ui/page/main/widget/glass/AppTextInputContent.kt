@file:Suppress("FunctionName")

package os.kei.ui.page.main.widget.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

@Immutable
data class AppTextInputContentStyle(
    val textStyle: TextStyle,
    val placeholderColor: Color,
    val cursorColor: Color = textStyle.color,
    val leadingContentGap: Dp = AppInteractiveTokens.controlContentGap,
    val placeholderMaxLines: Int = 1,
    val wrapFieldContentHeight: Boolean = true,
)

@Composable
fun AppTextInputContent(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    style: AppTextInputContentStyle,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    onFocusActiveChange: ((Boolean) -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val inputBinding = rememberAppTextInputBinding(value, onValueChange)
    val verticalContentAlignment =
        if (singleLine) {
            Alignment.CenterVertically
        } else {
            Alignment.Top
        }
    val contentAlignment = liquidInputContentAlignment(singleLine, style.textStyle.textAlign)
    val wrapContentHeightModifier =
        if (style.wrapFieldContentHeight) {
            Modifier.wrapContentHeight(align = verticalContentAlignment)
        } else {
            Modifier
        }

    @Composable
    fun TextInput(modifier: Modifier) {
        BasicTextField(
            value = inputBinding.value,
            onValueChange = inputBinding.onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            textStyle = style.textStyle,
            cursorBrush = SolidColor(style.cursorColor),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier =
                modifier
                    .then(fieldModifier)
                    .then(wrapContentHeightModifier)
                    .then(
                        if (onFocusActiveChange != null) {
                            Modifier.onFocusChanged { state ->
                                onFocusActiveChange(state.isFocused || state.hasFocus)
                            }
                        } else {
                            Modifier
                        },
                    ).then(
                        if (focusRequester != null) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        },
                    ),
            decorationBox = { innerTextField ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(wrapContentHeightModifier),
                    contentAlignment = contentAlignment,
                ) {
                    if (inputBinding.value.text.isBlank()) {
                        BasicText(
                            text = label,
                            style = style.textStyle.copy(color = style.placeholderColor),
                            maxLines = style.placeholderMaxLines.coerceAtLeast(1),
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }

    if (leadingContent == null) {
        TextInput(modifier = modifier)
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(style.leadingContentGap),
            verticalAlignment = verticalContentAlignment,
        ) {
            leadingContent()
            TextInput(modifier = Modifier.weight(1f))
        }
    }
}

internal fun liquidInputContentAlignment(
    singleLine: Boolean,
    textAlign: TextAlign,
): Alignment =
    when (textAlign) {
        TextAlign.Center -> if (singleLine) Alignment.Center else Alignment.TopCenter
        TextAlign.End, TextAlign.Right -> if (singleLine) Alignment.CenterEnd else Alignment.TopEnd
        else -> if (singleLine) Alignment.CenterStart else Alignment.TopStart
    }
