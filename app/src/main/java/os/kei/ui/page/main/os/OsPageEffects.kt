package os.kei.ui.page.main.os

import android.content.Context
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
import os.kei.ui.page.main.os.shortcut.LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField

@Composable
internal fun BindOsPageEvents(
    events: SharedFlow<OsPageEvent>,
    onLaunchExportDocument: (String, String) -> Unit,
    onExportFailed: (Throwable) -> Unit,
    onOperationFailed: (Throwable) -> Unit,
    onShellCommandCardSaved: () -> Unit,
    onShellCommandCardSaveFailed: () -> Unit,
    onShellCommandCardDeleted: (String) -> Unit,
    onActivityShortcutCardSaved: () -> Unit,
    onActivityShortcutCardDeleted: (String) -> Unit,
    onShellCommandCardCommandRequired: () -> Unit,
    onShellCommandCardNoPermission: () -> Unit,
    onShellCommandCardRunCompleted: () -> Unit,
    onShellCommandCardRunFailed: (Throwable) -> Unit,
    onRefreshCompleted: (Boolean) -> Unit,
) {
    val currentLaunchExportDocument = rememberUpdatedState(onLaunchExportDocument)
    val currentExportFailed = rememberUpdatedState(onExportFailed)
    val currentOperationFailed = rememberUpdatedState(onOperationFailed)
    val currentShellCommandCardSaved = rememberUpdatedState(onShellCommandCardSaved)
    val currentShellCommandCardSaveFailed = rememberUpdatedState(onShellCommandCardSaveFailed)
    val currentShellCommandCardDeleted = rememberUpdatedState(onShellCommandCardDeleted)
    val currentActivityShortcutCardSaved = rememberUpdatedState(onActivityShortcutCardSaved)
    val currentActivityShortcutCardDeleted = rememberUpdatedState(onActivityShortcutCardDeleted)
    val currentShellCommandCardCommandRequired = rememberUpdatedState(onShellCommandCardCommandRequired)
    val currentShellCommandCardNoPermission = rememberUpdatedState(onShellCommandCardNoPermission)
    val currentShellCommandCardRunCompleted = rememberUpdatedState(onShellCommandCardRunCompleted)
    val currentShellCommandCardRunFailed = rememberUpdatedState(onShellCommandCardRunFailed)
    val currentRefreshCompleted = rememberUpdatedState(onRefreshCompleted)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is OsPageEvent.LaunchExportDocument -> {
                    currentLaunchExportDocument.value(event.fileName, event.content)
                }

                is OsPageEvent.ExportFailed -> {
                    currentExportFailed.value(event.error)
                }

                is OsPageEvent.OperationFailed -> {
                    currentOperationFailed.value(event.error)
                }

                OsPageEvent.ShellCommandCardSaved -> {
                    currentShellCommandCardSaved.value()
                }

                OsPageEvent.ShellCommandCardSaveFailed -> {
                    currentShellCommandCardSaveFailed.value()
                }

                is OsPageEvent.ShellCommandCardDeleted -> {
                    currentShellCommandCardDeleted.value(event.cardId)
                }

                OsPageEvent.ActivityShortcutCardSaved -> {
                    currentActivityShortcutCardSaved.value()
                }

                is OsPageEvent.ActivityShortcutCardDeleted -> {
                    currentActivityShortcutCardDeleted.value(event.cardId)
                }

                OsPageEvent.ShellCommandCardCommandRequired -> {
                    currentShellCommandCardCommandRequired.value()
                }

                OsPageEvent.ShellCommandCardNoPermission -> {
                    currentShellCommandCardNoPermission.value()
                }

                OsPageEvent.ShellCommandCardRunCompleted -> {
                    currentShellCommandCardRunCompleted.value()
                }

                is OsPageEvent.ShellCommandCardRunFailed -> {
                    currentShellCommandCardRunFailed.value(event.error)
                }

                is OsPageEvent.RefreshCompleted -> {
                    currentRefreshCompleted.value(event.refreshed)
                }
            }
        }
    }
}

@Composable
@OptIn(FlowPreview::class)
internal fun BindOsExpandedStatePersistence(
    ready: Boolean,
    snapshotProvider: () -> OsUiSnapshot,
    persistSnapshot: suspend (OsUiSnapshot) -> Unit
) {
    val currentSnapshotProvider = rememberUpdatedState(snapshotProvider)
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(ready, snapshotFlowManager) {
        if (!ready) return@LaunchedEffect
        snapshotFlowManager.snapshotFlow {
            currentSnapshotProvider.value()
        }
            .debounce(200)
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                persistSnapshot(snapshot)
            }
    }
}

@Composable
internal fun BindOsScrollToTopEffect(
    scrollToTopSignal: Int,
    listState: LazyListState
) {
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
}

@Composable
internal fun BindOsShellCardReloadOnResume(
    lifecycleOwner: LifecycleOwner,
    reloadCards: () -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reloadCards()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
internal fun BindOsInitialCacheLoad(
    ready: Boolean,
    isPageActive: Boolean,
    hydrateInitialCache: suspend (Boolean) -> Unit
) {
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        hydrateInitialCache(isPageActive)
    }
}

