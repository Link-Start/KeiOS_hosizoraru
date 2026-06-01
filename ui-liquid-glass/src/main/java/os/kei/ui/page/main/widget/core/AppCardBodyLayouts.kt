package os.kei.ui.page.main.widget.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppCardBodyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CardLayoutRhythm.cardContentPadding,
    verticalSpacing: Dp = CardLayoutRhythm.sectionGap,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content
    )
}

@Composable
fun AppInfoListBody(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = CardLayoutRhythm.compactSectionGap,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCardBodyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(0.dp),
        verticalSpacing = verticalSpacing,
        content = content
    )
}
