package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.shared.currentRequestHandlerExtra
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotification
import io.modelcontextprotocol.kotlin.sdk.types.ProgressNotificationParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TaskSupport
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolExecution
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import io.modelcontextprotocol.kotlin.sdk.types.error
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.system.measureTimeMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a tool may run, and whether it needs to leave the MCP handler pool to do it.
 *
 * [dispatcherOverride] is null for everything but [Cpu], and that is the point. Until kotlin-sdk
 * 0.15.0 the protocol processed inbound messages serially on the transport read loop, so every tool
 * had to hop to its own dispatcher purely to stop one slow call blocking the whole connection. Each
 * profile therefore carried `AppDispatchers.mcpServer`, four threads wide.
 *
 * 0.15.0 dispatches handlers concurrently onto `ProtocolOptions.handlerCoroutineContext`, which
 * [os.kei.mcp.server.LocalMcpService] now pins to `AppDispatchers.mcpServer`. The hop became worse
 * than redundant: the handler already arrives on that pool, and hopping onto it again from itself
 * bought nothing while the four-thread cap turned into a second serialization point *behind* the one
 * the SDK had just removed. Four concurrent 60-second DeepScans held every thread, and a 4-second
 * CacheRead queued behind them — the exact head-of-line blocking the release notes describe fixing.
 *
 * So IO-shaped work now simply runs where it was dispatched. Only [Cpu] switches, leaving the
 * IO-backed handler pool for [Dispatchers.Default] so a compute-bound tool does not hold an IO thread.
 */
enum class McpToolExecutionProfile(
    val timeout: Duration,
    val dispatcherOverride: CoroutineDispatcher? = null,
    val progressInterval: Duration? = null
) {
    CacheRead(4.seconds),
    NormalWrite(8.seconds),
    Network(30.seconds, progressInterval = 5.seconds),
    DeepScan(60.seconds, progressInterval = 5.seconds),
    Cpu(4.seconds, dispatcherOverride = Dispatchers.Default)
}

/**
 * Emits `notifications/progress` on the request's own stream while a long tool runs.
 *
 * Only possible from kotlin-sdk 0.15.0: `RequestHandlerExtra` is now a `CoroutineContext.Element`, so
 * a nested helper can reach the in-flight request via [currentRequestHandlerExtra] without threading
 * it through every tool signature, and its `sendNotification` tags the notification with
 * `relatedRequestId` so transports route it onto the SSE stream that request is waiting on.
 *
 * Three conditions gate it, and all three are the client's or the profile's call rather than ours:
 * a profile long enough to be worth reporting, a client that asked by supplying a `progressToken` in
 * `_meta`, and an actual handler context. Any missing, and the tool runs exactly as before.
 *
 * The ticker is a plain elapsed-time heartbeat, not real completion: [total] is the profile timeout,
 * so a client can render "23s of at most 60s". Tools do not report their own units, and inventing a
 * percentage they cannot substantiate would be worse than an honest clock.
 */
private suspend fun <T> withToolProgress(
    profile: McpToolExecutionProfile,
    name: String,
    request: CallToolRequest,
    block: suspend () -> T
): T {
    val interval = profile.progressInterval ?: return block()
    val token = request.params.meta?.progressToken ?: return block()
    val extra = currentRequestHandlerExtra() ?: return block()
    val totalMs = profile.timeout.inWholeMilliseconds.toDouble()
    return coroutineScope {
        val ticker = launch {
            var elapsedMs = 0L
            while (true) {
                delay(interval)
                elapsedMs += interval.inWholeMilliseconds
                runCatching {
                    extra.sendNotification(
                        ProgressNotification(
                            ProgressNotificationParams(
                                progressToken = token,
                                progress = elapsedMs.toDouble(),
                                total = totalMs,
                                message = "$name running ${elapsedMs / 1000}s"
                            )
                        )
                    )
                }
            }
        }
        try {
            block()
        } finally {
            ticker.cancel()
        }
    }
}

