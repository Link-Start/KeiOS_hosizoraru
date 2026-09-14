package os.kei.feature.github.model

data class GitHubApkManifestInfo(
    val assetName: String,
    val fetchSource: String = "",
    val appLabel: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val minSdk: String = "",
    val targetSdk: String = "",
    val nativeAbis: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val metadata: List<GitHubApkManifestMetadata> = emptyList(),
    val manifestNodes: List<GitHubApkManifestNode> = emptyList(),
    val signatureInfo: GitHubApkSignatureInfo? = null,
    val releaseNotes: String = ""
)

data class GitHubRemoteApkVersionInfo(
    val releaseName: String = "",
    val releaseTag: String = "",
    val releaseUrl: String = "",
    val assetName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val fetchSource: String = "",
    val releaseNotes: String = ""
) {
    val versionCodeLong: Long?
        get() = versionCode.trim().toLongOrNull()

    fun hasVersion(): Boolean {
        return versionName.isNotBlank() || versionCode.isNotBlank()
    }

    fun versionLabel(): String {
        val name = versionName.trim()
        val code = versionCode.trim()
        return when {
            name.isNotBlank() && code.isNotBlank() -> "$name ($code)"
            name.isNotBlank() -> name
            code.isNotBlank() -> code
            else -> ""
        }
    }

    fun releaseLabel(): String {
        val name = releaseName.trim()
        val tag = releaseTag.trim()
        return when {
            name.isBlank() -> tag
            tag.isBlank() -> name
            name.equals(tag, ignoreCase = true) -> name
            else -> "$name · $tag"
        }
    }
}

data class GitHubApkManifestMetadata(
    val name: String,
    val value: String
)

data class GitHubInstalledPackageInfo(
    val packageName: String,
    val appLabel: String = "",
    val versionName: String = "",
    val versionCode: Long = -1L,
    val minSdk: Int = -1,
    val targetSdk: Int = -1,
    val apkSizeBytes: Long = -1L
)

data class GitHubApkManifestNode(
    val tagName: String,
    val displayName: String,
    val attributes: Map<String, String> = emptyMap()
)

data class GitHubApkSignatureInfo(
    val entryName: String,
    val subject: String = "",
    val issuer: String = "",
    val serialNumber: String = "",
    val algorithm: String = "",
    val notBeforeMillis: Long = -1L,
    val notAfterMillis: Long = -1L,
    val sha256: String = ""
)

/**
 * What happened when the pipeline tried to read a version out of a release's APK.
 *
 * The precise path used to end in `getOrNull()`, which collapsed four different situations into one
 * `null`: the setting is off, there was no release to inspect, the download or parse failed, and the
 * APK simply carried no version. Every one of them then fell back to comparing release *names*, and
 * nothing downstream could tell whether that fallback was the plan or the wreckage.
 *
 * Only [Resolved] settles a comparison. The rest are the reasons it did not, kept apart so the
 * status the reader sees, and the diagnostics behind it, can say which one applied.
 */
enum class GitHubPreciseApkOutcome {
    /** Not in play: the check is switched off, or this source has no APK reader. Names are all we promised. */
    Disabled,

    /** Switched on, but this channel had no release to look inside. */
    NoTarget,

    /** An APK was read and it carried a version code. */
    Resolved,

    /** Asked for and not obtained: the fetch failed, the parse failed, or the APK had no version. */
    Unresolved,
    ;

    val settlesComparison: Boolean
        get() = this == Resolved
}
