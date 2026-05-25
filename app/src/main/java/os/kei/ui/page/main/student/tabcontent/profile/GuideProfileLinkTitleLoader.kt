package os.kei.ui.page.main.student.tabcontent.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class GuideProfileLinkTitleUiState(
    val titles: Map<String, String> = emptyMap(),
    val missingLinks: Set<String> = emptySet(),
)

internal class GuideProfileLinkTitleLoader(
    private val scope: CoroutineScope,
    private val repository: GuideProfileLinkTitleRepository = GuideProfileLinkTitleRepository(),
) {
    private companion object {
        const val TITLE_STATE_LIMIT = 96
        const val MISSING_STATE_LIMIT = 128
    }

    private var loadingLinks: Set<String> = emptySet()
    private val mutableState = MutableStateFlow(GuideProfileLinkTitleUiState())
    val state: StateFlow<GuideProfileLinkTitleUiState> = mutableState.asStateFlow()

    fun requestTitles(rawLinks: List<String>) {
        val links =
            rawLinks
                .map { raw -> extractProfileExternalLink(raw).ifBlank { raw.trim() } }
                .filter { link -> link.isNotBlank() }
                .distinct()
        if (links.isEmpty()) return

        val current = mutableState.value
        val cachedTitles =
            links
                .filterNot { link -> current.titles.containsKey(link) }
                .mapNotNull { link ->
                    repository.cachedTitle(link)?.let { title -> link to title }
                }.toMap()
        if (cachedTitles.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    titles =
                        mergeLimitedMap(
                            current = state.titles,
                            added = cachedTitles,
                            limit = TITLE_STATE_LIMIT,
                        ),
                )
            }
        }

        val missingLinks =
            links.filter { link ->
                !current.titles.containsKey(link) &&
                    !current.missingLinks.contains(link) &&
                    !cachedTitles.containsKey(link) &&
                    !loadingLinks.contains(link)
            }
        if (missingLinks.isEmpty()) return

        loadingLinks += missingLinks
        scope.launch {
            try {
                val resolved =
                    missingLinks
                        .mapNotNull { link ->
                            repository
                                .resolveTitle(link)
                                .takeIf { it.isNotBlank() }
                                ?.let { title -> link to title }
                        }.toMap()
                val failed = missingLinks.toSet() - resolved.keys
                mutableState.update { state ->
                    state.copy(
                        titles =
                            mergeLimitedMap(
                                current = state.titles,
                                added = resolved,
                                limit = TITLE_STATE_LIMIT,
                            ),
                        missingLinks =
                            mergeLimitedSet(
                                current = state.missingLinks,
                                added = failed,
                                limit = MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.update { state ->
                    state.copy(
                        missingLinks =
                            mergeLimitedSet(
                                current = state.missingLinks,
                                added = missingLinks.toSet(),
                                limit = MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } finally {
                loadingLinks -= missingLinks
            }
        }
    }

    fun clearLoadingState() {
        loadingLinks = emptySet()
    }
}

private fun mergeLimitedMap(
    current: Map<String, String>,
    added: Map<String, String>,
    limit: Int,
): Map<String, String> {
    if (added.isEmpty()) return current
    val merged = LinkedHashMap<String, String>(current.size + added.size)
    current.forEach { (key, value) ->
        merged[key] = value
    }
    added.forEach { (key, value) ->
        merged.remove(key)
        merged[key] = value
    }
    while (merged.size > limit.coerceAtLeast(1)) {
        val firstKey = merged.entries.firstOrNull()?.key ?: break
        merged.remove(firstKey)
    }
    return merged.toMap()
}

private fun mergeLimitedSet(
    current: Set<String>,
    added: Set<String>,
    limit: Int,
): Set<String> {
    if (added.isEmpty()) return current
    val merged = LinkedHashSet<String>(current.size + added.size)
    merged.addAll(current)
    added.forEach { value ->
        merged.remove(value)
        merged.add(value)
    }
    while (merged.size > limit.coerceAtLeast(1)) {
        val first = merged.firstOrNull() ?: break
        merged.remove(first)
    }
    return merged.toSet()
}
