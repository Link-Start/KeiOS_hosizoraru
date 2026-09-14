package os.kei.feature.github.model

enum class GitHubReleaseSignalSource(
    /**
     * How far apart two timestamps from this source must be before the gap means anything.
     *
     * The API reports `published_at` and each asset's `updated_at`: real event times, exact enough
     * that a one-second difference is a real ordering. `releases.atom` reports `<updated>`, which is
     * when the release was last *touched* — so publishing a stable bumps the preview it supersedes,
     * and the two land seconds apart in whichever order the edits happened to commit.
     *
     * Measured on `MatsuriDayo/NekoBoxForAndroid`: its `preview` reads 29 seconds *newer* than the
     * `1.4.2` that replaced it, while the API puts it a week older. A rule reading that gap as
     * evidence gets the opposite answer depending only on which source the reader chose. Inside the
     * tolerance the gap is treated as no information at all, and the version numbers decide.
     *
     * A day, because that is the shape of the causality: shipping a stable is what edits the preview
     * it supersedes, and a preview line that is genuinely still building is touched days later, not
     * minutes.
     */
    val clockToleranceMillis: Long,
    /**
     * Whether this source had to work out which releases are pre-releases, rather than being told.
     *
     * The API states it per release. `releases/latest` states it for one release by definition — it
     * skips pre-releases, so whatever it points at is a stable one. The feed states it nowhere, so
     * [AtomEntry] and [AtomFallback] carry a reading of the tag, title and body, and nothing more.
     */
    val laneIsInferred: Boolean,
) {
    LatestRedirect(EDIT_CLOCK_TOLERANCE_MILLIS, laneIsInferred = false),
    AtomEntry(EDIT_CLOCK_TOLERANCE_MILLIS, laneIsInferred = true),
    AtomFallback(EDIT_CLOCK_TOLERANCE_MILLIS, laneIsInferred = true),
    GitHubApi(0L, laneIsInferred = false),
}

private const val EDIT_CLOCK_TOLERANCE_MILLIS = 24L * 60L * 60L * 1000L

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
    /**
     * When this release's assets last moved, if anything is attached and the source can see it.
     *
     * A rolling tag used to host CI builds is published once and never again, while the artifacts
     * inside it are replaced on every run. Its `published_at` can be years old while what it holds
     * is newer than the latest stable, so the release date alone is the wrong measure of whether
     * that line is still alive — see [GitHubReleaseVersionSignals.effectiveFreshnessMillis].
     */
    val assetsUpdatedAtMillis: Long? = null,
) {
    val candidates: List<String>
        get() = versionCandidates.map { candidate -> candidate.value }

    /**
     * The most recent moment this release changed in any way the reader would care about.
     *
     * The later of when it was published and when its assets last moved, so a rolling CI tag is
     * measured by what it holds rather than by when its tag was first cut.
     */
    val effectiveFreshnessMillis: Long?
        get() = listOfNotNull(updatedAtMillis, assetsUpdatedAtMillis).maxOrNull()
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
    /** @see GitHubReleaseVersionSignals.assetsUpdatedAtMillis */
    val assetsUpdatedAtMillis: Long? = null,
) {
    val displayVersion: String
        get() = title.ifBlank { tag }

    val candidates: List<String>
        get() = versionCandidates.map { candidate -> candidate.value }

    /** @see GitHubReleaseVersionSignals.effectiveFreshnessMillis */
    val effectiveFreshnessMillis: Long?
        get() = listOfNotNull(updatedAtMillis, assetsUpdatedAtMillis).maxOrNull()
}

/**
 * One page of a repository's releases, plus whether there was more behind it.
 *
 * The page limit used to be applied silently: thirty releases in, thirty releases considered, and
 * no way for anything downstream to know it was looking at a slice. `iebb/mithka` publishes 119
 * releases, so the window covers about three months of its history — a fine basis for "what is the
 * newest", and a poor one for any claim about the shape of the list.
 */
data class GitHubReleaseWindow(
    val entries: List<GitHubAtomReleaseEntry> = emptyList(),
    val windowWasFull: Boolean = false,
)

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
    /**
     * How [latestStable] and [latestPreRelease] were arrived at, when the source kept a record.
     *
     * `null` for sources that do not select — the Atom strategy and the plain git forges reduce
     * their feeds their own way, and claiming a selection they did not make would be worse than
     * admitting there is none.
     */
    val selection: GitHubReleaseSelection? = null,
)

/**
 * The same release, as the fields the comparison rules read.
 *
 * A feed entry and a release signal are the same release told twice, and the conversion between
 * them used to be a private helper inside the API strategy plus a hand-written copy in a test. Two
 * spellings of one mapping is how a field added to the model silently stops reaching the rules.
 */
fun GitHubAtomReleaseEntry.toReleaseVersionSignals(
    source: GitHubReleaseSignalSource = GitHubReleaseSignalSource.GitHubApi,
): GitHubReleaseVersionSignals =
    GitHubReleaseVersionSignals(
        displayVersion = displayVersion,
        rawTag = tag,
        rawName = title,
        link = link,
        updatedAtMillis = updatedAtMillis,
        versionCandidates = versionCandidates,
        source = source,
        channel = channel,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        hasDownloadableAsset = hasDownloadableAsset,
        assetsUpdatedAtMillis = assetsUpdatedAtMillis,
    )
