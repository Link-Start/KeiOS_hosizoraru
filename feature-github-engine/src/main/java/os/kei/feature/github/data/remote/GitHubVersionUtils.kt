package os.kei.feature.github.data.remote

import java.net.URI
import os.kei.core.versioning.VersionCandidate
import os.kei.core.versioning.VersionComparison
import os.kei.core.versioning.VersionChannel
import os.kei.core.versioning.VersionConfidence
import os.kei.core.versioning.VersioningEngine
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubReleaseSignalSource
import os.kei.feature.github.model.GitHubVersionCandidate
import os.kei.feature.github.model.GitHubVersionCandidateSource

object GitHubVersionUtils {
    fun buildRepositoryUrl(owner: String, repo: String): String {
        return "https://github.com/$owner/$repo"
    }

    fun buildReleaseUrl(owner: String, repo: String): String {
        return "${buildRepositoryUrl(owner, repo)}/releases"
    }

    fun buildReleaseTagUrl(owner: String, repo: String, tag: String): String {
        val normalized = tag.trim()
        if (normalized.isBlank()) return buildReleaseUrl(owner, repo)
        val encodedTag = java.net.URLEncoder.encode(normalized, Charsets.UTF_8.name())
            .replace("+", "%20")
        return "https://github.com/$owner/$repo/releases/tag/$encodedTag"
    }

