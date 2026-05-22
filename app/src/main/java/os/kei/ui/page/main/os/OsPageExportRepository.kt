package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.ui.page.main.os.shortcut.OsActivityShortcutCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class OsPageSectionCardExportRequest(
    val card: OsSectionCard,
    val sectionStates: Map<SectionKind, SectionState>,
    val activityShortcutCards: List<OsActivityShortcutCard>,
    val googleSystemServiceDefaults: OsGoogleSystemServiceConfig,
    val context: Context,
    val shizukuStatus: String,
)

internal data class OsPageExportDocument(
    val fileName: String,
    val content: String,
)

internal class OsPageExportRepository(
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
) {
    suspend fun buildSectionCardExport(request: OsPageSectionCardExportRequest): OsPageExportDocument =
        withContext(defaultDispatcher) {
            currentCoroutineContext().ensureActive()
            val rows =
                currentRowsForCard(
                    card = request.card,
                    sectionStates = request.sectionStates,
                    googleSystemServiceConfig =
                        request.activityShortcutCards.firstOrNull()?.config
                            ?: request.googleSystemServiceDefaults,
                    googleSystemServiceDefaults = request.googleSystemServiceDefaults,
                    context = request.context,
                )
            currentCoroutineContext().ensureActive()
            val generatedAt = outputFormatter().format(Date())
            val content =
                buildOsCardJson(
                    generatedAt = generatedAt,
                    shizukuStatus = request.shizukuStatus,
                    cardTitle = request.card.title(request.context),
                    rows = rows,
                )
            currentCoroutineContext().ensureActive()
            OsPageExportDocument(
                fileName = "keios-os-${exportSlug(request.card)}-${fileStampFormatter().format(Date())}.json",
                content = content,
            )
        }

    private fun outputFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    private fun fileStampFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.getDefault())
}
