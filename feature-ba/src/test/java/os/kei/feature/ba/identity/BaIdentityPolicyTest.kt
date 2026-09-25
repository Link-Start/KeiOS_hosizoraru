package os.kei.feature.ba.identity

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaIdentityPolicyTest {
    @Test
    fun `nickname limits follow server policy`() {
        assertEquals("ABCDEFGHIJ", sanitizeBaNickname("ABCDEFGHIJKL", BA_SERVER_INDEX_CN))
        assertEquals("ABCDEFGHIJKL", sanitizeBaNickname("ABCDEFGHIJKLM", BA_SERVER_INDEX_GLOBAL))
        assertEquals("ABCDEFGHIJ", sanitizeBaNickname("ABCDEFGHIJKL", BA_SERVER_INDEX_JP))
    }

    @Test
    fun `nickname truncation keeps complete code points`() {
        assertEquals("ABCDEFGHIJ", sanitizeBaNickname("ABCDEFGHIJ🙂", BA_SERVER_INDEX_CN))
        assertEquals("ABCDEFGHIJ🙂Z", sanitizeBaNickname("ABCDEFGHIJ🙂Z", BA_SERVER_INDEX_GLOBAL))
    }

    @Test
    fun `cn friend code keeps seven lowercase letters and digits`() {
        assertEquals("ab12cd3", normalizeBaFriendCodeInput(" AB12cd34Z ", BA_SERVER_INDEX_CN))
        assertEquals("ab12cd3", sanitizeBaFriendCode(" AB12cd34Z ", BA_SERVER_INDEX_CN))
        assertEquals("arisuke", sanitizeBaFriendCode("A1B2", BA_SERVER_INDEX_CN))
    }

    @Test
    fun `global and jp friend code keep eight uppercase letters`() {
        assertEquals("ABCDWXYZ", normalizeBaFriendCodeInput(" abcd1234wxyz ", BA_SERVER_INDEX_GLOBAL))
        assertEquals("YUKIARIS", sanitizeBaFriendCode(" yuki-aris ", BA_SERVER_INDEX_JP))
        assertEquals("ARISUKEI", sanitizeBaFriendCode(" yuki0001 ", BA_SERVER_INDEX_JP))
    }

    @Test
    fun `configured detection excludes placeholders and accepts known server formats`() {
        assertFalse(isBaFriendCodeConfigured("ARISUKEI", BA_SERVER_INDEX_GLOBAL))
        assertFalse(isBaFriendCodeConfigured("arisuke", BA_SERVER_INDEX_CN))
        assertFalse(isBaFriendCodeConfigured("ab12cd3", BA_SERVER_INDEX_GLOBAL))
        assertTrue(isBaFriendCodeConfigured("ab12cd3", BA_SERVER_INDEX_CN))
        assertTrue(isBaFriendCodeConfigured("GLOBALAB", BA_SERVER_INDEX_GLOBAL))
        assertTrue(isBaFriendCodeConfigured("YUKIARIS", BA_SERVER_INDEX_JP))
        assertFalse(isBaFriendCodeConfigured("GL12CD34", BA_SERVER_INDEX_GLOBAL))
        assertFalse(isBaFriendCodeConfigured("ARISUKEI"))
        assertFalse(isBaFriendCodeConfigured("arisuke"))
        assertFalse(isBaFriendCodeConfigured("arisukei"))
        assertFalse(isBaFriendCodeConfigured(""))
        assertFalse(isBaFriendCodeConfigured("A1B2"))
        assertTrue(isBaFriendCodeConfigured("ab12cd3"))
        assertTrue(isBaFriendCodeConfigured("GLOBALAB"))
    }
}
