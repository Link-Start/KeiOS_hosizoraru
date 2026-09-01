package os.kei.ui.page.main.github.page

import android.content.Context
import android.content.Intent
import os.kei.core.ext.showToast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import os.kei.R
import os.kei.core.log.AppLogger
import os.kei.core.system.HyperOsSettingsIntents
import os.kei.core.system.AppPackageChangedEvents
import os.kei.feature.github.domain.GitHubRefreshRuntimeStore
import os.kei.ui.page.main.github.query.OnlineShareTargetOption
import os.kei.ui.page.main.widget.chrome.BindScrollToTopEffect
import os.kei.ui.page.main.widget.chrome.rememberAppPageScrollTarget
import kotlin.time.Duration.Companion.milliseconds

private const val GITHUB_PAGE_ACTIVE_SYNC_DELAY_MS = 120L

@Composable
internal fun BindGitHubPageEffects(
    context: Context,
    listState: LazyListState,
    scrollToTopSignal: Int,
    isPageWarmActive: Boolean,
    isPageDataActive: Boolean,
    state: GitHubPageState,
    actions: GitHubPageActions,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    onLaunchAppListPermission: (Intent) -> Unit,
    onHistoryUnreadCountMayChange: () -> Unit,
) {
    BindGitHubPageLifecycleCoordinator(
        context = context,
        listState = listState,
        scrollToTopSignal = scrollToTopSignal,
        isPageWarmActive = isPageWarmActive,
        isPageDataActive = isPageDataActive,
        state = state,
        actions = actions,
        installedOnlineShareTargets = installedOnlineShareTargets,
        onLaunchAppListPermission = onLaunchAppListPermission,
        onHistoryUnreadCountMayChange = onHistoryUnreadCountMayChange,
    )
}

@Composable
internal fun BindGitHubPageLifecycleCoordinator(
    context: Context,
    listState: LazyListState,
    scrollToTopSignal: Int,
    isPageWarmActive: Boolean,
    isPageDataActive: Boolean,
    state: GitHubPageState,
    actions: GitHubPageActions,
    installedOnlineShareTargets: List<OnlineShareTargetOption>,
    onLaunchAppListPermission: (Intent) -> Unit,
    onHistoryUnreadCountMayChange: () -> Unit,
) {

    LaunchedEffect(installedOnlineShareTargets) {
        actions.handleInstalledOnlineShareTargetsChanged(installedOnlineShareTargets)
    }

    LaunchedEffect(isPageWarmActive) {
        if (!isPageWarmActive) return@LaunchedEffect
        val currentSignalVersion = actions.currentTrackStoreSignalVersion()
        if (!state.hasInitialized) {
            state.hasInitialized = true
            actions.initializeWarmSnapshot()
        } else if (currentSignalVersion > state.lastTrackStoreSignalVersion) {
            actions.syncTrackSnapshotFromStore(forceRefreshApps = false)
        }
        state.lastTrackStoreSignalVersion = currentSignalVersion
    }

    LaunchedEffect(isPageDataActive) {
        if (!isPageDataActive) return@LaunchedEffect
        delay(GITHUB_PAGE_ACTIVE_SYNC_DELAY_MS.milliseconds)
        AppLogger.d("GitHubPageLifecycle") {
            "page data active initialized=${state.hasActiveInitialized} " +
                "warmInitialized=${state.hasInitialized} tracked=${state.trackedItems.size} " +
                "cached=${state.checkStates.size}"
        }
        if (!state.hasInitialized) {
            state.hasInitialized = true
            actions.initializeWarmSnapshot()
        }
        actions.syncActiveShareImportFlowFromStore()
        if (!state.hasActiveInitialized) {
            state.hasActiveInitialized = true
            actions.initializePageActiveWork()
        } else {
            actions.syncLocalAppStateOnPageActive()
        }
        actions.trimExpiredPendingShareImportTrack()
    }

    LaunchedEffect(isPageWarmActive) {
        if (!isPageWarmActive) return@LaunchedEffect
        actions.trackStoreSignalVersions().collect { version ->
            if (version <= 0L) return@collect
            if (version <= state.lastTrackStoreSignalVersion) return@collect
            state.lastTrackStoreSignalVersion = version
            if (!state.hasInitialized) return@collect
            actions.syncTrackSnapshotFromStore(forceRefreshApps = isPageDataActive)
        }
    }

    LaunchedEffect(isPageWarmActive, actions) {
        if (!isPageWarmActive) return@LaunchedEffect
        GitHubRefreshRuntimeStore.state.collect { runtime ->
            actions.applyRefreshRuntimeDisplay(runtime)
        }
    }

    BindScrollToTopEffect(
        scrollToTopSignal = scrollToTopSignal,
        target = rememberAppPageScrollTarget(listState),
        isActive = isPageDataActive,
    )

    LaunchedEffect(state.appListLoaded, state.appList) {
        if (
            shouldAutoRequestAppListPermission(
                appListLoaded = state.appListLoaded,
                appListEmpty = state.appList.isEmpty(),
                hasAutoRequestedPermission = state.hasAutoRequestedPermission,
                isHyperOs3Device = HyperOsSettingsIntents.isHyperOs3Device(),
            )
        ) {
            state.hasAutoRequestedPermission = true
            val intent = actions.buildAppListPermissionIntent()
            if (intent != null) {
                onLaunchAppListPermission(intent)
            } else {
                context.showToast(context.getString(R.string.github_toast_open_permission_page_failed))
            }
        }
    }

    LaunchedEffect(isPageDataActive) {
        if (!isPageDataActive) return@LaunchedEffect
        AppPackageChangedEvents.events.collect { event ->
            actions.handlePackageChangedEvent(event)
            onHistoryUnreadCountMayChange()
        }
    }
}

internal fun shouldAutoRequestAppListPermission(
    appListLoaded: Boolean,
    appListEmpty: Boolean,
    hasAutoRequestedPermission: Boolean,
    isHyperOs3Device: Boolean,
): Boolean =
    appListLoaded &&
        appListEmpty &&
        !hasAutoRequestedPermission &&
        isHyperOs3Device
