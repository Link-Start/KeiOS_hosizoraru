package os.kei.ui.page.main.settings.support

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import os.kei.core.privilege.PrivilegeMode

class SettingsPrivilegedAppListAccessStateTest {
    @Test
    fun `root and shizuku package queries keep their mode in settings state`() {
        listOf(PrivilegeMode.Root to 317, PrivilegeMode.Shizuku to 241).forEach { (privilegeMode, count) ->
            val state = resolvePrivilegedAppListAccessState(privilegeMode, count)

            assertEquals(SettingsAppListAccessMode.Privileged, state?.mode, "$privilegeMode")
            assertEquals(privilegeMode, state?.privilegeMode, "$privilegeMode")
            assertEquals(count, state?.detectedCount, "$privilegeMode")
        }
    }

    @Test
    fun `disabled and failed queries fall back to direct detection`() {
        assertNull(resolvePrivilegedAppListAccessState(PrivilegeMode.Disabled, 200))
        assertNull(resolvePrivilegedAppListAccessState(PrivilegeMode.Root, null))
        assertNull(resolvePrivilegedAppListAccessState(PrivilegeMode.Root, 0))
    }
}
