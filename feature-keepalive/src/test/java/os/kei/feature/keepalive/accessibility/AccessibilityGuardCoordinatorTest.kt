package os.kei.feature.keepalive.accessibility

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AccessibilityGuardCoordinatorTest {
    @Test
    fun `check self records checked result when secure settings are readable and no policy is enabled`() = runTest {
        val coordinator = coordinator(bridge = FakeSecureSettingsBridge(readSuccess = true))

        val result = coordinator.checkSelf(AccessibilityGuardCheckReason.Manual)

        assertEquals(AccessibilityGuardCheckStatus.Checked, result.status)
        assertEquals(1, result.checkCount)
        assertEquals(1, result.healthyCount)
        assertEquals(0, result.warningCount)
        assertEquals("ready", result.privilegeStatus)
    }

    @Test
    fun `check self records healthy result when guard policies are enabled`() = runTest {
        val store =
            InMemoryGuardStore(
                AccessibilityGuardSettings(
                    daemonEnabled = true,
                    bootCheckEnabled = true,
                    screenOnCheckEnabled = true,
                ),
            )
        val coordinator = coordinator(store = store, bridge = FakeSecureSettingsBridge(readSuccess = true))

        val result = coordinator.checkSelf(AccessibilityGuardCheckReason.ForegroundServiceStart)

        assertEquals(AccessibilityGuardCheckStatus.Healthy, result.status)
        assertEquals(4, result.checkCount)
        assertEquals(4, result.healthyCount)
        assertEquals(0, result.warningCount)
    }

    @Test
    fun `check self records missing privilege when secure settings cannot be read`() = runTest {
        val store = InMemoryGuardStore(AccessibilityGuardSettings(daemonEnabled = true))
        val coordinator =
            coordinator(
                store = store,
                bridge = FakeSecureSettingsBridge(readSuccess = false, readReason = "permission denied"),
            )

        val result = coordinator.checkSelf(AccessibilityGuardCheckReason.ScreenOn)

        assertEquals(AccessibilityGuardCheckStatus.MissingPrivilege, result.status)
        assertEquals(2, result.checkCount)
        assertEquals(1, result.healthyCount)
        assertEquals(1, result.warningCount)
        assertEquals("permission denied", result.failureReason)
    }

    private fun coordinator(
        store: AccessibilityGuardStateStore = InMemoryGuardStore(),
        bridge: AccessibilitySecureSettingsBridge = FakeSecureSettingsBridge(),
        nowMs: Long = 1_000L,
    ): AccessibilityGuardCoordinator =
        AccessibilityGuardCoordinator(
            secureSettingsBridge = bridge,
            stateStore = store,
            wallClockMs = { nowMs },
            elapsedClockMs = { nowMs },
        )

    private class InMemoryGuardStore(
        private var settings: AccessibilityGuardSettings = AccessibilityGuardSettings(),
    ) : AccessibilityGuardStateStore {
        override fun loadSettings(): AccessibilityGuardSettings = settings

        override fun saveSettings(settings: AccessibilityGuardSettings) {
            this.settings = settings
        }
    }

    private class FakeSecureSettingsBridge(
        private val readSuccess: Boolean = true,
        private val readReason: String = "",
    ) : AccessibilitySecureSettingsBridge {
        override suspend fun readEnabledServiceIds(): AccessibilitySecureSettingRead =
            AccessibilitySecureSettingRead(
                rawValue = "",
                ids = emptySet(),
                success = readSuccess,
                reason = readReason,
            )
    }
}
