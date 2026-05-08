package os.kei.feature.github.data.remote

import org.json.JSONArray
import os.kei.feature.github.model.GitHubProfileField
import os.kei.feature.github.model.GitHubRepositoryProfileAvailabilityStatus
import os.kei.feature.github.model.GitHubRepositoryProfileConfidence
import os.kei.feature.github.model.GitHubRepositoryProfileSource
import os.kei.feature.github.model.GitHubRepositoryProfileSourceState
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

internal fun <T> profileField(
    value: T,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<T> {
    return GitHubProfileField(
        value = value,
        source = source,
        fetchedAtMillis = fetchedAtMillis,
        confidence = confidence
    )
}

internal fun stringField(
    value: String,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<String>? {
    return value.trim()
        .takeIf { it.isNotBlank() }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

internal fun intField(
    value: Int,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Int> {
    return profileField(value.coerceAtLeast(0), source, fetchedAtMillis, confidence)
}

internal fun longField(
    value: Long,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Long>? {
    return value
        .takeIf { it > 0L }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

internal fun booleanField(
    value: Boolean,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Boolean> {
    return profileField(value, source, fetchedAtMillis, confidence)
}

internal fun listField(
    value: List<String>,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<List<String>>? {
    return value
        .takeIf { it.isNotEmpty() }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

internal fun loaded(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
        fetchedAtMillis = fetchedAtMillis
    )
}

internal fun skipped(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    message: String = ""
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Skipped,
        fetchedAtMillis = fetchedAtMillis,
        message = message
    )
}

internal fun failed(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    error: Throwable
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Failed,
        fetchedAtMillis = fetchedAtMillis,
        message = error.message.orEmpty().take(180)
    )
}

internal fun JSONArray?.toStringList(): List<String> {
    this ?: return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

internal fun String.parseIsoInstantOrDefault(): Long {
    return runCatching {
        if (isBlank()) -1L else Instant.parse(this).toEpochMilli()
    }.getOrDefault(-1L)
}

internal fun String.androidInstallableAssetLike(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk")
}

internal fun String.androidBundleLike(): Boolean {
    return lowercase(Locale.ROOT).endsWith(".aab")
}

internal fun String.androidBuildArtifactLike(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".apk") ||
            lower.endsWith(".apks") ||
            lower.endsWith(".xapk") ||
            lower.endsWith(".aab") ||
            lower.contains("apk") ||
            lower.contains("android")
}

internal fun String.encodeGitHubPathSegment(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
}
