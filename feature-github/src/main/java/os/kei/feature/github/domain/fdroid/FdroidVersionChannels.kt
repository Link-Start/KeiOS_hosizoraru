package os.kei.feature.github.domain.fdroid

import os.kei.feature.github.model.GitHubReleaseChannel
import java.util.Locale

/**
 * Which channel an F-Droid build belongs to.
 *
 * F-Droid has no `prerelease` flag the way a GitHub release does. index-v2 carries `releaseChannels`,
 * which a repository *may* set to "beta" and mostly does not, so the version name has to be read as
 * well — a build named `1.4.0-rc2` is a release candidate whatever the index says about it.
 *
 * Shared rather than duplicated because two surfaces answer this question and have to agree on the
 * answer. The update check splits stable from pre-release before it selects a candidate; the version
 * history marks each row and anchors the newest pre-release. A page that called a build stable while
 * the checker treated it as a pre-release would be showing a different history than the one being
 * tracked.
 *
 * **Known gap, preserved on purpose.** `rc` is matched as a whole word, so `1.4.0-rc` and `1.4.0-rc.2`
 * are release candidates and `1.4.0-rc2` is not — the digit is a word character and closes no boundary.
 * That is the update check's behaviour as it has always been, and widening it here would change which
 * builds existing tracks get notified about rather than only what a page displays. A repository that
 * declares `releaseChannels` is unaffected either way. See `FdroidVersionChannelsTest`.
 */
fun fdroidReleaseChannelOf(
    releaseChannels: List<String>,
    versionName: String,
): GitHubReleaseChannel {
    val text = (releaseChannels + versionName)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    return when {
        "dev" in text || "snapshot" in text -> GitHubReleaseChannel.DEV
        "alpha" in text -> GitHubReleaseChannel.ALPHA
        "beta" in text -> GitHubReleaseChannel.BETA
        // Hoisted rather than built per call: the history page classifies every row of a repository
        // that may carry a hundred of them, on every list rebuild.
        RELEASE_CANDIDATE_PATTERN.containsMatchIn(text) -> GitHubReleaseChannel.RC
        "preview" in text -> GitHubReleaseChannel.PREVIEW
        else -> GitHubReleaseChannel.STABLE
    }
}

private val RELEASE_CANDIDATE_PATTERN = Regex("""\brc\b|release candidate""")
