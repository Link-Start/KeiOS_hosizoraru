package os.kei.mcp.server

import java.util.Locale

data class McpToolMeta(
    val name: String,
    val description: String
)

internal object McpToolCatalog {
    val runtimeToolNames = listOf(
        "keios.health.ping",
        "keios.app.info",
        "keios.app.version",
        "keios.shizuku.status",
        "keios.mcp.runtime.status",
        "keios.mcp.runtime.logs",
        "keios.mcp.runtime.config",
        "keios.mcp.claw.skill.guide"
    )

    val homeToolNames = listOf(
        "keios.home.overview.snapshot"
    )

    val osToolNames = listOf(
        "keios.os.cards.snapshot",
        "keios.os.activity.cards",
        "keios.os.shell.cards",
        "keios.os.cards.export",
        "keios.os.cards.import"
    )

    val systemToolNames = listOf(
        "keios.system.topinfo.query"
    )

    val githubToolNames = listOf(
        "keios.github.config.snapshot",
        "keios.github.tracks.snapshot",
        "keios.github.tracks.list",
        "keios.github.tracks.export",
        "keios.github.tracks.import",
        "keios.github.tracks.check",
        "keios.github.tracks.summary",
        "keios.github.link.parse",
        "keios.github.link.resolve",
        "keios.github.link.pending",
        "keios.github.discovery.search",
        "keios.github.repo.package.scan",
        "keios.github.package.repo.scan",
        "keios.github.stars.lists",
        "keios.github.stars.preview",
        "keios.github.stars.import",
        "keios.github.stars.apk.verify",
        "keios.github.cache.clear"
    )

    val baToolNames = listOf(
        "keios.ba.snapshot",
        "keios.ba.calendar.cache",
        "keios.ba.pool.cache",
        "keios.ba.guide.catalog.cache",
        "keios.ba.guide.cache.overview",
        "keios.ba.guide.cache.inspect",
        "keios.ba.guide.media.list",
        "keios.ba.guide.bgm.favorites",
        "keios.ba.cache.clear"
    )

    val all: List<McpToolMeta>
        get() = englishTools

    fun forLocale(locale: Locale): List<McpToolMeta> {
        val descriptions = when {
            locale.language.equals("zh", ignoreCase = true) -> zhDescriptions
            locale.language.equals("ja", ignoreCase = true) -> jaDescriptions
            else -> enDescriptions
        }
        return orderedToolNames.map { name ->
            McpToolMeta(
                name = name,
                description = descriptions[name] ?: enDescriptions[name].orEmpty()
            )
        }
    }

    fun descriptionFor(name: String, locale: Locale): String {
        return forLocale(locale).firstOrNull { it.name == name }?.description
            ?: enDescriptions[name].orEmpty()
    }

    private val orderedToolNames: List<String> =
        runtimeToolNames + homeToolNames + systemToolNames + osToolNames + githubToolNames + baToolNames

    private val englishTools: List<McpToolMeta>
        get() = orderedToolNames.map { name ->
            McpToolMeta(name = name, description = enDescriptions[name].orEmpty())
        }

