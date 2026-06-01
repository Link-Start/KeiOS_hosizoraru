package os.kei.ui.page.main.github.page

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES
import os.kei.core.io.readTextFromUriLimited
import os.kei.feature.github.data.local.GitHubTrackedItemsImportPayload
import os.kei.feature.github.domain.GitHubTrackedItemsImportPreview
import os.kei.feature.github.domain.GitHubTrackedItemsTransferService
import os.kei.feature.github.model.GitHubTrackedApp

internal class GitHubPageTransferRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.githubNetwork,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
) {
    suspend fun parseTrackedItemsImport(raw: String): GitHubTrackedItemsImportPayload {
        return withContext(defaultDispatcher) {
            GitHubTrackedItemsTransferService.parseImport(raw)
        }
    }

    suspend fun buildTrackedItemsImportPreview(
        payload: GitHubTrackedItemsImportPayload,
        existingItems: List<GitHubTrackedApp>
    ): GitHubTrackImportPreview {
        return withContext(defaultDispatcher) {
            GitHubTrackedItemsTransferService
                .buildImportPreview(
                    payload = payload,
                    existingItems = existingItems,
                )
                .toPagePreview()
        }
    }

    suspend fun buildTrackedItemsExportJson(
        items: List<GitHubTrackedApp>,
        exportedAtMillis: Long
    ): String {
        return withContext(defaultDispatcher) {
            GitHubTrackedItemsTransferService.buildExportJson(
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
        return contentResolver.readTextFromUriLimited(
            uri = uri,
            maxBytes = DEFAULT_BOUNDED_TEXT_READ_MAX_BYTES,
            ioDispatcher = ioDispatcher
        ).text
    }
}

private fun GitHubTrackedItemsImportPreview.toPagePreview(): GitHubTrackImportPreview =
    GitHubTrackImportPreview(
        payload = payload,
        fileItemCount = fileItemCount,
        validCount = validCount,
        duplicateCount = duplicateCount,
        invalidCount = invalidCount,
        newCount = newCount,
        updatedCount = updatedCount,
        unchangedCount = unchangedCount,
        mergedCount = mergedCount,
        githubRepositoryCount = githubRepositoryCount,
        gitRepositoryCount = gitRepositoryCount,
        directApkCount = directApkCount,
        preferPreReleaseCount = preferPreReleaseCount,
        latestReleaseDownloadCount = latestReleaseDownloadCount,
        actionsUpdateCount = actionsUpdateCount,
        preciseApkVersionOverrideCount = preciseApkVersionOverrideCount,
    )
