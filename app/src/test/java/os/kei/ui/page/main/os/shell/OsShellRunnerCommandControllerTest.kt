package os.kei.ui.page.main.os.shell

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OsShellRunnerCommandControllerTest {
    @Test
    fun `completed command stores final short output and emits completion event`() =
        runTest {
            val harness = createHarness()

            harness.runShellCommand(
                command = "whoami",
                timeoutMs = 3_000L,
                completionToast = true,
                onRunShellCommand = { command, timeoutMs, emitOutput ->
                    assertEquals("whoami", command)
                    assertEquals(3_000L, timeoutMs)
                    emitOutput("shell\n")
                    "shell"
                },
            )
            advanceUntilIdle()

            val outputState = harness.repository.observePersistentState().value.outputState
            assertFalse(harness.controller.executionState.value.runningCommand)
            assertEquals("shell", outputState.latestRunResultOutput)
            assertEquals("whoami", outputState.outputEntries.single().command)
            assertEquals("shell", outputState.outputEntries.single().result)
            assertEquals(OsShellRunnerEvent.LiquidToast("Completed"), harness.events.single())
        }

    @Test
    fun `streamed chunks collapse into one final output entry`() =
        runTest {
            val harness = createHarness()
            val expectedOutput =
                """
                  level: 100
                  Capacity level: -1
                """.trimIndent()

            harness.runShellCommand(
                command = "dumpsys battery | grep level",
                completionToast = false,
                onRunShellCommand = { _, _, emitOutput ->
                    emitOutput("  level: 100\n")
                    emitOutput(expectedOutput)
                    expectedOutput
                },
            )
            advanceUntilIdle()

            val outputState = harness.repository.observePersistentState().value.outputState
            assertFalse(harness.controller.executionState.value.runningCommand)
            assertEquals(1, outputState.outputEntries.size)
            assertEquals("dumpsys battery | grep level", outputState.outputEntries.single().command)
            assertEquals(expectedOutput, outputState.outputEntries.single().result)
            assertEquals(expectedOutput, outputState.latestRunResultOutput)
            assertTrue(harness.events.isEmpty())
        }

    @Test
    fun `latest streamed chunk becomes final output when executor returns null`() =
        runTest {
            val harness = createHarness()
            val latestOutput = "uid=2000(shell) gid=2000(shell) context=u:r:shell:s0"

            harness.runShellCommand(
                command = "id",
                completionToast = false,
                onRunShellCommand = { _, _, emitOutput ->
                    emitOutput("uid=2000(shell)")
                    emitOutput(latestOutput)
                    null
                },
            )
            advanceUntilIdle()

            val latestEntry = harness.repository.observePersistentState().value.outputState.outputEntries.single()
            assertFalse(harness.controller.executionState.value.runningCommand)
            assertEquals("id", latestEntry.command)
            assertEquals(latestOutput, latestEntry.result)
            assertEquals(latestOutput, harness.repository.observePersistentState().value.outputState.latestRunResultOutput)
        }

    @Test
    fun `executor throwable becomes final output without leaving running state`() =
        runTest {
            val harness = createHarness()

            harness.runShellCommand(
                command = "getprop ro.build.version.release",
                completionToast = false,
                onRunShellCommand = { _, _, _ ->
                    error("boom")
                },
            )
            advanceUntilIdle()

            val latestEntry = harness.repository.observePersistentState().value.outputState.outputEntries.single()
            assertFalse(harness.controller.executionState.value.runningCommand)
            assertEquals("getprop ro.build.version.release", latestEntry.command)
            assertEquals("boom", latestEntry.result)
        }

    @Test
    fun `user stop completes output with stopped marker and event`() =
        runTest {
            val harness = createHarness()
            val commandStarted = CompletableDeferred<Unit>()

            harness.runShellCommand(
                command = "sleep 10",
                completionToast = true,
                onRunShellCommand = { _, _, emitOutput ->
                    emitOutput("waiting")
                    commandStarted.complete(Unit)
                    delay(10_000L)
                    "done"
                },
            )
            commandStarted.await()
            runCurrent()

            harness.controller.stopShellCommand(showStoppedOutput = true)
            advanceUntilIdle()

            val latestEntry = harness.repository.observePersistentState().value.outputState.outputEntries.single()
            assertFalse(harness.controller.executionState.value.runningCommand)
            assertEquals("Stopped", latestEntry.result)
            assertTrue(latestEntry.isStopped)
            assertEquals(OsShellRunnerEvent.LiquidToast("Stopped"), harness.events.single())
        }

    private fun TestScope.createHarness(
        commandStoppedText: String = "Stopped",
        commandCompletedText: String = "Completed",
        noOutputText: String = "No output",
        outputResultLabel: String = "Result",
        outputTimeLabel: String = "Time",
    ): ShellRunnerControllerHarness =
        ShellRunnerControllerHarness(
            scope = this,
            commandStoppedText = commandStoppedText,
            commandCompletedText = commandCompletedText,
            noOutputText = noOutputText,
            outputResultLabel = outputResultLabel,
            outputTimeLabel = outputTimeLabel,
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ShellRunnerControllerHarness(
    private val scope: kotlinx.coroutines.test.TestScope,
    private val commandStoppedText: String,
    private val commandCompletedText: String,
    private val noOutputText: String,
    private val outputResultLabel: String,
    private val outputTimeLabel: String,
) {
    val repository = OsShellRunnerRepository(ioDispatcher = Dispatchers.Unconfined)
    val eventFlow = MutableSharedFlow<OsShellRunnerEvent>(replay = 8, extraBufferCapacity = 8)
    val events: List<OsShellRunnerEvent>
        get() = eventFlow.replayCache
    val controller =
        OsShellRunnerCommandController(
            scope = scope,
            repository = repository,
            events = eventFlow,
        )

    fun runShellCommand(
        command: String,
        timeoutMs: Long = 1_000L,
        completionToast: Boolean,
        onRunShellCommand: OsShellRunnerCommandExecutor,
    ) {
        controller.runShellCommand(
            command = command,
            timeoutMs = timeoutMs,
            commandStoppedText = commandStoppedText,
            commandCompletedText = commandCompletedText,
            noOutputText = noOutputText,
            outputResultLabel = outputResultLabel,
            outputTimeLabel = outputTimeLabel,
            completionToast = completionToast,
            onRunShellCommand = onRunShellCommand,
        )
    }
}
