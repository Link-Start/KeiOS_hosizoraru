package os.kei.ui.page.main.github.install

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import os.kei.ui.page.main.github.page.GitHubApkInfoDetailRequest
import os.kei.ui.page.main.github.page.GitHubManagedInstallConfirmRequest
import os.kei.ui.page.main.github.page.githubManagedInstallKey

/**
 * What one surface knows about the APK files it shows: the manifests it has inspected, what is installed
 * under each, and which of the two sheets a reader has open.
 *
 * Each surface owns one — the GitHub page, and each history page — since what a page has looked at is
 * that page's business. Whether a file is *being installed* is not, so [managedInstallLoading] is one set
 * for the whole process: an install confirmed on the release list is still running when the reader goes
 * back to the tracked card, and both have to say so.
 */
@Stable
internal class GitHubApkInstallState {
    var apkInfoDetailRequest by mutableStateOf<GitHubApkInfoDetailRequest?>(null)
    var managedInstallConfirmRequest by mutableStateOf<GitHubManagedInstallConfirmRequest?>(null)
    val apkInfoResults = mutableStateMapOf<String, GitHubApkManifestInfo>()
    val apkInfoInstalledResults = mutableStateMapOf<String, GitHubInstalledPackageInfo?>()
    val apkInfoLoading = mutableStateMapOf<String, Boolean>()
    val apkInfoErrors = mutableStateMapOf<String, String>()

    /** Keyed by [githubManagedInstallKey]. */
    val managedInstallLoading: SnapshotStateMap<String, Boolean> get() = GitHubManagedInstallRuns.running

    fun clearApkInfo() {
        apkInfoLoading.clear()
        apkInfoErrors.clear()
        apkInfoResults.clear()
        apkInfoInstalledResults.clear()
    }
}

/** The managed installs running in this process, whichever page started them. */
internal object GitHubManagedInstallRuns {
    val running = mutableStateMapOf<String, Boolean>()
}
