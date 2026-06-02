package os.kei.ui.page.main.github

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.R
import os.kei.feature.github.domain.GitHubRepositoryDiscoveryHttpException
import os.kei.feature.github.model.GitHubTrackedReleaseStatus
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

    @Test
    fun `release rate limit http message maps to stable page copy`() {
        val message = localizedGitHubPageErrorMessage(
            context = context,
            rawMessage = "GitHub release request failed (HTTP 403, API rate limit exceeded)",
        )

        assertEquals(
            context.getString(R.string.github_error_github_api_rate_limited),
            message,
        )
    }

    @Test
    fun `share import fallback uses page http localization`() {
        val message = localizedGitHubShareImportErrorMessage(
            context = context,
            rawMessage = "GitHub release request failed (HTTP 403, API rate limit exceeded)",
        )

        assertEquals(
            context.getString(R.string.github_error_github_api_rate_limited),
            message,
        )
    }

    @Test
    fun `failed release status localizes wrapped http detail`() {
        val rawStatus =
            GitHubTrackedReleaseStatus.Failed.failureMessage(
                "GitHub release request failed (HTTP 403, API rate limit exceeded)",
            )
        val message = localizedGitHubTrackedReleaseStatusMessage(context, rawStatus)

        assertEquals(
            context.getString(
                R.string.github_error_check_failed_detail,
                context.getString(R.string.github_error_github_api_rate_limited),
            ),
            message,
        )
    }

    @Test
    fun `direct apk http message maps to stable page copy`() {
        val message = localizedGitHubPageErrorMessage(
            context = context,
            rawMessage = "direct APK directory index failed (HTTP 404)",
        )

        assertEquals(
            context.getString(R.string.github_error_direct_apk_http_failed, 404),
            message,
        )
    }

    @Test
    fun `no apk asset message maps to shareable no apk copy`() {
        val message = localizedGitHubPageErrorMessage(
            context = context,
            rawMessage = "The latest stable release contains no APK asset",
        )

        assertEquals(
            context.getString(R.string.github_share_import_error_no_usable_apk),
            message,
        )
    }
}
