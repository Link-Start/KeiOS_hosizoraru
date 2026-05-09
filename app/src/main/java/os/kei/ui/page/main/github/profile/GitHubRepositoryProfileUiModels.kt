package os.kei.ui.page.main.github.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSnapshot
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceSummaryRow
import os.kei.feature.github.model.GitHubRepositoryProfileSummaryKey
import os.kei.feature.github.model.GitHubRepositoryProfileSummaryRow
import os.kei.feature.github.model.GitHubRepositoryProfileSummarySection
import os.kei.feature.github.model.toSummary
import os.kei.ui.page.main.github.asset.formatReleaseUpdatedAtNoYear

@Immutable
internal data class GitHubRepositoryProfileUiText(
    @param:StringRes val resId: Int = 0,
    val raw: String = "",
    val args: List<GitHubRepositoryProfileUiText> = emptyList()
) {
    companion object {
        fun raw(value: String): GitHubRepositoryProfileUiText {
            return GitHubRepositoryProfileUiText(raw = value)
        }

        fun res(
            @StringRes resId: Int,
            vararg args: GitHubRepositoryProfileUiText
        ): GitHubRepositoryProfileUiText {
            return GitHubRepositoryProfileUiText(
                resId = resId,
                args = args.toList()
            )
        }
    }
}

@Immutable
internal data class GitHubRepositoryProfileUiRow(
    val key: GitHubRepositoryProfileSummaryKey,
    @param:StringRes val labelRes: Int,
    val value: GitHubRepositoryProfileUiText,
    val level: GitHubDecisionLevel? = null,
    @param:StringRes val sourceLabelRes: Int = 0,
    @param:StringRes val confidenceLabelRes: Int = 0,
    val fetchedAtMillis: Long = -1L
)

@Immutable
internal data class GitHubRepositoryProfileUiSection(
    val section: GitHubRepositoryProfileSummarySection,
    @param:StringRes val titleRes: Int,
    val rows: List<GitHubRepositoryProfileUiRow>
)

@Immutable
internal data class GitHubRepositoryProfileSourceUiRow(
    val source: GitHubRepositoryProfileSource,
    @param:StringRes val sourceLabelRes: Int,
    @param:StringRes val statusLabelRes: Int,
    val level: GitHubDecisionLevel,
    val message: String,
    val elapsed: GitHubRepositoryProfileUiText?,
    val fromCache: Boolean,
    val required: Boolean,
    val fetchedAtMillis: Long
)

@Immutable
internal data class GitHubRepositoryProfileUiSummary(
    val ownerRepo: String,
    val fetchedAtMillis: Long,
    val sections: List<GitHubRepositoryProfileUiSection>,
    val sourceRows: List<GitHubRepositoryProfileSourceUiRow>
)

internal object GitHubRepositoryProfileUiMapper {
    fun build(profile: GitHubRepositoryProfileSnapshot?): GitHubRepositoryProfileUiSummary? {
        profile ?: return null
        val summary = profile.toSummary()
        val rows = summary.rows
            .filter { it.section != GitHubRepositoryProfileSummarySection.SourceAvailability }
            .map(::mapRow)
        val sections = rows
            .groupBy { row -> row.key.section() }
            .toSortedMap(compareBy { it.ordinal })
            .mapNotNull { (section, sectionRows) ->
                if (sectionRows.isEmpty()) {
                    null
                } else {
                    GitHubRepositoryProfileUiSection(
                        section = section,
                        titleRes = section.titleRes(),
                        rows = sectionRows
                    )
                }
            }
        return GitHubRepositoryProfileUiSummary(
            ownerRepo = "${summary.owner}/${summary.repo}",
            fetchedAtMillis = summary.fetchedAtMillis,
            sections = sections,
            sourceRows = summary.sourceRows.map(::mapSourceRow)
        )
    }

    private fun mapRow(row: GitHubRepositoryProfileSummaryRow): GitHubRepositoryProfileUiRow {
        return GitHubRepositoryProfileUiRow(
            key = row.key,
            labelRes = row.key.labelRes(),
            value = row.valueText(),
            level = row.level,
            sourceLabelRes = row.source?.labelRes() ?: 0,
            confidenceLabelRes = row.confidence?.labelRes() ?: 0,
            fetchedAtMillis = row.fetchedAtMillis
        )
    }

