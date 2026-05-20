package os.kei.feature.github.model

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitHubLookupConfigManagedInstallTest {
    @Test
    fun `app managed share install defaults off`() {
        assertFalse(GitHubLookupConfig().appManagedShareInstallEnabled)
    }

    @Test
    fun `app managed share install is part of copied lookup config`() {
        val config = GitHubLookupConfig().copy(appManagedShareInstallEnabled = true)

        assertTrue(config.appManagedShareInstallEnabled)
    }
}
