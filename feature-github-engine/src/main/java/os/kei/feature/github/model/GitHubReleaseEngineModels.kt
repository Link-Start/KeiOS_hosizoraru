package os.kei.feature.github.model

enum class GitHubReleaseSignalSource {
    LatestRedirect,
    AtomEntry,
    AtomFallback,
    GitHubApi,
}

enum class GitHubVersionCandidateSource(val priority: Int) {
    Tag(0),
    Title(1),
    Link(2),
    Id(3),
    Content(4),
}

data class GitHubVersionCandidate(
    val value: String,
    val source: GitHubVersionCandidateSource,
)

data class GitHubReleaseVersionSignals(
    val displayVersion: String,
    val rawTag: String,
    val rawName: String,
    val link: String = "",
    val updatedAtMillis: Long? = null,
    val versionCandidates: List<GitHubVersionCandidate> = emptyList(),
    val source: GitHubReleaseSignalSource = GitHubReleaseSignalSource.AtomFallback,
    val channel: GitHubReleaseChannel = GitHubReleaseChannel.UNKNOWN,
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    /**
     * Whether this release carries anything a user could install.
     *
     * `null` means unknown, which is the honest answer for every source that cannot see a release's
     * assets — the Atom feed carries release metadata and nothing else. Only `false` is a claim, and
     * only the GitHub API is in a position to make it.
     *
     * It exists because a release with no artifact cannot be an update: there is nothing to move to.
     * `MatsuriDayo/NekoBoxForAndroid` keeps a rolling `preview` tag whose body reads "current no
     * preview version" and whose asset list is empty; without this the track offers an update whose
     * download does not exist, and offers it forever, because the tag never changes.
     */
    val hasDownloadableAsset: Boolean? = null,
) {
    val candidates: List<String>
        get() = versionCandidates.map { candidate -> candidate.value }
}

data class GitHubAtomReleaseEntry(
    val entryId: String = "",
    val tag: String,
    val title: String,
    val link: String,
    val updatedAtMillis: Long? = null,
    val contentHtml: String = "",
    val contentText: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val versionCandidates: List<GitHubVersionCandidate> = emptyList(),
    val channel: GitHubReleaseChannel = GitHubReleaseChannel.UNKNOWN,
    val isLikelyPreRelease: Boolean,
    /** @see GitHubReleaseVersionSignals.hasDownloadableAsset */
    val hasDownloadableAsset: Boolean? = null,
) {
    val displayVersion: String
        get() = title.ifBlank { tag }

    val candidates: List<String>
        get() = versionCandidates.map { candidate -> candidate.value }
}

data class GitHubAtomFeed(
    val title: String = "",
    val feedUrl: String = "",
    val updatedAtMillis: Long? = null,
    val entries: List<GitHubAtomReleaseEntry> = emptyList(),
)

data class GitHubRepositoryReleaseSnapshot(
    val strategyId: String,
    val feed: GitHubAtomFeed,
    val latestStable: GitHubReleaseVersionSignals,
    val hasStableRelease: Boolean = true,
    val latestPreRelease: GitHubReleaseVersionSignals? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val repositoryArchived: Boolean = false,
    val repositoryFork: Boolean = false,
    val repositoryPushedAtMillis: Long = -1L,
    val upstreamFullName: String = "",
    val upstreamArchived: Boolean = false,
    val upstreamPushedAtMillis: Long = -1L,
    val repositoryProfile: GitHubRepositoryProfileSnapshot? = null,
)
