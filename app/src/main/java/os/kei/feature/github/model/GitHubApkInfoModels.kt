package os.kei.feature.github.model

data class GitHubApkManifestInfo(
    val assetName: String,
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
    val targetSdk: Int = -1
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
