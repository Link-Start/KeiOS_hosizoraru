package os.kei.feature.github.domain

import android.os.Build
import os.kei.core.install.LocalApkArchiveInfo
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkTrustReason
import os.kei.feature.github.model.GitHubApkTrustSignal
import os.kei.feature.github.model.GitHubDecisionLevel
import os.kei.feature.github.model.GitHubInstalledPackageInfo
import java.util.Locale

data class ApkTrustEvaluationInput(
    val asset: GitHubReleaseAssetFile,
    val supportedAbis: List<String>,
    val manifestInfo: GitHubApkManifestInfo? = null,
    val installedInfo: GitHubInstalledPackageInfo? = null,
    val expectedPackageName: String = "",
    val localArchiveInfo: LocalApkArchiveInfo? = null,
    val deviceSdkInt: Int = Build.VERSION.SDK_INT
)

object ApkTrustEvaluator {
    fun evaluate(input: ApkTrustEvaluationInput): GitHubApkTrustSignal {
        val reasons = linkedSetOf<GitHubApkTrustReason>()
        var level = GitHubDecisionLevel.Good
        val lowerName = input.asset.name.lowercase(Locale.ROOT)
        val extension = input.asset.name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        when {
            extension in sourceArchiveExtensions || "source" in lowerName -> {
                level = GitHubDecisionLevel.Risk
                reasons += GitHubApkTrustReason.SourceArchive
            }

            extension !in installableExtensions -> {
                level = level.raiseTo(GitHubDecisionLevel.Review)
                reasons += GitHubApkTrustReason.UnknownFormat
            }

            else -> reasons += GitHubApkTrustReason.ApkLike
        }

        evaluateAbi(input, lowerName, reasons).let { level = level.raiseTo(it) }
        evaluatePackageIdentity(input, reasons).let { level = level.raiseTo(it) }
        evaluateVersion(input, reasons).let { level = level.raiseTo(it) }
        evaluateSignature(input, reasons).let { level = level.raiseTo(it) }
        evaluateSdk(input, reasons).let { level = level.raiseTo(it) }
        evaluateBuildType(input, lowerName, reasons).let { level = level.raiseTo(it) }
        evaluatePermissions(input, reasons).let { level = level.raiseTo(it) }
        evaluateExportedComponents(input, reasons).let { level = level.raiseTo(it) }

        return GitHubApkTrustSignal(
            level = level,
            reasons = reasons.sortedBy { it.priority }
        )
    }

    private fun evaluateAbi(
        input: ApkTrustEvaluationInput,
        lowerName: String,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val supported = input.supportedAbis.map { it.lowercase(Locale.ROOT) }
        val manifestAbis =
            input.manifestInfo?.nativeAbis.orEmpty().map { it.lowercase(Locale.ROOT) }
        val archiveAbis =
            input.localArchiveInfo?.nativeAbis.orEmpty().map { it.lowercase(Locale.ROOT) }
        val fileAbis = knownAbis.filter { it in lowerName }
        val candidateAbis = (archiveAbis + manifestAbis + fileAbis).distinct()
        if (candidateAbis.isEmpty()) {
            if ("universal" in lowerName || "fat" in lowerName) {
                reasons += GitHubApkTrustReason.UniversalAsset
            }
            return GitHubDecisionLevel.Good
        }
        return if (candidateAbis.any { it in supported }) {
            reasons += GitHubApkTrustReason.PreferredAbi
            GitHubDecisionLevel.Good
        } else {
            reasons += GitHubApkTrustReason.IncompatibleAbi
            GitHubDecisionLevel.Risk
        }
    }

    private fun evaluatePackageIdentity(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val packageName = input.resolvedPackageName()
        val expected = input.expectedPackageName.trim()
            .ifBlank { input.installedInfo?.packageName.orEmpty() }
        if (packageName.isBlank() || expected.isBlank()) return GitHubDecisionLevel.Good
        return if (packageName.equals(expected, ignoreCase = true)) {
            reasons += GitHubApkTrustReason.PackageMatched
            GitHubDecisionLevel.Good
        } else {
            reasons += GitHubApkTrustReason.PackageMismatch
            GitHubDecisionLevel.Risk
        }
    }

    private fun evaluateVersion(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val installed =
            input.installedInfo?.versionCode?.takeIf { it >= 0L } ?: return GitHubDecisionLevel.Good
        val remote = input.localArchiveInfo?.versionCode?.takeIf { it >= 0L }
            ?: input.manifestInfo?.versionCode?.trim()?.toLongOrNull()
            ?: return GitHubDecisionLevel.Good
        return when {
            remote > installed -> {
                reasons += GitHubApkTrustReason.VersionUpgrade
                GitHubDecisionLevel.Good
            }

            remote < installed -> {
                reasons += GitHubApkTrustReason.VersionDowngrade
                GitHubDecisionLevel.Risk
            }

            else -> GitHubDecisionLevel.Good
        }
    }

    private fun evaluateSignature(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val installedSignatures = input.installedInfo?.signatureSha256.orEmpty().normalizedShaSet()
        if (installedSignatures.isEmpty()) return GitHubDecisionLevel.Good
        val candidateSignatures = buildSet {
            addAll(input.localArchiveInfo?.signatureSha256.orEmpty().normalizedShaSet())
            input.manifestInfo?.signatureInfo?.sha256?.normalizeSha()?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }
        if (candidateSignatures.isEmpty()) {
            reasons += GitHubApkTrustReason.SignatureUnknown
            return GitHubDecisionLevel.Review
        }
        return if (candidateSignatures.any { it in installedSignatures }) {
            reasons += GitHubApkTrustReason.SignatureMatched
            GitHubDecisionLevel.Good
        } else {
            reasons += GitHubApkTrustReason.SignatureMismatch
            GitHubDecisionLevel.Risk
        }
    }

