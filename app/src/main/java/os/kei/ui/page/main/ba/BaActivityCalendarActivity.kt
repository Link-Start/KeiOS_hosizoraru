@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import os.kei.R
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.card.filterVisibleCalendarEntries
import os.kei.ui.page.main.ba.support.BaCalendarEntry
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BaActivityCalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BaStandaloneActivityTheme {
                BaActivityCalendarPage(onClose = { finish() })
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val hostActivity = context.findBaHostActivity()
            val intent =
                Intent(context, BaActivityCalendarActivity::class.java).apply {
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
private fun BaActivityCalendarPage(onClose: () -> Unit) {
    KeiOSActivityRootBackHandler(
        needsInterception = false,
        onBack = onClose,
    )

    val context = LocalContext.current
    val calendarPoolViewModel: BaCalendarPoolViewModel = applicationViewModel(create = ::BaCalendarPoolViewModel)
    val settingsUiState by calendarPoolViewModel.settingsUiState.collectAsStateWithLifecycle()
    val snapshot = settingsUiState.snapshot
    val chromeUiState by calendarPoolViewModel.chromeUiState.collectAsStateWithLifecycle()
    val calendarUiState by calendarPoolViewModel.calendarUiState.collectAsStateWithLifecycle()
    val serverOptions =
        listOf(
            stringResource(R.string.ba_server_cn),
            stringResource(R.string.ba_server_global),
            stringResource(R.string.ba_server_jp),
        )
    val serverIndex = chromeUiState.serverIndex
    val reloadSignal = chromeUiState.calendarReloadSignal
    val hydrationReady = settingsUiState.loaded
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val accent = MiuixTheme.colorScheme.primary
    val serverTimeZone = serverRefreshTimeZone(serverIndex)
    val syncText =
        when {
            calendarUiState.loading || calendarUiState.refreshing -> {
                stringResource(R.string.ba_syncing)
            }

            calendarUiState.lastSyncMs > 0L -> {
                formatBaDateTimeNoYearInTimeZone(
                    calendarUiState.lastSyncMs,
                    serverTimeZone,
                )
            }

            else -> {
                stringResource(R.string.ba_state_not_synced)
            }
        }
    val countdownBlue = AppStatusColors.Refreshing

    DisposableEffect(calendarPoolViewModel) {
        onDispose { calendarPoolViewModel.clearServerPopup() }
    }

    LaunchedEffect(serverIndex, reloadSignal, snapshot.calendarRefreshIntervalHours, hydrationReady) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = true,
            serverIndex = serverIndex,
            reloadSignal = reloadSignal,
            calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
            hydrationReady = hydrationReady,
        )
    }

    val refreshIconRotation =
        if (calendarUiState.loading || calendarUiState.refreshing) {
            val loadingRotation by rememberInfiniteTransition(label = "ba_activity_calendar_refresh_rotation")
                .animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "ba_activity_calendar_refresh_rotation_value",
                )
            loadingRotation
        } else {
            0f
        }

    val pageTitle =
        stringResource(
            R.string.ba_calendar_title_format,
            serverOptions[serverIndex],
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
                contentDescription = stringResource(R.string.ba_calendar_cd_refresh),
                onClick = calendarPoolViewModel::requestCalendarReload,
                iconModifier =
                    Modifier.graphicsLayer {
                        rotationZ = refreshIconRotation
                    },
                width = 52.dp,
                height = 52.dp,
                variant = GlassVariant.Bar,
                iconTint =
                    if (calendarUiState.loading || calendarUiState.refreshing) {
                        countdownBlue
                    } else {
                        MiuixTheme.colorScheme.primary
                    },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MiuixTheme.colorScheme.background,
                                        accent.copy(alpha = if (isSystemInDarkTheme()) 0.11f else 0.07f),
                                        MiuixTheme.colorScheme.background,
                                    ),
                            ),
                        ).layerBackdrop(pageBackdrop),
            )
            BaActivityCalendarListContent(
                innerPadding = innerPadding,
                listState = listState,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                backdrop = pageBackdrop,
                serverOptions = serverOptions,
                serverIndex = serverIndex,
                showServerPopup = chromeUiState.showServerPopup,
                serverPopupAnchorBounds = chromeUiState.serverPopupAnchorBounds,
                showEndedActivities = snapshot.showEndedActivities,
                showCalendarPoolImages = snapshot.showCalendarPoolImages,
                entries = calendarUiState.entries,
                loading = calendarUiState.loading,
                refreshing = calendarUiState.refreshing,
                error = calendarUiState.error,
                syncText = syncText,
                syncTextColor = countdownBlue,
                onServerPopupChange = calendarPoolViewModel::updateServerPopupExpanded,
                onServerPopupAnchorBoundsChange = calendarPoolViewModel::updateServerPopupAnchorBounds,
                onServerSelected = { selected ->
                    val normalized = selected.coerceIn(serverOptions.indices)
                    calendarPoolViewModel.selectServer(normalized)
                },
                onOpenCalendarLink = { url ->
                    openBaExternalLink(context = context, url = url)
                },
            )
        }
    }
}

@Composable
private fun BaActivityCalendarListContent(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    backdrop: com.kyant.backdrop.Backdrop,
    serverOptions: List<String>,
    serverIndex: Int,
    showServerPopup: Boolean,
    serverPopupAnchorBounds: IntRect?,
    showEndedActivities: Boolean,
    showCalendarPoolImages: Boolean,
    entries: List<BaCalendarEntry>,
    loading: Boolean,
    refreshing: Boolean,
    error: String?,
    syncText: String,
    syncTextColor: Color,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    onOpenCalendarLink: (String) -> Unit,
) {
    val nowMs = rememberBaMinuteTickMs(enabled = !loading && entries.isNotEmpty())
    val visibleEntries =
        remember(
            entries,
            showEndedActivities,
            nowMs,
        ) {
            filterVisibleCalendarEntries(
                entries = entries,
                showEndedActivities = showEndedActivities,
                nowMs = nowMs,
            )
        }
    AppPageLazyColumn(
        innerPadding = innerPadding,
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
        bottomExtra = 40.dp,
        sectionSpacing = 14.dp,
    ) {
        item(
            key = "ba-calendar-server-panel",
            contentType = "ba_calendar_server_panel",
        ) {
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
        baActivityCalendarEntryItems(
            backdrop = backdrop,
            serverIndex = serverIndex,
            visibleEntries = visibleEntries,
            loading = loading,
            refreshing = refreshing,
            error = error,
            showEndedActivities = showEndedActivities,
            showCalendarPoolImages = showCalendarPoolImages,
            nowMs = nowMs,
            syncTextColor = syncTextColor,
            onOpenCalendarLink = onOpenCalendarLink,
        )
    }
}
