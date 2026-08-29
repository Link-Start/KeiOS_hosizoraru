package os.kei.feature.github.data.remote

data class GitHubReleaseAssetFile(
    val name: String,
    val downloadUrl: String,
    val apiAssetUrl: String = "",
    val sizeBytes: Long,
    val downloadCount: Int,
    val contentType: String = "",
    val updatedAtMillis: Long? = null,
    val digest: String = "",
    val signerSha256: List<String> = emptyList(),
)

const val GITHUB_ACTIONS_APK_ARTIFACT_CONTENT_TYPE =
    "application/vnd.keios.github-actions.apk-artifact+zip"

fun GitHubReleaseAssetFile.isGitHubActionsApkArtifactArchive(): Boolean =
    contentType.equals(GITHUB_ACTIONS_APK_ARTIFACT_CONTENT_TYPE, ignoreCase = true)

fun GitHubReleaseAssetFile.isPotentialNestedApkArchive(): Boolean =
    isGitHubActionsApkArtifactArchive() || name.endsWith(".zip", ignoreCase = true)

fun GitHubReleaseAssetFile.isVerifiedManagedInstallAsset(
    expectedPackageName: String,
    inspectedPackageName: String,
): Boolean {
    if (name.endsWith(".apk", ignoreCase = true)) return true
    if (!isPotentialNestedApkArchive()) return false
    val expected = expectedPackageName.trim()
    val inspected = inspectedPackageName.trim()
    return expected.isNotBlank() && inspected.equals(expected, ignoreCase = true)
}

data class GitHubReleaseAssetBundle(
    val releaseName: String,
    val tagName: String,
    val htmlUrl: String,
    val releaseUpdatedAtMillis: Long? = null,
    val releaseNotesBody: String = "",
    val assets: List<GitHubReleaseAssetFile>,
    val showingAllAssets: Boolean = false,
    val shortCommitSha: String = "",
    val fetchSource: String = "",
    val sourceConfigSignature: String = "",
)

data class GitHubReleaseNotesTarget(
    val releaseName: String,
    val tagName: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val latestInChannel: Boolean,
    val updatedAtMillis: Long? = null,
) {
    val id: String
        get() = "${tagName.trim()}|${htmlUrl.trim()}"
}

/**
 * One release as the release list shows it, rather than as an update check reduces it.
 *
 * The check pipeline keeps two releases — latest stable and latest pre-release — because that is all an
 * update decision needs. Browsing history needs the rest of the fields GitHub already returns in the same
 * response: the body, who published it, and how many files hang off it.
 */
data class GitHubReleaseListEntry(
    val tagName: String,
    val releaseName: String,
    val htmlUrl: String,
    val prerelease: Boolean,
    val publishedAtMillis: Long? = null,
    val authorName: String = "",
    val authorAvatarUrl: String = "",
    val bodyMarkdown: String = "",
    val assetCount: Int = 0,
    /** True only for the newest non-pre-release, and only on the first page. See [GitHubReleasePage]. */
    val latest: Boolean = false,
) {
    val id: String
        get() = "${tagName.trim()}|${htmlUrl.trim()}"

    val displayName: String
        get() = releaseName.ifBlank { tagName }
}

/**
 * A page of releases, sized the way GitHub sizes its own release list.
 *
 * [hasNextPage] is inferred from a full page rather than read from the `Link` header, because the client
 * hands back a parsed body and not the response. A full page that happens to be the last one costs one
 * empty fetch, which is the cheaper error of the two.
 *
 * [GitHubReleaseListEntry.latest] is only ever set on page 1: "latest" means the newest non-pre-release
 * in the repository, and a later page cannot know whether an earlier one held one.
 */
data class GitHubReleasePage(
    val entries: List<GitHubReleaseListEntry>,
    val page: Int,
    val perPage: Int,
    val hasNextPage: Boolean,
) {
    val hasPreviousPage: Boolean
        get() = page > 1
}

object GitHubReleaseAssetFetchSources {
    const val HTML = "html"
    const val API = "api"
}
