package os.kei.ui.page.main.github.page

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import os.kei.feature.github.data.local.GitHubTrackStore
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.model.GitHubTrackedApp

internal class GitHubPageTransferRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun parseTrackedItemsImport(raw: String): GitHubTrackedItemsImportPayload {
        return withContext(defaultDispatcher) {
            GitHubTrackStore.parseTrackedItemsImport(raw)
        }
    }

    suspend fun buildTrackedItemsImportPreview(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): GitHubTrackImportPreview {
        return withContext(defaultDispatcher) {
            val existingItemsById = existingItems.associateBy { it.id }
            var newCount = 0
            var updatedCount = 0
            var unchangedCount = 0
            payload.items.forEach { item ->
                when (val existingItem = existingItemsById[item.id]) {
                    null -> newCount += 1
                    item -> unchangedCount += 1
                    else -> updatedCount += 1
                }
            }
            val optionCounts = GitHubTrackStore.calculateTrackedItemsOptionCounts(payload.items)
            val sourceCounts = GitHubTrackStore.calculateTrackedItemsSourceCounts(payload.items)
            GitHubTrackImportPreview(
                payload = payload,
                fileItemCount = payload.sourceCount,
                validCount = payload.items.size,
                duplicateCount = payload.duplicateCount,
                invalidCount = payload.invalidCount,
                newCount = newCount,
                updatedCount = updatedCount,
                unchangedCount = unchangedCount,
                mergedCount = existingItems.size + newCount,
                githubRepositoryCount = sourceCounts.githubRepositoryCount,
                directApkCount = sourceCounts.directApkCount,
                preferPreReleaseCount = optionCounts.preferPreReleaseCount,
                latestReleaseDownloadCount = optionCounts.latestReleaseDownloadCount,
                actionsUpdateCount = optionCounts.actionsUpdateCount,
                preciseApkVersionOverrideCount = optionCounts.preciseApkVersionOverrideCount
            )
        }
    }

    suspend fun buildTrackedItemsExportJson(
        items: List<GitHubTrackedApp>,
        exportedAtMillis: Long
    ): String {
        return withContext(defaultDispatcher) {
            GitHubTrackStore.buildTrackedItemsExportJson(
                items = items,
                exportedAtMillis = exportedAtMillis
            )
        }
    }

    suspend fun writeText(
        contentResolver: ContentResolver,
        uri: Uri,
        content: String
    ) {
        withContext(ioDispatcher) {
            contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                checkNotNull(writer) { "openOutputStream returned null" }
                writer.write(content)
            }
        }
    }

    suspend fun readText(
        contentResolver: ContentResolver,
        uri: Uri
    ): String {
        return withContext(ioDispatcher) {
            contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                checkNotNull(reader) { "openInputStream returned null" }
                reader.readText()
            }
        }
    }
}
