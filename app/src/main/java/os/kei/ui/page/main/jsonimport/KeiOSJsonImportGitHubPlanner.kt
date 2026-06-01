package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.R
import os.kei.core.background.AppBackgroundScheduler
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.domain.GitHubTrackedItemsTransferService
import os.kei.feature.github.model.GitHubTrackedApp

internal class KeiOSJsonImportGitHubPlanner(
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher
) {
    suspend fun buildPlan(
        context: Context,
        file: KeiOSJsonImportFile,
        header: KeiOSJsonImportHeader
    ): KeiOSJsonImportPlan {
        val payload = withContext(defaultDispatcher) {
            GitHubTrackedItemsTransferService.parseImport(file.raw)
        }
        val existing = withContext(ioDispatcher) { GitHubTrackedItemsTransferService.loadItems() }
        val preview = withContext(defaultDispatcher) {
            buildPreview(
                context = context,
                header = header,
                payload = payload,
                existingItems = existing
            )
        }
        return ImportableKeiOSJsonPlan(preview) {
            applyImport(context, payload)
        }
    }

    private suspend fun applyImport(
        context: Context,
        payload: GitHubTrackedItemsImportPayload
    ): KeiOSJsonImportApplyResult {
        return withContext(ioDispatcher) {
            payload.items.forEachIndexed { index, _ ->
                currentCoroutineContext().ensureActive()
                if (index % JSON_IMPORT_YIELD_EVERY_ITEMS == 0) yield()
            }
            val result = GitHubTrackedItemsTransferService.applyImport(
                payload = payload,
                onRefreshNeeded = { AppBackgroundScheduler.scheduleGitHubRefresh(context) },
            )
            KeiOSJsonImportApplyResult(
                addedCount = result.addedCount,
                updatedCount = result.updatedCount,
                unchangedCount = result.unchangedCount,
                invalidCount = result.invalidCount,
                duplicateCount = result.duplicateCount
            )
        }
    }

    private suspend fun buildPreview(
        context: Context,
        header: KeiOSJsonImportHeader,
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): KeiOSJsonImportPreview {
        payload.items.forEachIndexed { index, _ ->
            currentCoroutineContext().ensureActive()
            if (index % JSON_IMPORT_YIELD_EVERY_ITEMS == 0) yield()
        }
        val preview = GitHubTrackedItemsTransferService.buildImportPreview(
            payload = payload,
            existingItems = existingItems,
        )
        return KeiOSJsonImportPreview(
            kind = header.kind,
            marker = header.marker.ifBlank { payload.format },
            version = header.version.takeIf { it > 0 } ?: payload.schemaVersion,
            highVersion = header.highVersion,
            readOnly = false,
            legacyFormat = header.legacyFormat,
            canImport = preview.canImport,
            totalCount = preview.fileItemCount,
            validCount = preview.validCount,
            newCount = preview.newCount,
            updatedCount = preview.updatedCount,
            unchangedCount = preview.unchangedCount,
            duplicateCount = preview.duplicateCount,
            invalidCount = preview.invalidCount,
            stats = buildJsonImportStats(
                context,
                preview.fileItemCount,
                preview.validCount,
                preview.newCount,
                preview.updatedCount,
                preview.unchangedCount,
                preview.invalidCount,
                preview.duplicateCount
            ) + listOf(
                KeiOSJsonImportStat(
                    context.getString(R.string.json_import_stat_actions),
                    preview.actionsUpdateCount.toString()
                ),
                KeiOSJsonImportStat(
                    context.getString(R.string.json_import_stat_precise_version),
                    preview.preciseApkVersionOverrideCount.toString()
                )
            ),
            samples = payload.items.take(KEIOS_JSON_IMPORT_SAMPLE_LIMIT).map {
                KeiOSJsonImportSample(
                    title = "${it.owner}/${it.repo}".ifBlank { it.repoUrl },
                    subtitle = listOf(
                        it.appLabel,
                        it.packageName
                    ).filter { value -> value.isNotBlank() }.joinToString(" · ")
                )
            }
        )
    }
}
