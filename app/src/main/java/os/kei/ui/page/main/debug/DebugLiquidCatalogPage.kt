@file:Suppress("FunctionName")

package os.kei.ui.page.main.debug

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import os.kei.ui.page.main.widget.isAppInDarkTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixOverscrollFactory
import os.kei.ui.testing.KeiOsTestTags
import os.kei.ui.testing.pageRootTestTag

@Composable
internal fun DebugLiquidCatalogPage(onClose: () -> Unit) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val accent = MiuixTheme.colorScheme.primary
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)

    AppPageScaffold(
        title = stringResource(R.string.debug_component_lab_liquid_catalog_title),
        modifier = Modifier.fillMaxSize().pageRootTestTag(KeiOsTestTags.DebugLiquidCatalogPageRoot),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop,
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MiuixTheme.colorScheme.background,
                                        accent.copy(alpha = if (isAppInDarkTheme()) 0.12f else 0.08f),
                                        MiuixTheme.colorScheme.background,
                                    ),
                            ),
                        ).layerBackdrop(pageBackdrop),
            )
            CompositionLocalProvider(
                LocalLiquidParentBackdrop provides pageBackdrop,
                // Pilot: MIUI spring overscroll on the catalog page only. Placement-translation
                // based (no RenderEffect), so it exercises how bounce moves liquid-glass sampling
                // before any global rollout replaces the app-wide overscroll disable.
                LocalOverscrollFactory provides MiuixOverscrollFactory,
            ) {
                AppPageLazyColumn(
                    innerPadding = innerPadding,
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    bottomExtra = 40.dp,
                    sectionSpacing = 14.dp,
                    overscrollEffect = rememberOverscrollEffect(),
                ) {
                    item {
                        DebugLiquidCatalogIntroCard(accent = accent)
                    }
                    item {
                        DebugLiquidButtonsCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidGlassDropdownCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidSearchFormCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidChromeCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidActionMenuCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidFeedbackCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidSheetCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidBackdropCard(accent = accent)
                    }
                    item {
                        DebugLiquidTransparentButtonsCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidSurfaceCardsCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidParentBackdropCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugMiuixTextureBlurCard(accent = accent)
                    }
                    item {
                        DebugLiquidParameterCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                    item {
                        DebugLiquidControlsCard(
                            accent = accent,
                            backdrop = pageBackdrop,
                        )
                    }
                }
            }
        }
    }
}
