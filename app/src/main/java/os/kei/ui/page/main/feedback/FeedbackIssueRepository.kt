package os.kei.ui.page.main.feedback

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import os.kei.BuildConfig
import os.kei.core.concurrency.AppDispatchers
import os.kei.core.io.SharedHttpClient
import os.kei.core.json.encodeCompact
import os.kei.core.json.optString
import os.kei.core.json.parseJsonObjectOrNull
import os.kei.core.log.AppLogStore
import os.kei.feature.github.domain.GitHubTrackService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ISSUE_API_URL = "https://api.github.com/repos/hosizoraru/KeiOS/issues"

internal class FeedbackIssueRepository(
    private val ioDispatcher: CoroutineDispatcher = AppDispatchers.fileIo,
    private val defaultDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation,
    private val httpClient: OkHttpClient = SharedHttpClient.base,
) {
    private val githubTrackService = GitHubTrackService(ioDispatcher)

    suspend fun loadDraftSnapshot(context: Context): FeedbackIssueDraftSnapshot =
        coroutineScope {
            val appContext = context.applicationContext
            val deviceInfoDeferred = async(ioDispatcher) { loadDeviceInfo(appContext) }
            val logStatsDeferred = async(ioDispatcher) { loadLogStats(appContext) }
            val logPreviewDeferred = async(ioDispatcher) { loadLogPreview(appContext) }
            val apiTokenAvailableDeferred = async(ioDispatcher) { hasGitHubApiToken() }
            val deviceInfo = deviceInfoDeferred.await()
            val logStats = logStatsDeferred.await()
            val logPreview = logPreviewDeferred.await()
            val apiTokenAvailable = apiTokenAvailableDeferred.await()
            val sanitizedLogPreview =
                withContext(defaultDispatcher) {
                    FeedbackIssueMarkdown.redactSensitiveText(logPreview.text)
                }
            FeedbackIssueDraftSnapshot(
                deviceInfo = deviceInfo,
                logStats = logStats,
                logPreview = sanitizedLogPreview,
                logPreviewTruncated = logPreview.truncated,
                apiTokenAvailable = apiTokenAvailable,
            )
        }

    suspend fun loadDeviceInfo(context: Context): FeedbackDeviceInfo =
        withContext(ioDispatcher) {
            val appContext = context.applicationContext
            val packageManager = appContext.packageManager
            val packageInfo =
                runCatching {
                    packageManager.getPackageInfo(
                        appContext.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                }.getOrNull()
            val installSource =
                runCatching {
                    packageManager
                        .getInstallSourceInfo(appContext.packageName)
                        .installingPackageName
                        .orEmpty()
                        .ifBlank { "Unknown" }
                }.getOrDefault("Unknown")
            FeedbackDeviceInfo(
                appVersionName =
                    packageInfo
                        ?.versionName
                        .orEmpty()
                        .ifBlank { BuildConfig.VERSION_NAME },
                appVersionCode = packageInfo?.longVersionCode ?: BuildConfig.VERSION_CODE.toLong(),
                packageName = appContext.packageName,
                buildType = BuildConfig.BUILD_TYPE,
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                abis = Build.SUPPORTED_ABIS.joinToString(separator = ", "),
                installSource = installSource,
            )
        }

    suspend fun loadLogStats(context: Context): AppLogStore.Stats =
        withContext(ioDispatcher) {
            runCatching { AppLogStore.stats(context.applicationContext) }
                .getOrDefault(AppLogStore.Stats.Empty)
        }

    suspend fun loadLogPreview(context: Context): AppLogStore.Preview =
        withContext(ioDispatcher) {
            runCatching { AppLogStore.previewText(context.applicationContext, maxChars = 8_000) }
                .getOrDefault(AppLogStore.Preview(text = "", fileCount = 0, truncated = false))
        }

    suspend fun clearLogs(context: Context): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching { AppLogStore.clear(context.applicationContext) }
        }

    suspend fun exportZip(
        context: Context,
        uri: Uri,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            AppLogStore.exportZipToUri(context.applicationContext, uri)
        }

    suspend fun buildLogExportFileName(): String =
        withContext(defaultDispatcher) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
            "keios-feedback-logs-$stamp.zip"
        }

    suspend fun hasGitHubApiToken(): Boolean =
        githubTrackService.loadApiToken().trim().isNotBlank()

    suspend fun submitIssueViaApi(
        title: String,
        body: String,
    ): FeedbackIssueSubmitResult {
        return withContext(ioDispatcher) {
            val token = githubTrackService.loadApiToken().trim()
            if (token.isBlank()) return@withContext FeedbackIssueSubmitResult.MissingToken
            val payload =
                buildJsonObject {
                    put("title", title.trim())
                    put("body", body.trim())
                    put(
                        "labels",
                        buildJsonArray {
                            add(JsonPrimitive("bug"))
                        },
                    )
                }.encodeCompact()
            val request =
                Request
                    .Builder()
                    .url(ISSUE_API_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer $token")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "KeiOS")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body.string()
                    if (response.isSuccessful) {
                        val issueUrl =
                            responseText.parseJsonObjectOrNull()
                                ?.optString("html_url")
                                .orEmpty()
                                .trim()
                                .ifBlank { "https://github.com/hosizoraru/KeiOS/issues" }
                        FeedbackIssueSubmitResult.Success(issueUrl)
                    } else {
                        val message =
                            runCatching {
                                responseText.parseJsonObjectOrNull()?.optString("message").orEmpty()
                            }.getOrNull().orEmpty().ifBlank { response.message }
                        FeedbackIssueSubmitResult.Failure(
                            statusCode = response.code,
                            message = message.ifBlank { "GitHub API request failed" },
                        )
                    }
                }
            }.getOrElse { error ->
                FeedbackIssueSubmitResult.Failure(
                    statusCode = null,
                    message = error.message ?: error.javaClass.simpleName,
                )
            }
        }
    }
}
