package os.kei.mcp.server

import android.content.Context
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import os.kei.core.system.ShizukuApiUtils
import java.util.Locale

class LocalMcpService(
    appContext: Context,
    shizukuApiUtils: ShizukuApiUtils,
    appVersionName: String,
    appVersionCode: Long,
    appPackageName: String,
    appLabel: String
) {
    @Volatile
    private var activeServer: Server? = null

    @Volatile
    private var mcpStateProvider: (() -> McpServerUiState)? = null

    @Volatile
    private var toolCallLogger: ((name: String, elapsedMs: Long, success: Boolean, error: String?) -> Unit)? = null

    private val environment = McpToolEnvironment(
        appContext = appContext,
        shizukuApiUtils = shizukuApiUtils,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        appPackageName = appPackageName,
        appLabel = appLabel,
        stateProvider = { mcpStateProvider?.invoke() },
        toolCallLogger = { name, elapsedMs, success, error ->
            toolCallLogger?.invoke(name, elapsedMs, success, error)
        }
    )
    private val runtimeTools = McpRuntimeTools(environment)
    private val skillContent = McpSkillContent(environment, runtimeTools::buildRuntimeConfigJson)
    private val homeTools = McpHomeTools(environment)
    private val systemOsTools = McpSystemOsTools(environment)
    private val githubTrackingTools = McpGitHubTrackingTools(environment)
    private val githubDiscoveryTools = McpGitHubDiscoveryTools(environment)
    private val githubActionsTools = McpGitHubActionsTools(environment)
    private val baTools = McpBaTools(environment)

    private val serverInstructions: String by lazy {
        skillContent.buildServerInstructions()
    }

    fun bindMcpStateProvider(provider: () -> McpServerUiState) {
        mcpStateProvider = provider
    }

    fun bindToolCallLogger(
        logger: (name: String, elapsedMs: Long, success: Boolean, error: String?) -> Unit
    ) {
        toolCallLogger = logger
    }

    fun createRuntimeServer(): Server {
        val server = createServer()
        activeServer = server
        return server
    }

    fun currentRuntimeServer(): Server? {
        return activeServer
    }

    fun clearRuntimeServer(server: Server) {
        if (activeServer === server) {
            activeServer = null
        }
    }

    fun getSkillMarkdownForUi(): String {
        return skillContent.loadSkillMarkdown()
    }

    fun listLocalTools(): List<McpToolMeta> {
        return McpToolCatalog.forLocale(currentLocale())
    }

    private fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "keios-local-mcp",
                version = environment.appVersionName
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false),
                    resources = ServerCapabilities.Resources(listChanged = false, subscribe = false),
                    prompts = ServerCapabilities.Prompts(listChanged = false)
                )
            ),
            instructions = serverInstructions
        )

        runtimeTools.register(server)
        skillContent.registerClawGuideTool(server)
        homeTools.register(server)
        systemOsTools.register(server)
        githubTrackingTools.register(server)
        githubDiscoveryTools.register(server)
        githubActionsTools.register(server)
        baTools.register(server)
        skillContent.registerResources(server)
        skillContent.registerPrompt(server)
        return server
    }

    private fun currentLocale(): Locale {
        return environment.currentLocale()
    }
}