    private val enDescriptions = mapOf(
        "keios.health.ping" to "Connectivity probe; returns pong.",
        "keios.app.info" to "Read app label, package, version, and Shizuku API level.",
        "keios.app.version" to "Read versionName and versionCode.",
        "keios.shizuku.status" to "Read current Shizuku status.",
        "keios.mcp.runtime.status" to "Read MCP runtime endpoint, clients, token state, and last error.",
        "keios.mcp.runtime.logs" to "Read MCP runtime logs. Args: limit=1..200.",
        "keios.mcp.runtime.config" to "Generate streamable HTTP client config. Args: mode=auto|local|lan, endpoint, serverName.",
        "keios.mcp.claw.skill.guide" to "Generate Claw onboarding with config JSON, resource URIs, and SKILL.md.",
        "keios.home.overview.snapshot" to "Read Home overview data for MCP, GitHub, and Blue Archive.",
        "keios.system.topinfo.query" to "Query cached system TopInfo values. Args: query, limit.",
        "keios.os.cards.snapshot" to "Read OS page visibility, expansion state, cache footprint, and card counts.",
        "keios.os.activity.cards" to "List OS Activity shortcut cards. Args: query, onlyVisible, limit.",
        "keios.os.shell.cards" to "List OS shell cards. Args: query, onlyVisible, includeOutput, limit.",
        "keios.os.cards.export" to "Export Activity, shell, or all OS cards. Args: target=activity|shell|all.",
        "keios.os.cards.import" to "Preview or import Activity or shell card JSON. Args: target, json, apply.",
        "keios.github.config.snapshot" to "Read GitHub strategy, token, refresh, share import, and cache settings.",
        "keios.github.tracks.snapshot" to "Read GitHub tracking counts, cache health, and update settings.",
        "keios.github.tracks.list" to "List tracked repositories. Args: repoFilter, limit.",
        "keios.github.tracks.export" to "Export tracked repositories as JSON. Args: repoFilter.",
        "keios.github.tracks.import" to "Preview or import tracked repositories JSON. Args: json, apply.",
        "keios.github.tracks.check" to "Check tracked repositories online. Args: repoFilter, onlyUpdates, limit.",
        "keios.github.tracks.summary" to "Summarize cached or online tracking state. Args: mode=cache|network, repoFilter.",
        "keios.github.link.parse" to "Parse GitHub repo, release, tag, and APK asset links from text.",
        "keios.github.link.resolve" to "Resolve a GitHub link into APK asset candidates.",
        "keios.github.link.pending" to "Read or clear pending pre-install share tracking state.",
        "keios.github.discovery.search" to "Search GitHub repositories. Args: query, limit.",
        "keios.github.repo.package.scan" to "Scan a repository release APK and read its package name. Args: repoUrl.",
        "keios.github.package.repo.scan" to "Reverse scan repositories for a package name. Args: packageName, appLabel, preferredRepoUrl, candidateLimit, verificationLimit.",
        "keios.github.stars.lists" to "Discover public GitHub Star Lists from a profile stars URL. Args: url.",
        "keios.github.stars.preview" to "Preview Star List import candidates. Args: source, username, listUrl, limit, quality.",
        "keios.github.stars.import" to "Preview or apply Star List import. Args: source, username, listUrl, limit, quality, apply.",
        "keios.github.stars.apk.verify" to "Verify latest stable release APK presence for repository URLs. Args: repoUrls, limit.",
        "keios.github.cache.clear" to "Clear GitHub release, check, and package-scan related caches.",
        "keios.ba.snapshot" to "Read Blue Archive AP, Cafe, notification, server, and ID snapshot.",
        "keios.ba.calendar.cache" to "Read Blue Archive event calendar cache. Args: serverIndex, includeEntries, limit.",
        "keios.ba.pool.cache" to "Read Blue Archive recruitment banner cache. Args: serverIndex, includeEntries, limit.",
        "keios.ba.guide.catalog.cache" to "Read Student Guide catalog cache. Args: tab, includeEntries, limit.",
        "keios.ba.guide.cache.overview" to "Read Student Guide detail cache footprint and latest sync.",
        "keios.ba.guide.cache.inspect" to "Inspect Student Guide detail cache by URL. Args: url, includeSections, refreshIntervalHours.",
        "keios.ba.guide.media.list" to "List gallery and voice media from Student Guide cache. Args: url, kind, limit.",
        "keios.ba.guide.bgm.favorites" to "List, export, or import Memorial Lobby BGM favorites.",
        "keios.ba.cache.clear" to "Clear Blue Archive and GitHub cache data. Args: scope, url."
    )

    private val zhDescriptions = enDescriptions + mapOf(
        "keios.github.config.snapshot" to "读取 GitHub 策略、Token、刷新、分享导入与缓存配置。",
        "keios.github.repo.package.scan" to "扫描仓库最新稳定 release APK 并读取包名。参数：repoUrl。",
        "keios.github.package.repo.scan" to "按包名反扫仓库并验证 APK 包名。参数：packageName、appLabel、preferredRepoUrl。",
        "keios.github.stars.lists" to "从 GitHub stars 主页发现公开 Star List。参数：url。",
        "keios.github.stars.preview" to "预览 Star List 导入候选。参数：source、username、listUrl、limit、quality。",
        "keios.github.stars.import" to "预览或执行 Star List 导入。参数：source、username、listUrl、limit、quality、apply。",
        "keios.github.stars.apk.verify" to "批量验证仓库最新稳定 release 是否含 APK。参数：repoUrls、limit。",
        "keios.github.cache.clear" to "清理 GitHub 检查、release asset 与扫描相关缓存。"
    )

    private val jaDescriptions = enDescriptions + mapOf(
        "keios.github.config.snapshot" to "GitHub の方式、Token、更新間隔、共有インポート、キャッシュ設定を読み取ります。",
        "keios.github.repo.package.scan" to "リポジトリの最新安定版 APK から package 名を読み取ります。引数: repoUrl。",
        "keios.github.package.repo.scan" to "package 名から候補リポジトリを逆引きし、APK package を検証します。",
        "keios.github.stars.lists" to "GitHub stars ページから公開 Star List を検出します。引数: url。",
        "keios.github.stars.preview" to "Star List インポート候補をプレビューします。",
        "keios.github.stars.import" to "Star List インポートをプレビューまたは実行します。",
        "keios.github.stars.apk.verify" to "リポジトリ一覧の最新安定版 release に APK があるか検証します。",
        "keios.github.cache.clear" to "GitHub 確認、release asset、スキャン関連キャッシュを削除します。"
    )
}
