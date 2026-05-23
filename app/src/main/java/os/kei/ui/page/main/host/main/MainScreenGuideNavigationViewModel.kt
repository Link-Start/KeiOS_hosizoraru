package os.kei.ui.page.main.host.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import os.kei.ui.page.main.student.page.state.BaStudentGuideRepository

internal sealed interface MainScreenGuideNavigationEvent {
    data object OpenStudentGuide : MainScreenGuideNavigationEvent
}

internal class MainScreenGuideNavigationViewModel : ViewModel() {
    private val repository = BaStudentGuideRepository()
    private val mutableEvents =
        MutableSharedFlow<MainScreenGuideNavigationEvent>(
            replay = 0,
            extraBufferCapacity = 1,
        )

    val events: SharedFlow<MainScreenGuideNavigationEvent> = mutableEvents.asSharedFlow()

    fun saveAndOpenCanonicalGuide(canonicalGuideUrl: String) {
        val normalizedUrl = canonicalGuideUrl.trim()
        if (normalizedUrl.isBlank()) return
        viewModelScope.launch {
            try {
                repository.saveCurrentUrlAsync(normalizedUrl)
                mutableEvents.emit(MainScreenGuideNavigationEvent.OpenStudentGuide)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                mutableEvents.emit(MainScreenGuideNavigationEvent.OpenStudentGuide)
            }
        }
    }
}
