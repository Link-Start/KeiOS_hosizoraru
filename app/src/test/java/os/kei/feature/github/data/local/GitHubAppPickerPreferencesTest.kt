package os.kei.feature.github.data.local

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubAppPickerPreferencesTest {
    @Test
    fun `default preferences hide tracked apps`() {
        val preferences = GitHubAppPickerPreferences()

        assertTrue(preferences.includeUserApps)
        assertFalse(preferences.includeSystemApps)
        assertFalse(preferences.includeTrackedApps)
    }
}
