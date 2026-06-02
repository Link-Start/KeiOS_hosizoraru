package os.kei.ui.page.main.github.section

import androidx.compose.runtime.Immutable
import com.tencent.mmkv.MMKV
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import os.kei.core.json.encodeCompact
import os.kei.core.json.optBoolean
import os.kei.core.json.optObject
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.core.prefs.KeiMmkv

@Immutable
internal data class GitHubTrackedReleaseExpansionState(
    val localVersionExpanded: Map<String, Boolean> = emptyMap(),
    val stableVersionExpanded: Map<String, Boolean> = emptyMap(),
    val preReleaseVersionExpanded: Map<String, Boolean> = emptyMap()
)

internal object GitHubTrackedReleaseUiStateStore {
    private const val KV_ID = "github_tracked_release_ui_state"
    private const val KEY_EXPANSION_STATE = "tracked_release_expansion_state"
    private const val JSON_LOCAL = "local"
    private const val JSON_STABLE = "stable"
    private const val JSON_PRE_RELEASE = "preRelease"

    private val store: MMKV by lazy { KeiMmkv.byId(KV_ID) }

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

    fun setLocalVersionExpanded(itemId: String, value: Boolean) {
        val current = load()
        save(
            current.copy(
                localVersionExpanded = current.localVersionExpanded.withExpandedValue(
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
                localVersionExpanded = current.localVersionExpanded - itemId,
                stableVersionExpanded = current.stableVersionExpanded - itemId,
                preReleaseVersionExpanded = current.preReleaseVersionExpanded - itemId
            )
        )
    }

    fun retain(validItemIds: Set<String>) {
        val current = load()
        save(
            GitHubTrackedReleaseExpansionState(
                localVersionExpanded = current.localVersionExpanded.filterKeys { it in validItemIds },
                stableVersionExpanded = current.stableVersionExpanded.filterKeys { it in validItemIds },
                preReleaseVersionExpanded = current.preReleaseVersionExpanded.filterKeys { it in validItemIds }
            )
        )
    }

    internal fun decodeExpansionState(raw: String): GitHubTrackedReleaseExpansionState {
        if (raw.isBlank()) return GitHubTrackedReleaseExpansionState()
        return runCatching {
            val root = raw.parseJsonObjectOrNull()
                ?: return@runCatching GitHubTrackedReleaseExpansionState()
            GitHubTrackedReleaseExpansionState(
                localVersionExpanded = decodeExpandedMap(root.optObject(JSON_LOCAL)),
                stableVersionExpanded = decodeExpandedMap(root.optObject(JSON_STABLE)),
                preReleaseVersionExpanded = decodeExpandedMap(root.optObject(JSON_PRE_RELEASE))
            )
        }.getOrDefault(GitHubTrackedReleaseExpansionState())
    }

    internal fun encodeExpansionState(state: GitHubTrackedReleaseExpansionState): String {
        return buildJsonObject {
            put(JSON_LOCAL, encodeExpandedMap(state.localVersionExpanded))
            put(JSON_STABLE, encodeExpandedMap(state.stableVersionExpanded))
            put(JSON_PRE_RELEASE, encodeExpandedMap(state.preReleaseVersionExpanded))
        }.encodeCompact()
    }

    private fun save(state: GitHubTrackedReleaseExpansionState) {
        val trimmed = GitHubTrackedReleaseExpansionState(
            localVersionExpanded = state.localVersionExpanded.onlyExpanded(),
            stableVersionExpanded = state.stableVersionExpanded.onlyExpanded(),
            preReleaseVersionExpanded = state.preReleaseVersionExpanded.onlyExpanded()
        )
        store.encode(KEY_EXPANSION_STATE, encodeExpansionState(trimmed))
    }

    private fun decodeExpandedMap(obj: JsonObject?): Map<String, Boolean> {
        obj ?: return emptyMap()
        return buildMap {
            obj.keys.forEach { key ->
                val itemId = key.trim()
                if (itemId.isNotBlank() && obj.optBoolean(key, false)) {
                    put(itemId, true)
                }
            }
        }
    }

    private fun encodeExpandedMap(map: Map<String, Boolean>): JsonObject {
        return buildJsonObject {
            map.onlyExpanded().keys.sorted().forEach { itemId ->
                put(itemId, true)
            }
        }
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