fun Server.addMcpTextTool(
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
        outputSchema = meta?.outputSchema,
        toolAnnotations = ToolAnnotations(
            title = meta?.title,
            readOnlyHint = meta?.readOnly,
            destructiveHint = meta?.destructive,
            idempotentHint = meta?.idempotent,
            openWorldHint = meta?.openWorld
        ),
        execution = ToolExecution(TaskSupport.Forbidden),
        meta = meta?.toProtocolMeta()
    ) { request ->
        executeMcpTextTool(
            environment = environment,
            name = name,
            profile = profile,
            outputContract = meta?.outputContract ?: McpToolOutputContract.KeyValueText,
            structuredOutputEnabled = meta?.outputSchema != null,
            request = request,
            handler = handler
        )
    }
}

private fun McpToolMeta.toProtocolMeta() = buildJsonObject {
    put("keios/group", group)
    put("keios/visibility", visibility.wireName)
    put("keios/maturity", maturity.wireName)
    put("keios/executionProfile", executionProfile.name)
    put("keios/output", outputContract.wireName)
    put("keios/entrypoint", name in MCP_ENTRYPOINT_TOOLS)
    put("keios/writeRequiresApply", name.endsWith(".import"))
    if (workflowTags.isNotEmpty()) {
        put(
            "keios/workflowTags",
            buildJsonArray {
                workflowTags.forEach { tag -> add(JsonPrimitive(tag)) }
            }
        )
    }
    if (recommendedFor.isNotEmpty()) {
        put(
            "keios/recommendedFor",
            buildJsonArray {
                recommendedFor.forEach { value -> add(JsonPrimitive(value)) }
            }
        )
    }
    put(
        "keios/arguments",
        buildJsonArray {
            arguments.forEach { argument ->
                add(
                    buildJsonObject {
                        put("name", argument.name)
                        put("type", argument.type.wireName)
                        put("required", argument.required)
                        if (argument.description.isNotBlank()) {
                            put("description", argument.description)
                        }
                        if (argument.defaultValue.isNotBlank()) {
                            put("default", argument.defaultValue)
                        }
                        if (argument.enumValues.isNotEmpty()) {
                            put(
                                "enum",
                                buildJsonArray {
                                    argument.enumValues.forEach { value -> add(JsonPrimitive(value)) }
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

internal suspend fun executeMcpTextTool(
    environment: McpToolEnvironment,
    name: String,
    profile: McpToolExecutionProfile,
    outputContract: McpToolOutputContract,
    structuredOutputEnabled: Boolean,
    request: CallToolRequest,
    handler: suspend (CallToolRequest) -> String
): CallToolResult {
    var output = ""
    val elapsedMs = try {
        measureTimeMillis {
            output = withTimeout(profile.timeout) {
                withToolProgress(profile = profile, name = name, request = request) {
                    // Already on the handler pool; only a compute-bound profile moves off it.
                    val override = profile.dispatcherOverride
                    if (override == null) handler(request) else withContext(override) { handler(request) }
                }
            }
        }
    } catch (error: TimeoutCancellationException) {
        val text = buildMcpToolErrorText(name, "timeout_${profile.timeout.inWholeMilliseconds}ms")
        environment.recordToolCall(
            name = name,
            profile = profile,
            elapsedMs = profile.timeout.inWholeMilliseconds,
            success = false,
            error = "timeout"
        )
        return CallToolResult.error(text)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val reason = error.message ?: error.javaClass.simpleName
        val text = buildMcpToolErrorText(name, reason)
        environment.recordToolCall(
            name = name,
            profile = profile,
            elapsedMs = 0L,
            success = false,
            error = reason
        )
        return CallToolResult.error(text)
    }

    val businessError = McpToolBusinessErrors.isBusinessError(output)
    environment.recordToolCall(
        name = name,
        profile = profile,
        elapsedMs = elapsedMs,
        success = !businessError,
        error = if (businessError) output.lineSequence().firstOrNull().orEmpty() else null
    )
    return if (businessError) {
        CallToolResult.error(output)
    } else {
        CallToolResult(
            content = listOf(TextContent(output)),
            structuredContent = if (structuredOutputEnabled) {
                buildJsonObject {
                    put("text", output)
                    put("format", outputContract.wireName)
                }
            } else {
                null
            }
        )
    }
}

private fun buildMcpToolErrorText(name: String, reason: String): String {
    return buildString {
        appendLine("ok=false")
        appendLine("tool=$name")
        appendLine("message=$reason")
    }.trim()
}

private fun ToolSchema.withRequired(requiredArguments: List<String>): ToolSchema {
    if (requiredArguments.isEmpty()) return this
    return copy(required = requiredArguments)
}
