package os.kei.core.privilege

import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrivilegeModeTest {
    @After
    fun resetRuntime() {
        PrivilegeModeRuntime.configure(PrivilegeMode.Default)
    }

    @Test
    fun `storage ids round trip`() {
        PrivilegeMode.entries.forEach { mode ->
            assertEquals(mode, PrivilegeMode.fromStorageId(mode.storageId))
        }
    }

    @Test
    fun `unknown storage id falls back to disabled and whitespace is tolerated`() {
        assertEquals(PrivilegeMode.Disabled, PrivilegeMode.Default)
        assertEquals(PrivilegeMode.Disabled, PrivilegeMode.fromStorageId("magisk"))
        assertEquals(PrivilegeMode.Disabled, PrivilegeMode.fromStorageId(null))
        assertEquals(PrivilegeMode.Disabled, PrivilegeMode.fromStorageId("  "))
        assertEquals(PrivilegeMode.Root, PrivilegeMode.fromStorageId("  root "))
    }

    @Test
    fun `switching modes notifies registered listeners`() {
        val observed = mutableListOf<PrivilegeMode>()
        val listener: (PrivilegeMode) -> Unit = { observed += it }
        PrivilegeModeRuntime.addListener(listener)
        try {
            PrivilegeModeRuntime.set(PrivilegeMode.Root)
            PrivilegeModeRuntime.set(PrivilegeMode.Root)
            PrivilegeModeRuntime.set(PrivilegeMode.Shizuku)
        } finally {
            PrivilegeModeRuntime.removeListener(listener)
        }

        assertEquals(listOf(PrivilegeMode.Root, PrivilegeMode.Shizuku), observed)
    }

    @Test
    fun `configure applies without notifying listeners`() {
        val observed = mutableListOf<PrivilegeMode>()
        val listener: (PrivilegeMode) -> Unit = { observed += it }
        PrivilegeModeRuntime.addListener(listener)
        try {
            PrivilegeModeRuntime.configure(PrivilegeMode.Root)
        } finally {
            PrivilegeModeRuntime.removeListener(listener)
        }

        assertEquals(PrivilegeMode.Root, PrivilegeModeRuntime.mode)
        assertTrue(observed.isEmpty())
    }
}
