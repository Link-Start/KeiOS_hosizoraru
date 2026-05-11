package os.kei.ui.page.main.github.page

import os.kei.feature.github.model.GitHubTrackedApp

internal data class GitHubTrackCardFocusRequest(
    val trackId: String,
    val version: Int
)

internal fun githubTrackedListLeadingItemCount(
    pendingTrackVisible: Boolean,
    attachCandidateVisible: Boolean,
    previewVisible: Boolean,
    resultVisible: Boolean
): Int {
    return 1 +
            listOf(
                pendingTrackVisible,
                attachCandidateVisible,
                previewVisible,
                resultVisible
            ).count { it }
}

internal fun githubTrackedLazyListIndex(
    targetTrackId: String,
    sortedTracked: List<GitHubTrackedApp>,
    leadingItemCount: Int
): Int? {
    val normalizedTarget = targetTrackId.trim()
    if (normalizedTarget.isBlank()) return null
    val itemIndex = sortedTracked.indexOfFirst { it.id == normalizedTarget }
    if (itemIndex < 0) return null
    return leadingItemCount.coerceAtLeast(0) + itemIndex
}
