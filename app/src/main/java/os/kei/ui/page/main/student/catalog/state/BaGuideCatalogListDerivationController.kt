package os.kei.ui.page.main.student.catalog.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.catalog.BaGuideCatalogTab

internal class BaGuideCatalogListDerivationController(
    private val scope: CoroutineScope,
    private val repository: BaGuideCatalogRepository,
) {
    private val listDerivationJobs = mutableMapOf<BaGuideCatalogTab, Job>()
    private val listDerivationInputs = mutableMapOf<BaGuideCatalogTab, BaGuideCatalogListInput>()
    private var studentBgmListDerivationJob: Job? = null
    private var studentBgmListDerivationInput: BaGuideStudentBgmListInput? = null
    private var favoriteBgmListDerivationJob: Job? = null
    private var favoriteBgmListDerivationInput: BaGuideFavoriteBgmListInput? = null
    private var studentBgmDisplayedDerivationJob: Job? = null
    private var studentBgmDisplayedDerivationInput: BaGuideStudentBgmDisplayedInput? = null

    private val mutableCatalogListDerivedStates =
        MutableStateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>>(emptyMap())
    private val mutableStudentBgmListDerivedState =
        MutableStateFlow(BaGuideStudentBgmListDerivedState.Empty)
    private val mutableFavoriteBgmListDerivedState =
        MutableStateFlow(BaGuideFavoriteBgmListDerivedState.Empty)
    private val mutableStudentBgmDisplayedDerivedState =
        MutableStateFlow(BaGuideStudentBgmDisplayedDerivedState.Empty)

    val catalogListDerivedStates: StateFlow<Map<BaGuideCatalogTab, BaGuideCatalogListDerivedState>> =
        mutableCatalogListDerivedStates.asStateFlow()
    val studentBgmListDerivedState: StateFlow<BaGuideStudentBgmListDerivedState> =
        mutableStudentBgmListDerivedState.asStateFlow()
    val favoriteBgmListDerivedState: StateFlow<BaGuideFavoriteBgmListDerivedState> =
        mutableFavoriteBgmListDerivedState.asStateFlow()
    val studentBgmDisplayedDerivedState: StateFlow<BaGuideStudentBgmDisplayedDerivedState> =
        mutableStudentBgmDisplayedDerivedState.asStateFlow()

    fun requestCatalogListState(input: BaGuideCatalogListInput) {
        val previousInput = listDerivationInputs[input.tab]
        val hasDerivedState = mutableCatalogListDerivedStates.value.containsKey(input.tab)
        if (previousInput == input && hasDerivedState) return

        listDerivationInputs[input.tab] = input
        listDerivationJobs[input.tab]?.cancel()
        mutableCatalogListDerivedStates.update { states ->
            val current = states[input.tab] ?: BaGuideCatalogListDerivedState.Empty
            states + (input.tab to current.copy(deriving = true))
        }
        listDerivationJobs[input.tab] =
            scope.launch {
                val derivedState = repository.deriveCatalogListState(input)
                if (listDerivationInputs[input.tab] != input) return@launch
                mutableCatalogListDerivedStates.update { states ->
                    states + (input.tab to derivedState)
                }
            }
    }

    fun requestStudentBgmListState(input: BaGuideStudentBgmListInput) {
        val previousInput = studentBgmListDerivationInput
        if (
            previousInput == input &&
            mutableStudentBgmListDerivedState.value !== BaGuideStudentBgmListDerivedState.Empty
        ) {
            return
        }

        studentBgmListDerivationInput = input
        studentBgmListDerivationJob?.cancel()
        mutableStudentBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        studentBgmListDerivationJob =
            scope.launch {
                val derivedState = repository.deriveStudentBgmListState(input)
                if (studentBgmListDerivationInput != input) return@launch
                mutableStudentBgmListDerivedState.value = derivedState
            }
    }

    fun requestFavoriteBgmListState(input: BaGuideFavoriteBgmListInput) {
        val previousInput = favoriteBgmListDerivationInput
        if (
            previousInput == input &&
            mutableFavoriteBgmListDerivedState.value !== BaGuideFavoriteBgmListDerivedState.Empty
        ) {
            return
        }

        favoriteBgmListDerivationInput = input
        favoriteBgmListDerivationJob?.cancel()
        mutableFavoriteBgmListDerivedState.update { state ->
            state.copy(deriving = true)
        }
        favoriteBgmListDerivationJob =
            scope.launch {
                val derivedState = repository.deriveFavoriteBgmListState(input)
                if (favoriteBgmListDerivationInput != input) return@launch
                mutableFavoriteBgmListDerivedState.value = derivedState
            }
    }

    fun requestStudentBgmDisplayedState(input: BaGuideStudentBgmDisplayedInput) {
        val previousInput = studentBgmDisplayedDerivationInput
        if (previousInput == input && mutableStudentBgmDisplayedDerivedState.value.input == input) return

        studentBgmDisplayedDerivationInput = input
        studentBgmDisplayedDerivationJob?.cancel()
        mutableStudentBgmDisplayedDerivedState.update { state ->
            state.copy(
                input = input,
                deriving = true,
            )
        }
        studentBgmDisplayedDerivationJob =
            scope.launch {
                val derivedState = repository.deriveStudentBgmDisplayedState(input)
                if (studentBgmDisplayedDerivationInput != input) return@launch
                mutableStudentBgmDisplayedDerivedState.value = derivedState
            }
    }

    fun cancel() {
        listDerivationJobs.values.forEach { job -> job.cancel() }
        studentBgmListDerivationJob?.cancel()
        favoriteBgmListDerivationJob?.cancel()
        studentBgmDisplayedDerivationJob?.cancel()
    }
}
