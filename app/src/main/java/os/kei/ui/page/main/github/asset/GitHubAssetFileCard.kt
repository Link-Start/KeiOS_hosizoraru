@file:Suppress("FunctionName")

package os.kei.ui.page.main.github.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.core.AppCardBodyColumn
import os.kei.ui.page.main.widget.core.AppSurfaceCard
import os.kei.ui.page.main.widget.core.AppTypographyTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GitHubAssetFileCard(
    title: String,
    containerColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    titleColor: Color = MiuixTheme.colorScheme.onBackground,
    titleMaxLines: Int = 3,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    verticalSpacing: Dp = 7.dp,
    captureLocalBackdrop: Boolean = true,
    onClick: (() -> Unit)? = null,
    pills: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    AppSurfaceCard(
        modifier = modifier,
        containerColor = containerColor,
        borderColor = borderColor,
        captureLocalBackdrop = captureLocalBackdrop,
        onClick = onClick,
    ) {
        AppCardBodyColumn(
            contentPadding = contentPadding,
            verticalSpacing = verticalSpacing,
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = titleColor,
                fontSize = AppTypographyTokens.Body.fontSize,
                lineHeight = AppTypographyTokens.Body.lineHeight,
                fontWeight = AppTypographyTokens.BodyEmphasis.fontWeight,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                content = { pills() },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                actions()
            }
            supportingContent?.invoke()
        }
    }
}
