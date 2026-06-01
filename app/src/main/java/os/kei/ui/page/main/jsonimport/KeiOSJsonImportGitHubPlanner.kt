package os.kei.ui.page.main.jsonimport

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import os.kei.R
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.domain.GitHubTrackedItemsImportApplier
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
            GitHubTrackStore.parseTrackedItemsImport(file.raw)
        }
        val existing = withContext(ioDispatcher) { GitHubTrackStore.load() }
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
            val result = GitHubTrackedItemsImportApplier.apply(context, payload)
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
        val existingById = existingItems.associateBy { it.id }
        var newCount = 0
        var updatedCount = 0
        var unchangedCount = 0
        payload.items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            if (index % JSON_IMPORT_YIELD_EVERY_ITEMS == 0) yield()
            when (existingById[item.id]) {
                null -> newCount += 1
                item -> unchangedCount += 1
                else -> updatedCount += 1
            }
        }
        val optionCounts = GitHubTrackStore.calculateTrackedItemsOptionCounts(payload.items)
        return KeiOSJsonImportPreview(
            kind = header.kind,
            marker = header.marker.ifBlank { payload.format },
            version = header.version.takeIf { it > 0 } ?: payload.schemaVersion,
            highVersion = header.highVersion,
            readOnly = false,
            legacyFormat = header.legacyFormat,
            canImport = payload.items.isNotEmpty(),
            totalCount = payload.sourceCount,
            validCount = payload.items.size,
            newCount = newCount,
            updatedCount = updatedCount,
            unchangedCount = unchangedCount,
            duplicateCount = payload.duplicateCount,
            invalidCount = payload.invalidCount,
            stats = buildJsonImportStats(
                context,
                payload.sourceCount,
                payload.items.size,
                newCount,
                updatedCount,
                unchangedCount,
                payload.invalidCount,
                payload.duplicateCount
            ) + listOf(
                KeiOSJsonImportStat(
                    context.getString(R.string.json_import_stat_actions),
                    optionCounts.actionsUpdateCount.toString()
                ),
                KeiOSJsonImportStat(
                    context.getString(R.string.json_import_stat_precise_version),
                    optionCounts.preciseApkVersionOverrideCount.toString()
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
