@file:Suppress("FunctionName")

package os.kei.ui.page.main.jsonimport

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

@Composable
internal fun KeiOSJsonImportPage(
    state: KeiOSJsonImportUiState,
    onConfirmImport: () -> Unit,
    onOpenResult: (KeiOSJsonImportKind) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

    KeiOSActivityRootBackHandler(
        needsInterception = state.busy,
        onBack = onClose,
    )

    AppPageScaffold(
        title = stringResource(R.string.json_import_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = false,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        KeiOSJsonImportContent(
            innerPadding = innerPadding,
            listState = listState,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection,
            pageBackdrop = pageBackdrop,
            state = state,
            onConfirmImport = onConfirmImport,
            onOpenResult = onOpenResult,
            onClose = onClose,
        )
    }
}