    private fun mapSourceRow(
        row: GitHubRepositoryProfileSourceSummaryRow
    ): GitHubRepositoryProfileSourceUiRow {
        return GitHubRepositoryProfileSourceUiRow(
            source = row.source,
            sourceLabelRes = row.source.labelRes(),
            statusLabelRes = row.status.labelRes(),
            level = row.status.level(),
            message = row.message,
            elapsed = row.elapsedMs
                .takeIf { it > 0L }
                ?.let {
                    GitHubRepositoryProfileUiText.res(
                        R.string.github_profile_source_elapsed,
                        GitHubRepositoryProfileUiText.raw(it.toString())
                    )
                },
            fromCache = row.fromCache,
            required = row.required,
            fetchedAtMillis = row.fetchedAtMillis
        )
    }
}

@Composable
internal fun gitHubRepositoryProfileUiText(text: GitHubRepositoryProfileUiText): String {
    if (text.raw.isNotBlank() || text.resId == 0) return text.raw
    val resolvedArgs = text.args.map { gitHubRepositoryProfileUiText(it) as Any }.toTypedArray()
    return stringResource(text.resId, *resolvedArgs)
}

private fun GitHubRepositoryProfileSummaryRow.valueText(): GitHubRepositoryProfileUiText {
    return when (key) {
        GitHubRepositoryProfileSummaryKey.RepositoryState -> repositoryStateText(value)
        GitHubRepositoryProfileSummaryKey.ForkState -> forkStateText(value)
        GitHubRepositoryProfileSummaryKey.LastPush -> timeText(numericValue)
        GitHubRepositoryProfileSummaryKey.LatestRelease -> GitHubRepositoryProfileUiText.raw(value)
        GitHubRepositoryProfileSummaryKey.AndroidAssets -> androidAssetsText(value)
        GitHubRepositoryProfileSummaryKey.ApkIdentity -> GitHubRepositoryProfileUiText.raw(value)
        GitHubRepositoryProfileSummaryKey.ActionsStatus -> actionsStatusText(value)
        GitHubRepositoryProfileSummaryKey.CommunityFiles -> communityFilesText(value)
        GitHubRepositoryProfileSummaryKey.TrafficViews,
        GitHubRepositoryProfileSummaryKey.TrafficClones,
        GitHubRepositoryProfileSummaryKey.SecurityAlerts -> GitHubRepositoryProfileUiText.raw(value)

        GitHubRepositoryProfileSummaryKey.ForkSync -> forkSyncText(value)
        GitHubRepositoryProfileSummaryKey.SourceLoaded,
        GitHubRepositoryProfileSummaryKey.SourceFailed,
        GitHubRepositoryProfileSummaryKey.SourceSkipped -> GitHubRepositoryProfileUiText.raw(value)
    }
}

private fun repositoryStateText(value: String): GitHubRepositoryProfileUiText {
    return when (value) {
        "disabled" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_disabled)
        "archived" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_archived)
        else -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_active)
    }
}

private fun forkStateText(value: String): GitHubRepositoryProfileUiText {
    return when {
        value.startsWith("fork:") -> GitHubRepositoryProfileUiText.res(
            R.string.github_profile_value_fork_with_upstream,
            GitHubRepositoryProfileUiText.raw(value.removePrefix("fork:"))
        )

        value == "fork" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_fork)
        else -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_independent)
    }
}

private fun timeText(value: Long?): GitHubRepositoryProfileUiText {
    val label = formatReleaseUpdatedAtNoYear(value)
        ?: return GitHubRepositoryProfileUiText.res(R.string.common_unknown)
    return GitHubRepositoryProfileUiText.raw(label)
}

private fun androidAssetsText(value: String): GitHubRepositoryProfileUiText {
    val apk = value.substringAfter("apk:", "").substringBefore(",").toIntOrNull() ?: 0
    val bundle = value.substringAfter("bundle:", "").toIntOrNull() ?: 0
    return GitHubRepositoryProfileUiText.res(
        R.string.github_profile_value_android_assets,
        GitHubRepositoryProfileUiText.raw(apk.toString()),
        GitHubRepositoryProfileUiText.raw(bundle.toString())
    )
}

private fun actionsStatusText(value: String): GitHubRepositoryProfileUiText {
    return when (value.lowercase()) {
        "success" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_actions_success)
        "failure" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_actions_failure)
        "cancelled" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_actions_cancelled)
        "timed_out" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_actions_timed_out)
        "completed" -> GitHubRepositoryProfileUiText.res(R.string.github_profile_value_actions_completed)
        else -> GitHubRepositoryProfileUiText.raw(value)
    }
}

private fun communityFilesText(value: String): GitHubRepositoryProfileUiText {
    val readme = value.contains("readme:true")
    val license = value.contains("license:true")
    return GitHubRepositoryProfileUiText.res(
        R.string.github_profile_value_community_files,
        availabilityText(readme),
        availabilityText(license)
    )
}

