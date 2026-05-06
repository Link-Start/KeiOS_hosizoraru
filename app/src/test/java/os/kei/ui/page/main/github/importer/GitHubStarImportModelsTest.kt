package os.kei.ui.page.main.github.importer

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubStarImportModelsTest {
    @Test
    fun `username input accepts raw username and github profile links`() {
        assertEquals("hosizoraru", "hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "@hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "https://github.com/hosizoraru".toGitHubUsernameInput())
        assertEquals(
            "hosizoraru",
            "https://github.com/hosizoraru?tab=stars".toGitHubUsernameInput()
        )
        assertEquals("hosizoraru", "github.com/hosizoraru?tab=stars".toGitHubUsernameInput())
    }

    @Test
    fun `username input accepts github stars paths`() {
        assertEquals("hosizoraru", "https://github.com/stars/hosizoraru".toGitHubUsernameInput())
        assertEquals("hosizoraru", "/stars/hosizoraru".toGitHubUsernameInput())
        assertEquals(
            "hosizoraru",
            "/stars/hosizoraru/lists/android-apk".toGitHubUsernameInput()
        )
        assertEquals(
            "hosizoraru",
            "stars/hosizoraru/lists/android-apk".toGitHubUsernameInput()
        )
    }

    @Test
    fun `username input rejects repository links and reserved github routes`() {
        assertEquals("", "https://github.com/hosizoraru/KeiOS".toGitHubUsernameInput())
        assertEquals("", "https://github.com/settings".toGitHubUsernameInput())
        assertEquals("", "https://example.com/hosizoraru".toGitHubUsernameInput())
        assertFalse("https://github.com/hosizoraru/KeiOS".isValidGitHubUsernameInput())
        assertTrue("https://github.com/hosizoraru?tab=stars".isValidGitHubUsernameInput())
    }

}
