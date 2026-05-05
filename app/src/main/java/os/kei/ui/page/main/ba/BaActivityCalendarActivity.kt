package os.kei.ui.page.main.ba

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.ExperimentalActivityApi
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import os.kei.R
import os.kei.core.platform.PredictiveBackOemCompat
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.card.BaCalendarEntryPanel
import os.kei.ui.page.main.ba.card.BaCalendarStatePanel
import os.kei.ui.page.main.ba.card.filterVisibleCalendarEntries
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.os.appLucideRefreshIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.glass.AppLiquidIconButton
import os.kei.ui.page.main.widget.glass.GlassVariant
import os.kei.ui.page.main.widget.motion.LocalPredictiveBackAnimationsEnabled
import os.kei.ui.page.main.widget.motion.LocalTransitionAnimationsEnabled
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

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
            val intent = Intent(context, BaActivityCalendarActivity::class.java).apply {
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
private fun BaStandaloneActivityTheme(content: @Composable () -> Unit) {
    val colorSchemeMode = when (UiPrefs.getAppThemeMode()) {
        AppThemeMode.FOLLOW_SYSTEM -> ColorSchemeMode.System
        AppThemeMode.LIGHT -> ColorSchemeMode.Light
        AppThemeMode.DARK -> ColorSchemeMode.Dark
    }
    val transitionAnimationsEnabled = UiPrefs.isTransitionAnimationsEnabled()
    val predictiveBackPolicy = PredictiveBackOemCompat.currentPolicy(
        transitionAnimationsEnabled = transitionAnimationsEnabled,
        predictiveBackAnimationsEnabled = UiPrefs.isPredictiveBackAnimationsEnabled()
    )

    MiuixTheme(controller = ThemeController(colorSchemeMode)) {
        CompositionLocalProvider(
            LocalTransitionAnimationsEnabled provides transitionAnimationsEnabled,
            LocalPredictiveBackAnimationsEnabled provides predictiveBackPolicy.frameworkAnimationsEnabled,
        ) {
            content()
        }
    }
}

@Composable
private fun BaActivityCalendarPage(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot = remember { BASettingsStore.loadSnapshot() }
    val calendarPoolViewModel: BaCalendarPoolViewModel = viewModel()
    val calendarUiState by calendarPoolViewModel.calendarUiState.collectAsState()
    val serverOptions = listOf(
        stringResource(R.string.ba_server_cn),
        stringResource(R.string.ba_server_global),
        stringResource(R.string.ba_server_jp)
    )
    var serverIndex by remember { mutableIntStateOf(snapshot.serverIndex) }
    var reloadSignal by remember { mutableIntStateOf(0) }
    var hydrationReady by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val pageBackdrop = rememberLayerBackdrop()
    val topBarColor = rememberAppTopBarColor(enableBackdropEffects = true)
    val accent = MiuixTheme.colorScheme.primary
    val visibleEntries = remember(
        calendarUiState.entries,
        snapshot.showEndedActivities,
        nowMs
    ) {
        filterVisibleCalendarEntries(
            entries = calendarUiState.entries,
            showEndedActivities = snapshot.showEndedActivities,
            nowMs = nowMs
        )
    }
    val serverTimeZone = serverRefreshTimeZone(serverIndex)
    val syncText = when {
        calendarUiState.loading -> stringResource(R.string.ba_syncing)
        calendarUiState.lastSyncMs > 0L -> formatBaDateTimeNoYearInTimeZone(
            calendarUiState.lastSyncMs,
            serverTimeZone
        )

        else -> stringResource(R.string.ba_state_not_synced)
    }
    val countdownBlue = Color(0xFF60A5FA)

    LaunchedEffect(Unit) {
        hydrationReady = true
    }

    LaunchedEffect(serverIndex, reloadSignal, snapshot.calendarRefreshIntervalHours, hydrationReady) {
        calendarPoolViewModel.syncCalendar(
            isPageActive = true,
            serverIndex = serverIndex,
            reloadSignal = reloadSignal,
            calendarRefreshIntervalHours = snapshot.calendarRefreshIntervalHours,
            hydrationReady = hydrationReady
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    BaActivityCalendarBackHandler(onBack = onClose)

    AppPageScaffold(
        title = stringResource(R.string.ba_calendar_activity_title),
        modifier = Modifier.fillMaxSize(),
        scrollBehavior = scrollBehavior,
        topBarColor = topBarColor,
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
                onClick = { reloadSignal += 1 },
                width = 52.dp,
                height = 52.dp,
                variant = GlassVariant.Bar,
                iconTint = if (calendarUiState.loading) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onBackground
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
            AppPageLazyColumn(
                innerPadding = innerPadding,
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                bottomExtra = 40.dp,
                sectionSpacing = 14.dp
            ) {
                item {
                    BaActivityCalendarOverviewPanel(
                        backdrop = pageBackdrop,
                        title = stringResource(
                            R.string.ba_calendar_title_format,
                            serverOptions[serverIndex]
                        ),
                        syncText = syncText,
                        syncTextColor = countdownBlue,
                    )
                }
                when {
                    !calendarUiState.error.isNullOrBlank() -> {
                        item {
                            BaCalendarStatePanel(
                                backdrop = pageBackdrop,
                                text = calendarUiState.error.orEmpty(),
                                accentColor = Color(0xFFF59E0B),
                                effectsEnabled = true,
                            )
                        }
                    }

                    !calendarUiState.loading && visibleEntries.isEmpty() -> {
                        item {
                            BaCalendarStatePanel(
                                backdrop = pageBackdrop,
                                text = if (snapshot.showEndedActivities) {
                                    stringResource(R.string.ba_calendar_empty_all)
                                } else {
                                    stringResource(R.string.ba_calendar_empty_active)
                                },
                                accentColor = MiuixTheme.colorScheme.onBackgroundVariant,
                                effectsEnabled = true,
                            )
                        }
                    }

                    else -> {
                        items(visibleEntries.size) { index ->
                            val activity = visibleEntries[index]
                            BaCalendarEntryPanel(
                                backdrop = pageBackdrop,
                                isPageActive = true,
                                serverIndex = serverIndex,
                                activity = activity,
                                nowMs = nowMs,
                                showCalendarPoolImages = snapshot.showCalendarPoolImages,
                                effectsEnabled = true,
                                onOpenCalendarLink = { url ->
                                    openBaExternalLink(context = context, url = url)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BaActivityCalendarOverviewPanel(
    backdrop: com.kyant.backdrop.Backdrop,
    title: String,
    syncText: String,
    syncTextColor: Color,
) {
    BaLiquidPanel(
        backdrop = backdrop,
        modifier = Modifier.fillMaxWidth(),
        accentColor = MiuixTheme.colorScheme.primary,
        variant = GlassVariant.Content,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = syncText,
                color = syncTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalActivityApi::class)
@Composable
private fun BaActivityCalendarBackHandler(
    onBack: () -> Unit,
) {
    val predictiveBackEnabled = LocalTransitionAnimationsEnabled.current &&
            LocalPredictiveBackAnimationsEnabled.current
    BackHandler {
        onBack()
    }
    PredictiveBackHandler(enabled = predictiveBackEnabled) { backEvents ->
        var handledByProgress = false
        try {
            backEvents.collect { event ->
                if (!handledByProgress && event.progress >= 0.995f) {
                    handledByProgress = true
                    onBack()
                }
            }
            if (!handledByProgress) {
                onBack()
            }
        } catch (_: CancellationException) {
        }
    }
}

private tailrec fun Context.findBaHostActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext?.findBaHostActivity()
        else -> null
    }
}