private fun availabilityText(value: Boolean): GitHubRepositoryProfileUiText {
    return GitHubRepositoryProfileUiText.res(
        if (value) {
            R.string.github_profile_value_available
        } else {
            R.string.github_profile_value_missing
        }
    )
}

private fun forkSyncText(value: String): GitHubRepositoryProfileUiText {
    val ahead = value.substringAfter("ahead:", "").substringBefore(",").toIntOrNull() ?: 0
    val behind = value.substringAfter("behind:", "").toIntOrNull() ?: 0
    return GitHubRepositoryProfileUiText.res(
        R.string.github_profile_value_fork_sync,
        GitHubRepositoryProfileUiText.raw(ahead.toString()),
        GitHubRepositoryProfileUiText.raw(behind.toString())
    )
}

private fun GitHubRepositoryProfileSummaryKey.section(): GitHubRepositoryProfileSummarySection {
    return when (this) {
        GitHubRepositoryProfileSummaryKey.RepositoryState,
        GitHubRepositoryProfileSummaryKey.ForkState -> GitHubRepositoryProfileSummarySection.Lifecycle

        GitHubRepositoryProfileSummaryKey.LastPush -> GitHubRepositoryProfileSummarySection.Activity
        GitHubRepositoryProfileSummaryKey.LatestRelease -> GitHubRepositoryProfileSummarySection.Release
        GitHubRepositoryProfileSummaryKey.AndroidAssets,
        GitHubRepositoryProfileSummaryKey.ApkIdentity -> GitHubRepositoryProfileSummarySection.Distribution

        GitHubRepositoryProfileSummaryKey.ActionsStatus -> GitHubRepositoryProfileSummarySection.Actions
        GitHubRepositoryProfileSummaryKey.CommunityFiles -> GitHubRepositoryProfileSummarySection.Community
        GitHubRepositoryProfileSummaryKey.TrafficViews,
        GitHubRepositoryProfileSummaryKey.TrafficClones -> GitHubRepositoryProfileSummarySection.Traffic

        GitHubRepositoryProfileSummaryKey.ForkSync -> GitHubRepositoryProfileSummarySection.ForkSync
        GitHubRepositoryProfileSummaryKey.SecurityAlerts -> GitHubRepositoryProfileSummarySection.Security
        GitHubRepositoryProfileSummaryKey.SourceLoaded,
        GitHubRepositoryProfileSummaryKey.SourceFailed,
        GitHubRepositoryProfileSummaryKey.SourceSkipped -> {
            GitHubRepositoryProfileSummarySection.SourceAvailability
        }
    }
}

@StringRes
private fun GitHubRepositoryProfileSummarySection.titleRes(): Int {
    return when (this) {
        GitHubRepositoryProfileSummarySection.Lifecycle -> R.string.github_profile_section_lifecycle
        GitHubRepositoryProfileSummarySection.Activity -> R.string.github_profile_section_activity
        GitHubRepositoryProfileSummarySection.Release -> R.string.github_profile_section_release
        GitHubRepositoryProfileSummarySection.Distribution -> R.string.github_profile_section_distribution
        GitHubRepositoryProfileSummarySection.Actions -> R.string.github_profile_section_actions
        GitHubRepositoryProfileSummarySection.Community -> R.string.github_profile_section_community
        GitHubRepositoryProfileSummarySection.Traffic -> R.string.github_profile_section_traffic
        GitHubRepositoryProfileSummarySection.ForkSync -> R.string.github_profile_section_fork_sync
        GitHubRepositoryProfileSummarySection.Security -> R.string.github_profile_section_security
        GitHubRepositoryProfileSummarySection.SourceAvailability -> R.string.github_profile_section_sources
    }
}

