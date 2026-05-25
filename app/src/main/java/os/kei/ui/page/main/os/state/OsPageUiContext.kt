package os.kei.ui.page.main.os.state

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.host.pager.MainPageBackdropSet
import os.kei.ui.page.main.host.pager.rememberMainPageBackdropSet
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal data class OsPageUiContext(
    val context: Context,
    val density: Density,
    val textBundle: OsPageTextBundle,
    val backdrops: MainPageBackdropSet,
    val topBarMaterialBackdrop: Color,
    val isDark: Boolean,
    val inactiveColor: Color,
    val titleColor: Color,
    val cachedColor: Color,
    val refreshingColor: Color,
    val syncedColor: Color,
    val surfaceColor: Color,
    val searchBarHideThresholdPx: Float,
)

@Composable
internal fun rememberOsPageUiContext(
    enableFullBackdropEffects: Boolean,
    enableTopBarBackdropEffects: Boolean = enableFullBackdropEffects,
): OsPageUiContext {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textBundle = rememberOsPageTextBundle()
    val isDark = isSystemInDarkTheme()
    val backdrops =
        rememberMainPageBackdropSet(
            keyPrefix = "os",
            refreshOnCompositionEnter = true,
            distinctLayers = enableFullBackdropEffects,
        )
    val topBarMaterialBackdrop = rememberAppTopBarColor(enableBackdropEffects = enableTopBarBackdropEffects)
    val searchBarHideThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }
    val inactiveColor = MiuixTheme.colorScheme.onBackgroundVariant
    val titleColor = MiuixTheme.colorScheme.onBackground
    val surfaceColor = MiuixTheme.colorScheme.surface
    return remember(
        context,
        density,
        textBundle,
        backdrops,
        topBarMaterialBackdrop,
        isDark,
        inactiveColor,
        titleColor,
        surfaceColor,
        searchBarHideThresholdPx,
    ) {
        OsPageUiContext(
            context = context,
            density = density,
            textBundle = textBundle,
            backdrops = backdrops,
            topBarMaterialBackdrop = topBarMaterialBackdrop,
            isDark = isDark,
            inactiveColor = inactiveColor,
            titleColor = titleColor,
            cachedColor = AppStatusColors.Cached,
            refreshingColor = AppStatusColors.Refreshing,
            syncedColor = AppStatusColors.Fresh,
            surfaceColor = surfaceColor,
            searchBarHideThresholdPx = searchBarHideThresholdPx,
        )
    }
}
