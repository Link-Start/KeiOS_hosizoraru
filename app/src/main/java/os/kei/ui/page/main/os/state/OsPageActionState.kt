package os.kei.ui.page.main.os.state

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import os.kei.R
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.InfoRow
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.SectionKind
import os.kei.ui.page.main.os.SectionState
import os.kei.ui.page.main.os.ensureOsSectionLoaded
import os.kei.ui.page.main.os.refreshAllOsSections
import os.kei.ui.page.main.os.runOsShellCommandCard
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.state.PageActionState

internal data class OsPageActionState(
    val ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    val applyCardVisibility: suspend (OsSectionCard, Boolean) -> Unit,
    val applyActivityCardVisibility: suspend (String, Boolean) -> Unit,
    val applyShellCommandCardVisibility: suspend (String, Boolean) -> Unit,
    val runShellCommandCard: suspend (OsShellCommandCard) -> Unit,
    val refreshAllSections: suspend () -> Unit,
) : PageActionState

internal fun createOsPageActionState(
    context: Context,
    scope: CoroutineScope,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    osPageViewModel: OsPageViewModel,
    sectionLoadMutex: Mutex,
    sectionLoadDeferreds: MutableMap<SectionKind, Deferred<List<InfoRow>>>,
    visibleCardsProvider: () -> Set<OsSectionCard>,
    sectionStatesProvider: () -> Map<SectionKind, SectionState>,
    updateSection: (SectionKind, (SectionState) -> SectionState) -> Unit,
    onCachePersistedChanged: (Boolean) -> Unit,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    updateShellCommandCards: (List<OsShellCommandCard>) -> Unit,
    runningShellCommandCardIdsProvider: () -> Set<String>,
    onRunningShellCommandCardIdsChange: (Set<String>) -> Unit,
    onRefreshingChange: (Boolean) -> Unit,
    onRefreshProgressChange: (Float) -> Unit,
    shellCardCommandRequiredToast: String,
    shellCardRunCompletedToast: String,
    shellRunNoPermissionText: String,
    shellRunNoOutputText: String,
    noRefreshableCardText: String,
    refreshCompletedText: String,
): OsPageActionState {
    val ensureLoad: suspend (SectionKind, Boolean) -> Unit = { section, forceRefresh ->
        ensureOsSectionLoaded(
            section = section,
            forceRefresh = forceRefresh,
            visibleCardsProvider = visibleCardsProvider,
            sectionStatesProvider = sectionStatesProvider,
            sectionLoadMutex = sectionLoadMutex,
            sectionLoadDeferreds = sectionLoadDeferreds,
            scope = scope,
            context = context,
            shizukuStatus = shizukuStatus,
            shizukuApiUtils = shizukuApiUtils,
            updateSection = updateSection,
            onCachePersistedChanged = onCachePersistedChanged,
        )
    }

    val applyCardVisibility: suspend (OsSectionCard, Boolean) -> Unit = { card, visible ->
        osPageViewModel.applySectionCardVisibility(
            card = card,
            visible = visible,
            ensureLoad = ensureLoad,
        )
    }

    val applyActivityCardVisibility: suspend (String, Boolean) -> Unit = { cardId, visible ->
        osPageViewModel.applyActivityCardVisibility(
            cardId = cardId,
            visible = visible,
            defaults = googleSystemServiceDefaults,
        )
    }

    val applyShellCommandCardVisibility: suspend (String, Boolean) -> Unit = { cardId, visible ->
        osPageViewModel.applyShellCommandCardVisibility(
            cardId = cardId,
            visible = visible,
        )
    }

    val runShellCommandCard: suspend (OsShellCommandCard) -> Unit = { card ->
        runOsShellCommandCard(
            card = card,
            context = context,
            shizukuApiUtils = shizukuApiUtils,
            shellCardCommandRequiredToast = shellCardCommandRequiredToast,
            shellCardRunCompletedToast = shellCardRunCompletedToast,
            shellRunNoPermissionToast = shellRunNoPermissionText,
            shellRunNoOutputText = shellRunNoOutputText,
            runningCardIdsProvider = runningShellCommandCardIdsProvider,
            updateRunningCardIds = onRunningShellCommandCardIdsChange,
            onCardsReload = updateShellCommandCards,
            runFailedMessage = { throwable ->
                context.getString(
                    R.string.os_shell_card_toast_run_failed,
                    throwable.javaClass.simpleName,
                )
            },
        )
    }

    val refreshAllSections: suspend () -> Unit = {
        refreshAllOsSections(
            context = context,
            visibleCardsProvider = visibleCardsProvider,
            setRefreshing = onRefreshingChange,
            setRefreshProgress = onRefreshProgressChange,
            ensureLoad = ensureLoad,
            noRefreshableCardText = noRefreshableCardText,
            refreshCompletedText = refreshCompletedText,
        )
    }

    return OsPageActionState(
        ensureLoad = ensureLoad,
        applyCardVisibility = applyCardVisibility,
        applyActivityCardVisibility = applyActivityCardVisibility,
        applyShellCommandCardVisibility = applyShellCommandCardVisibility,
        runShellCommandCard = runShellCommandCard,
        refreshAllSections = refreshAllSections,
    )
}