    private fun evaluateSdk(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val minSdk = input.localArchiveInfo?.minSdk?.takeIf { it >= 0 }
            ?: input.manifestInfo?.minSdk?.trim()?.toIntOrNull()
            ?: return GitHubDecisionLevel.Good
        return if (minSdk > input.deviceSdkInt) {
            reasons += GitHubApkTrustReason.MinSdkTooHigh
            GitHubDecisionLevel.Risk
        } else {
            GitHubDecisionLevel.Good
        }
    }

    private fun evaluateBuildType(
        input: ApkTrustEvaluationInput,
        lowerName: String,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        var level = GitHubDecisionLevel.Good
        if ("unsigned" in lowerName) {
            reasons += GitHubApkTrustReason.UnsignedBuild
            level = GitHubDecisionLevel.Risk
        }
        val debugByName = debugNameMarkers.any { it in lowerName }
        val debugByArchive = input.localArchiveInfo?.debuggable == true ||
                input.manifestInfo?.manifestNodes.orEmpty().any { node ->
                    node.tagName == "application" && node.attributes["debuggable"] == "true"
                }
        if (debugByName || debugByArchive) {
            reasons += GitHubApkTrustReason.DebugBuild
            level = level.raiseTo(GitHubDecisionLevel.Review)
        }
        val testOnly = input.localArchiveInfo?.testOnly == true ||
                input.manifestInfo?.manifestNodes.orEmpty().any { node ->
                    node.attributes["testOnly"] == "true"
                }
        if (testOnly) {
            reasons += GitHubApkTrustReason.TestOnly
            level = level.raiseTo(GitHubDecisionLevel.Review)
        }
        return level
    }

    private fun evaluatePermissions(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val hasSensitivePermission = input.manifestInfo?.permissions.orEmpty()
            .any { permission -> permission in sensitivePermissions }
        if (!hasSensitivePermission) return GitHubDecisionLevel.Good
        reasons += GitHubApkTrustReason.SensitivePermission
        return GitHubDecisionLevel.Review
    }

    private fun evaluateExportedComponents(
        input: ApkTrustEvaluationInput,
        reasons: MutableSet<GitHubApkTrustReason>
    ): GitHubDecisionLevel {
        val hasRiskyExport = input.manifestInfo?.manifestNodes.orEmpty().any { node ->
            node.tagName in componentTags &&
                    node.attributes["exported"] == "true" &&
                    node.attributes["permission"].isNullOrBlank()
        }
        if (!hasRiskyExport) return GitHubDecisionLevel.Good
        reasons += GitHubApkTrustReason.ExportedComponent
        return GitHubDecisionLevel.Review
    }

    private fun ApkTrustEvaluationInput.resolvedPackageName(): String {
        return localArchiveInfo?.packageName.orEmpty()
            .ifBlank { manifestInfo?.packageName.orEmpty() }
    }

    private fun GitHubDecisionLevel.raiseTo(other: GitHubDecisionLevel): GitHubDecisionLevel {
        return if (other.severity > severity) other else this
    }

    private val GitHubDecisionLevel.severity: Int
        get() = when (this) {
            GitHubDecisionLevel.Good -> 0
            GitHubDecisionLevel.Review -> 1
            GitHubDecisionLevel.Risk -> 2
        }

    private val GitHubApkTrustReason.priority: Int
        get() = when (this) {
            GitHubApkTrustReason.PackageMismatch -> 0
            GitHubApkTrustReason.SignatureMismatch -> 1
            GitHubApkTrustReason.VersionDowngrade -> 2
            GitHubApkTrustReason.MinSdkTooHigh -> 3
            GitHubApkTrustReason.IncompatibleAbi -> 4
            GitHubApkTrustReason.UnsignedBuild -> 5
            GitHubApkTrustReason.DebugBuild -> 6
            GitHubApkTrustReason.TestOnly -> 7
            GitHubApkTrustReason.SensitivePermission -> 8
            GitHubApkTrustReason.ExportedComponent -> 9
            GitHubApkTrustReason.SignatureUnknown -> 10
            GitHubApkTrustReason.SourceArchive -> 11
            GitHubApkTrustReason.UnknownFormat -> 12
            GitHubApkTrustReason.PackageMatched -> 13
            GitHubApkTrustReason.SignatureMatched -> 14
            GitHubApkTrustReason.VersionUpgrade -> 15
            GitHubApkTrustReason.PreferredAbi -> 16
            GitHubApkTrustReason.UniversalAsset -> 17
            GitHubApkTrustReason.ApkLike -> 18
        }

    private val sourceArchiveExtensions = setOf("zip", "tar", "gz", "tgz")
    private val installableExtensions = setOf("apk", "apks", "xapk")
    private val knownAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    private val debugNameMarkers = setOf("debug", "snapshot", "dev", "canary")
    private val componentTags = setOf("activity", "service", "receiver", "provider")
    private val sensitivePermissions = setOf(
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.PACKAGE_USAGE_STATS"
    )
}

private fun List<String>.normalizedShaSet(): Set<String> {
    return mapNotNull { value ->
        value.normalizeSha().takeIf { it.isNotBlank() }
    }.toSet()
}

private fun String.normalizeSha(): String {
    return trim()
        .replace(":", "")
        .lowercase(Locale.ROOT)
}
