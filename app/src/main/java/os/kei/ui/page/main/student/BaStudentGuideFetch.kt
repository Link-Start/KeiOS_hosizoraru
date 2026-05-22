package os.kei.ui.page.main.student

import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import os.kei.feature.ba.data.remote.GameKeeBaContentSource
import os.kei.feature.ba.data.remote.GameKeeBaContentDetail
import os.kei.feature.ba.data.remote.GameKeeRepository
import os.kei.ui.page.main.student.fetch.extractGuideContentIdFromUrl
import os.kei.ui.page.main.student.fetch.extractMeta
import os.kei.ui.page.main.student.fetch.extractStatsFromHtml
import os.kei.ui.page.main.student.fetch.normalizeGuideUrl
import os.kei.ui.page.main.student.fetch.normalizeImageUrl
import os.kei.ui.page.main.student.fetch.parseGuideDetailFromContentJson
import os.kei.ui.page.main.student.fetch.parser.firstImageFromAny
import os.kei.ui.page.main.student.fetch.stripHtml
import kotlin.coroutines.cancellation.CancellationException
import os.kei.core.concurrency.AppDispatchers

private fun fetchGuideInfoByApi(sourceUrl: String): BaStudentGuideInfo {
    val target = normalizeGuideUrl(sourceUrl)
    require(target.isNotBlank()) { "empty url" }
    val contentId = extractGuideContentIdFromUrl(target)
        ?: error("unable to resolve content_id")

    val refererPath = runCatching { target.toUri().path.orEmpty() }
        .getOrDefault("/ba/tj/$contentId.html")
        .ifBlank { "/ba/tj/$contentId.html" }

    val contentDetail = GameKeeRepository.fetchBaContentDetail(
        contentId = contentId,
        refererPath = refererPath
    )
    if (contentDetail.contentSource == GameKeeBaContentSource.Empty) {
        error("empty guide content ${contentDetail.errorSummary}")
    }
    return buildGuideInfoFromContentDetail(
        target = target,
        contentDetail = contentDetail
    )
}

private suspend fun fetchGuideInfoByApiAsync(
    sourceUrl: String,
    networkDispatcher: CoroutineDispatcher,
    parseDispatcher: CoroutineDispatcher
): BaStudentGuideInfo {
    val target = normalizeGuideUrl(sourceUrl)
    require(target.isNotBlank()) { "empty url" }
    val contentId = extractGuideContentIdFromUrl(target)
        ?: error("unable to resolve content_id")
    val refererPath = runCatching { target.toUri().path.orEmpty() }
        .getOrDefault("/ba/tj/$contentId.html")
        .ifBlank { "/ba/tj/$contentId.html" }
    val contentDetail = withContext(networkDispatcher) {
        GameKeeRepository.fetchBaContentDetail(
            contentId = contentId,
            refererPath = refererPath
        )
    }
    if (contentDetail.contentSource == GameKeeBaContentSource.Empty) {
        error("empty guide content ${contentDetail.errorSummary}")
    }
    return withContext(parseDispatcher) {
        buildGuideInfoFromContentDetail(
            target = target,
            contentDetail = contentDetail
        )
    }
}

private fun buildGuideInfoFromContentDetail(
    target: String,
    contentDetail: GameKeeBaContentDetail
): BaStudentGuideInfo {
    val summaryFromApi = stripHtml(contentDetail.summary)
    val detail = parseGuideDetailFromContentJson(contentDetail.resolvedContentJson, target)
    if (!isGuideDetailExtractComplete(detail)) {
        error("incomplete guide content source=${contentDetail.contentSource} ${contentDetail.errorSummary}")
    }
    val imageFromData = firstImageFromAny(
        any = contentDetail.mediaPayload,
        sourceUrl = target
    )
    val imageUrl = detail.imageUrl.ifBlank { imageFromData }
    val stats = detail.stats
    val summary = detail.summary
        .ifBlank { summaryFromApi }
        .ifBlank {
            stats.take(4).joinToString(" · ") { "${it.first}：${it.second}" }
        }
        .ifBlank { "暂无更多参数，可点击来源查看完整图鉴。" }
    val description = summaryFromApi.ifBlank { summary }

    return BaStudentGuideInfo(
        sourceUrl = target,
        title = contentDetail.title,
        subtitle = contentDetail.subtitle,
        description = description,
        imageUrl = imageUrl,
        summary = summary,
        stats = stats,
        skillRows = detail.skillRows,
        profileRows = detail.profileRows,
        galleryItems = detail.galleryItems,
        growthRows = detail.growthRows,
        simulateRows = detail.simulateRows,
        voiceRows = detail.voiceRows,
        voiceCvJp = detail.voiceCvJp,
        voiceCvCn = detail.voiceCvCn,
        voiceCvByLanguage = detail.voiceCvByLanguage,
        voiceLanguageHeaders = detail.voiceLanguageHeaders,
        voiceEntries = detail.voiceEntries,
        tabSkillIconUrl = detail.tabSkillIconUrl,
        tabProfileIconUrl = detail.tabProfileIconUrl,
        tabVoiceIconUrl = detail.tabVoiceIconUrl,
        tabGalleryIconUrl = detail.tabGalleryIconUrl,
        tabSimulateIconUrl = detail.tabSimulateIconUrl,
        syncedAtMs = System.currentTimeMillis()
    )
}

