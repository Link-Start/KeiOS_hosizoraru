package os.kei.ui.page.main.github

import os.kei.feature.github.data.remote.GitHubReleaseAssetBundle
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubActionsArtifact
import os.kei.feature.github.model.GitHubActionsArtifactKind
import os.kei.feature.github.model.GitHubActionsArtifactMatch
import os.kei.feature.github.model.GitHubActionsArtifactNameTraits
import os.kei.feature.github.model.GitHubActionsArtifactPlatform
import os.kei.feature.github.model.GitHubActionsRunArtifacts
import os.kei.feature.github.model.GitHubActionsRunBranchTrust
import os.kei.feature.github.model.GitHubActionsRunMatch
import os.kei.feature.github.model.GitHubActionsRunTraits
import os.kei.feature.github.model.GitHubActionsWorkflowRun
import os.kei.feature.github.model.GitHubReleaseChannel
import os.kei.feature.github.model.GitHubTrackedApp

internal object GitHubEnhancedInfoFixture {
    const val nowMillis: Long = 1_777_000_000_000L

    val trackedItem = GitHubTrackedApp(
        repoUrl = "https://github.com/wxxsfxyzm/InstallerX-Revived",
        owner = "wxxsfxyzm",
        repo = "InstallerX-Revived",
        packageName = "com.wxxsfxyzm.installerx",
        appLabel = "InstallerX"
    )

    val releaseAsset = GitHubReleaseAssetFile(
        name = "InstallerX-Revived-offline-v2.3.2.apk",
        downloadUrl = "https://github.com/wxxsfxyzm/InstallerX-Revived/releases/download/v2.3.2/app.apk",
        apiAssetUrl = "https://api.github.com/repos/wxxsfxyzm/InstallerX-Revived/releases/assets/42",
        sizeBytes = 4_700_000L,
        downloadCount = 120,
        contentType = "application/vnd.android.package-archive",
        updatedAtMillis = nowMillis - 58L * 24L * 60L * 60L * 1000L
    )

    val releaseBundle = GitHubReleaseAssetBundle(
        releaseName = "InstallerX Revived stable v2.3.2",
        tagName = "v2.3.2",
        htmlUrl = "https://github.com/wxxsfxyzm/InstallerX-Revived/releases/tag/v2.3.2",
        releaseUpdatedAtMillis = releaseAsset.updatedAtMillis,
        releaseNotesBody = """
            # v2.3.2
            - Improve installer handoff reliability
            - Refresh APK selection metadata
            - Polish release notes rendering
        """.trimIndent(),
        assets = listOf(releaseAsset),
        showingAllAssets = true,
        shortCommitSha = "abc1234",
        fetchSource = "api",
        sourceConfigSignature = "fixture"
    )

    val versionState = VersionCheckUi(
        localVersion = "2.3.2",
        latestStableName = releaseBundle.releaseName,
        latestStableRawTag = releaseBundle.tagName,
        latestStableUrl = releaseBundle.htmlUrl,
        latestStableUpdatedAtMillis = releaseBundle.releaseUpdatedAtMillis ?: 0L,
        hasUpdate = false,
        releaseHint = "InstallerX 已最新"
    )

    val artifact = GitHubActionsArtifact(
        id = 420L,
        name = "KeiOS-arm64-v8a-release.apk",
        sizeBytes = 8_388_608L,
        expired = false,
        digest = "sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
        archiveDownloadUrl = "https://api.github.com/repos/voyager/KeiOS/actions/artifacts/420/zip",
        workflowRunId = 66L,
        workflowRunHeadBranch = "main",
        workflowRunHeadSha = "1234567890abcdef1234567890abcdef12345678",
        updatedAtMillis = nowMillis - 2L * 60L * 60L * 1000L,
        expiresAtMillis = nowMillis + 88L * 24L * 60L * 60L * 1000L
    )

    val artifactMatch = GitHubActionsArtifactMatch(
        artifact = artifact,
        traits = GitHubActionsArtifactNameTraits(
            normalizedName = artifact.name.lowercase(),
            extension = "apk",
            kind = GitHubActionsArtifactKind.AndroidPackage,
            platform = GitHubActionsArtifactPlatform.Android,
            abi = "arm64-v8a",
            buildTypes = listOf("release"),
            version = "v1.2.4",
            channel = GitHubReleaseChannel.STABLE,
            releaseLike = true
        ),
        score = 94,
        reasons = listOf("android-artifact", "preferred-abi", "release-build")
    )

    val runMatch = GitHubActionsRunMatch(
        runArtifacts = GitHubActionsRunArtifacts(
            run = GitHubActionsWorkflowRun(
                id = 66L,
                displayTitle = "Build Release",
                runNumber = 1024L,
                event = "workflow_dispatch",
                status = "completed",
                conclusion = "success",
                headBranch = "main",
                headSha = artifact.workflowRunHeadSha,
                htmlUrl = "https://github.com/voyager/KeiOS/actions/runs/66",
                updatedAtMillis = artifact.updatedAtMillis
            ),
            artifacts = listOf(artifact)
        ),
        traits = GitHubActionsRunTraits(
            normalizedBranch = "main",
            normalizedEvent = "workflow_dispatch",
            normalizedStatus = "completed",
            normalizedConclusion = "success",
            branchTrust = GitHubActionsRunBranchTrust.DefaultBranch,
            completed = true,
            successful = true,
            defaultBranch = true,
            safeForRecommendation = true
        ),
        artifactMatches = listOf(artifactMatch),
        score = 98,
        reasons = listOf("trusted-run", "android-artifacts")
    )
}
