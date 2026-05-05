package os.kei.ui.page.main.ba

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.card.BaPoolEntryPanel
import os.kei.ui.page.main.ba.card.BaPoolStatePanel
import os.kei.ui.page.main.ba.card.filterVisiblePoolEntries
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.student.BaStudentGuideStore
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.page.BaStudentGuidePage
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.core.AppAronaLoadingPanel
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BaPoolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BaStandaloneActivityTheme {
                BaPoolRoot(onClose = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val hostActivity = context.findBaHostActivity()
            val intent = Intent(context, BaPoolActivity::class.java).apply {
                if (hostActivity == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (hostActivity != null) {
                hostActivity.startActivity(intent)
            } else {
                context.startActivity(intent)
            }
        }
    }
}

@Composable
private fun BaPoolRoot(
    onClose: () -> Unit,
) {
    var guideOpen by remember { mutableStateOf(false) }
    var guideNonce by remember { mutableLongStateOf(0L) }

    if (guideOpen) {
        key(guideNonce) {
            BaStudentGuidePage(
                liquidActionBarLayeredStyleEnabled = true,
                onBack = { guideOpen = false },
            )
        }
    } else {
        BaPoolPage(
            onClose = onClose,
            onOpenGuide = {
                guideNonce = System.nanoTime()
                guideOpen = true
            },
        )
    }
}

@Composable
private fun BaPoolPage(
    onClose: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = remember { BASettingsStore.loadSnapshot() }
    val calendarPoolViewModel: BaCalendarPoolViewModel = viewModel()
    val poolUiState by calendarPoolViewModel.poolUiState.collectAsState()
    val serverOptions = listOf(
        stringResource(R.string.ba_server_cn),
        stringResource(R.string.ba_server_global),
        stringResource(R.string.ba_server_jp)
    )
    var serverIndex by remember { mutableIntStateOf(snapshot.serverIndex) }
    var showServerPopup by remember { mutableStateOf(false) }
    var serverPopupAnchorBounds by remember { mutableStateOf<IntRect?>(null) }
    var reloadSignal by remember { mutableIntStateOf(0) }
    var hydrationReady by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val accent = MiuixTheme.colorScheme.primary
    val serverTimeZone = serverRefreshTimeZone(serverIndex)
    val syncText = when {
        poolUiState.loading -> stringResource(R.string.ba_syncing)
        poolUiState.lastSyncMs > 0L -> formatBaDateTimeNoYearInTimeZone(
            poolUiState.lastSyncMs,
            serverTimeZone
        )

        else -> stringResource(R.string.ba_state_not_synced)
    }
    val countdownBlue = Color(0xFF60A5FA)

    LaunchedEffect(Unit) {
        hydrationReady = true
    }

    LaunchedEffect(
        serverIndex,
        reloadSignal,
        snapshot.calendarRefreshIntervalHours,
        hydrationReady
    ) {
        calendarPoolViewModel.syncPool(
            isPageActive = true,
            serverIndex = serverIndex,
            reloadSignal = reloadSignal,
            calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
            hydrationReady = hydrationReady
        )
    }

    val refreshIconRotation = if (poolUiState.loading) {
        val loadingRotation by rememberInfiniteTransition(label = "ba_pool_refresh_rotation")
            .animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "ba_pool_refresh_rotation_value",
            )
        loadingRotation
    } else {
        0f
    }

    val pageTitle = stringResource(
        R.string.ba_pool_title_format,
        serverOptions[serverIndex]
    )

    AppPageScaffold(
        title = pageTitle,
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
        titleBackdrop = pageBackdrop,
        reserveTopEndActionSpace = true,
        navigationIcon = {
            AppLiquidNavigationButton(
                icon = appLucideBackIcon(),
                contentDescription = stringResource(R.string.common_close),
                onClick = onClose,
                backdrop = pageBackdrop,
            )
        },
        actions = {
            AppLiquidIconButton(
                backdrop = pageBackdrop,
                icon = appLucideRefreshIcon(),
                contentDescription = stringResource(R.string.ba_pool_cd_refresh),
                onClick = { reloadSignal += 1 },
                iconModifier = Modifier.graphicsLayer {
                    rotationZ = refreshIconRotation
                },
                width = 52.dp,
                height = 52.dp,
                variant = GlassVariant.Bar,
                iconTint = if (poolUiState.loading) {
                    countdownBlue
                } else {
                    MiuixTheme.colorScheme.primary
                },
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MiuixTheme.colorScheme.background,
                                accent.copy(alpha = if (isSystemInDarkTheme()) 0.11f else 0.07f),
                                MiuixTheme.colorScheme.background
                            )
                        )
                    )
                    .layerBackdrop(pageBackdrop)
            )
            BaPoolListContent(
                innerPadding = innerPadding,
                listState = listState,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                backdrop = pageBackdrop,
                serverOptions = serverOptions,
                serverIndex = serverIndex,
                showServerPopup = showServerPopup,
                serverPopupAnchorBounds = serverPopupAnchorBounds,
                showEndedPools = snapshot.showEndedPools,
                showCalendarPoolImages = snapshot.showCalendarPoolImages,
                entries = poolUiState.entries,
                loading = poolUiState.loading,
                error = poolUiState.error,
                syncText = syncText,
                syncTextColor = countdownBlue,
                onServerPopupChange = { showServerPopup = it },
                onServerPopupAnchorBoundsChange = { serverPopupAnchorBounds = it },
                onServerSelected = { selected ->
                    val normalized = selected.coerceIn(serverOptions.indices)
                    serverIndex = normalized
                    BASettingsStore.saveServerIndex(normalized)
                    showServerPopup = false
                },
                onOpenPoolStudentGuide = { url ->
                    openBaPoolGuideLink(
                        context = context,
                        rawUrl = url,
                        onOpenGuide = onOpenGuide
                    )
                },
                onOpenCalendarLink = { url ->
                    openBaExternalLink(context = context, url = url)
                },
            )
        }
    }
}

