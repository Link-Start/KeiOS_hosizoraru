@file:Suppress("FunctionName")

package os.kei.ui.page.main.ba

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import os.kei.R
import os.kei.core.ext.showToast
import os.kei.core.intent.SafeExternalIntents
import os.kei.core.ui.effect.rememberAppTopBarColor
import os.kei.ui.page.main.ba.card.filterVisiblePoolEntries
import os.kei.ui.page.main.ba.support.BaPoolEntry
import os.kei.ui.page.main.ba.support.formatBaDateTimeNoYearInTimeZone
import os.kei.ui.page.main.ba.support.serverRefreshTimeZone
import os.kei.ui.page.main.back.KeiOSActivityRootBackHandler
import os.kei.ui.page.main.common.applicationViewModel
import os.kei.ui.page.main.os.appLucideBackIcon
import os.kei.ui.page.main.widget.chrome.AppLiquidNavigationButton
import os.kei.ui.page.main.widget.chrome.appManagedPageBackgroundActive
import os.kei.ui.page.main.widget.chrome.AppPageScaffold
import os.kei.ui.page.main.widget.isAppInDarkTheme
import os.kei.ui.page.main.widget.status.AppStatusColors
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BaPoolListContent(
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
    refreshing: Boolean,
    error: String?,
    syncText: String,
    syncTextColor: Color,
    onServerPopupChange: (Boolean) -> Unit,
    onServerPopupAnchorBoundsChange: (IntRect?) -> Unit,
    onServerSelected: (Int) -> Unit,
    onOpenPoolStudentGuide: (String) -> Unit,
    onOpenCalendarLink: (String) -> Unit,
) {
    val nowMs = rememberBaMinuteTickMs(enabled = !loading && entries.isNotEmpty())
    val visibleEntries =
        remember(
            entries,
            showEndedPools,
            nowMs,
        ) {
            filterVisiblePoolEntries(
                entries = entries,
                showEndedPools = showEndedPools,
                nowMs = nowMs,
            )
        }
    BaCalendarPoolStackedLayout(
        innerPadding = innerPadding,
        listState = listState,
        nestedScrollConnection = nestedScrollConnection,
        backdrop = backdrop,
        serverOptions = serverOptions,
        serverIndex = serverIndex,
        syncText = syncText,
        syncTextColor = syncTextColor,
        showServerPopup = showServerPopup,
        serverPopupAnchorBounds = serverPopupAnchorBounds,
        onServerPopupChange = onServerPopupChange,
        onServerPopupAnchorBoundsChange = onServerPopupAnchorBoundsChange,
        onServerSelected = onServerSelected,
    ) {
        baPoolEntryItems(
            backdrop = backdrop,
            serverIndex = serverIndex,
            visibleEntries = visibleEntries,
            loading = loading,
            refreshing = refreshing,
            error = error,
            showEndedPools = showEndedPools,
            showCalendarPoolImages = showCalendarPoolImages,
            nowMs = nowMs,
            syncTextColor = syncTextColor,
            onOpenPoolStudentGuide = onOpenPoolStudentGuide,
            onOpenCalendarLink = onOpenCalendarLink,
        )
    }
}

internal fun openBaPoolGuideLink(
    context: Context,
    scope: CoroutineScope,
    calendarPoolViewModel: BaCalendarPoolViewModel,
    rawUrl: String,
    onOpenGuide: () -> Unit,
) {
    scope.launch {
        when (val plan = calendarPoolViewModel.preparePoolGuideOpen(rawUrl)) {
            BaPoolGuideOpenPlan.Missing -> {
                context.showToast(R.string.main_toast_pool_guide_missing)
            }

            is BaPoolGuideOpenPlan.OpenInApp -> {
                onOpenGuide()
            }

            is BaPoolGuideOpenPlan.OpenExternal -> {
                openBaStandaloneExternalLink(context, plan.url)
            }
        }
    }
}

private fun openBaStandaloneExternalLink(
    context: Context,
    url: String,
) {
    val intent = SafeExternalIntents.browsableViewIntent(url, newTask = true)
    if (intent == null) {
        context.showToast(R.string.ba_error_open_activity_link)
        return
    }
    runCatching { context.startActivity(intent) }.onFailure {
        context.showToast(R.string.ba_error_open_activity_link)
    }
}
