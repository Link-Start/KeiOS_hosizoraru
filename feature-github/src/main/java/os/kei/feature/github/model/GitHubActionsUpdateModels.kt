package os.kei.feature.github.model

data class GitHubActionsRecommendedRunSnapshot(
    val trackId: String,
    val owner: String,
    val repo: String,
    val appLabel: String,
    val workflowId: Long,
    val workflowName: String,
    val workflowPath: String,
    val runId: Long,
    val runNumber: Long,
    val runAttempt: Int,
    val runDisplayName: String,
    val headBranch: String,
    val headSha: String,
    val event: String,
    val status: String,
    val conclusion: String,
    val htmlUrl: String,
    val artifactCount: Int,
    val androidArtifactCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val checkedAtMillis: Long
) {
    val runLabel: String
        get() = if (runNumber > 0L) "#$runNumber" else "#$runId"

    fun isNewerThan(previous: GitHubActionsRecommendedRunSnapshot): Boolean {
        if (runId == previous.runId) return false
        if (workflowId == previous.workflowId && runNumber > 0L && previous.runNumber > 0L) {
            return runNumber > previous.runNumber
        }
        if (createdAtMillis > 0L && previous.createdAtMillis > 0L) {
            return createdAtMillis > previous.createdAtMillis
        }
        return runId > previous.runId
    }
}

data class GitHubActionsNotificationHistoryRecord(
    val trackId: String,
    val owner: String,
    val repo: String,
    val appLabel: String,
    val workflowId: Long,
    val workflowName: String,
    val workflowPath: String,
    val runId: Long,
    val runNumber: Long,
    val runAttempt: Int,
    val runDisplayName: String,
    val headBranch: String,
    val headSha: String,
    val event: String,
    val status: String,
    val conclusion: String,
    val htmlUrl: String,
    val artifactCount: Int,
    val androidArtifactCount: Int,
    val checkedAtMillis: Long,
    val notifiedAtMillis: Long,
    val notificationTitle: String = "",
    val notificationContent: String = "",
) {
    val runLabel: String
        get() = if (runNumber > 0L) "#$runNumber" else "#$runId"

    val repositoryLabel: String
        get() = "${owner}/${repo}"
}
