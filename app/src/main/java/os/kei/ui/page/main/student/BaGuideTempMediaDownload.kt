package os.kei.ui.page.main.student

import os.kei.feature.ba.data.remote.GameKeeNetworkClient
import os.kei.feature.ba.data.remote.GameKeeNetworkResult
import java.io.File

internal object BaGuideTempMediaDownload {
    fun withForceNetworkQuery(
        url: String,
        clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
    ): String {
        val value = url.trim()
        if (value.isBlank()) return value
        if (!value.startsWith("http://") && !value.startsWith("https://")) return value
        val mark = "__keios_force_ts=${clock.nowMs()}"
        val suffix = if (value.contains("?")) "&$mark" else "?$mark"
        return value + suffix
    }

    fun downloadWithValidation(
        normalizedUrl: String,
        targetFile: File,
        forceReDownload: Boolean,
        clock: BaGuideMediaCacheClock = BaGuideSystemMediaCacheClock,
    ): Boolean {
        val strictGif =
            BaGuideTempMediaValidation.looksLikeGifUrl(normalizedUrl) ||
                targetFile.extension.equals("gif", ignoreCase = true)
        val retryCount =
            when {
                strictGif -> 3
                BaGuideTempMediaValidation.looksLikeImageUrl(normalizedUrl) -> 2
                else -> 1
            }
        repeat(retryCount) { attempt ->
            if (targetFile.exists()) {
                runCatching { targetFile.delete() }
            }
            val requestUrl =
                when {
                    forceReDownload -> withForceNetworkQuery(normalizedUrl, clock)
                    attempt > 0 -> withForceNetworkQuery(normalizedUrl, clock)
                    else -> normalizedUrl
                }
            val ok =
                GameKeeNetworkClient.downloadToFile(
                    requestUrl,
                    targetFile,
                ) is GameKeeNetworkResult.Success
            if (ok && BaGuideTempMediaValidation.isUsableCachedMedia(normalizedUrl, targetFile)) {
                return true
            }
            runCatching { targetFile.delete() }
        }
        return false
    }
}
