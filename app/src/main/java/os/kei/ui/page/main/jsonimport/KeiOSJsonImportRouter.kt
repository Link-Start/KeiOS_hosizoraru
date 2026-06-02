package os.kei.ui.page.main.jsonimport

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import os.kei.core.json.optArray
import os.kei.core.json.optInt
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull
import os.kei.core.json.parseJsonObjectOrNull

private const val GITHUB_TRACKED_FORMAT_PREFIX = "keios.github.tracked"
private const val GITHUB_TRACKED_SCHEMA_VERSION = 3
private const val OS_ACTIVITY_SCHEMA = "keios.os.activity.cards.v1"
private const val OS_SHELL_SCHEMA = "keios.os.shell.cards.v1"
private const val OS_BUNDLE_SCHEMA = "keios.os.cards.bundle.v1"
private const val OS_SCHEMA_VERSION = 1
private const val BA_CATALOG_FAVORITES_TYPE = "keios.ba.catalog_favorites"
private const val BA_BGM_FAVORITES_TYPE = "keios.ba.bgm_favorites"
private const val BA_ALL_FAVORITES_TYPE = "keios.ba.catalog_all_favorites"
private const val BA_SCHEMA_VERSION = 1
private const val MCP_LOGS_SCHEMA = "keios.mcp.logs.v1"
private const val OS_INFO_CARD_SCHEMA = "keios.os.card.v1"

