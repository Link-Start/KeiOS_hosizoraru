package os.kei.feature.github.model

enum class GitHubRepositoryProfileSummarySection {
    Lifecycle,
    Activity,
    Release,
    Distribution,
    Actions,
    Community,
    Traffic,
    ForkSync,
    Security,
    SourceAvailability
}

enum class GitHubRepositoryProfileSummaryKey {
    RepositoryState,
    ForkState,
    LastPush,
    LatestRelease,
    AndroidAssets,
    ApkIdentity,
    ActionsStatus,
    CommunityFiles,
    TrafficViews,
    TrafficClones,
    ForkSync,
    SecurityAlerts,
    SourceLoaded,
    SourceFailed,
    SourceSkipped
}

data class GitHubRepositoryProfileSummaryRow(
    val section: GitHubRepositoryProfileSummarySection,
    val key: GitHubRepositoryProfileSummaryKey,
    val value: String,
    val numericValue: Long? = null,
    val level: GitHubDecisionLevel? = null,
    val source: GitHubRepositoryProfileSource? = null,
    val confidence: GitHubRepositoryProfileConfidence? = null,
    val fetchedAtMillis: Long = -1L
)

data class GitHubRepositoryProfileSourceSummaryRow(
    val source: GitHubRepositoryProfileSource,
    val status: GitHubRepositoryProfileAvailabilityStatus,
    val fetchedAtMillis: Long,
    val message: String = "",
    val elapsedMs: Long = 0L,
    val fromCache: Boolean = false,
    val required: Boolean = false
)

data class GitHubRepositoryProfileSummary(
    val owner: String,
    val repo: String,
    val sourceConfigSignature: String,
    val fetchedAtMillis: Long,
    val rows: List<GitHubRepositoryProfileSummaryRow>,
    val sourceRows: List<GitHubRepositoryProfileSourceSummaryRow>
)

object GitHubRepositoryProfileSummaryBuilder {
    fun build(profile: GitHubRepositoryProfileSnapshot): GitHubRepositoryProfileSummary {
        return GitHubRepositoryProfileSummary(
            owner = profile.owner,
            repo = profile.repo,
            sourceConfigSignature = profile.sourceConfigSignature,
            fetchedAtMillis = profile.fetchedAtMillis,
            rows = buildRows(profile),
            sourceRows = profile.sourceAvailability.map { state ->
                GitHubRepositoryProfileSourceSummaryRow(
                    source = state.source,
                    status = state.status,
                    fetchedAtMillis = state.fetchedAtMillis,
                    message = state.message,
                    elapsedMs = state.elapsedMs,
                    fromCache = state.fromCache,
                    required = state.required
                )
            }
        )
    }

    private fun buildRows(
        profile: GitHubRepositoryProfileSnapshot
    ): List<GitHubRepositoryProfileSummaryRow> {
        return buildList {
            repositoryState(profile)?.let(::add)
            forkState(profile)?.let(::add)
            profile.activity.pushedAtMillis?.let { field ->
                add(
                    field.row(
                        GitHubRepositoryProfileSummarySection.Activity,
                        GitHubRepositoryProfileSummaryKey.LastPush
                    )
                )
            }
            latestRelease(profile)?.let(::add)
            androidAssets(profile)?.let(::add)
            apkIdentity(profile)?.let(::add)
            actionsStatus(profile)?.let(::add)
            communityFiles(profile)?.let(::add)
            trafficViews(profile)?.let(::add)
            trafficClones(profile)?.let(::add)
            forkSync(profile)?.let(::add)
            securityAlerts(profile)?.let(::add)
            addAll(sourceAvailability(profile))
        }
    }

    private fun repositoryState(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val archived = profile.lifecycle.archived
        val disabled = profile.lifecycle.disabled
        val field = disabled ?: archived ?: return null
        val value = when {
            disabled?.value == true -> "disabled"
            archived?.value == true -> "archived"
            else -> "active"
        }
        val level = when (value) {
            "active" -> GitHubDecisionLevel.Good
            else -> GitHubDecisionLevel.Risk
        }
        return field.row(
            section = GitHubRepositoryProfileSummarySection.Lifecycle,
            key = GitHubRepositoryProfileSummaryKey.RepositoryState,
            value = value,
            level = level
        )
    }

