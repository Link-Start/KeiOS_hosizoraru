package os.kei.ui.page.main.ba.support

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaNativeBgmMediaNotificationPrefsTest {
    @Test
    fun `native BGM media notification defaults off and persists`() {
        val store = FakeKeyValueStore()
        val prefs = BaNativeBgmMediaNotificationPrefs(store)

        assertFalse(prefs.loadEnabled())

        prefs.saveEnabled(true)
        assertTrue(prefs.loadEnabled())

        prefs.saveEnabled(false)
        assertFalse(prefs.loadEnabled())
    }

    @Test
    fun `native BGM media notification uses stable preference key`() {
        val store = FakeKeyValueStore()
        val prefs = BaNativeBgmMediaNotificationPrefs(store)

        prefs.saveEnabled(true)

        assertTrue(store.values[BA_NATIVE_BGM_MEDIA_NOTIFICATION_KEY] == true)
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
