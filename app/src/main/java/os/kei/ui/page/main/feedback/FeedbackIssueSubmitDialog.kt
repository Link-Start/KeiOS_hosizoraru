@file:Suppress("FunctionName")

package os.kei.ui.page.main.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.os.appLucideWarningIcon
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import os.kei.ui.page.main.widget.glass.AppLiquidDialogActionButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun FeedbackSubmitConfirmDialog(
    mode: FeedbackSubmitMode?,
    apiTokenAvailable: Boolean,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmBrowser: () -> Unit,
    onConfirmApi: () -> Unit,
) {
    val confirmText =
        when (mode) {
            FeedbackSubmitMode.Browser -> stringResource(R.string.feedback_issue_confirm_browser)
            FeedbackSubmitMode.GitHubApi -> stringResource(R.string.feedback_issue_confirm_api)
            null -> ""
        }
    val summary =
        when (mode) {
            FeedbackSubmitMode.Browser -> {
                stringResource(R.string.feedback_issue_confirm_browser_summary)
            }

            FeedbackSubmitMode.GitHubApi -> {
                if (apiTokenAvailable) {
                    stringResource(R.string.feedback_issue_confirm_api_summary)
                } else {
                    stringResource(R.string.feedback_issue_confirm_api_missing_token)
                }
            }

            null -> {
                ""
            }
        }
    WindowDialog(
        show = mode != null,
        title = stringResource(R.string.feedback_issue_confirm_title),
        summary = summary,
        onDismissRequest = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            FeedbackConfirmChecklist()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                )
                AppLiquidDialogActionButton(
                    modifier = Modifier.weight(1f),
                    text = confirmText,
                    enabled = !submitting,
                    containerColor = MiuixTheme.colorScheme.primary,
                    variant = GlassVariant.SheetPrimaryAction,
                    onClick = {
                        when (mode) {
                            FeedbackSubmitMode.Browser -> onConfirmBrowser()
                            FeedbackSubmitMode.GitHubApi -> onConfirmApi()
                            null -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedbackConfirmChecklist() {
    val items =
        listOf(
            stringResource(R.string.feedback_issue_confirm_check_public),
            stringResource(R.string.feedback_issue_confirm_check_sensitive),
            stringResource(R.string.feedback_issue_confirm_check_steps),
            stringResource(R.string.feedback_issue_confirm_check_zip),
        )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = appLucideWarningIcon(),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = item,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    fontSize = AppTypographyTokens.Supporting.fontSize,
                    lineHeight = AppTypographyTokens.Supporting.lineHeight,
                )
            }
        }
    }
}