internal object KeiOSJsonImportRouter {
    fun inspect(raw: String): KeiOSJsonImportHeader {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.EmptyFile)
        }
        return when (trimmed.firstOrNull()) {
            '[' -> inspectLegacyArray(
                trimmed.parseJsonArrayOrNull()
                    ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.ParseFailed)
            )
            '{' -> inspectObject(
                trimmed.parseJsonObjectOrNull()
                    ?: throw KeiOSJsonImportException(KeiOSJsonImportFailureReason.ParseFailed)
            )
            else -> KeiOSJsonImportHeader(kind = KeiOSJsonImportKind.Unknown)
        }
    }

    private fun inspectObject(root: JsonObject): KeiOSJsonImportHeader {
        val format = root.optString("format").trim()
        val schema = root.optString("schema").trim()
        val type = root.optString("type").trim()
        val marker = listOf(format, schema, type).firstOrNull { it.isNotBlank() }.orEmpty()
        val version = root.optInt(
            if (type.startsWith("keios.ba.")) "version" else "schemaVersion",
            versionFromMarker(marker)
        )
        val kind = when {
            format.startsWith(GITHUB_TRACKED_FORMAT_PREFIX) -> KeiOSJsonImportKind.GitHubTracked
            schema == OS_ACTIVITY_SCHEMA -> KeiOSJsonImportKind.OsActivityCards
            schema == OS_SHELL_SCHEMA -> KeiOSJsonImportKind.OsShellCards
            schema == OS_BUNDLE_SCHEMA -> KeiOSJsonImportKind.OsCardsBundle
            type == BA_CATALOG_FAVORITES_TYPE -> KeiOSJsonImportKind.BaCatalogFavorites
            type == BA_BGM_FAVORITES_TYPE -> KeiOSJsonImportKind.BaBgmFavorites
            type == BA_ALL_FAVORITES_TYPE -> KeiOSJsonImportKind.BaAllFavorites
            schema == MCP_LOGS_SCHEMA -> KeiOSJsonImportKind.McpLogs
            schema == OS_INFO_CARD_SCHEMA -> KeiOSJsonImportKind.OsInfoCard
            root.containsKey("trackedItems") -> KeiOSJsonImportKind.GitHubTracked
            looksLikeGitHubItems(root.optArray("items")) -> KeiOSJsonImportKind.GitHubTracked
            root.containsKey("activity") && root.containsKey("shell") -> KeiOSJsonImportKind.OsCardsBundle
            root.containsKey("catalogFavorites") && root.containsKey("bgmFavorites") -> KeiOSJsonImportKind.BaAllFavorites
            root.containsKey("bgmFavorites") -> KeiOSJsonImportKind.BaBgmFavorites
            looksLikeBgmFavorites(root.optArray("favorites")) -> KeiOSJsonImportKind.BaBgmFavorites
            root.containsKey("favorites") || root.containsKey("catalogFavorites") || root.containsKey("students") ->
                KeiOSJsonImportKind.BaCatalogFavorites

            else -> KeiOSJsonImportKind.Unknown
        }
        return KeiOSJsonImportHeader(
            kind = kind,
            marker = marker,
            version = version,
            highVersion = isHighVersion(kind = kind, version = version),
            readOnly = kind == KeiOSJsonImportKind.McpLogs || kind == KeiOSJsonImportKind.OsInfoCard,
            legacyFormat = marker.isBlank()
        )
    }

    private fun inspectLegacyArray(array: JsonArray): KeiOSJsonImportHeader {
        val kind = when {
            looksLikeGitHubItems(array) -> KeiOSJsonImportKind.GitHubTracked
            looksLikeOsActivityItems(array) -> KeiOSJsonImportKind.OsActivityCards
            looksLikeOsShellItems(array) -> KeiOSJsonImportKind.OsShellCards
            looksLikeBgmFavorites(array) -> KeiOSJsonImportKind.BaBgmFavorites
            else -> KeiOSJsonImportKind.BaCatalogFavorites
        }
        return KeiOSJsonImportHeader(
            kind = kind,
            marker = "",
            version = 0,
            highVersion = false,
            readOnly = false,
            legacyFormat = true
        )
    }

    private fun looksLikeGitHubItems(array: JsonArray?): Boolean {
        if (array == null || array.isEmpty()) return false
        repeat(minOf(array.size, 8)) { index ->
            val item = array.getOrNull(index) as? JsonObject ?: return@repeat
            if (
                item.containsKey("repoUrl") ||
                item.containsKey("owner") && item.containsKey("repo") ||
                item.containsKey("packageName") && item.containsKey("preferPreRelease")
            ) {
                return true
            }
        }
        return false
    }

    private fun looksLikeOsActivityItems(array: JsonArray?): Boolean {
        if (array == null || array.isEmpty()) return false
        repeat(minOf(array.size, 8)) { index ->
            val item = array.getOrNull(index) as? JsonObject ?: return@repeat
            if (item.containsKey("className") || item.containsKey("intentAction") || item.containsKey("intentUriData")) {
                return true
            }
        }
        return false
    }

    private fun looksLikeOsShellItems(array: JsonArray?): Boolean {
        if (array == null || array.isEmpty()) return false
        repeat(minOf(array.size, 8)) { index ->
            val item = array.getOrNull(index) as? JsonObject ?: return@repeat
            if (item.containsKey("command") || item.containsKey("runOutput")) {
                return true
            }
        }
        return false
    }

    private fun looksLikeBgmFavorites(array: JsonArray?): Boolean {
        if (array == null || array.isEmpty()) return false
        repeat(minOf(array.size, 8)) { index ->
            val item = array.getOrNull(index) as? JsonObject ?: return@repeat
            if (item.containsKey("audioUrl") || item.containsKey("studentTitle") && item.containsKey("imageUrl")) {
                return true
            }
        }
        return false
    }

    private fun versionFromMarker(marker: String): Int {
        val versionText = marker.substringAfterLast("/v", missingDelimiterValue = "")
            .ifBlank { marker.substringAfterLast(".v", missingDelimiterValue = "") }
        return versionText.toIntOrNull() ?: 0
    }

    private fun isHighVersion(kind: KeiOSJsonImportKind, version: Int): Boolean {
        return when (kind) {
            KeiOSJsonImportKind.GitHubTracked -> version > GITHUB_TRACKED_SCHEMA_VERSION
            KeiOSJsonImportKind.OsActivityCards,
            KeiOSJsonImportKind.OsShellCards,
            KeiOSJsonImportKind.OsCardsBundle -> version > OS_SCHEMA_VERSION

            KeiOSJsonImportKind.BaCatalogFavorites,
            KeiOSJsonImportKind.BaBgmFavorites,
            KeiOSJsonImportKind.BaAllFavorites -> version > BA_SCHEMA_VERSION

            else -> false
        }
    }
}
