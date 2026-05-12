package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.remote.GitHubShareIntentParser
import os.kei.feature.github.model.GitHubLookupConfig
import os.kei.feature.github.model.GitHubShareImportFlowMode

internal enum class GitHubShareImportActivityDisplayState {
    Hidden,
    Sheet,
    SendingInstall,
    Finish
}

internal object GitHubShareImportActivityLaunchPolicy {
    fun forIncomingShare(
        sharedText: String?,
        lookupConfig: GitHubLookupConfig
    ): GitHubShareImportActivityDisplayState {
        val normalized = sharedText?.trim().orEmpty()
        if (normalized.isBlank()) return GitHubShareImportActivityDisplayState.Finish
        if (!GitHubShareIntentParser.looksLikeGitHubShareText(normalized)) {
            return GitHubShareImportActivityDisplayState.Finish
        }
        return when (lookupConfig.shareImportFlowMode) {
            GitHubShareImportFlowMode.NotificationFirst ->
                GitHubShareImportActivityDisplayState.Hidden

            GitHubShareImportFlowMode.SheetAssisted ->
                GitHubShareImportActivityDisplayState.Sheet
        }
    }
}
