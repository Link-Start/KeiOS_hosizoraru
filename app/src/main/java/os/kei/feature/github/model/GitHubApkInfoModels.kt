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
    val metadata: List<GitHubApkManifestMetadata> = emptyList()
)

data class GitHubApkManifestMetadata(
    val name: String,
    val value: String
)