    private fun forkState(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val fork = profile.lifecycle.fork ?: return null
        val upstream = profile.lifecycle.upstream?.fullName?.value.orEmpty()
        val value = when {
            fork.value && upstream.isNotBlank() -> "fork:$upstream"
            fork.value -> "fork"
            else -> "independent"
        }
        return fork.row(
            section = GitHubRepositoryProfileSummarySection.Lifecycle,
            key = GitHubRepositoryProfileSummaryKey.ForkState,
            value = value,
            level = if (fork.value) GitHubDecisionLevel.Review else GitHubDecisionLevel.Good
        )
    }

    private fun latestRelease(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val tag =
            profile.releases.latestStableTag ?: profile.releases.latestPreReleaseTag ?: return null
        val time = profile.releases.latestStablePublishedAtMillis
            ?: profile.releases.latestPreReleasePublishedAtMillis
        return tag.row(
            section = GitHubRepositoryProfileSummarySection.Release,
            key = GitHubRepositoryProfileSummaryKey.LatestRelease,
            value = tag.value,
            numericValue = time?.value
        )
    }

    private fun androidAssets(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val apkCount = profile.distribution.apkLikeAssetCount ?: return null
        val bundleCount = profile.distribution.androidBundleAssetCount?.value ?: 0
        val value = "apk:${apkCount.value},bundle:$bundleCount"
        return apkCount.row(
            section = GitHubRepositoryProfileSummarySection.Distribution,
            key = GitHubRepositoryProfileSummaryKey.AndroidAssets,
            value = value,
            numericValue = apkCount.value.toLong(),
            level = if (apkCount.value > 0) GitHubDecisionLevel.Good else GitHubDecisionLevel.Review
        )
    }

    private fun apkIdentity(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val packageName = profile.distribution.latestStableApkPackageName
            ?: profile.localFit.remotePackageName
            ?: return null
        return packageName.row(
            section = GitHubRepositoryProfileSummarySection.Distribution,
            key = GitHubRepositoryProfileSummaryKey.ApkIdentity,
            value = packageName.value
        )
    }

    private fun actionsStatus(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val conclusion =
            profile.actions.latestRunConclusion ?: profile.actions.latestRunStatus ?: return null
        val level = when (conclusion.value.lowercase()) {
            "success", "completed" -> GitHubDecisionLevel.Good
            "failure", "timed_out", "cancelled", "startup_failure", "action_required" -> GitHubDecisionLevel.Risk
            else -> GitHubDecisionLevel.Review
        }
        return conclusion.row(
            section = GitHubRepositoryProfileSummarySection.Actions,
            key = GitHubRepositoryProfileSummaryKey.ActionsStatus,
            value = conclusion.value,
            numericValue = profile.actions.latestRunUpdatedAtMillis?.value,
            level = level
        )
    }

    private fun communityFiles(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val readme = profile.community.hasReadme
        val license = profile.community.hasLicense
        val field = readme ?: license ?: return null
        val value = "readme:${readme?.value == true},license:${license?.value == true}"
        val level = if (readme?.value == true && license?.value == true) {
            GitHubDecisionLevel.Good
        } else {
            GitHubDecisionLevel.Review
        }
        return field.row(
            section = GitHubRepositoryProfileSummarySection.Community,
            key = GitHubRepositoryProfileSummaryKey.CommunityFiles,
            value = value,
            level = level
        )
    }

    private fun trafficViews(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val views = profile.traffic.viewCount ?: return null
        return views.row(
            section = GitHubRepositoryProfileSummarySection.Traffic,
            key = GitHubRepositoryProfileSummaryKey.TrafficViews,
            value = views.value.toString(),
            numericValue = views.value.toLong()
        )
    }

