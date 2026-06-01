package os.kei.feature.home.data

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class HomeOverviewRepositoryTest {
    @Test
    fun `store refresh flow emits manual github ba and webdav reasons`() = runBlocking {
        val reasons = buildHomeOverviewStoreRefreshFlow(
            refreshRequests = flowOf("manual"),
            githubVersions = flowOf(0L, 10L),
            baHomeOverviewVersions = flowOf(0L, 20L),
            webDavVersions = flowOf(0L, 30L),
        ).toList()

        assertEquals(
            setOf("manual", "github_store_10", "ba_store_20", "webdav_store_30"),
            reasons.toSet()
        )
    }
}
