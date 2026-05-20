package os.kei.feature.github.install

import android.content.Context
import android.content.pm.PackageInstaller
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.feature.github.data.remote.GitHubReleaseAssetFile
import os.kei.feature.github.data.remote.isGitHubActionsApkArtifactArchive
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

internal data class GitHubInstallSessionWriteResult(
    val bytesWritten: Long,
    val totalBytes: Long,
    val archiveInfo: GitHubApkArchiveInfo = GitHubApkArchiveInfo(),
)

internal class GitHubInstallSessionWriter(
    private val client: OkHttpClient,
) {
    suspend fun streamApkIntoSession(
        context: Context,
        resolvedUrl: String,
        asset: GitHubReleaseAssetFile,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ): GitHubInstallSessionWriteResult =
        if (asset.isGitHubActionsApkArtifactArchive()) {
            streamActionsApkArchiveIntoSession(
                context = context,
                resolvedUrl = resolvedUrl,
                assetName = asset.name,
                declaredSizeBytes = asset.sizeBytes,
                session = session,
                sessionId = sessionId,
                onProgress = onProgress,
            )
        } else {
            streamDirectApkIntoSession(
                context = context,
                resolvedUrl = resolvedUrl,
                asset = asset,
                session = session,
                sessionId = sessionId,
                onProgress = onProgress,
            )
        }

    private suspend fun streamDirectApkIntoSession(
        context: Context,
        resolvedUrl: String,
        asset: GitHubReleaseAssetFile,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ): GitHubInstallSessionWriteResult {
        val request =
            Request
                .Builder()
                .url(resolvedUrl)
                .header("User-Agent", "KeiOS-App/1.0 (Android)")
                .header(
                    "Accept",
                    "application/vnd.android.package-archive, application/octet-stream;q=0.9, */*;q=0.1",
                ).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body
            val totalBytes =
                when {
                    body.contentLength() > 0L -> body.contentLength()
                    asset.sizeBytes > 0L -> asset.sizeBytes
                    else -> -1L
                }
            val fileName = asset.name.trim().ifBlank { "base.apk" }
            val tempApkFile = createGitHubTempApkFile(context, asset.name)
            try {
                val progress =
                    GitHubInstallProgressEmitter(
                        sessionId = sessionId,
                        totalBytes = totalBytes,
                        onProgress = onProgress,
                    )
                progress.emit(force = true)
                body.byteStream().use { input ->
                    session.openWrite(fileName, 0, totalBytes).use { output ->
                        FileOutputStream(tempApkFile).use { archiveOutput ->
                            val buffer = ByteArray(GITHUB_APK_STREAM_BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                archiveOutput.write(buffer, 0, read)
                                progress.add(read.toLong())
                                progress.emit()
                            }
                            archiveOutput.flush()
                        }
                        progress.emit(force = true)
                        val archiveInfo = readGitHubApkArchiveInfo(context, tempApkFile)
                        emitStagingProgress(
                            sessionId = sessionId,
                            totalRead = progress.totalRead,
                            totalBytes = totalBytes,
                            archiveInfo = archiveInfo,
                            onProgress = onProgress,
                        )
                        session.fsync(output)
                        return GitHubInstallSessionWriteResult(
                            bytesWritten = progress.totalRead,
                            totalBytes = totalBytes,
                            archiveInfo = archiveInfo,
                        )
                    }
                }
            } finally {
                runCatching { tempApkFile.delete() }
            }
        }
    }

    private suspend fun streamActionsApkArchiveIntoSession(
        context: Context,
        resolvedUrl: String,
        assetName: String,
        declaredSizeBytes: Long,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ): GitHubInstallSessionWriteResult {
        val archiveFile = createGitHubTempInstallFile(context, assetName, ".zip")
        try {
            downloadToTempFile(
                resolvedUrl = resolvedUrl,
                declaredSizeBytes = declaredSizeBytes,
                outputFile = archiveFile,
                sessionId = sessionId,
                onProgress = onProgress,
            )
            ZipFile(archiveFile).use { zipFile ->
                if (zipFile.isGitHubDirectApkArchive()) {
                    return streamDownloadedApkFileIntoSession(
                        context = context,
                        apkFile = archiveFile,
                        sessionName = assetName.toGitHubApkSessionName(),
                        session = session,
                        sessionId = sessionId,
                        onProgress = onProgress,
                    )
                }
                val apkEntry =
                    selectGitHubInstallApkEntry(zipFile)
                        ?: throw IOException("Actions artifact contains no installable APK")
                return streamZipEntryIntoSession(
                    context = context,
                    zipFile = zipFile,
                    apkEntry = apkEntry,
                    session = session,
                    sessionId = sessionId,
                    onProgress = onProgress,
                )
            }
        } finally {
            runCatching { archiveFile.delete() }
        }
    }

    private suspend fun streamZipEntryIntoSession(
        context: Context,
        zipFile: ZipFile,
        apkEntry: ZipEntry,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ): GitHubInstallSessionWriteResult {
        val entrySize = apkEntry.size.takeIf { it > 0L } ?: -1L
        val sessionName = apkEntry.name.substringAfterLast('/').ifBlank { "base.apk" }
        val progress =
            GitHubInstallProgressEmitter(
                sessionId = sessionId,
                totalBytes = entrySize,
                onProgress = onProgress,
            )
        progress.emit(force = true)
        val tempApkFile = createGitHubTempApkFile(context, sessionName)
        try {
            zipFile.getInputStream(apkEntry).use { input ->
                session.openWrite(sessionName, 0, entrySize).use { output ->
                    FileOutputStream(tempApkFile).use { archiveOutput ->
                        val buffer = ByteArray(GITHUB_APK_STREAM_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            archiveOutput.write(buffer, 0, read)
                            progress.add(read.toLong())
                            progress.emit()
                        }
                        archiveOutput.flush()
                    }
                    progress.emit(force = true)
                    val archiveInfo = readGitHubApkArchiveInfo(context, tempApkFile)
                    emitStagingProgress(
                        sessionId = sessionId,
                        totalRead = progress.totalRead,
                        totalBytes = entrySize,
                        archiveInfo = archiveInfo,
                        onProgress = onProgress,
                    )
                    session.fsync(output)
                    return GitHubInstallSessionWriteResult(progress.totalRead, entrySize, archiveInfo)
                }
            }
        } finally {
            runCatching { tempApkFile.delete() }
        }
    }

    private suspend fun streamDownloadedApkFileIntoSession(
        context: Context,
        apkFile: File,
        sessionName: String,
        session: PackageInstaller.Session,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ): GitHubInstallSessionWriteResult {
        val totalBytes = apkFile.length().takeIf { it > 0L } ?: -1L
        val progress =
            GitHubInstallProgressEmitter(
                sessionId = sessionId,
                totalBytes = totalBytes,
                onProgress = onProgress,
            )
        progress.emit(force = true)
        FileInputStream(apkFile).use { input ->
            session.openWrite(sessionName, 0, totalBytes).use { output ->
                val buffer = ByteArray(GITHUB_APK_STREAM_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    progress.add(read.toLong())
                    progress.emit()
                }
                progress.emit(force = true)
                val archiveInfo = readGitHubApkArchiveInfo(context, apkFile)
                emitStagingProgress(
                    sessionId = sessionId,
                    totalRead = progress.totalRead,
                    totalBytes = totalBytes,
                    archiveInfo = archiveInfo,
                    onProgress = onProgress,
                )
                session.fsync(output)
                return GitHubInstallSessionWriteResult(progress.totalRead, totalBytes, archiveInfo)
            }
        }
    }

    private suspend fun downloadToTempFile(
        resolvedUrl: String,
        declaredSizeBytes: Long,
        outputFile: File,
        sessionId: Int,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ) {
        val request =
            Request
                .Builder()
                .url(resolvedUrl)
                .header("User-Agent", "KeiOS-App/1.0 (Android)")
                .header("Accept", "application/zip, application/octet-stream;q=0.9, */*;q=0.1")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            val body = response.body
            val totalBytes =
                when {
                    body.contentLength() > 0L -> body.contentLength()
                    declaredSizeBytes > 0L -> declaredSizeBytes
                    else -> -1L
                }
            val progress =
                GitHubInstallProgressEmitter(
                    sessionId = sessionId,
                    totalBytes = totalBytes,
                    onProgress = onProgress,
                )
            progress.emit(force = true)
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(GITHUB_APK_STREAM_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        progress.add(read.toLong())
                        progress.emit()
                    }
                    output.flush()
                }
            }
            progress.emit(force = true)
        }
    }

    private suspend fun emitStagingProgress(
        sessionId: Int,
        totalRead: Long,
        totalBytes: Long,
        archiveInfo: GitHubApkArchiveInfo,
        onProgress: suspend (GitHubApkInstallProgress) -> Unit,
    ) {
        onProgress(
            GitHubApkInstallProgress(
                stage = GitHubApkInstallStage.Staging,
                progressPercent = 100,
                downloadedBytes = totalRead,
                totalBytes = totalBytes,
                sessionId = sessionId,
                appLabel = archiveInfo.appLabel,
                packageName = archiveInfo.packageName,
                versionName = archiveInfo.versionName,
                versionCode = archiveInfo.versionCode,
                minSdk = archiveInfo.minSdk,
                targetSdk = archiveInfo.targetSdk,
            ),
        )
    }
}
