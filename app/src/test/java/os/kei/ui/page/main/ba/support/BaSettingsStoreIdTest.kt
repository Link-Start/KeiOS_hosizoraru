package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaSettingsStoreIdTest {
    @Test
    fun sharedIdModeKeepsTheSameNicknameAndFriendCodeAcrossServers() {
        val accessor = BaIdSettingsAccessor(InMemoryBaIdKeyValueStore())
        accessor.saveIndependentByServerEnabled(false)
        accessor.saveNickname("Shared")
        accessor.saveFriendCode("ABCDEFGH")

        assertFalse(accessor.loadIndependentByServerEnabled())
        assertEquals("Shared", accessor.loadNickname(0))
        assertEquals("Shared", accessor.loadNickname(1))
        assertEquals("ABCDEFGH", accessor.loadFriendCode(0))
        assertEquals("ABCDEFGH", accessor.loadFriendCode(1))
    }

    @Test
    fun serverSpecificIdModeKeepsNicknameAndFriendCodePerServer() {
        val accessor = BaIdSettingsAccessor(InMemoryBaIdKeyValueStore())
        accessor.saveNickname("Shared")
        accessor.saveFriendCode("ABCDEFGH")
        accessor.saveIndependentByServerEnabled(true)

        accessor.saveNickname("JP", serverIndex = 2)
        accessor.saveFriendCode("JPFriend", serverIndex = 2)
        accessor.saveNickname("Global", serverIndex = 1)
        accessor.saveFriendCode("GLFriend", serverIndex = 1)

        assertTrue(accessor.loadIndependentByServerEnabled())
        assertEquals("JP", accessor.loadNickname(2))
        assertEquals("JPFRIEND", accessor.loadFriendCode(2))
        assertEquals("Global", accessor.loadNickname(1))
        assertEquals("GLFRIEND", accessor.loadFriendCode(1))
        assertEquals("Shared", accessor.loadNickname())
        assertEquals("ABCDEFGH", accessor.loadFriendCode())
    }

    @Test
    fun serverSpecificIdModeFallsBackToSharedIdWhenServerValueIsEmpty() {
        val accessor = BaIdSettingsAccessor(InMemoryBaIdKeyValueStore())
        accessor.saveNickname("Shared")
        accessor.saveFriendCode("ABCDEFGH")
        accessor.saveIndependentByServerEnabled(true)

        assertEquals("Shared", accessor.loadNickname(0))
        assertEquals("ABCDEFGH", accessor.loadFriendCode(0))
    }

    private class InMemoryBaIdKeyValueStore : BaIdKeyValueStore {
        private val values = mutableMapOf<String, Any>()

        override fun decodeBool(key: String, defaultValue: Boolean): Boolean {
            return values[key] as? Boolean ?: defaultValue
        }

        override fun encode(key: String, value: Boolean) {
            values[key] = value
        }

        override fun decodeString(key: String, defaultValue: String): String? {
            return values[key] as? String ?: defaultValue
        }

        override fun encode(key: String, value: String) {
            values[key] = value
        }
    }
}
