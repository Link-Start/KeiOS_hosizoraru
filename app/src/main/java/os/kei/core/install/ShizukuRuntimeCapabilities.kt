package os.kei.core.install

import android.Manifest
import android.content.pm.PackageManager
import os.kei.core.system.ShizukuApiUtils
import os.kei.core.system.ShizukuRuntimeState
import rikka.shizuku.Shizuku

data class ShizukuInstallCapability(
    val runtimeState: ShizukuRuntimeState,
    val remoteInstallPermissionGranted: Boolean
) {
    val sessionReady: Boolean
        get() = runtimeState.binderAlive &&
                !runtimeState.preV11 &&
                runtimeState.permissionGranted &&
                remoteInstallPermissionGranted

    val shellReady: Boolean
        get() = runtimeState.commandReady

    val anyBackendReady: Boolean
        get() = sessionReady || shellReady

    val statusText: String
        get() = when {
            !runtimeState.binderAlive -> runtimeState.statusText
            runtimeState.preV11 -> runtimeState.statusText
            !runtimeState.permissionGranted -> runtimeState.statusText
            sessionReady -> "Shizuku install session ready"
            shellReady -> "Shizuku shell install ready"
            !remoteInstallPermissionGranted -> "Shizuku remote install permission unavailable"
            else -> runtimeState.statusText
        }
}

interface ShizukuRuntimeProbe {
    fun runtimeState(): ShizukuRuntimeState
    fun remotePermissionGranted(permission: String): Boolean
}

class AndroidShizukuRuntimeProbe(
    private val shizukuApiUtils: ShizukuApiUtils = ShizukuApiUtils()
) : ShizukuRuntimeProbe {
    override fun runtimeState(): ShizukuRuntimeState {
        return shizukuApiUtils.runtimeState()
    }

    override fun remotePermissionGranted(permission: String): Boolean {
        return runCatching {
            Shizuku.checkRemotePermission(permission) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }
}

class ShizukuRuntimeCapabilities(
    private val probe: ShizukuRuntimeProbe = AndroidShizukuRuntimeProbe()
) {
    fun current(): ShizukuInstallCapability {
        val state = probe.runtimeState()
        val remoteInstallGranted = state.binderAlive &&
                !state.preV11 &&
                state.permissionGranted &&
                probe.remotePermissionGranted(Manifest.permission.INSTALL_PACKAGES)
        return ShizukuInstallCapability(
            runtimeState = state,
            remoteInstallPermissionGranted = remoteInstallGranted
        )
    }
}
