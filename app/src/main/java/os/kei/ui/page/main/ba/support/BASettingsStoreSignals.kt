package os.kei.ui.page.main.ba.support

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object BASettingsStoreSignals {
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    fun notifyChanged(atMillis: Long = System.currentTimeMillis()) {
        _version.value = atMillis
    }
}
