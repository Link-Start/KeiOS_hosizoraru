package os.kei.core.system

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PropUtilsTest {
    @Test
    fun `system property parser keeps values with brackets and equals`() {
        val parsed = parseSystemPropertiesOutput(
            """
            [ro.build.version.sdk]: [36]
            [ro.demo.brackets]: [value[inner]=ok]
            [invalid]
            plain=value
            """.trimIndent()
        )

        assertEquals("36", parsed["ro.build.version.sdk"])
        assertEquals("value[inner]=ok", parsed["ro.demo.brackets"])
        assertFalse(parsed.containsKey("invalid"))
        assertFalse(parsed.containsKey("plain"))
    }
}
