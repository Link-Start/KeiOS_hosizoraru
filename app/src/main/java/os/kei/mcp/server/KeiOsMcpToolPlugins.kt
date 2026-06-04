package os.kei.mcp.server

import io.modelcontextprotocol.kotlin.sdk.server.Server

object KeiOsMcpToolPlugins {
    fun create(): List<McpServerToolPlugin> =
        listOf(
            HomePlugin,
            SystemOsPlugin,
            GitHubTrackingPlugin,
            GitHubDiscoveryPlugin,
            GitHubActionsPlugin,
            BaPlugin,
        )

    private data object HomePlugin : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.homeToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpHomeTools(environment).register(server)
        }
    }

    private data object SystemOsPlugin : McpServerToolPlugin {
        override val toolNames: List<String> =
            McpToolCatalog.systemToolNames + McpToolCatalog.osToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpSystemOsTools(environment).register(server)
        }
    }

    private data object GitHubTrackingPlugin : McpServerToolPlugin {
        override val toolNames: List<String> =
            listOf(
                "keios.github.tracks.snapshot",
                "keios.github.tracks.list",
                "keios.github.tracks.export",
                "keios.github.tracks.import",
                "keios.github.tracks.check",
                "keios.github.tracks.summary",
                "keios.github.link.parse",
                "keios.github.link.resolve",
                "keios.github.link.pending",
                "keios.github.cache.clear",
            )

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpGitHubTrackingTools(environment).register(server)
        }
    }

    private data object GitHubDiscoveryPlugin : McpServerToolPlugin {
        override val toolNames: List<String> =
            listOf(
                "keios.github.config.snapshot",
                "keios.github.discovery.search",
                "keios.github.repo.package.scan",
                "keios.github.direct_apk.inspect",
                "keios.github.package.repo.scan",
                "keios.github.stars.lists",
                "keios.github.stars.preview",
                "keios.github.stars.import",
                "keios.github.stars.apk.verify",
            )

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpGitHubDiscoveryTools(environment).register(server)
        }
    }

    private data object GitHubActionsPlugin : McpServerToolPlugin {
        override val toolNames: List<String> =
            listOf("keios.github.actions.recommended")

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpGitHubActionsTools(environment).register(server)
        }
    }

    private data object BaPlugin : McpServerToolPlugin {
        override val toolNames: List<String> = McpToolCatalog.baToolNames

        override fun registerTools(
            server: Server,
            environment: McpToolEnvironment,
        ) {
            McpBaTools(environment).register(server)
        }
    }
}
