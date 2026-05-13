package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

enum class McpSchemaType(val wireName: String) {
    String("string"),
    Integer("integer"),
    Boolean("boolean")
}

data class McpToolArgumentSpec(
    val name: String,
    val type: McpSchemaType,
    val required: Boolean = false,
    val description: String = "",
    val enumValues: List<String> = emptyList(),
    val defaultValue: String = ""
)

internal object McpSchema {
    fun string(
        name: String,
        required: Boolean = false,
        description: String = "",
        enumValues: List<String> = emptyList(),
        defaultValue: String = ""
    ): McpToolArgumentSpec {
        return McpToolArgumentSpec(
            name = name,
            type = McpSchemaType.String,
            required = required,
            description = description,
            enumValues = enumValues,
            defaultValue = defaultValue
        )
    }

    fun integer(
        name: String,
        required: Boolean = false,
        description: String = "",
        defaultValue: String = ""
    ): McpToolArgumentSpec {
        return McpToolArgumentSpec(
            name = name,
            type = McpSchemaType.Integer,
            required = required,
            description = description,
            defaultValue = defaultValue
        )
    }

    fun boolean(
        name: String,
        required: Boolean = false,
        description: String = "",
        defaultValue: String = ""
    ): McpToolArgumentSpec {
        return McpToolArgumentSpec(
            name = name,
            type = McpSchemaType.Boolean,
            required = required,
            description = description,
            defaultValue = defaultValue
        )
    }

    fun toolSchema(arguments: List<McpToolArgumentSpec>): ToolSchema {
        return ToolSchema(
            properties = buildJsonObject {
                arguments.forEach { argument ->
                    put(
                        argument.name,
                        buildJsonObject {
                            put("type", JsonPrimitive(argument.type.wireName))
                            if (argument.description.isNotBlank()) {
                                put("description", JsonPrimitive(argument.description))
                            }
                            if (argument.enumValues.isNotEmpty()) {
                                put(
                                    "enum",
                                    buildJsonArray {
                                        argument.enumValues.forEach { value ->
                                            add(JsonPrimitive(value))
                                        }
                                    }
                                )
                            }
                            if (argument.defaultValue.isNotBlank()) {
                                put("default", JsonPrimitive(argument.defaultValue))
                            }
                        }
                    )
                }
            },
            required = arguments.filter { it.required }.map { it.name }.ifEmpty { null }
        )
    }
}
