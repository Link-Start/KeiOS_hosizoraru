package os.kei.ui.page.main.ba

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import os.kei.R
import os.kei.core.prefs.AppThemeMode
import os.kei.core.prefs.UiPrefs
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.card.BaCalendarCard
import os.kei.ui.page.main.ba.card.filterVisibleCalendarEntries
import os.kei.ui.page.main.ba.support.BASettingsStore
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.AppPageLazyColumn
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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

    MiuixTheme(controller = ThemeController(colorSchemeMode)) {
        content()
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
                    BaCalendarCard(
                        backdrop = pageBackdrop,
                        isPageActive = true,
                        serverOptions = serverOptions,
                        serverIndex = serverIndex,
                        baCalendarLoading = calendarUiState.loading,
                        baCalendarLastSyncMs = calendarUiState.lastSyncMs,
                        baCalendarError = calendarUiState.error,
                        visibleCalendarEntries = visibleEntries,
                        nowMs = nowMs,
                        showEndedActivities = snapshot.showEndedActivities,
                        showCalendarPoolImages = snapshot.showCalendarPoolImages,
                        effectsEnabled = true,
                        onRefreshCalendar = { reloadSignal += 1 },
                        onOpenCalendarLink = { url -> openBaExternalLink(context = context, url = url) },
                    )
                }
            }
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
