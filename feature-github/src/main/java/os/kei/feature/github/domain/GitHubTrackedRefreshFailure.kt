package os.kei.feature.github.domain

import os.kei.feature.github.model.GitHubTrackedApp
import os.kei.feature.github.model.GitHubTrackedSourceMode

data class GitHubTrackedRefreshFailure(
    val trackId: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val sourceMode: GitHubTrackedSourceMode,
    val message: String,
    val elapsedMs: Long = 0L,
) {
    fun logSummary(): String =
        "trackId=$trackId owner=$owner repo=$repo package=$packageName " +
            "label=$appLabel source=${sourceMode.storageId} elapsed=${elapsedMs}ms " +
            "message=${compactMessage(message)}"

    companion object {
        fun from(
            item: GitHubTrackedApp,
            message: String,
            elapsedMs: Long = 0L,
        ): GitHubTrackedRefreshFailure =
            GitHubTrackedRefreshFailure(
                trackId = item.id,
                owner = item.owner,
                repo = item.repo,
                packageName = item.packageName,
                appLabel = item.appLabel,
                sourceMode = item.sourceMode,
                message = message,
                elapsedMs = elapsedMs,
            )
    }
}

private fun compactMessage(raw: String): String =
    raw
        .lineSequence()
        .joinToString(" ") { it.trim() }
        .trim()
        .ifBlank { "unknown" }
        .take(600)
