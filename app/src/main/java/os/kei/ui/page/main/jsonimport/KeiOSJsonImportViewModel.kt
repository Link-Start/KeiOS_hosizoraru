package os.kei.ui.page.main.jsonimport

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import os.kei.core.log.AppLogger

internal class KeiOSJsonImportViewModel : ViewModel() {
    private val repository = KeiOSJsonImportRepository()
    private val _uiState = MutableStateFlow(KeiOSJsonImportUiState())
    val uiState: StateFlow<KeiOSJsonImportUiState> = _uiState.asStateFlow()

    private var currentJob: Job? = null
    private var pendingPlan: KeiOSJsonImportPlan? = null

    fun loadIntent(context: Context, intent: Intent?) {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val appContext = context.applicationContext
            val source = repository.sourceFromIntent(appContext, intent)
            pendingPlan = null
            _uiState.value = KeiOSJsonImportUiState(
                stage = KeiOSJsonImportStage.Reading,
                sourceName = source.displayName,
                sourceSizeBytes = source.declaredSizeBytes,
                busy = true
            )
            try {
                val plan = repository.buildPlan(appContext, source) { stage ->
                    _uiState.update { state ->
                        state.copy(stage = stage, busy = stage != KeiOSJsonImportStage.PreviewReady)
                    }
                }
                pendingPlan = plan
                _uiState.update { state ->
                    state.copy(
                        stage = KeiOSJsonImportStage.PreviewReady,
                        sourceName = source.displayName.ifBlank { state.sourceName },
                        sourceDescription = source.uri?.toString().orEmpty(),
                        sourceSizeBytes = source.declaredSizeBytes,
                        preview = plan.preview,
                        applyResult = null,
                        errorMessage = "",
                        busy = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(TAG, "json import preview failed", error)
                _uiState.update { state ->
                    state.copy(
                        stage = KeiOSJsonImportStage.Failed,
                        errorMessage = repository.errorMessage(appContext, error),
                        busy = false
                    )
                }
            }
        }
    }

    fun confirmImport(context: Context) {
        val plan = pendingPlan as? ImportableKeiOSJsonPlan ?: return
        if (_uiState.value.busy) return
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            val appContext = context.applicationContext
            _uiState.update { state ->
                state.copy(
                    stage = KeiOSJsonImportStage.Importing,
                    busy = true,
                    errorMessage = ""
                )
            }
            try {
                val result = plan.apply()
                pendingPlan = null
                _uiState.update { state ->
                    state.copy(
                        stage = KeiOSJsonImportStage.Done,
                        applyResult = result,
                        busy = false
                    )
                }
                AppLogger.i(
                    TAG,
                    "json import applied kind=${plan.preview.kind} added=${result.addedCount} updated=${result.updatedCount}"
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLogger.w(TAG, "json import apply failed", error)
                val displayError = error as? KeiOSJsonImportException
                    ?: KeiOSJsonImportException(
                        reason = KeiOSJsonImportFailureReason.ApplyFailed,
                        cause = error
                    )
                _uiState.update { state ->
                    state.copy(
                        stage = KeiOSJsonImportStage.Failed,
                        errorMessage = repository.errorMessage(appContext, displayError),
                        busy = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        currentJob?.cancel()
        super.onCleared()
    }

    private companion object {
        private const val TAG = "KeiOSJsonImport"
    }
}
