package os.kei.ui.page.main.github

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryHttpException
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class GitHubRepositoryDiscoveryErrorLocalizationTest {
    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `rate limited discovery http exception maps to short token guidance`() {
        val message = localizedGitHubRepositoryDiscoveryErrorMessage(
            context = context,
            error = GitHubRepositoryDiscoveryHttpException(
                statusCode = 403,
                responseMessage = "API rate limit exceeded",
            ),
        )

        assertEquals(
            context.getString(R.string.github_error_repo_discovery_rate_limited),
            message,
        )
    }

    @Test
    fun `legacy raw discovery http message maps to short token guidance`() {
        val message = localizedGitHubRepositoryDiscoveryErrorMessage(
            context = context,
            error = IllegalStateException(
                "GitHub discovery request failed: HTTP 403 API rate limit exceeded",
            ),
        )

        assertEquals(
            context.getString(R.string.github_error_repo_discovery_rate_limited),
            message,
        )
    }
}