    fun parseOwnerRepo(urlOrPath: String): Pair<String, String>? {
        val raw = urlOrPath.trim()
            .removePrefix("git+")
            .removeSuffix(".git")
            .trimEnd('/')
        if (raw.isBlank()) return null

        if (raw.contains(":") && raw.contains("@") && raw.contains("github.com")) {
            val afterColon = raw.substringAfter(':', "")
            val ownerRepo = afterColon.removePrefix("/").split("/")
            if (ownerRepo.size >= 2) return ownerRepo[0] to ownerRepo[1]
        }

        val asUri = runCatching { URI(raw) }.getOrNull()
        if (asUri != null && asUri.host?.contains("github.com", ignoreCase = true) == true) {
            val segments = asUri.path.trim('/').split('/').filter { it.isNotBlank() }
            if (segments.size >= 2) return segments[0] to segments[1].removeSuffix(".git")
        }

        val normalized = raw
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("github.com/")
            .trim('/')
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.size >= 2) return parts[0] to parts[1].removeSuffix(".git")
        return null
    }

    fun buildVersionCandidates(
        vararg inputs: Pair<GitHubVersionCandidateSource, String>,
    ): List<GitHubVersionCandidate> {
        return VersioningEngine.buildCandidates(
            inputs = inputs.map { (source, value) -> source.priority to value },
        ).map { candidate -> candidate.toGitHubCandidate() }
    }

    fun normalizeVersionCandidates(text: String): List<String> {
        return VersioningEngine.normalizeCandidates(text)
    }

    fun compareVersionToCandidates(
        localVersion: String,
        candidates: List<String>,
    ): Int? {
        return VersioningEngine.compareLocalVersionToRemote(
            localVersion = localVersion,
            remoteCandidates = candidates.map { value ->
                VersionCandidate(
                    value = value,
                    sourcePriority = GitHubVersionCandidateSource.Content.priority,
                )
            },
        )?.order?.legacyValue
    }

    fun compareVersionToStructuredCandidates(
        localVersion: String,
        candidates: List<GitHubVersionCandidate>,
        remoteChannel: GitHubReleaseChannel? = null,
    ): Int? {
        return VersioningEngine.compareLocalVersionToRemote(
            localVersion = localVersion,
            remoteCandidates = candidates.toCoreCandidates(remoteChannel),
        )?.order?.legacyValue
    }

    fun compareVersionNameAndCodeToStructuredCandidates(
        localVersion: String,
        localVersionCode: Long,
        candidates: List<GitHubVersionCandidate>,
        remoteChannel: GitHubReleaseChannel? = null,
    ): Int? {
        return VersioningEngine.compareLocalVersionNameAndCodeToRemote(
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            remoteCandidates = candidates.toCoreCandidates(remoteChannel),
        )?.order?.legacyValue
    }

    /**
     * The same comparison as [compareVersionNameAndCodeToStructuredCandidates], kept whole.
     *
     * The `Int?` form throws away everything but the sign: how much of the two version strings
     * actually lined up, which rule decided it, and what the two sides were reduced to. The engine
     * needs all of that to say how far a reader should trust the answer, so it reads this and takes
     * the sign from [VersionComparison.order] itself.
     */
    fun compareLocalVersionNameAndCodeToCandidatesDetailed(
        localVersion: String,
        localVersionCode: Long,
        candidates: List<GitHubVersionCandidate>,
        remoteChannel: GitHubReleaseChannel? = null,
    ): VersionComparison? {
        return VersioningEngine.compareLocalVersionNameAndCodeToRemote(
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            remoteCandidates = candidates.toCoreCandidates(remoteChannel),
        )
    }

    fun remoteCandidateMatchesLocalVersionNameAndCode(
        localVersion: String,
        localVersionCode: Long,
        remoteCandidates: List<GitHubVersionCandidate>,
        remoteChannel: GitHubReleaseChannel? = null,
    ): Boolean {
        return VersioningEngine.remoteCandidateMatchesLocalVersionNameAndCode(
            localVersion = localVersion,
            localVersionCode = localVersionCode,
            remoteCandidates = remoteCandidates.toCoreCandidates(remoteChannel),
        )
    }

    fun compareStructuredCandidateSets(
        leftCandidates: List<GitHubVersionCandidate>,
        rightCandidates: List<GitHubVersionCandidate>,
        leftChannel: GitHubReleaseChannel? = null,
        rightChannel: GitHubReleaseChannel? = null,
    ): Int? {
        return VersioningEngine.compareRemoteCandidateSets(
            leftCandidates = leftCandidates.toCoreCandidates(leftChannel),
            rightCandidates = rightCandidates.toCoreCandidates(rightChannel),
        )?.order?.legacyValue
    }

    fun referToSameReleaseVersion(
        leftCandidates: List<GitHubVersionCandidate>,
        rightCandidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority,
        leftChannel: GitHubReleaseChannel? = null,
        rightChannel: GitHubReleaseChannel? = null,
    ): Boolean {
        return VersioningEngine.referToSameReleaseVersion(
            leftCandidates = leftCandidates.toCoreCandidates(leftChannel),
            rightCandidates = rightCandidates.toCoreCandidates(rightChannel),
            maxSourcePriority = maxSourcePriority,
        )
    }

    fun hasComparableVersionCandidates(
        candidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority,
    ): Boolean {
        return VersioningEngine.hasComparableCandidates(
            candidates = candidates.toCoreCandidates(),
            maxSourcePriority = maxSourcePriority,
        )
    }

    fun hasMeaningfulPreReleaseVersionCandidates(
        candidates: List<GitHubVersionCandidate>,
        maxSourcePriority: Int = GitHubVersionCandidateSource.Link.priority,
    ): Boolean {
        return VersioningEngine.hasMeaningfulPreReleaseCandidates(
            candidates = candidates.toCoreCandidates(),
            maxSourcePriority = maxSourcePriority,
        )
    }

    /** Both clocks are `effectiveFreshnessMillis`, never `updatedAtMillis`. */
    fun isRelevantPreRelease(
        preReleaseCandidates: List<GitHubVersionCandidate>,
        stableCandidates: List<GitHubVersionCandidate>,
        preReleaseFreshnessMillis: Long? = null,
        stableFreshnessMillis: Long? = null,
        preReleaseChannel: GitHubReleaseChannel? = null,
        stableChannel: GitHubReleaseChannel? = null,
        /** @see GitHubReleaseSignalSource.clockToleranceMillis */
        clockToleranceMillis: Long = 0L,
    ): Boolean {
        return VersioningEngine.isRelevantPreRelease(
            preReleaseCandidates = preReleaseCandidates.toCoreCandidates(preReleaseChannel),
            stableCandidates = stableCandidates.toCoreCandidates(stableChannel),
            preReleaseFreshnessMillis = preReleaseFreshnessMillis,
            stableFreshnessMillis = stableFreshnessMillis,
            clockToleranceMillis = clockToleranceMillis,
        )
    }

    fun isAbandonedPreRelease(
        /** Pass [GitHubReleaseVersionSignals.effectiveFreshnessMillis], not the publish date. */
        preReleaseFreshnessMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        return VersioningEngine.isAbandonedPreRelease(
            preReleaseFreshnessMillis = preReleaseFreshnessMillis,
            nowMillis = nowMillis,
        )
    }

    fun classifyVersionChannel(text: String): GitHubReleaseChannel? {
        return VersioningEngine.classifyChannel(text)?.toGitHubChannel()
    }

    fun compareCandidateSets(
        leftCandidates: List<String>,
        rightCandidates: List<String>,
    ): Int? {
        return compareCandidateSetsWithSources(
            leftCandidates = leftCandidates,
            rightCandidates = rightCandidates.map { value ->
                GitHubVersionCandidate(value, GitHubVersionCandidateSource.Content)
            },
        )
    }

    fun compareReleaseCandidateValues(
        left: String,
        right: String,
        includeLowConfidence: Boolean = false,
    ): Int? {
        val comparison = VersioningEngine.compareRemoteCandidateSets(
            leftCandidates = listOf(VersionCandidate(left, sourcePriority = 0)),
            rightCandidates = listOf(VersionCandidate(right, sourcePriority = 0)),
        ) ?: return null
        if (!includeLowConfidence && comparison.confidence == VersionConfidence.Low) return null
        return comparison.order.legacyValue
    }

    fun compareCandidateSetsWithSources(
        leftCandidates: List<String>,
        rightCandidates: List<GitHubVersionCandidate>,
        rightChannel: GitHubReleaseChannel? = null,
    ): Int? {
        return VersioningEngine.compareLocalCandidateSets(
            leftCandidates = leftCandidates,
            rightCandidates = rightCandidates.toCoreCandidates(rightChannel),
        )?.order?.legacyValue
    }
}

