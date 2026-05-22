@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import os.kei.R
import os.kei.ui.page.main.widget.glass.LiquidCircularProgressBar
import os.kei.ui.page.main.widget.status.StatusPill
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GitHubShareImportResolvingContent(phase: GitHubShareImportPhase) {
    Column(
        modifier =
            Modifier
                .shareImportSheetSafeArea()
                .heightIn(min = 236.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LiquidCircularProgressBar(size = 24.dp)
        Spacer(modifier = Modifier.height(12.dp))
        StatusPill(
            label = stringResource(phase.labelRes),
            color = phase.color,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.github_share_import_dialog_summary_parsing),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}
