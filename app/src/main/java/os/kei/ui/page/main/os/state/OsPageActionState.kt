package os.kei.ui.page.main.os.state

import android.content.Context
import os.kei.R
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.SectionKind
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.state.PageActionState

internal data class OsPageActionState(
    val ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    val applyCardVisibility: (OsSectionCard, Boolean) -> Unit,
    val applyActivityCardVisibility: (String, Boolean) -> Unit,
    val applyShellCommandCardVisibility: (String, Boolean) -> Unit,
    val runShellCommandCard: (OsShellCommandCard) -> Unit,
    val refreshAllSections: () -> Unit,
) : PageActionState

internal fun createOsPageActionState(
    context: Context,
    shizukuStatus: String,
    shizukuApiUtils: ShizukuApiUtils,
    osPageViewModel: OsPageViewModel,
    googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    shellRunNoOutputText: String,
    builtInShellCommandCards: List<OsShellCommandCard>,
): OsPageActionState {
    val ensureLoad: suspend (SectionKind, Boolean) -> Unit = { section, forceRefresh ->
        osPageViewModel.ensureSectionLoaded(
            section = section,
            forceRefresh = forceRefresh,
            context = context,
            shizukuStatus = shizukuStatus,
            shizukuApiUtils = shizukuApiUtils,
        )
    }

    val applyCardVisibility: (OsSectionCard, Boolean) -> Unit = { card, visible ->
        osPageViewModel.applySectionCardVisibility(
            card = card,
            visible = visible,
            ensureLoad = ensureLoad,
        )
    }

    val applyActivityCardVisibility: (String, Boolean) -> Unit = { cardId, visible ->
        osPageViewModel.applyActivityCardVisibility(
            cardId = cardId,
            visible = visible,
            defaults = googleSystemServiceDefaults,
        )
    }

    val applyShellCommandCardVisibility: (String, Boolean) -> Unit = { cardId, visible ->
        osPageViewModel.applyShellCommandCardVisibility(
            cardId = cardId,
            visible = visible,
            builtInShellCommandCards = builtInShellCommandCards,
        )
    }

    val runShellCommandCard: (OsShellCommandCard) -> Unit = { card ->
        osPageViewModel.runShellCommandCard(
            card = card,
            shizukuApiUtils = shizukuApiUtils,
            shellRunNoOutputText = shellRunNoOutputText,
            shellRunFailedOutput = { reason ->
                context.getString(R.string.os_shell_card_toast_run_failed, reason)
            },
            builtInShellCommandCards = builtInShellCommandCards,
        )
    }

    val refreshAllSections: () -> Unit = {
        osPageViewModel.refreshAllSections(
            ensureLoad = ensureLoad,
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