private fun fetchGuideInfoFromHtml(sourceUrl: String): BaStudentGuideInfo {
    val target = normalizeGuideUrl(sourceUrl)
    require(target.isNotBlank()) { "empty url" }
    val html = GameKeeRepository.fetchHtml(
        pathOrUrl = target,
        refererPath = "/ba/"
    )
    if (html.isBlank()) error("empty html")
    return buildGuideInfoFromHtml(target = target, html = html)
}

private suspend fun fetchGuideInfoFromHtmlAsync(
    sourceUrl: String,
    networkDispatcher: CoroutineDispatcher,
    parseDispatcher: CoroutineDispatcher
): BaStudentGuideInfo {
    val target = normalizeGuideUrl(sourceUrl)
    require(target.isNotBlank()) { "empty url" }
    val html = withContext(networkDispatcher) {
        GameKeeRepository.fetchHtml(
            pathOrUrl = target,
            refererPath = "/ba/"
        )
    }
    if (html.isBlank()) error("empty html")
    return withContext(parseDispatcher) {
        buildGuideInfoFromHtml(target = target, html = html)
    }
}

private fun buildGuideInfoFromHtml(
    target: String,
    html: String
): BaStudentGuideInfo {
    val ogTitle = extractMeta(html, "og:title")
    val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        .find(html)
        ?.groupValues
        ?.getOrNull(1)
        .orEmpty()
    val rawTitle = if (ogTitle.isNotBlank()) ogTitle else stripHtml(titleTag)
    val title = rawTitle.ifBlank { "图鉴信息" }

    val siteName = extractMeta(html, "og:site_name").ifBlank { "GameKee" }
    val description = extractMeta(html, "og:description")
        .ifBlank { extractMeta(html, "description") }
        .ifBlank { "未解析到详细描述，可点击下方来源查看完整图鉴。" }
    val imageUrl = normalizeImageUrl(target, extractMeta(html, "og:image"))

    val paragraphRegex = Regex(
        "<p[^>]*>(.*?)</p>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val summary = paragraphRegex.findAll(html)
        .map { stripHtml(it.groupValues[1]) }
        .firstOrNull { it.length >= 12 }
        ?: description

    val stats = extractStatsFromHtml(html)

    return BaStudentGuideInfo(
        sourceUrl = target,
        title = title,
        subtitle = siteName,
        description = description,
        imageUrl = imageUrl,
        summary = summary,
        stats = stats,
        profileRows = stats.map { (k, v) -> BaGuideRow(k, v) },
        syncedAtMs = System.currentTimeMillis()
    )
}

fun fetchGuideInfo(sourceUrl: String): BaStudentGuideInfo {
    val apiResult = runCatching { fetchGuideInfoByApi(sourceUrl) }
    apiResult.getOrNull()?.let { return it }

    val htmlResult = runCatching { fetchGuideInfoFromHtml(sourceUrl) }
    htmlResult.getOrNull()?.takeIf(::isGuideInfoPayloadComplete)?.let { return it }

    val apiError = apiResult.exceptionOrNull()?.guideFetchErrorPreview().orEmpty()
    val htmlError = htmlResult.exceptionOrNull()?.guideFetchErrorPreview().orEmpty()
        .ifBlank { "incomplete html fallback" }
    error("guide fetch failed api=$apiError html=$htmlError")
}

suspend fun fetchGuideInfoAsync(
    sourceUrl: String,
    networkDispatcher: CoroutineDispatcher = AppDispatchers.baFetch,
    parseDispatcher: CoroutineDispatcher = AppDispatchers.uiDerivation
): BaStudentGuideInfo {
    val apiResult = runCatchingCancellable {
        fetchGuideInfoByApiAsync(
            sourceUrl = sourceUrl,
            networkDispatcher = networkDispatcher,
            parseDispatcher = parseDispatcher
        )
    }
    apiResult.getOrNull()?.let { return it }

    val htmlResult = runCatchingCancellable {
        fetchGuideInfoFromHtmlAsync(
            sourceUrl = sourceUrl,
            networkDispatcher = networkDispatcher,
            parseDispatcher = parseDispatcher
        )
    }
    htmlResult.getOrNull()?.takeIf(::isGuideInfoPayloadComplete)?.let { return it }

    val apiError = apiResult.exceptionOrNull()?.guideFetchErrorPreview().orEmpty()
    val htmlError = htmlResult.exceptionOrNull()?.guideFetchErrorPreview().orEmpty()
        .ifBlank { "incomplete html fallback" }
    error("guide fetch failed api=$apiError html=$htmlError")
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

private fun isGuideDetailExtractComplete(detail: os.kei.ui.page.main.student.fetch.GuideDetailExtract): Boolean {
    return detail.stats.isNotEmpty() ||
            detail.skillRows.isNotEmpty() ||
            detail.profileRows.isNotEmpty() ||
            detail.galleryItems.isNotEmpty() ||
            detail.growthRows.isNotEmpty() ||
            detail.simulateRows.isNotEmpty() ||
            detail.voiceRows.isNotEmpty() ||
            detail.voiceEntries.isNotEmpty()
}

private fun Throwable.guideFetchErrorPreview(limit: Int = 180): String {
    val root = generateSequence(this) { it.cause }.last()
    val raw = if (root === this) {
        "${javaClass.simpleName}:${message.orEmpty()}"
    } else {
        "${javaClass.simpleName}:${message.orEmpty()} root=${root.javaClass.simpleName}:${root.message.orEmpty()}"
    }
    val compact = raw
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .trim()
    return if (compact.length <= limit) compact else compact.take(limit) + "..."
}
