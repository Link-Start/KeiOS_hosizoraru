package os.kei.buildlogic

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.util.Properties

data class AppSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val name: String = "$major.$minor.$patch"

    fun toVersionCode(commitCount: Int): Int =
        (major * 10_000_000) +
            (minor * 100_000) +
            (patch * 1_000) +
            commitCount.coerceIn(0, 999)
}

fun maxSemVer(
    first: AppSemVer,
    second: AppSemVer,
): AppSemVer =
    when {
        first.major != second.major -> if (first.major > second.major) first else second
        first.minor != second.minor -> if (first.minor > second.minor) first else second
        first.patch >= second.patch -> first
        else -> second
    }

fun parseSemVerTagOrNull(raw: String?): AppSemVer? {
    val normalized = raw?.trim().orEmpty()
    val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""").matchEntire(normalized) ?: return null
    val (major, minor, patch) = match.destructured
    return AppSemVer(
        major = major.toInt(),
        minor = minor.toInt(),
        patch = patch.toInt(),
    )
}

data class GitVersionSnapshot(
    val relativeCommitCount: Int,
    val totalCommitCount: Int,
    val shortHash: String,
    val branchName: String,
    val worktreeDirty: Boolean,
    val gitAvailable: Boolean,
)

fun Project.runGitCommandOrNull(vararg args: String): String? =
    runCatching {
        val output =
            providers.exec {
                commandLine("git", *args)
                workingDir = rootDir
                isIgnoreExitValue = true
            }
        val exitCode = output.result.get().exitValue
        val stdout = output.standardOutput.asText.get().trim()
        stdout.takeIf { exitCode == 0 && it.isNotEmpty() }
    }.getOrNull()

fun Project.latestMergedSemVerTagOrNull(): String? =
    runGitCommandOrNull("tag", "--merged", "HEAD", "--sort=-v:refname")
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { parseSemVerTagOrNull(it) != null }

fun Project.gitRelativeCommitCountOrNull(anchorTag: String): Int? =
    runGitCommandOrNull("rev-list", "--count", "$anchorTag..HEAD")?.toIntOrNull()

fun Project.gitTotalCommitCountOrNull(): Int? =
    runGitCommandOrNull("rev-list", "--count", "HEAD")?.toIntOrNull()

fun Project.readLocalPropertyOrNull(key: String): String? {
    val localPropsFile = rootProject.file("local.properties")
    if (!localPropsFile.exists()) return null
    return runCatching {
        val props = Properties()
        localPropsFile.inputStream().use(props::load)
        props.getProperty(key)
    }.getOrNull()
}

fun Project.readGradleOrLocalPropertyOrNull(key: String): String? =
    providers.gradleProperty(key).orNull
        ?: readLocalPropertyOrNull(key)

fun Project.readGradleEnvOrLocalPropertyOrNull(
    key: String,
    envKey: String,
): String? =
    providers.gradleProperty(key).orNull
        ?: providers.environmentVariable(envKey).orNull
        ?: readLocalPropertyOrNull(key)

fun Project.readBooleanPropertyOrNull(key: String): Boolean? =
    providers.gradleProperty(key).orNull?.toBooleanStrictOrNull()
        ?: readLocalPropertyOrNull(key)?.toBooleanStrictOrNull()

fun Project.readBooleanBuildPropertyOrNull(
    key: String,
    envKey: String,
): Boolean? =
    providers.gradleProperty(key).orNull?.toBooleanStrictOrNull()
        ?: providers.environmentVariable(envKey).orNull?.toBooleanStrictOrNull()
        ?: readLocalPropertyOrNull(key)?.toBooleanStrictOrNull()

fun Project.readIntBuildPropertyOrNull(
    key: String,
    envKey: String,
): Int? =
    readGradleEnvOrLocalPropertyOrNull(key, envKey)
        ?.trim()
        ?.toIntOrNull()

fun normalizeGitLabel(
    value: String?,
    fallback: String,
): String =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.replace(Regex("""[^A-Za-z0-9._-]"""), "-")
        ?: fallback

fun normalizeGitHash(value: String?): String = normalizeGitLabel(value, fallback = "local").take(12)

abstract class BuildTimestampValueSource : ValueSource<Long, ValueSourceParameters.None> {
    override fun obtain(): Long = System.currentTimeMillis()
}

data class KeiosVersionMetadata(
    val releaseVersion: AppSemVer,
    val benchmarkVersion: AppSemVer,
    val versionAnchorTag: String,
    val gitVersionSnapshot: GitVersionSnapshot,
    val buildTimestampMillisProvider: Provider<Long>,
    val commitTimestampMillis: Long,
    val releaseVersionName: String,
    val releaseVersionCode: Int,
    val nonReleaseVersionName: String,
    val preReleaseVersionCode: Int,
)

fun Project.resolveKeiosVersionMetadata(releaseTargetVersion: AppSemVer): KeiosVersionMetadata {
    val configuredReleaseVersion =
        parseSemVerTagOrNull(readGradleEnvOrLocalPropertyOrNull("keios.version.name", "KEIOS_VERSION_NAME"))
    val configuredVersionAnchorTag =
        readGradleEnvOrLocalPropertyOrNull("keios.version.anchorTag", "KEIOS_VERSION_ANCHOR_TAG")
    val discoveredVersionAnchorTag = configuredVersionAnchorTag ?: latestMergedSemVerTagOrNull()
    val discoveredReleaseVersion = parseSemVerTagOrNull(discoveredVersionAnchorTag)
    val releaseVersion =
        configuredReleaseVersion
            ?: discoveredReleaseVersion?.let { maxSemVer(it, releaseTargetVersion) }
            ?: releaseTargetVersion
    val benchmarkVersion =
        parseSemVerTagOrNull(readGradleEnvOrLocalPropertyOrNull("keios.nextVersion.name", "KEIOS_NEXT_VERSION_NAME"))
            ?: releaseVersion.copy(patch = releaseVersion.patch + 1)
    val versionAnchorTag = discoveredVersionAnchorTag ?: "v${releaseVersion.name}"
    val gitShortHashValue =
        normalizeGitHash(
            readGradleEnvOrLocalPropertyOrNull("keios.git.shortHash", "KEIOS_GIT_SHORT_HASH")
                ?: runGitCommandOrNull("rev-parse", "--short", "HEAD"),
        )
    val gitBranchNameValue =
        normalizeGitLabel(
            readGradleEnvOrLocalPropertyOrNull("keios.git.branchName", "KEIOS_GIT_BRANCH_NAME")
                ?: runGitCommandOrNull("rev-parse", "--abbrev-ref", "HEAD"),
            fallback = "local",
        )
    val gitDirtyValue = readBooleanBuildPropertyOrNull("keios.git.worktreeDirty", "KEIOS_GIT_WORKTREE_DIRTY") ?: false
    val gitRelativeCommitCount =
        readIntBuildPropertyOrNull("keios.git.relativeCommitCount", "KEIOS_GIT_RELATIVE_COMMIT_COUNT")
            ?: gitRelativeCommitCountOrNull(versionAnchorTag)
            ?: 0
    val gitTotalCommitCount =
        readIntBuildPropertyOrNull("keios.git.totalCommitCount", "KEIOS_GIT_TOTAL_COMMIT_COUNT")
            ?: gitTotalCommitCountOrNull()
            ?: 0
    val gitVersionSnapshot =
        GitVersionSnapshot(
            relativeCommitCount = gitRelativeCommitCount,
            totalCommitCount = gitTotalCommitCount,
            shortHash = gitShortHashValue,
            branchName = gitBranchNameValue,
            worktreeDirty = gitDirtyValue,
            gitAvailable =
                readBooleanBuildPropertyOrNull("keios.git.available", "KEIOS_GIT_AVAILABLE")
                    ?: (gitTotalCommitCount > 0 || gitShortHashValue != "local"),
        )
    val buildTimestampMillisOverride =
        readGradleEnvOrLocalPropertyOrNull("keios.build.timestampMillis", "KEIOS_BUILD_TIMESTAMP_MILLIS")
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    val buildTimestampMillisProvider =
        buildTimestampMillisOverride
            ?.let { providers.provider { it } }
            ?: providers.of(BuildTimestampValueSource::class.java) {}
    val commitTimestampMillis: Long = run {
        val overrideMillis =
            readGradleEnvOrLocalPropertyOrNull("keios.git.commitTimestampMillis", "KEIOS_GIT_COMMIT_TIMESTAMP_MILLIS")
                ?.trim()
                ?.toLongOrNull()
        if (overrideMillis != null && overrideMillis > 0L) return@run overrideMillis

        val commitMillisSec = runGitCommandOrNull("log", "-1", "--format=%ct")?.trim()?.toLongOrNull()
        if (commitMillisSec != null && commitMillisSec > 0L) return@run commitMillisSec * 1000L

        0L
    }
    val releaseVersionName = releaseVersion.name
    val releaseVersionCode = releaseVersion.toVersionCode(commitCount = 999)
    val nonReleaseVersionName =
        "${benchmarkVersion.name}+${gitVersionSnapshot.relativeCommitCount}.g${gitVersionSnapshot.shortHash}"
    val preReleaseVersionCode =
        benchmarkVersion.toVersionCode(
            commitCount = gitVersionSnapshot.relativeCommitCount.coerceIn(0, 998),
        )
    return KeiosVersionMetadata(
        releaseVersion = releaseVersion,
        benchmarkVersion = benchmarkVersion,
        versionAnchorTag = versionAnchorTag,
        gitVersionSnapshot = gitVersionSnapshot,
        buildTimestampMillisProvider = buildTimestampMillisProvider,
        commitTimestampMillis = commitTimestampMillis,
        releaseVersionName = releaseVersionName,
        releaseVersionCode = releaseVersionCode,
        nonReleaseVersionName = nonReleaseVersionName,
        preReleaseVersionCode = preReleaseVersionCode,
    )
}
