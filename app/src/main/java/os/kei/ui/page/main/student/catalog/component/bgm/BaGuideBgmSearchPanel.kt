@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.component.bgm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import os.kei.R
import os.kei.ui.page.main.widget.glass.AppLiquidSearchField
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaGuideBgmSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    backdrop: Backdrop,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(54.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppLiquidSearchField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.ba_catalog_bgm_search_placeholder),
            backdrop = backdrop,
            modifier = Modifier.fillMaxSize(),
            textColor = MiuixTheme.colorScheme.onBackground,
            variant = GlassVariant.Content,
            horizontalPadding = 18.dp,
            verticalPadding = 0.dp,
            focusRequester = focusRequester,
        )
    }
}
