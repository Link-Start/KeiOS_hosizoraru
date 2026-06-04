package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
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
import os.kei.core.concurrency.AppDispatchers

enum class McpToolExecutionProfile(
    val timeout: Duration,
    val dispatcher: CoroutineDispatcher
) {
    CacheRead(4.seconds, AppDispatchers.mcpServer),
    NormalWrite(8.seconds, AppDispatchers.mcpServer),
    Network(30.seconds, AppDispatchers.mcpServer),
    DeepScan(60.seconds, AppDispatchers.mcpServer),
    Cpu(4.seconds, Dispatchers.Default)
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
                withContext(profile.dispatcher) {
                    handler(request)
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