@Composable
internal fun BindOsShizukuInvalidation(
    shizukuReady: Boolean,
    onInvalidate: () -> Unit
) {
    LaunchedEffect(shizukuReady) {
        onInvalidate()
    }
}

@Composable
internal fun BindOsVisibleSectionLoadEffects(
    cacheLoaded: Boolean,
    isDataActive: Boolean,
    visibleCards: Set<OsSectionCard>,
    systemTableExpanded: Boolean,
    secureTableExpanded: Boolean,
    globalTableExpanded: Boolean,
    androidPropsExpanded: Boolean,
    javaPropsExpanded: Boolean,
    linuxEnvExpanded: Boolean,
    ensureLoad: suspend (SectionKind) -> Unit
) {
    BindOsSectionLoadEffect(
        expanded = systemTableExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.SYSTEM,
        section = SectionKind.SYSTEM,
        ensureLoad = ensureLoad
    )
    BindOsSectionLoadEffect(
        expanded = secureTableExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.SECURE,
        section = SectionKind.SECURE,
        ensureLoad = ensureLoad
    )
    BindOsSectionLoadEffect(
        expanded = globalTableExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.GLOBAL,
        section = SectionKind.GLOBAL,
        ensureLoad = ensureLoad
    )
    BindOsSectionLoadEffect(
        expanded = androidPropsExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.ANDROID,
        section = SectionKind.ANDROID,
        ensureLoad = ensureLoad
    )
    BindOsSectionLoadEffect(
        expanded = javaPropsExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.JAVA,
        section = SectionKind.JAVA,
        ensureLoad = ensureLoad
    )
    BindOsSectionLoadEffect(
        expanded = linuxEnvExpanded,
        cacheLoaded = cacheLoaded,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.LINUX,
        section = SectionKind.LINUX,
        ensureLoad = ensureLoad
    )
}

@Composable
private fun BindOsSectionLoadEffect(
    expanded: Boolean,
    cacheLoaded: Boolean,
    isDataActive: Boolean,
    visibleCards: Set<OsSectionCard>,
    card: OsSectionCard,
    section: SectionKind,
    ensureLoad: suspend (SectionKind) -> Unit
) {
    LaunchedEffect(expanded, visibleCards, cacheLoaded, isDataActive) {
        if (!cacheLoaded) return@LaunchedEffect
        if (isDataActive && expanded && isCardVisible(visibleCards, card)) {
            ensureLoad(section)
        }
    }
}

@Composable
internal fun BindOsActivitySuggestionLoadEffect(
    showActivitySuggestionSheet: Boolean,
    googleSystemServiceSuggestionTarget: ShortcutSuggestionField,
    activityShortcutDraftPackageName: String,
    context: Context,
    requestActivitySuggestions: (
        context: Context,
        show: Boolean,
        target: ShortcutSuggestionField,
        packageName: String,
    ) -> Unit,
) {
    LaunchedEffect(
        context,
        showActivitySuggestionSheet,
        googleSystemServiceSuggestionTarget,
        activityShortcutDraftPackageName,
    ) {
        requestActivitySuggestions(
            context,
            showActivitySuggestionSheet,
            googleSystemServiceSuggestionTarget,
            activityShortcutDraftPackageName,
        )
    }
}

@Composable
internal fun BindOsCardExpandedStateMaps(
    activityShortcutCards: List<OsActivityShortcutCard>,
    activityCardExpanded: MutableMap<String, Boolean>,
    initialGoogleSystemServiceExpanded: Boolean,
    shellCommandCards: List<OsShellCommandCard>,
    shellCommandCardExpanded: MutableMap<String, Boolean>
) {
    LaunchedEffect(activityShortcutCards, initialGoogleSystemServiceExpanded) {
        val currentIds = activityShortcutCards.map { it.id }.toSet()
        activityCardExpanded.keys.toList().forEach { id ->
            if (!currentIds.contains(id)) {
                activityCardExpanded.remove(id)
            }
        }
        activityShortcutCards.forEachIndexed { index, card ->
            val usesStoredDefaultExpansion = index == 0 && (
                card.id == LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID ||
                    card.id == BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
                )
            if (usesStoredDefaultExpansion) {
                activityCardExpanded[card.id] = initialGoogleSystemServiceExpanded
            } else if (!activityCardExpanded.containsKey(card.id)) {
                activityCardExpanded[card.id] = false
            }
        }
    }

    LaunchedEffect(shellCommandCards) {
        val currentIds = shellCommandCards.map { it.id }.toSet()
        shellCommandCardExpanded.keys.toList().forEach { id ->
            if (!currentIds.contains(id)) {
                shellCommandCardExpanded.remove(id)
            }
        }
        shellCommandCards.forEach { card ->
            if (!shellCommandCardExpanded.containsKey(card.id)) {
                shellCommandCardExpanded[card.id] = false
            }
        }
    }
}
