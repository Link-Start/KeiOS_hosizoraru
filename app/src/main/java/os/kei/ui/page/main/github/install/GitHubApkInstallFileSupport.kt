package os.kei.ui.page.main.github.install

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.download.AppPrivateDownloadManager
import os.kei.core.intent.SafeExternalIntents
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.seconds

internal data class GitHubApkInstallDownloadedFile(
    val file: File,
    val name: String,
    val sizeBytes: Long
)

internal class GitHubApkInstallFileDownloader(
    private val client: OkHttpClient = defaultClient
) {
    suspend fun download(
        context: Context,
        url: String,
        fileName: String,
        onProgress: suspend (Long, Long) -> Unit
    ): GitHubApkInstallDownloadedFile = withContext(Dispatchers.IO) {
        val safeUrl = SafeExternalIntents.httpsExternalUrlOrNull(url)
            ?: error("download url must be https")
        val safeName = AppPrivateDownloadManager.sanitizeDownloadFileName(fileName)
        val targetDir = File(context.cacheDir, "github_apk_install").apply { mkdirs() }
        pruneInstallCache(targetDir)
        val target = File(targetDir, "${System.currentTimeMillis()}-$safeName")
        val request = Request.Builder()
            .url(safeUrl)
            .header("User-Agent", "KeiOS")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }
                val body = response.body
                val totalBytes = body.contentLength().coerceAtLeast(0L)
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(copied, totalBytes)
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            runCatching { target.delete() }
            throw error
        } catch (error: Throwable) {
            runCatching { target.delete() }
            throw error
        }
        GitHubApkInstallDownloadedFile(
            file = target,
            name = safeName,
            sizeBytes = target.length()
        )
    }

    private companion object {
        const val CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20.seconds)
                .readTimeout(120.seconds)
                .build()
        }

        fun pruneInstallCache(targetDir: File) {
            val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
            targetDir.listFiles()
                .orEmpty()
                .filter { file -> file.lastModified() in 1 until cutoff }
                .forEach { file -> runCatching { file.deleteRecursively() } }
        }
    }
}

internal class GitHubApkInstallArchiveExtractor {
    suspend fun extractCandidates(
        context: Context,
        downloaded: GitHubApkInstallDownloadedFile
    ): List<GitHubApkInstallDownloadedFile> = withContext(Dispatchers.IO) {
        if (!downloaded.name.endsWith(".zip", ignoreCase = true)) {
            return@withContext listOf(downloaded)
        }
        var targetDir: File? = null
        val candidates = mutableListOf<GitHubApkInstallDownloadedFile>()
        try {
            ZipInputStream(downloaded.file.inputStream().buffered()).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val entryName = entry.name.substringAfterLast('/').trim()
                    if (!entry.isDirectory && entryName.endsWith(".apk", ignoreCase = true)) {
                        val safeName = AppPrivateDownloadManager.sanitizeDownloadFileName(entryName)
                        val dir = targetDir ?: File(
                            context.cacheDir,
                            "github_apk_install/extracted-${System.currentTimeMillis()}"
                        ).apply {
                            mkdirs()
                            targetDir = this
                        }
                        val target = File(dir, safeName)
                        target.outputStream().use { output -> zip.copyTo(output) }
                        candidates += GitHubApkInstallDownloadedFile(
                            file = target,
                            name = safeName,
                            sizeBytes = target.length()
                        )
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: CancellationException) {
            runCatching { targetDir?.deleteRecursively() }
            throw error
        } catch (error: Throwable) {
            runCatching { targetDir?.deleteRecursively() }
            throw error
        }
        candidates
    }
}
