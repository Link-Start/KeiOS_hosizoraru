package os.kei.ui.page.main.os

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OsPageRowsStateLoader(
    private val scope: CoroutineScope,
    private val repository: OsPageRepository,
) {
    private val mutableState = MutableStateFlow(OsPageRowsUiDerivedState.Empty)
    private var rowsDerivationJob: Job? = null
    val state: StateFlow<OsPageRowsUiDerivedState> = mutableState.asStateFlow()

    fun request(input: OsPageRowsDerivationInput) {
        val current = mutableState.value
        if (current.input == input && !current.deriving) return
        rowsDerivationJob?.cancel()
        rowsDerivationJob =
            scope.launch {
                mutableState.update { state ->
                    state.copy(
                        input = input,
                        deriving = true,
                    )
                }
                mutableState.value = repository.buildRowsDerivedState(input)
            }
    }

    fun cancel() {
        rowsDerivationJob?.cancel()
        rowsDerivationJob = null
    }
}