    private fun trafficClones(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val clones = profile.traffic.cloneCount ?: return null
        return clones.row(
            section = GitHubRepositoryProfileSummarySection.Traffic,
            key = GitHubRepositoryProfileSummaryKey.TrafficClones,
            value = clones.value.toString(),
            numericValue = clones.value.toLong()
        )
    }

    private fun forkSync(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val behind = profile.forkSync.behindBy ?: return null
        val ahead = profile.forkSync.aheadBy?.value ?: 0
        val value = "ahead:$ahead,behind:${behind.value}"
        return behind.row(
            section = GitHubRepositoryProfileSummarySection.ForkSync,
            key = GitHubRepositoryProfileSummaryKey.ForkSync,
            value = value,
            numericValue = behind.value.toLong(),
            level = if (behind.value > 0) GitHubDecisionLevel.Review else GitHubDecisionLevel.Good
        )
    }

    private fun securityAlerts(
        profile: GitHubRepositoryProfileSnapshot
    ): GitHubRepositoryProfileSummaryRow? {
        val dependabot = profile.security.openDependabotAlertsCount
        val codeScanning = profile.security.openCodeScanningAlertsCount
        val field = dependabot ?: codeScanning ?: return null
        val total = (dependabot?.value ?: 0) + (codeScanning?.value ?: 0)
        return field.row(
            section = GitHubRepositoryProfileSummarySection.Security,
            key = GitHubRepositoryProfileSummaryKey.SecurityAlerts,
            value = total.toString(),
            numericValue = total.toLong(),
            level = if (total > 0) GitHubDecisionLevel.Review else GitHubDecisionLevel.Good
        )
    }

    private fun sourceAvailability(
        profile: GitHubRepositoryProfileSnapshot
    ): List<GitHubRepositoryProfileSummaryRow> {
        val grouped = profile.sourceAvailability.groupingBy { it.status }.eachCount()
        return buildList {
            grouped[GitHubRepositoryProfileAvailabilityStatus.Loaded]?.let { count ->
                add(sourceAvailabilityRow(GitHubRepositoryProfileSummaryKey.SourceLoaded, count))
            }
            grouped[GitHubRepositoryProfileAvailabilityStatus.Failed]?.let { count ->
                add(
                    sourceAvailabilityRow(
                        GitHubRepositoryProfileSummaryKey.SourceFailed,
                        count,
                        GitHubDecisionLevel.Review
                    )
                )
            }
            grouped[GitHubRepositoryProfileAvailabilityStatus.Skipped]?.let { count ->
                add(sourceAvailabilityRow(GitHubRepositoryProfileSummaryKey.SourceSkipped, count))
            }
        }
    }

    private fun sourceAvailabilityRow(
        key: GitHubRepositoryProfileSummaryKey,
        count: Int,
        level: GitHubDecisionLevel? = null
    ): GitHubRepositoryProfileSummaryRow {
        return GitHubRepositoryProfileSummaryRow(
            section = GitHubRepositoryProfileSummarySection.SourceAvailability,
            key = key,
            value = count.toString(),
            numericValue = count.toLong(),
            level = level
        )
    }

    private fun <T> GitHubProfileField<T>.row(
        section: GitHubRepositoryProfileSummarySection,
        key: GitHubRepositoryProfileSummaryKey,
        value: String = this.value.toString(),
        numericValue: Long? = this.value as? Long,
        level: GitHubDecisionLevel? = null
    ): GitHubRepositoryProfileSummaryRow {
        return GitHubRepositoryProfileSummaryRow(
            section = section,
            key = key,
            value = value,
            numericValue = numericValue,
            level = level,
            source = source,
            confidence = confidence,
            fetchedAtMillis = fetchedAtMillis
        )
    }
}

fun GitHubRepositoryProfileSnapshot.toSummary(): GitHubRepositoryProfileSummary {
    return GitHubRepositoryProfileSummaryBuilder.build(this)
}
