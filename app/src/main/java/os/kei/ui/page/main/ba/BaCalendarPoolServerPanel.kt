package os.kei.ui.page.main.ba

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppDropdownSelector
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaCalendarPoolServerPanel(
    backdrop: Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    syncText: String,
    syncTextColor: Color,
    expanded: Boolean,
    anchorBounds: IntRect?,
    onExpandedChange: (Boolean) -> Unit,
    onAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        accentColor = MiuixTheme.colorScheme.primary,
        variant = GlassVariant.Content,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ba_overview_server_label),
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = syncText,
                color = syncTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppDropdownSelector(
                selectedText = serverOptions[serverIndex],
                options = serverOptions,
                selectedIndex = serverIndex,
                expanded = expanded,
                anchorBounds = anchorBounds,
                onExpandedChange = onExpandedChange,
                onSelectedIndexChange = onServerSelected,
                onAnchorBoundsChange = onAnchorBoundsChange,
                backdrop = backdrop,
                variant = GlassVariant.Content,
            )
        }
    }
}