private fun List<GitHubVersionCandidate>.toCoreCandidates(
    channelHint: GitHubReleaseChannel? = null,
): List<VersionCandidate> {
    val coreChannelHint = channelHint?.toCoreVersionChannelHint()
    return map { candidate ->
        VersionCandidate(
            value = candidate.value,
            sourcePriority = candidate.source.priority,
            channelHint = coreChannelHint,
        )
    }
}

private fun VersionCandidate.toGitHubCandidate(): GitHubVersionCandidate {
    val source = GitHubVersionCandidateSource.entries.firstOrNull { entry ->
        entry.priority == sourcePriority
    } ?: GitHubVersionCandidateSource.Content
    return GitHubVersionCandidate(value = value, source = source)
}

private fun VersionChannel.toGitHubChannel(): GitHubReleaseChannel {
    return when (this) {
        VersionChannel.DEV -> GitHubReleaseChannel.DEV
        VersionChannel.ALPHA -> GitHubReleaseChannel.ALPHA
        VersionChannel.BETA -> GitHubReleaseChannel.BETA
        VersionChannel.RC -> GitHubReleaseChannel.RC
        VersionChannel.PREVIEW -> GitHubReleaseChannel.PREVIEW
        VersionChannel.STABLE -> GitHubReleaseChannel.STABLE
        VersionChannel.UNKNOWN -> GitHubReleaseChannel.UNKNOWN
    }
}

internal fun GitHubReleaseChannel.toCoreVersionChannelHint(): VersionChannel? {
    return when (this) {
        GitHubReleaseChannel.DEV -> VersionChannel.DEV
        GitHubReleaseChannel.ALPHA -> VersionChannel.ALPHA
        GitHubReleaseChannel.BETA -> VersionChannel.BETA
        GitHubReleaseChannel.RC -> VersionChannel.RC
        GitHubReleaseChannel.PREVIEW -> VersionChannel.PREVIEW
        GitHubReleaseChannel.STABLE -> VersionChannel.STABLE
        GitHubReleaseChannel.UNKNOWN -> null
    }
}
