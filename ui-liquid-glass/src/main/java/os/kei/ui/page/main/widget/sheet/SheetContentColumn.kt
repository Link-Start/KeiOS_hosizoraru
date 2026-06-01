package os.kei.ui.page.main.widget.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import os.kei.ui.page.main.widget.chrome.AppChromeTokens

@Composable
fun SheetContentColumn(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    verticalSpacing: Dp = AppChromeTokens.pageSectionGapLarge,
    content: @Composable () -> Unit,
) {
    val scrollState = rememberScrollState()
    val overflowReporter by rememberUpdatedState(LocalLiquidSheetContentOverflowReporter.current)
    val scrollModifier =
        if (scrollable) {
            Modifier.verticalScroll(scrollState)
        } else {
            Modifier
        }
    LaunchedEffect(scrollable, scrollState.maxValue) {
        overflowReporter(scrollable && scrollState.maxValue > 0)
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        content()
    }
}
