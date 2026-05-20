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

fun <T> profileField(
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

fun stringField(
    value: String,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<String>? {
    return value.trim()
        .takeIf { it.isNotBlank() }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

fun intField(
    value: Int,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Int> {
    return profileField(value.coerceAtLeast(0), source, fetchedAtMillis, confidence)
}

fun longField(
    value: Long,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Long>? {
    return value
        .takeIf { it > 0L }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

fun booleanField(
    value: Boolean,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<Boolean> {
    return profileField(value, source, fetchedAtMillis, confidence)
}

fun listField(
    value: List<String>,
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    confidence: GitHubRepositoryProfileConfidence = GitHubRepositoryProfileConfidence.High
): GitHubProfileField<List<String>>? {
    return value
        .takeIf { it.isNotEmpty() }
        ?.let { profileField(it, source, fetchedAtMillis, confidence) }
}

fun loaded(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    elapsedMs: Long = 0L,
    required: Boolean = false,
    fromCache: Boolean = false
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Loaded,
        fetchedAtMillis = fetchedAtMillis,
        elapsedMs = elapsedMs,
        fromCache = fromCache,
        required = required
    )
}

fun skipped(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    message: String = "",
    elapsedMs: Long = 0L,
    required: Boolean = false,
    fromCache: Boolean = false
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Skipped,
        fetchedAtMillis = fetchedAtMillis,
        message = message,
        elapsedMs = elapsedMs,
        fromCache = fromCache,
        required = required
    )
}

fun failed(
    source: GitHubRepositoryProfileSource,
    fetchedAtMillis: Long,
    error: Throwable,
    elapsedMs: Long = 0L,
    required: Boolean = false,
    fromCache: Boolean = false
): GitHubRepositoryProfileSourceState {
    return GitHubRepositoryProfileSourceState(
        source = source,
        status = GitHubRepositoryProfileAvailabilityStatus.Failed,
        fetchedAtMillis = fetchedAtMillis,
        message = error.message.orEmpty().take(180),
        elapsedMs = elapsedMs,
        fromCache = fromCache,
        required = required
    )
}

fun JSONArray?.toStringList(): List<String> {
    this ?: return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

fun String.parseIsoInstantOrDefault(): Long {
    return runCatching {
        if (isBlank()) -1L else Instant.parse(this).toEpochMilli()
    }.getOrDefault(-1L)
}

fun String.androidInstallableAssetLike(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk")
}

fun String.androidBundleLike(): Boolean {
    return lowercase(Locale.ROOT).endsWith(".aab")
}

fun String.androidBuildArtifactLike(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".apk") ||
            lower.endsWith(".apks") ||
            lower.endsWith(".xapk") ||
            lower.endsWith(".aab") ||
            lower.contains("apk") ||
            lower.contains("android")
}

fun String.encodeGitHubPathSegment(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
}
