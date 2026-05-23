package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OsActivityShortcutIconLoader(
    private val scope: CoroutineScope,
    private val repository: OsActivityShortcutIconRepository = OsActivityShortcutIconRepository(),
) {
    private var activityIconLoadingKeys: Set<String> = emptySet()
    private var packageIconLoadingPackages: Set<String> = emptySet()
    private val mutableState = MutableStateFlow(OsActivityShortcutIconUiState())
    val state: StateFlow<OsActivityShortcutIconUiState> = mutableState.asStateFlow()

    fun requestActivityShortcutIcons(
        context: Context,
        requests: List<OsActivityShortcutIconRequest>,
    ) {
        val normalizedRequests =
            requests
                .filter { request ->
                    request.packageName.isNotBlank() && request.className.isNotBlank()
                }.distinctBy { request ->
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                }
        if (normalizedRequests.isEmpty()) return

        val currentState = mutableState.value
        val missingRequests =
            normalizedRequests.filter { request ->
                val key =
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                !currentState.bitmaps.containsKey(key) &&
                    !currentState.missingKeys.contains(key) &&
                    !activityIconLoadingKeys.contains(key) &&
                    !repository.isMissing(key)
            }
        if (missingRequests.isEmpty()) return

        val requestedKeys =
            missingRequests
                .map { request ->
                    osActivityShortcutIconKey(
                        packageName = request.packageName,
                        className = request.className,
                    )
                }.toSet()
        activityIconLoadingKeys += requestedKeys
        val appContext = context.applicationContext
        scope.launch {
            try {
                val result =
                    repository.loadActivityIcons(
                        context = appContext,
                        requests = missingRequests,
                    )
                mutableState.update { state ->
                    state.copy(
                        bitmaps = state.bitmaps + result.bitmaps,
                        missingKeys = state.missingKeys + result.missingKeys,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.update { state ->
                    state.copy(missingKeys = state.missingKeys + requestedKeys)
                }
            } finally {
                activityIconLoadingKeys -= requestedKeys
            }
        }
    }

    fun requestPackageIcons(
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
                .filterNot { currentState.packageBitmaps.containsKey(it) }
                .mapNotNull { packageName ->
                    repository.cachedPackageBitmap(packageName)?.let { bitmap ->
                        packageName to bitmap
                    }
                }.toMap()
        if (cachedBitmaps.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(packageBitmaps = state.packageBitmaps + cachedBitmaps)
            }
        }

        val missingPackages =
            normalizedPackages.filter { packageName ->
                !currentState.packageBitmaps.containsKey(packageName) &&
                    !currentState.missingPackages.contains(packageName) &&
                    !packageIconLoadingPackages.contains(packageName) &&
                    !cachedBitmaps.containsKey(packageName)
            }
        if (missingPackages.isEmpty()) return

        packageIconLoadingPackages += missingPackages
        val appContext = context.applicationContext
        scope.launch {
            try {
                val result =
                    repository.loadPackageIcons(
                        context = appContext,
                        packageNames = missingPackages,
                    )
                mutableState.update { state ->
                    state.copy(
                        packageBitmaps = state.packageBitmaps + result.bitmaps,
                        missingPackages = state.missingPackages + result.missingPackages,
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.update { state ->
                    state.copy(missingPackages = state.missingPackages + missingPackages)
                }
            } finally {
                packageIconLoadingPackages -= missingPackages
            }
        }
    }

    fun clearLoadingState() {
        activityIconLoadingKeys = emptySet()
        packageIconLoadingPackages = emptySet()
    }
}
