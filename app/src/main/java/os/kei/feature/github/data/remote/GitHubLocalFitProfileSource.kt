package os.kei.feature.github.data.remote

import os.kei.feature.github.model.GitHubRepositoryLocalFitProfile
import os.kei.feature.github.model.GitHubRepositoryProfileSource

internal object GitHubLocalFitProfileSource {
    fun build(
        request: GitHubRepositoryProfileRequest,
        fetchedAtMillis: Long
    ): GitHubRepositoryLocalFitProfile {
        val remoteApk = request.preciseStableApkVersion ?: request.precisePreReleaseApkVersion
        val localPackage = request.localPackageName.trim()
        val remotePackage = remoteApk?.packageName.orEmpty().trim()
        val packageMatched = localPackage.isNotBlank() &&
                remotePackage.isNotBlank() &&
                localPackage.equals(remotePackage, ignoreCase = true)
        val packageMismatchKnown =
            localPackage.isNotBlank() && remotePackage.isNotBlank() && !packageMatched
        return GitHubRepositoryLocalFitProfile(
            localPackageName = stringField(
                localPackage,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remotePackageName = stringField(
                remotePackage,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            packageNameMatched = when {
                packageMatched -> booleanField(
                    true,
                    GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis
                )

                packageMismatchKnown -> booleanField(
                    false,
                    GitHubRepositoryProfileSource.LocalInstall,
                    fetchedAtMillis
                )

                else -> null
            },
            localVersionName = stringField(
                request.localVersionName,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remoteVersionName = stringField(
                remoteApk?.versionName.orEmpty(),
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            localVersionCode = longField(
                request.localVersionCode,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            ),
            remoteVersionCode = longField(
                remoteApk?.versionCodeLong ?: -1L,
                GitHubRepositoryProfileSource.LocalInstall,
                fetchedAtMillis
            )
        )
    }
}
