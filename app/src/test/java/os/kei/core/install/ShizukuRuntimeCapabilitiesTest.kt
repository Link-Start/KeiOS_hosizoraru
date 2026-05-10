package os.kei.core.install

import org.junit.Test
import os.kei.core.system.ShizukuCommandIdentity
import os.kei.core.system.ShizukuRuntimeState
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShizukuRuntimeCapabilitiesTest {
    @Test
    fun `binder dead disables every install backend`() {
        val capability = capability(
            state = state(binderAlive = false),
            remoteInstallGranted = true
        )

        assertFalse(capability.sessionReady)
        assertFalse(capability.shellReady)
        assertFalse(capability.anyBackendReady)
    }

    @Test
    fun `pre v11 disables every install backend`() {
        val capability = capability(
            state = state(preV11 = true),
            remoteInstallGranted = true
        )

        assertFalse(capability.sessionReady)
        assertFalse(capability.shellReady)
    }

    @Test
    fun `missing self permission disables every install backend`() {
        val capability = capability(
            state = state(permissionGranted = false),
            remoteInstallGranted = true
        )

        assertFalse(capability.sessionReady)
        assertFalse(capability.shellReady)
    }

    @Test
    fun `remote install permission gates session while shell remains usable`() {
        val capability = capability(
            state = state(commandIdentity = ShizukuCommandIdentity.SHELL),
            remoteInstallGranted = false
        )

        assertFalse(capability.sessionReady)
        assertTrue(capability.shellReady)
        assertTrue(capability.anyBackendReady)
    }

    @Test
    fun `shell and root identities allow shell fallback`() {
        assertTrue(
            capability(
                state = state(commandIdentity = ShizukuCommandIdentity.SHELL),
                remoteInstallGranted = false
            ).shellReady
        )
        assertTrue(
            capability(
                state = state(commandIdentity = ShizukuCommandIdentity.ROOT),
                remoteInstallGranted = true
            ).shellReady
        )
    }

    @Test
    fun `unsupported uid can still use session when remote install permission is granted`() {
        val capability = capability(
            state = state(commandIdentity = ShizukuCommandIdentity.UNSUPPORTED),
            remoteInstallGranted = true
        )

        assertTrue(capability.sessionReady)
        assertFalse(capability.shellReady)
    }

    private fun capability(
        state: ShizukuRuntimeState,
        remoteInstallGranted: Boolean
    ): ShizukuInstallCapability {
        return ShizukuRuntimeCapabilities(
            object : ShizukuRuntimeProbe {
                override fun runtimeState(): ShizukuRuntimeState = state
                override fun remotePermissionGranted(permission: String): Boolean =
                    remoteInstallGranted
            }
        ).current()
    }

    private fun state(
        binderAlive: Boolean = true,
        preV11: Boolean = false,
        permissionGranted: Boolean = true,
        commandIdentity: ShizukuCommandIdentity = ShizukuCommandIdentity.SHELL
    ): ShizukuRuntimeState {
        return ShizukuRuntimeState(
            binderAlive = binderAlive,
            preV11 = preV11,
            permissionGranted = permissionGranted,
            serviceUid = when (commandIdentity) {
                ShizukuCommandIdentity.ROOT -> 0
                ShizukuCommandIdentity.SHELL -> 2000
                ShizukuCommandIdentity.UNSUPPORTED -> 10_000
            },
            commandIdentity = commandIdentity
        )
    }
}
