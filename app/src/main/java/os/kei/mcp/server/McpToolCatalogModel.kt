package os.kei.mcp.server

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
}

