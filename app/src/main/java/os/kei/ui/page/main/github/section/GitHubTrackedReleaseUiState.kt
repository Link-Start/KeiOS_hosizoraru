package os.kei.ui.page.main.github.section

import androidx.compose.runtime.Immutable
import com.tencent.mmkv.MMKV
import org.json.JSONObject

@Immutable
internal data class GitHubTrackedReleaseExpansionState(
    val stableVersionExpanded: Map<String, Boolean> = emptyMap(),
    val preReleaseVersionExpanded: Map<String, Boolean> = emptyMap()
)

internal object GitHubTrackedReleaseUiStateStore {
    private const val KV_ID = "github_tracked_release_ui_state"
    private const val KEY_EXPANSION_STATE = "tracked_release_expansion_state"
    private const val JSON_STABLE = "stable"
    private const val JSON_PRE_RELEASE = "preRelease"

    private val store: MMKV by lazy { MMKV.mmkvWithID(KV_ID) }

    fun load(): GitHubTrackedReleaseExpansionState {
        return decodeExpansionState(store.decodeString(KEY_EXPANSION_STATE).orEmpty())
    }

    fun setStableVersionExpanded(itemId: String, value: Boolean) {
        val current = load()
        save(
            current.copy(
                stableVersionExpanded = current.stableVersionExpanded.withExpandedValue(
                    itemId,
                    value
                )
            )
        )
    }

    fun setPreReleaseVersionExpanded(itemId: String, value: Boolean) {
        val current = load()
        save(
            current.copy(
                preReleaseVersionExpanded = current.preReleaseVersionExpanded.withExpandedValue(
                    itemId,
                    value
                )
            )
        )
    }

    fun remove(itemId: String) {
        val current = load()
        save(
            GitHubTrackedReleaseExpansionState(
                stableVersionExpanded = current.stableVersionExpanded - itemId,
                preReleaseVersionExpanded = current.preReleaseVersionExpanded - itemId
            )
        )
    }

    fun retain(validItemIds: Set<String>) {
        val current = load()
        save(
            GitHubTrackedReleaseExpansionState(
                stableVersionExpanded = current.stableVersionExpanded.filterKeys { it in validItemIds },
                preReleaseVersionExpanded = current.preReleaseVersionExpanded.filterKeys { it in validItemIds }
            )
        )
    }

    internal fun decodeExpansionState(raw: String): GitHubTrackedReleaseExpansionState {
        if (raw.isBlank()) return GitHubTrackedReleaseExpansionState()
        return runCatching {
            val root = JSONObject(raw)
            GitHubTrackedReleaseExpansionState(
                stableVersionExpanded = decodeExpandedMap(root.optJSONObject(JSON_STABLE)),
                preReleaseVersionExpanded = decodeExpandedMap(root.optJSONObject(JSON_PRE_RELEASE))
            )
        }.getOrDefault(GitHubTrackedReleaseExpansionState())
    }

    internal fun encodeExpansionState(state: GitHubTrackedReleaseExpansionState): String {
        return JSONObject()
            .put(JSON_STABLE, encodeExpandedMap(state.stableVersionExpanded))
            .put(JSON_PRE_RELEASE, encodeExpandedMap(state.preReleaseVersionExpanded))
            .toString()
    }

    private fun save(state: GitHubTrackedReleaseExpansionState) {
        val trimmed = GitHubTrackedReleaseExpansionState(
            stableVersionExpanded = state.stableVersionExpanded.onlyExpanded(),
            preReleaseVersionExpanded = state.preReleaseVersionExpanded.onlyExpanded()
        )
        store.encode(KEY_EXPANSION_STATE, encodeExpansionState(trimmed))
    }

    private fun decodeExpandedMap(obj: JSONObject?): Map<String, Boolean> {
        obj ?: return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val itemId = key.trim()
                if (itemId.isNotBlank() && obj.optBoolean(key, false)) {
                    put(itemId, true)
                }
            }
        }
    }

    private fun encodeExpandedMap(map: Map<String, Boolean>): JSONObject {
        val obj = JSONObject()
        map.onlyExpanded().keys.sorted().forEach { itemId ->
            obj.put(itemId, true)
        }
        return obj
    }

    private fun Map<String, Boolean>.withExpandedValue(
        itemId: String,
        value: Boolean
    ): Map<String, Boolean> {
        val normalizedItemId = itemId.trim()
        if (normalizedItemId.isBlank()) return onlyExpanded()
        return if (value) {
            onlyExpanded() + (normalizedItemId to true)
        } else {
            onlyExpanded() - normalizedItemId
        }
    }

    private fun Map<String, Boolean>.onlyExpanded(): Map<String, Boolean> {
        return filter { (itemId, expanded) -> itemId.isNotBlank() && expanded }
    }
}
