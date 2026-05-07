package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertEquals

class GitHubLookupModelsTest {
    @Test
    fun `share import flow mode resolves storage ids`() {
        assertEquals(
            GitHubShareImportFlowMode.SheetAssisted,
            GitHubShareImportFlowMode.fromStorageId("sheet_assisted")
        )
        assertEquals(
            GitHubShareImportFlowMode.NotificationFirst,
            GitHubShareImportFlowMode.fromStorageId("notification_first")
        )
    }

    @Test
    fun `unknown share import flow mode falls back to sheet assisted`() {
        assertEquals(
            GitHubShareImportFlowMode.SheetAssisted,
            GitHubShareImportFlowMode.fromStorageId("missing")
        )
    }
}
