@file:Suppress("ktlint:standard:filename")

package os.kei.ui.page.main.github.share

import os.kei.feature.github.data.local.GitHubPendingShareImportAttachCandidateRecord

internal data class GitHubPendingShareImportAttachCandidate(
    val projectUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val versionName: String = "",
    val versionCode: String = "",
    val eventAction: String,
    val detectedAtMillis: Long = GitHubSystemShareImportClock.nowMs(),
    val firstInstallTimeMs: Long = -1L,
)

internal fun GitHubPendingShareImportAttachCandidate.toPendingAttachCandidateRecord(): GitHubPendingShareImportAttachCandidateRecord =
    GitHubPendingShareImportAttachCandidateRecord(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        versionName = versionName,
        versionCode = versionCode,
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = firstInstallTimeMs,
    )

internal fun GitHubPendingShareImportAttachCandidateRecord.toShareImportAttachCandidate(): GitHubPendingShareImportAttachCandidate =
    GitHubPendingShareImportAttachCandidate(
        projectUrl = projectUrl,
        owner = owner,
        repo = repo,
        packageName = packageName,
        appLabel = appLabel,
        versionName = versionName,
        versionCode = versionCode,
        eventAction = eventAction,
        detectedAtMillis = detectedAtMillis,
        firstInstallTimeMs = firstInstallTimeMs,
    )
