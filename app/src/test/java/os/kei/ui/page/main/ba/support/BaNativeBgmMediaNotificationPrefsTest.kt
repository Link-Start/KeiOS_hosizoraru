package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaNativeBgmMediaNotificationPrefsTest {
    @Test
    fun `native BGM media notification defaults on and persists`() {
        val store = FakeKeyValueStore()
        val prefs = BaNativeBgmMediaNotificationPrefs(store)

        assertTrue(prefs.loadEnabled())

        prefs.saveEnabled(false)
        assertFalse(prefs.loadEnabled())
        // The key is persisted in MMKV; renaming it would silently reset every user's choice.
        assertEquals(false, store.values["native_bgm_media_notification_enabled"])

        prefs.saveEnabled(true)
        assertTrue(prefs.loadEnabled())
    }

    private class FakeKeyValueStore : BaNativeBgmMediaNotificationKeyValueStore {
        val values = mutableMapOf<String, Boolean>()

        override fun decodeBool(key: String, defaultValue: Boolean): Boolean {
            return values[key] ?: defaultValue
        }

        override fun encode(key: String, value: Boolean) {
            values[key] = value
        }
    }
}
