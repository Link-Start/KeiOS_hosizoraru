package os.kei.feature.github.model

data class GitHubApkManifestInfo(
    val assetName: String,
    val fetchSource: String = "",
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
    val signatureInfo: GitHubApkSignatureInfo? = null
)

data class GitHubRemoteApkVersionInfo(
    val releaseName: String = "",
    val releaseTag: String = "",
    val releaseUrl: String = "",
    val assetName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: String = "",
    val fetchSource: String = ""
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
    val signatureSha256: List<String> = emptyList()
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

enum class GitHubApkTrustReason {
    PreferredAbi,
    UniversalAsset,
    IncompatibleAbi,
    DebugBuild,
    UnsignedBuild,
    SourceArchive,
    ApkLike,
    UnknownFormat,
    PackageMatched,
    PackageMismatch,
    SignatureMatched,
    SignatureMismatch,
    SignatureUnknown,
    VersionUpgrade,
    VersionDowngrade,
    MinSdkTooHigh,
    TestOnly,
    SensitivePermission,
    ExportedComponent
}

data class GitHubApkTrustSignal(
    val level: GitHubDecisionLevel,
    val reasons: List<GitHubApkTrustReason>
)
