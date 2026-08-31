package os.kei.mcp.server

import java.io.File
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import org.junit.Test

/**
 * The MCP server must have exactly one place where handler concurrency is capped.
 *
 * kotlin-sdk 0.15.0 stopped processing inbound messages serially on the transport read loop and began
 * dispatching handlers concurrently onto `ProtocolOptions.handlerCoroutineContext`. Both halves of
 * that have to be honoured here, and each one is silent when it is wrong:
 *
 * 1. **The context has to be pinned.** Left at its `Dispatchers.Default` default, every MCP tool —
 *    overwhelmingly network and disk work — runs on the CPU pool.
 * 2. **Tools must not hop again after arriving.** Each profile used to carry
 *    `AppDispatchers.mcpServer` and `executeMcpTextTool` hopped onto it unconditionally, which was
 *    correct while the SDK was serial and became a second, *narrower* serialization point once it was
 *    not: four threads shared by the 30s network and 60s deep-scan profiles, with 4-second cache
 *    reads queued behind them.
 *
 * Neither shows up as a failure — the server keeps answering, just one long tool at a time — so the
 * shape is asserted here rather than left to be rediscovered from a frame chart.
 */
class McpHandlerDispatchSourceTest {
    @Test
    fun theServerPinsItsHandlerCoroutineContext() {
        val source = sourceFile(LOCAL_SERVICE)

        assertTrue(
            "handlerCoroutineContext = AppDispatchers.mcpServer" in source,
            "$LOCAL_SERVICE must pin handlerCoroutineContext, or every MCP tool runs on the CPU pool",
        )
    }

    @Test
    fun onlyComputeBoundWorkLeavesTheHandlerPool() {
        val overrides =
            McpToolExecutionProfile.entries.filter { profile -> profile.dispatcherOverride != null }

        assertTrue(
            overrides == listOf(McpToolExecutionProfile.Cpu),
            "Only Cpu may leave the handler pool; the rest run where the SDK dispatched them. Found: $overrides",
        )
        assertTrue(
            McpToolExecutionProfile.Cpu.dispatcherOverride === Dispatchers.Default,
            "Cpu must move compute-bound work off the IO-backed handler pool",
        )
    }

    /**
     * Keyed on the guard, not on the call: an unconditional `withContext` is exactly the regression.
     */
    @Test
    fun theExecutionPathHopsOnlyWhenAProfileAsksForIt() {
        val source = sourceFile(EXECUTION)

        assertTrue(
            "profile.dispatcherOverride" in source,
            "$EXECUTION must consult the override rather than hop unconditionally",
        )
        assertTrue(
            "withContext(profile.dispatcher)" !in source,
            "$EXECUTION must not reintroduce the unconditional dispatcher hop",
        )
    }

    /**
     * Progress is opt-in from both sides, and the long profiles are the ones that need it.
     */
    @Test
    fun theLongProfilesReportProgress() {
        val reporting =
            McpToolExecutionProfile.entries
                .filter { profile -> profile.progressInterval != null }
                .toSet()

        assertTrue(
            reporting == setOf(McpToolExecutionProfile.Network, McpToolExecutionProfile.DeepScan),
            "The 30s and 60s profiles are the ones worth reporting on. Found: $reporting",
        )
        reporting.forEach { profile ->
            assertTrue(
                profile.progressInterval!! < profile.timeout,
                "${profile.name} would time out before emitting a single progress notification",
            )
        }
    }

    @Test
    fun progressIsGatedOnTheClientAskingForIt() {
        val source = sourceFile(EXECUTION)

        assertTrue(
            "params.meta?.progressToken" in source,
            "Progress must only be sent when the client supplied a progressToken in _meta",
        )
        assertTrue(
            "currentRequestHandlerExtra()" in source,
            "Progress must be sent through the in-flight request so it is tagged with relatedRequestId",
        )
    }
}

private const val LOCAL_SERVICE = "feature-mcp/src/main/java/os/kei/mcp/server/LocalMcpService.kt"
private const val EXECUTION = "feature-mcp/src/main/java/os/kei/mcp/server/McpToolExecution.kt"

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    val root =
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile }
    return requireNotNull(root) { "Unable to locate the repository root from $workingDirectory" }
}

private fun sourceFile(relativePath: String): String =
    File(repositoryRoot(), relativePath).let { file ->
        require(file.isFile) { "Unable to locate $relativePath" }
        file.readText()
    }
