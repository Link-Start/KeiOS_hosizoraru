@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.sheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.support.CopyModeSelectionContainer
import os.kei.ui.page.main.widget.support.copyModeAwareRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
) {
    val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
internal fun GitHubDecisionDetailTextLine(
    text: String,
    maxLines: Int = 3,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    CopyModeSelectionContainer(
        modifier =
            modifier.copyModeAwareRow(
                copyPayload = text,
            ),
    ) {
        Text(
            text = text,
            color = if (accent) MiuixTheme.colorScheme.onBackground else MiuixTheme.colorScheme.onBackgroundVariant,
            fontSize = if (accent) AppTypographyTokens.Body.fontSize else AppTypographyTokens.Supporting.fontSize,
            lineHeight = if (accent) AppTypographyTokens.Body.lineHeight else AppTypographyTokens.Supporting.lineHeight,
            fontWeight = if (accent) AppTypographyTokens.BodyEmphasis.fontWeight else null,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
