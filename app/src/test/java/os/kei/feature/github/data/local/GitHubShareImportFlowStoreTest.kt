package os.kei.feature.github.data.local

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubShareImportFlowStoreTest {
    @Test
    fun `preview and attach ttl expires after active preview window`() {
        assertFalse(isGitHubShareImportPreviewExpired(NOW_MS, NOW_MS + 5_000L))
        assertFalse(
            isGitHubShareImportPreviewExpired(
                createdAtMillis = NOW_MS,
                nowMillis = NOW_MS + GITHUB_SHARE_IMPORT_ACTIVE_PREVIEW_MAX_AGE_MS
            )
        )
        assertTrue(
            isGitHubShareImportPreviewExpired(
                createdAtMillis = NOW_MS,
                nowMillis = NOW_MS + GITHUB_SHARE_IMPORT_ACTIVE_PREVIEW_MAX_AGE_MS + 1L
            )
        )
        assertTrue(isGitHubShareImportPreviewExpired(0L, NOW_MS))
    }

    @Test
    fun `result ttl keeps completed state longer than active preview`() {
        assertFalse(
            isGitHubShareImportResultExpired(
                completedAtMillis = NOW_MS,
                nowMillis = NOW_MS + GITHUB_SHARE_IMPORT_ACTIVE_PREVIEW_MAX_AGE_MS + 1L
            )
        )
        assertTrue(
            isGitHubShareImportResultExpired(
                completedAtMillis = NOW_MS,
                nowMillis = NOW_MS + GITHUB_SHARE_IMPORT_ACTIVE_RESULT_MAX_AGE_MS + 1L
            )
        )
    }

    private companion object {
        private const val NOW_MS = 1_777_392_000_000L
    }
}