@Composable
private fun BaPoolListContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    backdrop: com.kyant.backdrop.Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    showServerPopup: Boolean,
    serverPopupAnchorBounds: IntRect?,
    showEndedPools: Boolean,
    showCalendarPoolImages: Boolean,
    entries: List<BaPoolEntry>,
    loading: Boolean,
    error: String?,
    syncText: String,
    syncTextColor: Color,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenCalendarLink: (String) -> Unit,
) {
    val nowMs = rememberBaStandaloneTickMs(enabled = !loading && error.isNullOrBlank())
    val visibleEntries = remember(
        entries,
        showEndedPools,
        nowMs
    ) {
        filterVisiblePoolEntries(
            entries = entries,
            showEndedPools = showEndedPools,
            nowMs = nowMs
        )
    }
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
        bottomExtra = 40.dp,
        sectionSpacing = 14.dp
    ) {
        item {
            BaCalendarPoolServerPanel(
                backdrop = backdrop,
                serverOptions = serverOptions,
                serverIndex = serverIndex,
                syncText = syncText,
                syncTextColor = syncTextColor,
                expanded = showServerPopup,
                anchorBounds = serverPopupAnchorBounds,
                onExpandedChange = onServerPopupChange,
                onAnchorBoundsChange = onServerPopupAnchorBoundsChange,
                onServerSelected = onServerSelected,
            )
        }
        when {
            loading -> {
                item {
                    BaPoolLoadingPanel(accentColor = syncTextColor)
                }
            }

            !error.isNullOrBlank() -> {
                item {
                    BaPoolStatePanel(
                        backdrop = backdrop,
                        text = error.orEmpty(),
                        accentColor = Color(0xFFF59E0B),
                        effectsEnabled = true,
                    )
                }
            }

            visibleEntries.isEmpty() -> {
                item {
                    BaPoolStatePanel(
                        backdrop = backdrop,
                        text = if (showEndedPools) {
                            stringResource(R.string.ba_pool_empty_all)
                        } else {
                            stringResource(R.string.ba_pool_empty_active)
                        },
                        accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                        effectsEnabled = true,
                    )
                }
            }

            else -> {
                items(visibleEntries.size) { index ->
                    val pool = visibleEntries[index]
                    BaPoolEntryPanel(
                        backdrop = backdrop,
                        isPageActive = true,
                        serverIndex = serverIndex,
                        pool = pool,
                        nowMs = nowMs,
                        showCalendarPoolImages = showCalendarPoolImages,
                        effectsEnabled = true,
                        onOpenPoolStudentGuide = onOpenPoolStudentGuide,
                        onOpenCalendarLink = onOpenCalendarLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun BaPoolLoadingPanel(
    accentColor: Color,
) {
    AppAronaLoadingPanel(accent = accentColor)
}

private val baGuideDetailPathRegex = Regex("""^/ba/tj/\d+(?:\.html)?$""", RegexOption.IGNORE_CASE)

private fun openBaPoolGuideLink(
    context: Context,
    rawUrl: String,
    onOpenGuide: () -> Unit,
) {
    val normalized = normalizeGuideUrl(rawUrl)
    if (normalized.isBlank()) {
        Toast.makeText(
            context,
            context.getString(R.string.main_toast_pool_guide_missing),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val uri = runCatching { normalized.toUri() }.getOrNull()
    val host = uri?.host?.lowercase().orEmpty()
    val hostAccepted = host == "www.gamekee.com" || host == "gamekee.com"
    if (hostAccepted && baGuideDetailPathRegex.matches(uri?.path.orEmpty())) {
        val contentId = extractGuideContentIdFromUrl(normalized)
        if (contentId != null && contentId > 0L) {
            BaStudentGuideStore.setCurrentUrl("https://www.gamekee.com/ba/tj/$contentId.html")
            onOpenGuide()
            return
        }
        Toast.makeText(
            context,
            context.getString(R.string.main_toast_pool_guide_missing),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    openBaStandaloneExternalLink(context, normalized)
}

private fun openBaStandaloneExternalLink(
    context: Context,
    url: String,
) {
    val intent = SafeExternalIntents.browsableViewIntent(url, newTask = true)
    if (intent == null) {
        Toast.makeText(
            context,
            context.getString(R.string.ba_error_open_activity_link),
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.ba_error_open_activity_link),
            Toast.LENGTH_SHORT
        ).show()
    }
}
