package os.kei.ui.page.main.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebDavSyncRepositoryTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `load local counts runs on injected dispatcher and preserves per item failures`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = WebDavSyncRepository(ioDispatcher = dispatcher)
            var githubCountCalls = 0
            var shellCountCalls = 0
            val ports =
                mapOf(
                    WebDavSyncItem.GitHubTracked to
                        webDavSyncDataPort(
                            localCount = {
                                githubCountCalls += 1
                                75
                            },
                        ),
                    WebDavSyncItem.OsShellCards to
                        webDavSyncDataPort(
                            localCount = {
                                shellCountCalls += 1
                                error("shell cards unavailable")
                            },
                        ),
                )

            val countsDeferred = async { repository.loadLocalCounts(ports) }

            assertFalse(countsDeferred.isCompleted)
            advanceUntilIdle()

            val counts = countsDeferred.await()
            assertEquals(75, counts[WebDavSyncItem.GitHubTracked])
            assertEquals(-1, counts[WebDavSyncItem.OsShellCards])
            assertEquals(1, githubCountCalls)
            assertEquals(1, shellCountCalls)
        }
}

private fun webDavSyncDataPort(localCount: () -> Int): WebDavSyncDataPort =
    WebDavSyncDataPort(
        exportJson = { "{}" },
        merge = {},
        localCount = localCount,
        countRemoteItems = { 0 },
    )
