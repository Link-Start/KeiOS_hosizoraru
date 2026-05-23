package os.kei.ui.page.main.os

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.os.shortcut.ShortcutSuggestionField

internal class OsActivitySuggestionController(
    private val scope: CoroutineScope,
    private val repository: OsPageRepository,
) {
    private val mutableState = MutableStateFlow(OsActivitySuggestionUiState())
    private val mutableChromeState = MutableStateFlow(OsActivitySuggestionChromeState())
    private var suggestionJob: Job? = null

    val state: StateFlow<OsActivitySuggestionUiState> = mutableState.asStateFlow()
    val chromeState: StateFlow<OsActivitySuggestionChromeState> = mutableChromeState.asStateFlow()

    fun openSheet(target: ShortcutSuggestionField) {
        mutableChromeState.update { state ->
            when (target) {
                ShortcutSuggestionField.PackageName -> {
                    state.copy(
                        showSheet = true,
                        target = target,
                        packageQuery = "",
                    )
                }

                ShortcutSuggestionField.ClassName -> {
                    state.copy(
                        showSheet = true,
                        target = target,
                        classQuery = "",
                    )
                }

                else -> {
                    state.copy(
                        showSheet = true,
                        target = target,
                    )
                }
            }
        }
    }

    fun dismissSheet() {
        mutableChromeState.update { state -> state.copy(showSheet = false) }
    }

    fun resetQueries() {
        mutableChromeState.update { state ->
            state.copy(
                packageQuery = "",
                classQuery = "",
            )
        }
    }

    fun updatePackageQuery(query: String) {
        mutableChromeState.update { state ->
            if (state.packageQuery == query) state else state.copy(packageQuery = query)
        }
    }

    fun updateClassQuery(query: String) {
        mutableChromeState.update { state ->
            if (state.classQuery == query) state else state.copy(classQuery = query)
        }
    }

    fun request(
        context: Context,
        show: Boolean,
        target: ShortcutSuggestionField,
        packageName: String,
    ) {
        suggestionJob?.cancel()
        if (!show) {
            mutableState.update { state ->
                state.copy(
                    packageSuggestionsLoading = false,
                    classSuggestionsLoading = false,
                )
            }
            return
        }
        val appContext = context.applicationContext
        suggestionJob =
            scope.launch {
                when (target) {
                    ShortcutSuggestionField.PackageName -> loadPackageSuggestions(appContext)
                    ShortcutSuggestionField.ClassName -> loadClassSuggestions(
                        context = appContext,
                        packageName = packageName,
                    )

                    else -> {
                        mutableState.update { state ->
                            state.copy(
                                packageSuggestionsLoading = false,
                                classSuggestionsLoading = false,
                            )
                        }
                    }
                }
            }
    }

    fun cancel() {
        suggestionJob?.cancel()
        suggestionJob = null
    }

    private suspend fun loadPackageSuggestions(context: Context) {
        mutableState.update { state -> state.copy(packageSuggestionsLoading = true) }
        val suggestions =
            runCatching {
                repository.loadActivityShortcutPackageSuggestions(context)
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                emptyList()
            }
        mutableState.update { state ->
            state.copy(
                packageSuggestions = suggestions,
                packageSuggestionsLoading = false,
            )
        }
    }

    private suspend fun loadClassSuggestions(
        context: Context,
        packageName: String,
    ) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            mutableState.update { state ->
                state.copy(
                    classSuggestions = emptyList(),
                    classSuggestionsLoading = false,
                )
            }
            return
        }
        mutableState.update { state -> state.copy(classSuggestionsLoading = true) }
        val suggestions =
            runCatching {
                repository.loadActivityShortcutClassSuggestions(
                    context = context,
                    packageName = normalizedPackageName,
                )
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                emptyList()
            }
        mutableState.update { state ->
            state.copy(
                classSuggestions = suggestions,
                classSuggestionsLoading = false,
            )
        }
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) throw this
}
