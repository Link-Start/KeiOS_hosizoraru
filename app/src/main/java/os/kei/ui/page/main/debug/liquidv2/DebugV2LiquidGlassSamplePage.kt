package os.kei.ui.page.main.debug.liquidv2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideConfigIcon
import os.kei.ui.page.main.os.appLucideGridIcon
import os.kei.ui.page.main.os.appLucideHomeIcon
import os.kei.ui.page.main.os.appLucideLayersIcon
import os.kei.ui.page.main.os.appLucideSearchIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Immutable
private data class V2SampleDestination(
    val title: String,
    val icon: ImageVector,
    val page: V2SamplePage
)

internal enum class V2SamplePage {
    Surfaces,
    Controls,
    Inputs,
    Navigation,
    Scenarios
}

@Composable
internal fun DebugV2LiquidGlassSamplePage(
    onClose: () -> Unit
) {
    val scrollBehavior = MiuixScrollBehavior()
    val rootBackdrop = rememberLayerBackdrop()
    val palette = rememberV2LiquidGlassPalette()
    val pagerState = rememberPagerState(pageCount = { V2SamplePage.entries.size })
    val coroutineScope = rememberCoroutineScope()
    val destinations = rememberV2SampleDestinations()
    var pageScrollInProgress by remember { mutableStateOf(false) }
    var heavyReady by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.settledPage) {
        pageScrollInProgress = false
    }
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress) {
        heavyReady = false
        if (!pagerState.isScrollInProgress) {
            delay(260)
            heavyReady = true
        }
    }
    val tabItems = remember(destinations) {
        destinations.map {
            V2GlassTabItem(
                label = it.title,
                icon = it.icon,
                contentDescription = it.title
            )
        }
    }

    AppPageScaffold(
        title = stringResource(R.string.debug_v2_liquid_sample_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = Color.Transparent,
        titleBackdrop = rootBackdrop,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = rootBackdrop
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MiuixTheme.colorScheme.background,
                                palette.accent.copy(alpha = if (isSystemInDarkTheme()) 0.13f else 0.08f),
                                MiuixTheme.colorScheme.background
                            )
                        )
                    )
                    .layerBackdrop(rootBackdrop)
            ) {
                V2LiquidPageBackdropPattern(
                    accent = palette.accent,
                    modifier = Modifier.matchParentSize()
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(bottom = 94.dp),
                beyondViewportPageCount = 0,
                pageSpacing = 12.dp,
                verticalAlignment = Alignment.Top
            ) { pageIndex ->
                val destination = destinations[pageIndex]
                val active = heavyReady &&
                        !pagerState.isScrollInProgress &&
                        pageIndex == pagerState.settledPage
                V2LiquidGlassSampleContent(
                    page = destination.page,
                    active = active,
                    rootBackdrop = rootBackdrop,
                    onScrollInProgressChange = { scrolling ->
                        if (active) {
                            pageScrollInProgress = scrolling
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            V2GlassBottomTabs(
                items = tabItems,
                selectedIndex = pagerState.targetPage,
                onSelectedIndexChange = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                backdrop = rootBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                compact = true,
                scrollBehavior = V2LiquidDockScrollBehavior(
                    collapsed = pagerState.isScrollInProgress || pageScrollInProgress
                ),
                spec = V2LiquidDockSpec(
                    height = 62.dp,
                    itemMinWidth = 48.dp,
                    outerPadding = 4.dp,
                    indicatorInset = 5.dp,
                    selectedBlobStyle = V2LiquidMaterialStyle.Dock
                )
            )
        }
    }
}

@Composable
private fun rememberV2SampleDestinations(): List<V2SampleDestination> {
    val surfacesTitle = stringResource(R.string.debug_v2_liquid_page_surfaces)
    val controlsTitle = stringResource(R.string.debug_v2_liquid_page_controls)
    val inputsTitle = stringResource(R.string.debug_v2_liquid_page_inputs)
    val navigationTitle = stringResource(R.string.debug_v2_liquid_page_navigation)
    val scenariosTitle = stringResource(R.string.debug_v2_liquid_page_scenarios)
    val layersIcon = appLucideLayersIcon()
    val configIcon = appLucideConfigIcon()
    val searchIcon = appLucideSearchIcon()
    val homeIcon = appLucideHomeIcon()
    val gridIcon = appLucideGridIcon()
    return remember(
        surfacesTitle,
        controlsTitle,
        inputsTitle,
        navigationTitle,
        scenariosTitle,
        layersIcon,
        configIcon,
        searchIcon,
        homeIcon,
        gridIcon
    ) {
        listOf(
            V2SampleDestination(
                title = surfacesTitle,
                icon = layersIcon,
                page = V2SamplePage.Surfaces
            ),
            V2SampleDestination(
                title = controlsTitle,
                icon = configIcon,
                page = V2SamplePage.Controls
            ),
            V2SampleDestination(
                title = inputsTitle,
                icon = searchIcon,
                page = V2SamplePage.Inputs
            ),
            V2SampleDestination(
                title = navigationTitle,
                icon = homeIcon,
                page = V2SamplePage.Navigation
            ),
            V2SampleDestination(
                title = scenariosTitle,
                icon = gridIcon,
                page = V2SamplePage.Scenarios
            )
        )
    }
}

@Composable
private fun V2LiquidPageBackdropPattern(
    accent: Color,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    Canvas(modifier = modifier) {
        val baseAlpha = if (isDark) 0.18f else 0.12f
        drawRoundRect(
            color = accent.copy(alpha = baseAlpha),
            topLeft = Offset(size.width * 0.62f, size.height * 0.08f),
            size = Size(size.width * 0.48f, size.height * 0.22f),
            cornerRadius = CornerRadius(size.height * 0.10f)
        )
        drawRoundRect(
            color = Color(0xFF4FD8C8).copy(alpha = if (isDark) 0.13f else 0.10f),
            topLeft = Offset(size.width * -0.12f, size.height * 0.32f),
            size = Size(size.width * 0.66f, size.height * 0.26f),
            cornerRadius = CornerRadius(size.height * 0.12f)
        )
        drawRoundRect(
            color = Color(0xFFFF5C8A).copy(alpha = if (isDark) 0.10f else 0.08f),
            topLeft = Offset(size.width * 0.20f, size.height * 0.72f),
            size = Size(size.width * 0.72f, size.height * 0.20f),
            cornerRadius = CornerRadius(size.height * 0.10f)
        )
    }
}
