package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class McpSchemaType(val wireName: String) {
    String("string"),
    Integer("integer"),
    Boolean("boolean")
}

data class McpToolArgumentSpec(
    val name: String,
    val type: McpSchemaType,
    val required: Boolean = false
)

internal object McpSchema {
    fun string(name: String, required: Boolean = false): McpToolArgumentSpec {
        return McpToolArgumentSpec(name = name, type = McpSchemaType.String, required = required)
    }

    fun integer(name: String, required: Boolean = false): McpToolArgumentSpec {
        return McpToolArgumentSpec(name = name, type = McpSchemaType.Integer, required = required)
    }

    fun boolean(name: String, required: Boolean = false): McpToolArgumentSpec {
        return McpToolArgumentSpec(name = name, type = McpSchemaType.Boolean, required = required)
    }

    fun toolSchema(arguments: List<McpToolArgumentSpec>): ToolSchema {
        return ToolSchema(
            properties = buildJsonObject {
                arguments.forEach { argument ->
                    put(
                        argument.name,
                        buildJsonObject {
                            put("type", JsonPrimitive(argument.type.wireName))
                        }
                    )
                }
            },
            required = arguments.filter { it.required }.map { it.name }.ifEmpty { null }
        )
    }
}
