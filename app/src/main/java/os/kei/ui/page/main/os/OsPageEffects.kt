@file:Suppress("FunctionName")

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import os.kei.core.ui.snapshot.rememberAppSnapshotFlowManager
import os.kei.ui.page.main.os.shell.OsShellCardImportMergeResult
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.os.shortcut.BUILTIN_GOOGLE_SETTINGS_SAMPLE_CARD_ID
import os.kei.ui.page.main.os.shortcut.LEGACY_GOOGLE_SYSTEM_SERVICE_CARD_ID
import os.kei.ui.page.main.os.shortcut.OsActivityCardImportMergeResult
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import os.kei.ui.page.main.os.shortcut.ShortcutActivityClassOption
import os.kei.ui.page.main.os.shortcut.ShortcutInstalledAppOption
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField
import os.kei.ui.page.main.os.transfer.OsCardImportPreview

private typealias OsPageEventCollector = suspend (suspend (OsPageEvent) -> Unit) -> Unit

@Composable
internal fun BindOsPageEvents(
    collectEvents: OsPageEventCollector,
    onLaunchExportDocument: (String, String) -> Unit,
    onExportFailed: (Throwable) -> Unit,
    onCardExportWritten: () -> Unit,
    onCardExportWriteFailed: (Throwable) -> Unit,
    onCardImportPreviewReady: (OsCardImportPreview) -> Unit,
    onCardImportFailed: (Throwable) -> Unit,
    onCardTransferCompleted: () -> Unit,
    onActivityCardsImported: (OsActivityCardImportMergeResult) -> Unit,
    onShellCardsImported: (OsShellCardImportMergeResult) -> Unit,
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
    onLaunchActivityShortcut: (OsGoogleSystemServiceConfig) -> Unit,
    onActivityShortcutInvalidTarget: () -> Unit,
    onShowActivityShortcutEditor: (OsActivityShortcutEditorRequest) -> Unit,
    onShowShellCommandCardEditor: (OsShellCommandCard) -> Unit,
) {
    val currentLaunchExportDocument = rememberUpdatedState(onLaunchExportDocument)
    val currentExportFailed = rememberUpdatedState(onExportFailed)
    val currentCardExportWritten = rememberUpdatedState(onCardExportWritten)
    val currentCardExportWriteFailed = rememberUpdatedState(onCardExportWriteFailed)
    val currentCardImportPreviewReady = rememberUpdatedState(onCardImportPreviewReady)
    val currentCardImportFailed = rememberUpdatedState(onCardImportFailed)
    val currentCardTransferCompleted = rememberUpdatedState(onCardTransferCompleted)
    val currentActivityCardsImported = rememberUpdatedState(onActivityCardsImported)
    val currentShellCardsImported = rememberUpdatedState(onShellCardsImported)
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
    val currentLaunchActivityShortcut = rememberUpdatedState(onLaunchActivityShortcut)
    val currentActivityShortcutInvalidTarget = rememberUpdatedState(onActivityShortcutInvalidTarget)
    val currentShowActivityShortcutEditor = rememberUpdatedState(onShowActivityShortcutEditor)
    val currentShowShellCommandCardEditor = rememberUpdatedState(onShowShellCommandCardEditor)
    LaunchedEffect(collectEvents) {
        collectEvents { event ->
            when (event) {
                is OsPageEvent.LaunchExportDocument -> {
                    currentLaunchExportDocument.value(event.fileName, event.content)
                }

                is OsPageEvent.ExportFailed -> {
                    currentExportFailed.value(event.error)
                }

                OsPageEvent.CardExportWritten -> {
                    currentCardExportWritten.value()
                }

                is OsPageEvent.CardExportWriteFailed -> {
                    currentCardExportWriteFailed.value(event.error)
                }

                is OsPageEvent.CardImportPreviewReady -> {
                    currentCardImportPreviewReady.value(event.preview)
                }

                is OsPageEvent.CardImportFailed -> {
                    currentCardImportFailed.value(event.error)
                }

                OsPageEvent.CardTransferCompleted -> {
                    currentCardTransferCompleted.value()
                }

                is OsPageEvent.ActivityCardsImported -> {
                    currentActivityCardsImported.value(event.result)
                }

                is OsPageEvent.ShellCardsImported -> {
                    currentShellCardsImported.value(event.result)
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

                is OsPageEvent.LaunchActivityShortcut -> {
                    currentLaunchActivityShortcut.value(event.config)
                }

                OsPageEvent.ActivityShortcutInvalidTarget -> {
                    currentActivityShortcutInvalidTarget.value()
                }

                is OsPageEvent.ShowActivityShortcutEditor -> {
                    currentShowActivityShortcutEditor.value(event.request)
                }

                is OsPageEvent.ShowShellCommandCardEditor -> {
                    currentShowShellCommandCardEditor.value(event.card)
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
    persistSnapshot: suspend (OsUiSnapshot) -> Unit,
) {
    val currentSnapshotProvider = rememberUpdatedState(snapshotProvider)
    val snapshotFlowManager = rememberAppSnapshotFlowManager()
    LaunchedEffect(ready, snapshotFlowManager) {
        if (!ready) return@LaunchedEffect
        snapshotFlowManager
            .snapshotFlow {
                currentSnapshotProvider.value()
            }.debounce(200)
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                persistSnapshot(snapshot)
            }
    }
}

@Composable
internal fun BindOsScrollToTopEffect(
    scrollToTopSignal: Int,
    listState: LazyListState,
) {
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal > 0) listState.animateScrollToItem(0)
    }
}

@Composable
internal fun BindOsShellCardReloadOnResume(
    lifecycleOwner: LifecycleOwner,
    reloadCards: () -> Unit,
) {
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
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
    cacheLoaded: Boolean,
    isPageActive: Boolean,
    hydrateInitialCache: suspend (Boolean) -> Unit,
) {
    LaunchedEffect(ready, cacheLoaded, isPageActive) {
        if (!ready) return@LaunchedEffect
        if (cacheLoaded && !isPageActive) return@LaunchedEffect
        hydrateInitialCache(isPageActive)
    }
}

@Composable
internal fun BindOsShizukuInvalidation(
    shizukuReady: Boolean,
    onInvalidate: () -> Unit,
) {
    LaunchedEffect(shizukuReady) {
        onInvalidate()
    }
}

@Composable
internal fun BindOsVisibleSectionLoadEffects(
    cacheLoaded: Boolean,
    initialVisibleRefreshComplete: Boolean,
    isDataActive: Boolean,
    visibleCards: Set<OsSectionCard>,
    systemTableExpanded: Boolean,
    secureTableExpanded: Boolean,
    globalTableExpanded: Boolean,
    androidPropsExpanded: Boolean,
    javaPropsExpanded: Boolean,
    linuxEnvExpanded: Boolean,
    ensureLoad: suspend (SectionKind) -> Unit,
) {
    BindOsSectionLoadEffect(
        expanded = systemTableExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.SYSTEM,
        section = SectionKind.SYSTEM,
        ensureLoad = ensureLoad,
    )
    BindOsSectionLoadEffect(
        expanded = secureTableExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.SECURE,
        section = SectionKind.SECURE,
        ensureLoad = ensureLoad,
    )
    BindOsSectionLoadEffect(
        expanded = globalTableExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.GLOBAL,
        section = SectionKind.GLOBAL,
        ensureLoad = ensureLoad,
    )
    BindOsSectionLoadEffect(
        expanded = androidPropsExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.ANDROID,
        section = SectionKind.ANDROID,
        ensureLoad = ensureLoad,
    )
    BindOsSectionLoadEffect(
        expanded = javaPropsExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.JAVA,
        section = SectionKind.JAVA,
        ensureLoad = ensureLoad,
    )
    BindOsSectionLoadEffect(
        expanded = linuxEnvExpanded,
        cacheLoaded = cacheLoaded,
        initialVisibleRefreshComplete = initialVisibleRefreshComplete,
        isDataActive = isDataActive,
        visibleCards = visibleCards,
        card = OsSectionCard.LINUX,
        section = SectionKind.LINUX,
        ensureLoad = ensureLoad,
    )
}

@Composable
private fun BindOsSectionLoadEffect(
    expanded: Boolean,
    cacheLoaded: Boolean,
    initialVisibleRefreshComplete: Boolean,
    isDataActive: Boolean,
    visibleCards: Set<OsSectionCard>,
    card: OsSectionCard,
    section: SectionKind,
    ensureLoad: suspend (SectionKind) -> Unit,
) {
    LaunchedEffect(
        expanded,
        visibleCards,
        cacheLoaded,
        initialVisibleRefreshComplete,
        isDataActive,
    ) {
        if (!cacheLoaded) return@LaunchedEffect
        if (!initialVisibleRefreshComplete) return@LaunchedEffect
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
internal fun BindOsActivityShortcutIconPreloadEffect(
    active: Boolean,
    activityShortcutCards: List<OsActivityShortcutCard>,
    showActivitySuggestionSheet: Boolean,
    googleSystemServiceSuggestionTarget: ShortcutSuggestionField,
    activityShortcutDraftPackageName: String,
    packageSuggestions: List<ShortcutInstalledAppOption>,
    classSuggestions: List<ShortcutActivityClassOption>,
    context: Context,
    requestActivityShortcutIcons: (Context, List<OsActivityShortcutIconRequest>) -> Unit,
    requestPackageIcons: (Context, List<String>) -> Unit,
) {
    LaunchedEffect(
        active,
        activityShortcutCards,
        showActivitySuggestionSheet,
        googleSystemServiceSuggestionTarget,
        activityShortcutDraftPackageName,
        packageSuggestions,
        classSuggestions,
    ) {
        if (!active) return@LaunchedEffect
        val requests =
            activityShortcutIconRequests(activityShortcutCards) +
                if (showActivitySuggestionSheet) {
                    activitySuggestionIconRequests(
                        packageName = activityShortcutDraftPackageName,
                        classSuggestions = classSuggestions,
                    )
                } else {
                    emptyList()
                }
        requestActivityShortcutIcons(context, requests)
        val packageIconPackages =
            buildList {
                activityShortcutCards
                    .map { card -> card.config.packageName }
                    .forEach(::add)
                if (
                    showActivitySuggestionSheet &&
                    googleSystemServiceSuggestionTarget == ShortcutSuggestionField.PackageName
                ) {
                    addAll(activityPackageSuggestionIconPackages(packageSuggestions))
                }
            }
        if (
            packageIconPackages.isNotEmpty()
        ) {
            requestPackageIcons(
                context,
                packageIconPackages,
            )
        }
    }
}

@Composable
internal fun BindOsCardExpandedStateMaps(
    activityShortcutCards: List<OsActivityShortcutCard>,
    initialGoogleSystemServiceExpanded: Boolean,
    shellCommandCards: List<OsShellCommandCard>,
    syncActivityCardExpansion: (List<OsActivityShortcutCard>, Boolean) -> Unit,
    syncShellCommandCardExpansion: (List<OsShellCommandCard>) -> Unit,
) {
    LaunchedEffect(activityShortcutCards, initialGoogleSystemServiceExpanded) {
        syncActivityCardExpansion(activityShortcutCards, initialGoogleSystemServiceExpanded)
    }

    LaunchedEffect(shellCommandCards) {
        syncShellCommandCardExpansion(shellCommandCards)
    }
}
