package os.kei.ui.page.main.github.section

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import os.kei.ui.page.main.github.VersionCheckUi
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(
    application = GitHubTrackedItemsSectionTestApp::class,
    sdk = [35]
)
class GitHubTrackedItemsSectionTest {
    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `local version row is hidden before first real check state`() {
        assertNull(formatLocalVersionText(context, null))
    }

    @Test
    fun `local version row shows uninstalled only after real check state`() {
        assertEquals(
            context.getString(R.string.github_item_value_local_version_uninstalled),
            formatLocalVersionText(context, VersionCheckUi())
        )
    }

    @Test
    fun `local version row formats checked local version with code`() {
        assertEquals(
            "1.2.3 (42)",
            formatLocalVersionText(
                context,
                VersionCheckUi(
                    localVersion = "1.2.3",
                    localVersionCode = 42L
                )
            )
        )
    }
}

class GitHubTrackedItemsSectionTestApp : Application()
