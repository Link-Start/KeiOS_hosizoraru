package os.kei.feature.github.data.remote

import java.util.Locale

internal object GitHubReleaseAssetSelector {
    fun selectDisplayAssets(
        bundle: GitHubReleaseAssetBundle,
        aggressiveFiltering: Boolean,
        includeAllAssets: Boolean
    ): GitHubReleaseAssetBundle {
        val allAssets = bundle.assets.filterNonSourceAssets().sortForDisplay()
        val apkAssets = filterRelevantApks(allAssets, aggressiveFiltering)
        val showingAllAssets = includeAllAssets || apkAssets.isEmpty()
        return bundle.copy(
            assets = if (showingAllAssets) allAssets else apkAssets,
            showingAllAssets = showingAllAssets
        )
    }

    fun filterRelevantApks(
        assets: List<GitHubReleaseAssetFile>,
        aggressiveFiltering: Boolean
    ): List<GitHubReleaseAssetFile> {
        val apkCandidates = assets.filterNonSourceAssets()
            .asSequence()
            .filter { asset ->
                val lowerName = asset.name.lowercase(Locale.ROOT)
                lowerName.endsWith(".apk") && !lowerName.contains("metadata")
            }
            .toList()

        val hasExplicitArm64 = apkCandidates.any { asset ->
            val lowerName = asset.name.lowercase(Locale.ROOT)
            "arm64-v8a" in lowerName
        }

        return apkCandidates
            .asSequence()
            .filter { asset ->
                val lowerName = asset.name.lowercase(Locale.ROOT)
                !aggressiveFiltering || !isAggressivelyIgnoredApk(
                    lowerName = lowerName,
                    hasExplicitArm64 = hasExplicitArm64
                )
            }
            .sortForDisplay()
    }

    fun List<GitHubReleaseAssetFile>.filterNonSourceAssets(): List<GitHubReleaseAssetFile> {
        return asSequence()
            .filter { asset -> !isSourceCodeArchive(asset.name) }
            .toList()
    }

    fun List<GitHubReleaseAssetFile>.sortForDisplay(): List<GitHubReleaseAssetFile> {
        return asSequence().sortForDisplay()
    }

    private fun Sequence<GitHubReleaseAssetFile>.sortForDisplay(): List<GitHubReleaseAssetFile> {
        return sortedWith(
            compareBy<GitHubReleaseAssetFile> { assetDisplayPriority(it.name) }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        ).toList()
    }

    private fun isAggressivelyIgnoredApk(
        lowerName: String,
        hasExplicitArm64: Boolean
    ): Boolean {
        return "armeabi-v7a" in lowerName ||
                "x86_64" in lowerName ||
                armeabiTokenRegex.containsMatchIn(lowerName) ||
                x86TokenRegex.containsMatchIn(lowerName) ||
                (hasExplicitArm64 && ("universal" in lowerName || "fat" in lowerName))
    }

    private fun isSourceCodeArchive(fileName: String): Boolean {
        return fileName.equals("Source code.zip", ignoreCase = true) ||
                fileName.equals("Source code.tar.gz", ignoreCase = true)
    }

    private fun assetDisplayPriority(fileName: String): Int {
        val lower = fileName.lowercase(Locale.ROOT)
        return if (lower.endsWith(".apk")) apkAssetPriority(fileName) else 10
    }

    private fun apkAssetPriority(fileName: String): Int {
        val lower = fileName.lowercase(Locale.ROOT)
        return when {
            "arm64-v8a" in lower || "aarch64" in lower ||
                    arm64TokenRegex.containsMatchIn(lower) -> 0

            "universal" in lower || "fat" in lower -> 1
            "armeabi-v7a" in lower || "armv7" in lower ||
                    armeabiTokenRegex.containsMatchIn(lower) -> 2

            "x86_64" in lower -> 3
            x86TokenRegex.containsMatchIn(lower) -> 4
            else -> 5
        }
    }

    private val arm64TokenRegex = Regex("""(?:^|[^a-z0-9])arm64(?:[^a-z0-9]|$)""")
    private val armeabiTokenRegex = Regex("""(?:^|[^a-z0-9])armeabi(?:[^a-z0-9]|$)""")
    private val x86TokenRegex = Regex("""(?:^|[^a-z0-9])x86(?:[^a-z0-9]|$)""")
}
