package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers

internal class OsPageRefreshRepository(
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun refreshableSections(visibleCards: Set<OsSectionCard>): List<SectionKind> =
        withContext(defaultDispatcher) {
            val visibleSections = visibleSectionKinds(visibleCards)
            SectionKind.entries.filter { section -> visibleSections.contains(section) }
        }
}
