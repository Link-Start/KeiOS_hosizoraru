package os.kei.ui.page.main.host.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import os.kei.core.prefs.LauncherIconDesign
import os.kei.core.log.AppLogLevel
import os.kei.core.prefs.NonHomeBackgroundAlignment
import os.kei.core.prefs.NonHomeBackgroundContentScale
import os.kei.core.prefs.NonHomeBackgroundPageStyle
import os.kei.core.prefs.SuperIslandFloatBehavior
import os.kei.core.prefs.SuperIslandAutoClose
import os.kei.core.prefs.UiPrefs
import os.kei.core.prefs.UiPrefsRepository
import os.kei.core.prefs.UiPrefsSnapshot
import os.kei.core.privilege.PrivilegeMode

internal class MainScreenPrefsViewModel : ViewModel() {
    private val repository = UiPrefsRepository(initialSnapshot = UiPrefs.defaultSnapshot())
    val snapshot: StateFlow<UiPrefsSnapshot> =
        repository
            .observeSnapshots()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = repository.observeSnapshots().value,
            )
    private var loadJob: Job? = null

    fun loadInitialSnapshot() {
        if (loadJob != null) return
        loadJob =
            viewModelScope.launch {
                repository.refreshSnapshot()
            }
    }

    fun updateLiquidSwitchEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setLiquidSwitchEnabled(value)
        }
    }

    fun updateLiquidToastEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setLiquidToastEnabled(value)
        }
    }

    fun updateReduceToastInterruptionEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setReduceToastInterruptionEnabled(value)
        }
    }

    fun updateTransitionAnimationsEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setTransitionAnimationsEnabled(value)
        }
    }

    fun updatePredictiveBackAnimationsEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setPredictiveBackAnimationsEnabled(value)
        }
    }

    fun updateSearchAutoFocusEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setSearchAutoFocusEnabled(value)
        }
    }

    fun updateGripAwareFloatingDockEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setGripAwareFloatingDockEnabled(value)
        }
    }

    fun updateHomeIconHdrEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setHomeIconHdrEnabled(value)
        }
    }

    fun updateHomeDynamicFullEffectEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setHomeDynamicFullEffectEnabled(value)
        }
    }

    fun updatePreloadingEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setPreloadingEnabled(value)
        }
    }

    fun updatePrivilegeMode(value: PrivilegeMode) {
        launchRepositoryUpdate {
            setPrivilegeModeId(value.storageId)
        }
    }

    fun updateLauncherIconDesign(value: LauncherIconDesign) {
        launchRepositoryUpdate {
            setLauncherIconDesign(value)
        }
    }

    fun updateNonHomeBackgroundEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setNonHomeBackgroundEnabled(value)
        }
    }

    fun updateNonHomeBackgroundUri(value: String) {
        launchRepositoryUpdate {
            setNonHomeBackgroundUri(value)
        }
    }

    fun updateNonHomeBackgroundOpacity(value: Float) {
        launchRepositoryUpdate {
            setNonHomeBackgroundOpacity(value)
        }
    }

    fun updateNonHomeBackgroundContentScale(value: NonHomeBackgroundContentScale) {
        launchRepositoryUpdate {
            setNonHomeBackgroundContentScale(value)
        }
    }

    fun updateNonHomeBackgroundAlignment(value: NonHomeBackgroundAlignment) {
        launchRepositoryUpdate {
            setNonHomeBackgroundAlignment(value)
        }
    }

    fun updateNonHomeBackgroundPageStyle(value: NonHomeBackgroundPageStyle) {
        launchRepositoryUpdate {
            setNonHomeBackgroundPageStyle(value)
        }
    }

    fun updateNonHomeBackgroundScrim(value: Float) {
        launchRepositoryUpdate {
            setNonHomeBackgroundScrim(value)
        }
    }

    fun updateNonHomeBackgroundDepthEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setNonHomeBackgroundDepthEnabled(value)
        }
    }

    fun updateNonHomeBackgroundSaturation(value: Float) {
        launchRepositoryUpdate {
            setNonHomeBackgroundSaturation(value)
        }
    }

    fun resetNonHomeBackgroundRendering() {
        launchRepositoryUpdate {
            resetNonHomeBackgroundRendering()
        }
    }

    fun applyNonHomeBackgroundReadableSuggestion(isDarkTheme: Boolean) {
        launchRepositoryUpdate {
            applyNonHomeBackgroundReadableSuggestion(isDarkTheme)
        }
    }

    fun updateSuperIslandNotificationEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setSuperIslandNotificationEnabled(value)
        }
    }

    fun updateSuperIslandFloatBehavior(value: SuperIslandFloatBehavior) {
        launchRepositoryUpdate {
            setSuperIslandFloatBehavior(value)
        }
    }

    fun updateSuperIslandAutoClose(value: SuperIslandAutoClose) {
        launchRepositoryUpdate {
            setSuperIslandAutoClose(value)
        }
    }

    fun updateSuperIslandFirstFloatEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setSuperIslandFirstFloatEnabled(value)
        }
    }

    fun updateSuperIslandBypassRestrictionEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setSuperIslandBypassRestrictionEnabled(value)
        }
    }

    fun updateSuperIslandRestoreDelayMs(value: Int) {
        launchRepositoryUpdate {
            setSuperIslandRestoreDelayMs(value)
        }
    }

    fun updateLogLevel(value: AppLogLevel) {
        launchRepositoryUpdate {
            setLogLevel(value)
        }
    }

    fun updateTextCopyCapabilityExpanded(value: Boolean) {
        launchRepositoryUpdate {
            setTextCopyCapabilityExpanded(value)
        }
    }

    fun updateCacheDiagnosticsEnabled(value: Boolean) {
        launchRepositoryUpdate {
            setCacheDiagnosticsEnabled(value)
        }
    }

    fun updateVisibleBottomPageNames(value: Set<String>) {
        launchRepositoryUpdate {
            saveVisibleBottomPageNames(value)
        }
    }

    private fun launchRepositoryUpdate(persist: suspend UiPrefsRepository.() -> Unit) {
        viewModelScope.launch {
            repository.persist()
        }
    }
}
