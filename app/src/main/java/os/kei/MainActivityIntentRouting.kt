package os.kei

internal data class MainActivityIntentRoute(
    val targetBottomPage: String,
    val mcpServerAction: String?,
    val shortcutAction: String?,
    val githubActionsTrackId: String? = null,
    val baAccountId: String? = null
)

internal object MainActivityIntentRouting {
    fun sanitize(
        rawTargetBottomPage: String?,
        rawMcpServerAction: String?,
        rawShortcutAction: String?,
        rawGitHubActionsTrackId: String? = null,
        rawBaAccountId: String? = null
    ): MainActivityIntentRoute? {
        val target = normalizeTargetBottomPage(rawTargetBottomPage) ?: return null
        val mcpServerAction = normalizeMcpServerAction(
            targetBottomPage = target,
            rawAction = rawMcpServerAction
        )
        val shortcutAction = normalizeShortcutAction(
            targetBottomPage = target,
            rawAction = rawShortcutAction
        )
        val githubActionsTrackId = normalizeGitHubActionsTrackId(
            targetBottomPage = target,
            rawTrackId = rawGitHubActionsTrackId
        )
        val baAccountId = normalizeBaAccountId(
            targetBottomPage = target,
            rawAccountId = rawBaAccountId,
        )
        return MainActivityIntentRoute(
            targetBottomPage = target,
            mcpServerAction = mcpServerAction,
            shortcutAction = shortcutAction,
            githubActionsTrackId = githubActionsTrackId,
            baAccountId = baAccountId,
        )
    }

    private fun normalizeTargetBottomPage(raw: String?): String? {
        return when (raw?.trim()) {
            MainActivity.TARGET_BOTTOM_PAGE_OS -> MainActivity.TARGET_BOTTOM_PAGE_OS
            MainActivity.TARGET_BOTTOM_PAGE_GITHUB -> MainActivity.TARGET_BOTTOM_PAGE_GITHUB
            MainActivity.TARGET_BOTTOM_PAGE_MCP -> MainActivity.TARGET_BOTTOM_PAGE_MCP
            MainActivity.TARGET_BOTTOM_PAGE_BA -> MainActivity.TARGET_BOTTOM_PAGE_BA
            else -> null
        }
    }

    private fun normalizeMcpServerAction(
        targetBottomPage: String,
        rawAction: String?
    ): String? {
        if (targetBottomPage != MainActivity.TARGET_BOTTOM_PAGE_MCP) return null
        return when (rawAction?.trim()) {
            MainActivity.MCP_SERVER_ACTION_TOGGLE -> MainActivity.MCP_SERVER_ACTION_TOGGLE
            else -> null
        }
    }

    private fun normalizeShortcutAction(
        targetBottomPage: String,
        rawAction: String?
    ): String? {
        return when (rawAction?.trim()) {
            MainActivity.SHORTCUT_ACTION_BA_AP_ISLAND ->
                if (targetBottomPage == MainActivity.TARGET_BOTTOM_PAGE_BA) {
                    MainActivity.SHORTCUT_ACTION_BA_AP_ISLAND
                } else {
                    null
                }
            MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK ->
                if (targetBottomPage == MainActivity.TARGET_BOTTOM_PAGE_BA) {
                    MainActivity.SHORTCUT_ACTION_BA_OPEN_BGM_PLAYBACK
                } else {
                    null
                }
            MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED ->
                if (targetBottomPage == MainActivity.TARGET_BOTTOM_PAGE_GITHUB) {
                    MainActivity.SHORTCUT_ACTION_GITHUB_REFRESH_TRACKED
                } else {
                    null
                }
            else -> null
        }
    }

    private fun normalizeGitHubActionsTrackId(
        targetBottomPage: String,
        rawTrackId: String?
    ): String? {
        if (targetBottomPage != MainActivity.TARGET_BOTTOM_PAGE_GITHUB) return null
        return rawTrackId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeBaAccountId(
        targetBottomPage: String,
        rawAccountId: String?,
    ): String? {
        if (targetBottomPage != MainActivity.TARGET_BOTTOM_PAGE_BA) return null
        return rawAccountId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }
}
