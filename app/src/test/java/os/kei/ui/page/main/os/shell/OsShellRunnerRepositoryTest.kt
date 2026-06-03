package os.kei.ui.page.main.os.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OsShellRunnerRepositoryTest {
    @Test
    fun `streaming output updates persistent latest result`() =
        runTest {
            val repository = OsShellRunnerRepository(ioDispatcher = Dispatchers.Unconfined)

            repository.startStreamingOutput(command = "id")
            repository.updateStreamingOutput(
                command = "id",
                result = "uid=2000(shell)",
                commandStoppedText = "Stopped",
            )

            val outputState = repository.observePersistentState().value.outputState
            assertEquals(1, outputState.outputEntries.size)
            assertEquals("id", outputState.outputEntries.single().command)
            assertEquals("uid=2000(shell)", outputState.outputEntries.single().result)
            assertEquals("uid=2000(shell)", outputState.latestRunResultOutput)
        }

    @Test
    fun `completed stopped output marks latest entry stopped`() =
        runTest {
            val repository = OsShellRunnerRepository(ioDispatcher = Dispatchers.Unconfined)

            repository.startStreamingOutput(command = "sleep 10")
            repository.completeStreamingOutput(
                command = "sleep 10",
                result = "Stopped",
                commandStoppedText = "Stopped",
                outputResultLabel = "Result",
                outputTimeLabel = "Time",
            )

            val latestEntry = repository.observePersistentState().value.outputState.outputEntries.single()
            assertEquals("Stopped", latestEntry.result)
            assertTrue(latestEntry.isStopped)
        }
}
