package os.kei.core.privilege

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivilegeStatusTest {
    @Test
    fun `only ready reports command readiness`() {
        PrivilegeStatusCode.entries.forEach { code ->
            val status = PrivilegeStatus(mode = PrivilegeMode.Shizuku, code = code)
            if (code == PrivilegeStatusCode.Ready) {
                assertTrue(status.isCommandReady, "expected $code to be ready")
            } else {
                assertFalse(status.isCommandReady, "expected $code to not be ready")
            }
        }
    }
}
