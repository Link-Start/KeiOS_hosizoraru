package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.error
import io.modelcontextprotocol.kotlin.sdk.types.success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class McpToolExecutionProfile(
    val timeout: Duration,
    val dispatcher: CoroutineDispatcher
) {
    CacheRead(4.seconds, Dispatchers.IO),
    NormalWrite(8.seconds, Dispatchers.IO),
    Network(30.seconds, Dispatchers.IO),
    DeepScan(60.seconds, Dispatchers.IO),
    Cpu(4.seconds, Dispatchers.Default)
}

internal fun Server.addMcpTextTool(
    environment: McpToolEnvironment,
    name: String,
    inputSchema: ToolSchema = McpToolCatalog.schemaFor(name),
    handler: suspend (CallToolRequest) -> String
) {
    val meta = McpToolCatalog.metaForName(name, environment.currentLocale())
    val profile = meta?.executionProfile ?: McpToolExecutionProfile.CacheRead
    addTool(
        name = name,
        description = meta?.description ?: name,
        inputSchema = inputSchema.withRequired(meta?.requiredArguments.orEmpty()),
        toolAnnotations = ToolAnnotations(
            title = meta?.title,
            readOnlyHint = meta?.readOnly,
            destructiveHint = meta?.destructive,
            idempotentHint = meta?.idempotent,
            openWorldHint = meta?.openWorld
        )
    ) { request ->
        executeMcpTextTool(
            environment = environment,
            name = name,
            profile = profile,
            request = request,
            handler = handler
        )
    }
}

internal suspend fun executeMcpTextTool(
    environment: McpToolEnvironment,
    name: String,
    profile: McpToolExecutionProfile,
    request: CallToolRequest,
    handler: suspend (CallToolRequest) -> String
): CallToolResult {
    var output = ""
    val elapsedMs = try {
        measureTimeMillis {
            output = withTimeout(profile.timeout) {
                withContext(profile.dispatcher) {
                    handler(request)
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        val text = buildMcpToolErrorText(name, "timeout_${profile.timeout.inWholeMilliseconds}ms")
        environment.recordToolCall(name = name, elapsedMs = profile.timeout.inWholeMilliseconds, success = false, error = "timeout")
        return CallToolResult.error(text)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val reason = error.message ?: error.javaClass.simpleName
        val text = buildMcpToolErrorText(name, reason)
        environment.recordToolCall(name = name, elapsedMs = 0L, success = false, error = reason)
        return CallToolResult.error(text)
    }

    val businessError = output.isMcpBusinessErrorText()
    environment.recordToolCall(
        name = name,
        elapsedMs = elapsedMs,
        success = !businessError,
        error = if (businessError) output.lineSequence().firstOrNull().orEmpty() else null
    )
    return if (businessError) {
        CallToolResult.error(output)
    } else {
        CallToolResult.success(output)
    }
}

private fun buildMcpToolErrorText(name: String, reason: String): String {
    return buildString {
        appendLine("ok=false")
        appendLine("tool=$name")
        appendLine("message=$reason")
    }.trim()
}

private fun String.isMcpBusinessErrorText(): Boolean {
    val first = lineSequence().firstOrNull().orEmpty().trim()
    return first == "ok=false" ||
        first == "target=unknown" ||
        first.startsWith("hasTarget=false") ||
        contains("message=query_required") ||
        contains("message=url_required") ||
        contains("message=repoUrl_required") ||
        contains("message=repoUrls_required") ||
        contains("message=invalid_package_name") ||
        contains("message=url_required_for_ba_guide_url")
}

private fun ToolSchema.withRequired(requiredArguments: List<String>): ToolSchema {
    if (requiredArguments.isEmpty()) return this
    return copy(required = requiredArguments)
}
