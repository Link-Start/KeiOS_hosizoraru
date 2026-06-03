package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema

data class McpToolMeta(
    val name: String,
    val description: String,
    val group: String = "",
    val title: String? = null,
    val arguments: List<McpToolArgumentSpec> = emptyList(),
    val readOnly: Boolean = true,
    val destructive: Boolean = false,
    val idempotent: Boolean = true,
    val openWorld: Boolean = false,
    val executionProfile: McpToolExecutionProfile = McpToolExecutionProfile.CacheRead,
    val visibility: McpToolVisibility = McpToolVisibility.Advanced,
    val maturity: McpToolMaturity = McpToolMaturity.Stable,
    val outputContract: McpToolOutputContract = McpToolOutputContract.KeyValueText,
    val workflowTags: List<String> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val outputSchema: ToolSchema? = null
) {
    val requiredArguments: List<String>
        get() = arguments.filter { it.required }.map { it.name }
}

internal data class McpToolDefinition(
    val readOnly: Boolean = true,
    val destructive: Boolean = false,
    val idempotent: Boolean = true,
    val openWorld: Boolean = false,
    val executionProfile: McpToolExecutionProfile = McpToolExecutionProfile.CacheRead,
    val visibility: McpToolVisibility = McpToolVisibility.Advanced,
    val maturity: McpToolMaturity = McpToolMaturity.Stable,
    val outputContract: McpToolOutputContract = McpToolOutputContract.KeyValueText,
    val workflowTags: List<String> = emptyList(),
    val recommendedFor: List<String> = emptyList(),
    val outputSchema: ToolSchema? = null
)

enum class McpToolVisibility(val wireName: String) {
    Entrypoint("entrypoint"),
    Workflow("workflow"),
    Advanced("advanced"),
    Internal("internal")
}

enum class McpToolMaturity(val wireName: String) {
    Stable("stable"),
    Preview("preview"),
    Experimental("experimental")
}

enum class McpToolOutputContract(val wireName: String) {
    KeyValueText("key_value_text"),
    Markdown("markdown"),
    JsonText("json_text")
}

internal object McpToolDomains {
    const val RUNTIME = "runtime"
    const val HOME = "home"
    const val SYSTEM = "system"
    const val OS = "os"
    const val GITHUB = "github"
    const val BA = "ba"
    const val DEV = "dev"
}