@StringRes
private fun GitHubRepositoryProfileSummaryKey.labelRes(): Int {
    return when (this) {
        GitHubRepositoryProfileSummaryKey.RepositoryState -> R.string.github_profile_label_repository_state
        GitHubRepositoryProfileSummaryKey.ForkState -> R.string.github_profile_label_fork_state
        GitHubRepositoryProfileSummaryKey.LastPush -> R.string.github_profile_label_last_push
        GitHubRepositoryProfileSummaryKey.LatestRelease -> R.string.github_profile_label_latest_release
        GitHubRepositoryProfileSummaryKey.AndroidAssets -> R.string.github_profile_label_android_assets
        GitHubRepositoryProfileSummaryKey.ApkIdentity -> R.string.github_profile_label_apk_identity
        GitHubRepositoryProfileSummaryKey.ActionsStatus -> R.string.github_profile_label_actions_status
        GitHubRepositoryProfileSummaryKey.CommunityFiles -> R.string.github_profile_label_community_files
        GitHubRepositoryProfileSummaryKey.TrafficViews -> R.string.github_profile_label_traffic_views
        GitHubRepositoryProfileSummaryKey.TrafficClones -> R.string.github_profile_label_traffic_clones
        GitHubRepositoryProfileSummaryKey.ForkSync -> R.string.github_profile_label_fork_sync
        GitHubRepositoryProfileSummaryKey.SecurityAlerts -> R.string.github_profile_label_security_alerts
        GitHubRepositoryProfileSummaryKey.SourceLoaded -> R.string.github_profile_source_status_loaded
        GitHubRepositoryProfileSummaryKey.SourceFailed -> R.string.github_profile_source_status_failed
        GitHubRepositoryProfileSummaryKey.SourceSkipped -> R.string.github_profile_source_status_skipped
    }
}

@StringRes
private fun GitHubRepositoryProfileSource.labelRes(): Int {
    return when (this) {
        GitHubRepositoryProfileSource.GitHubApiRepository -> R.string.github_profile_source_api_repository
        GitHubRepositoryProfileSource.GitHubApiReleases -> R.string.github_profile_source_api_releases
        GitHubRepositoryProfileSource.AtomReleaseFeed -> R.string.github_profile_source_atom_release
        GitHubRepositoryProfileSource.HtmlRepositoryPage -> R.string.github_profile_source_html_repository
        GitHubRepositoryProfileSource.HtmlLatestReleaseRedirect -> R.string.github_profile_source_web_latest
        GitHubRepositoryProfileSource.ReleaseAssetsApi -> R.string.github_profile_source_release_assets_api
        GitHubRepositoryProfileSource.ReleaseAssetsHtml -> R.string.github_profile_source_release_assets_html
        GitHubRepositoryProfileSource.ActionsRunsApi -> R.string.github_profile_source_actions_runs
        GitHubRepositoryProfileSource.ActionsArtifactsApi -> R.string.github_profile_source_actions_artifacts
        GitHubRepositoryProfileSource.CommunityProfileApi -> R.string.github_profile_source_community
        GitHubRepositoryProfileSource.TrafficViewsApi -> R.string.github_profile_source_traffic_views
        GitHubRepositoryProfileSource.TrafficClonesApi -> R.string.github_profile_source_traffic_clones
        GitHubRepositoryProfileSource.ForkCompareApi -> R.string.github_profile_source_fork_compare
        GitHubRepositoryProfileSource.DependabotAlertsApi -> R.string.github_profile_source_dependabot
        GitHubRepositoryProfileSource.CodeScanningAlertsApi -> R.string.github_profile_source_code_scanning
        GitHubRepositoryProfileSource.LocalInstall -> R.string.github_profile_source_local
        GitHubRepositoryProfileSource.OptionalEnhancedEndpoint -> R.string.github_profile_source_optional
        GitHubRepositoryProfileSource.Cache -> R.string.github_profile_source_cache
    }
}

@StringRes
private fun GitHubRepositoryProfileConfidence.labelRes(): Int {
    return when (this) {
        GitHubRepositoryProfileConfidence.High -> R.string.github_profile_confidence_high
        GitHubRepositoryProfileConfidence.Medium -> R.string.github_profile_confidence_medium
        GitHubRepositoryProfileConfidence.Low -> R.string.github_profile_confidence_low
    }
}

@StringRes
private fun GitHubRepositoryProfileAvailabilityStatus.labelRes(): Int {
    return when (this) {
        GitHubRepositoryProfileAvailabilityStatus.Loaded -> R.string.github_profile_source_status_loaded
        GitHubRepositoryProfileAvailabilityStatus.Failed -> R.string.github_profile_source_status_failed
        GitHubRepositoryProfileAvailabilityStatus.Skipped -> R.string.github_profile_source_status_skipped
    }
}

private fun GitHubRepositoryProfileAvailabilityStatus.level(): GitHubDecisionLevel {
    return when (this) {
        GitHubRepositoryProfileAvailabilityStatus.Loaded -> GitHubDecisionLevel.Good
        GitHubRepositoryProfileAvailabilityStatus.Failed -> GitHubDecisionLevel.Review
        GitHubRepositoryProfileAvailabilityStatus.Skipped -> GitHubDecisionLevel.Review
    }
}
