package os.kei.feature.github.data.remote

import os.kei.core.json.optBoolean
import os.kei.core.json.optObject
import os.kei.core.json.optString
import os.kei.core.json.parseJsonArrayOrNull

internal data class GitHubTrackedRepoSnapshotItem(
    val repoUrl: String,
    val owner: String,
    val repo: String,
    val packageName: String,
    val appLabel: String,
    val preferPreRelease: Boolean
) {
    val id: String = "$owner/$repo"
}

internal object GitHubTrackedRepoDeviceSnapshot {
    private const val RESOURCE_PATH = "/github/tracked_repos_device_snapshot.json"

    val items: List<GitHubTrackedRepoSnapshotItem> by lazy {
        val raw = checkNotNull(javaClass.getResourceAsStream(RESOURCE_PATH)) {
            "Missing test resource: $RESOURCE_PATH"
        }.bufferedReader().use { it.readText() }
        val array = raw.parseJsonArrayOrNull()
            ?: error("tracked repo device snapshot should parse")
        buildList {
            for (index in array.indices) {
                val item = array.optObject(index) ?: continue
                add(
                    GitHubTrackedRepoSnapshotItem(
                        repoUrl = item.optString("repoUrl"),
                        owner = item.optString("owner"),
                        repo = item.optString("repo"),
                        packageName = item.optString("packageName"),
                        appLabel = item.optString("appLabel"),
                        preferPreRelease = when {
                            item.containsKey("preferPreRelease") -> item.optBoolean(
                                "preferPreRelease",
                                false
                            )
                            else -> item.optBoolean("checkPreRelease", false)
                        }
                    )
                )
            }
        }
    }
}
