package os.kei.ui.page.main.github.page

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.github.section.GitHubTrackedItemsExpansionState
import os.kei.ui.page.main.github.section.GitHubTrackedReleaseExpansionState

internal class GitHubTrackedExpansionStateController(
    private val scope: CoroutineScope,
    private val repository: GitHubPageRepository,
) {
    private val mutableState = MutableStateFlow(GitHubTrackedItemsExpansionState())
    val state: StateFlow<GitHubTrackedItemsExpansionState> = mutableState.asStateFlow()

    fun applyPersistedTrackedReleaseExpansionState(snapshot: GitHubTrackedReleaseExpansionState) {
        mutableState.update { state ->
            state.copy(
                trackedLocalVersionExpanded =
                    state.trackedLocalVersionExpanded.ifEmpty { snapshot.localVersionExpanded },
                trackedStableVersionExpanded =
                    state.trackedStableVersionExpanded.ifEmpty { snapshot.stableVersionExpanded },
                trackedPreReleaseVersionExpanded =
                    state.trackedPreReleaseVersionExpanded.ifEmpty { snapshot.preReleaseVersionExpanded },
            )
        }
    }

    fun setTrackedCardExpanded(
        itemId: String,
        expanded: Boolean,
    ) {
        updateTrackedExpansionMap(itemId) { state, normalizedId ->
            state.copy(
                trackedCardExpanded = state.trackedCardExpanded.withBoolean(normalizedId, expanded),
            )
        }
    }

    fun removeTrackedCardExpansion(itemId: String) {
        updateTrackedExpansionMap(itemId) { state, normalizedId ->
            state.copy(
                trackedCardExpanded = state.trackedCardExpanded.withoutKey(normalizedId),
            )
        }
    }

    fun setTrackedStableVersionExpanded(
        itemId: String,
        expanded: Boolean,
    ) {
        val normalizedId = itemId.trim()
        if (normalizedId.isBlank()) return
        updateTrackedExpansionMap(normalizedId) { state, id ->
            state.copy(
                trackedStableVersionExpanded = state.trackedStableVersionExpanded.withBoolean(id, expanded),
            )
        }
        scope.launch {
            repository.saveTrackedStableVersionExpanded(normalizedId, expanded)
        }
    }

    fun setTrackedLocalVersionExpanded(
        itemId: String,
        expanded: Boolean,
    ) {
        val normalizedId = itemId.trim()
        if (normalizedId.isBlank()) return
        updateTrackedExpansionMap(normalizedId) { state, id ->
            state.copy(
                trackedLocalVersionExpanded = state.trackedLocalVersionExpanded.withBoolean(id, expanded),
            )
        }
        scope.launch {
            repository.saveTrackedLocalVersionExpanded(normalizedId, expanded)
        }
    }

    fun setTrackedPreReleaseVersionExpanded(
        itemId: String,
        expanded: Boolean,
    ) {
        val normalizedId = itemId.trim()
        if (normalizedId.isBlank()) return
        updateTrackedExpansionMap(normalizedId) { state, id ->
            state.copy(
                trackedPreReleaseVersionExpanded =
                    state.trackedPreReleaseVersionExpanded.withBoolean(id, expanded),
            )
        }
        scope.launch {
            repository.saveTrackedPreReleaseVersionExpanded(normalizedId, expanded)
        }
    }

    fun removeTrackedExpansion(
        itemId: String,
        removePersistedReleaseExpansion: Boolean,
    ) {
        val normalizedId = itemId.trim()
        if (normalizedId.isBlank()) return
        updateTrackedExpansionMap(normalizedId) { state, id ->
            state.copy(
                trackedCardExpanded = state.trackedCardExpanded.withoutKey(id),
                trackedLocalVersionExpanded = state.trackedLocalVersionExpanded.withoutKey(id),
                trackedStableVersionExpanded = state.trackedStableVersionExpanded.withoutKey(id),
                trackedPreReleaseVersionExpanded = state.trackedPreReleaseVersionExpanded.withoutKey(id),
            )
        }
        if (removePersistedReleaseExpansion) {
            scope.launch {
                repository.removeTrackedReleaseExpansion(normalizedId)
            }
        }
    }

    fun retainTrackedExpansion(validItemIds: Set<String>) {
        val normalizedIds =
            validItemIds
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        mutableState.update { state ->
            state.copy(
                trackedCardExpanded = state.trackedCardExpanded.retainKeys(normalizedIds),
                trackedLocalVersionExpanded = state.trackedLocalVersionExpanded.retainKeys(normalizedIds),
                trackedStableVersionExpanded = state.trackedStableVersionExpanded.retainKeys(normalizedIds),
                trackedPreReleaseVersionExpanded = state.trackedPreReleaseVersionExpanded.retainKeys(normalizedIds),
            )
        }
    }

    private inline fun updateTrackedExpansionMap(
        itemId: String,
        crossinline transform: (GitHubTrackedItemsExpansionState, String) -> GitHubTrackedItemsExpansionState,
    ) {
        val normalizedId = itemId.trim()
        if (normalizedId.isBlank()) return
        mutableState.update { state -> transform(state, normalizedId) }
    }
}

private fun Map<String, Boolean>.withBoolean(
    key: String,
    value: Boolean,
): Map<String, Boolean> =
    if (value) {
        this + (key to true)
    } else {
        withoutKey(key)
    }

private fun Map<String, Boolean>.withoutKey(key: String): Map<String, Boolean> =
    if (containsKey(key)) {
        this - key
    } else {
        this
    }

private fun Map<String, Boolean>.retainKeys(validKeys: Set<String>): Map<String, Boolean> =
    if (keys.all { it in validKeys }) {
        this
    } else {
        filterKeys { it in validKeys }
    }
