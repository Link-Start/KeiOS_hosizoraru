package os.kei.ui.page.main.github.page

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class GitHubAppIconLoader(
    private val scope: CoroutineScope,
    private val repository: GitHubAppIconRepository = GitHubAppIconRepository(),
) {
    private var loadingPackages: Set<String> = emptySet()
    private val mutableState = MutableStateFlow(GitHubAppIconUiState())
    val state: StateFlow<GitHubAppIconUiState> = mutableState.asStateFlow()

    fun requestIcons(
        context: Context,
        packageNames: List<String>,
    ) {
        val normalizedPackages =
            packageNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (normalizedPackages.isEmpty()) return

        val currentState = mutableState.value
        val cachedBitmaps =
            normalizedPackages
                .filterNot { currentState.bitmaps.containsKey(it) }
                .mapNotNull { packageName ->
                    repository.cachedBitmap(packageName)?.let { bitmap -> packageName to bitmap }
                }.toMap()
        if (cachedBitmaps.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(bitmaps = state.bitmaps + cachedBitmaps)
            }
        }

        val missingPackages =
            normalizedPackages.filter { packageName ->
                !currentState.bitmaps.containsKey(packageName) &&
                    !currentState.missingPackages.contains(packageName) &&
                    !loadingPackages.contains(packageName) &&
                    !cachedBitmaps.containsKey(packageName)
            }
        if (missingPackages.isEmpty()) return

        loadingPackages += missingPackages
        val appContext = context.applicationContext
        scope.launch {
            try {
                val result =
                    repository.loadIcons(
                        context = appContext,
                        packageNames = missingPackages,
                    )
                mutableState.update { state ->
                    state.copy(
                        bitmaps = state.bitmaps + result.bitmaps,
                        missingPackages = state.missingPackages + result.missingPackages,
                    )
                }
            } finally {
                loadingPackages -= missingPackages
            }
        }
    }

    fun clearLoadingState() {
        loadingPackages = emptySet()
    }
}
