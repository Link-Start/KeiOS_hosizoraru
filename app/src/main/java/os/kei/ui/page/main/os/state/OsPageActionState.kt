package os.kei.ui.page.main.os.state

import android.content.Context
import os.kei.core.shizuku.ShizukuApiUtils
import os.kei.ui.page.main.os.OsGoogleSystemServiceConfig
import os.kei.ui.page.main.os.OsPageViewModel
import os.kei.ui.page.main.os.OsSectionCard
import os.kei.ui.page.main.os.SectionKind
import os.kei.ui.page.main.os.shell.OsShellCommandCard
import os.kei.ui.page.main.state.PageActionState

internal data class OsPageActionState(
    val ensureLoad: suspend (SectionKind, Boolean) -> Unit,
    val applyCardVisibility: suspend (OsSectionCard, Boolean) -> Unit,
    val applyActivityCardVisibility: suspend (String, Boolean) -> Unit,
    val applyShellCommandCardVisibility: suspend (String, Boolean) -> Unit,
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

    val runShellCommandCard: (OsShellCommandCard) -> Unit = { card ->
        osPageViewModel.runShellCommandCard(
            card = card,
            shizukuApiUtils = shizukuApiUtils,
            shellRunNoOutputText = shellRunNoOutputText,
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
