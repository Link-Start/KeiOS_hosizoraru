package os.kei.ui.page.main.student

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class GuideMediaImageLoader(
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val repository: GuideMediaImageRepository = GuideMediaImageRepository(),
) {
    private companion object {
        const val MEDIA_IMAGE_STATE_LIMIT = 192
        const val MEDIA_GIF_TARGET_STATE_LIMIT = 96
        const val MEDIA_MISSING_STATE_LIMIT = 256
    }

    private var mediaImageLoadingKeys: Set<String> = emptySet()
    private var mediaGifTargetLoadingKeys: Set<String> = emptySet()
    private val mutableState = MutableStateFlow(GuideMediaImageUiState())
    val state: StateFlow<GuideMediaImageUiState> = mutableState.asStateFlow()

    fun requestImages(requests: List<GuideMediaImageRequest>) {
        val normalizedRequests =
            requests
                .mapNotNull { request ->
                    val source = normalizeGuideMediaSource(request.source)
                    if (source.isBlank() || isGifMediaSource(source)) {
                        null
                    } else {
                        GuideMediaImageRequest(
                            source = source,
                            maxDecodeDimension = request.maxDecodeDimension,
                            forceReload = request.forceReload,
                        )
                    }
                }.groupBy { request ->
                    guideMediaImageKey(request.source, request.maxDecodeDimension)
                }.map { (_, groupedRequests) ->
                    val first = groupedRequests.first()
                    first.copy(forceReload = groupedRequests.any { it.forceReload })
                }
        if (normalizedRequests.isEmpty()) return

        val currentState = mutableState.value
        val cachedBitmaps =
            normalizedRequests
                .mapNotNull { request ->
                    val key = guideMediaImageKey(request.source, request.maxDecodeDimension)
                    if (request.forceReload) return@mapNotNull null
                    if (currentState.bitmaps.containsKey(key)) return@mapNotNull null
                    repository.cachedBitmap(request)?.let { bitmap -> key to bitmap }
                }.toMap()
        if (cachedBitmaps.isNotEmpty()) {
            mutableState.update { state ->
                state.copy(
                    bitmaps =
                        mergeLimitedMap(
                            current = state.bitmaps,
                            added = cachedBitmaps,
                            limit = MEDIA_IMAGE_STATE_LIMIT,
                        ),
                )
            }
        }

        val missingRequests =
            normalizedRequests.filter { request ->
                val key = guideMediaImageKey(request.source, request.maxDecodeDimension)
                if (mediaImageLoadingKeys.contains(key)) {
                    false
                } else if (request.forceReload) {
                    true
                } else {
                    !currentState.bitmaps.containsKey(key) &&
                        !currentState.missingKeys.contains(key) &&
                        !cachedBitmaps.containsKey(key)
                }
            }
        if (missingRequests.isEmpty()) return

        val loadingKeys =
            missingRequests
                .map { request -> guideMediaImageKey(request.source, request.maxDecodeDimension) }
                .toSet()
        mediaImageLoadingKeys += loadingKeys
        scope.launch {
            try {
                val result =
                    repository.loadImages(
                        context = appContext,
                        requests = missingRequests,
                    )
                mutableState.update { state ->
                    state.copy(
                        bitmaps =
                            mergeLimitedMap(
                                current = state.bitmaps,
                                added = result.bitmaps,
                                limit = MEDIA_IMAGE_STATE_LIMIT,
                            ),
                        missingKeys =
                            mergeLimitedSet(
                                current = state.missingKeys,
                                added = result.missingKeys,
                                limit = MEDIA_MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.update { state ->
                    state.copy(
                        missingKeys =
                            mergeLimitedSet(
                                current = state.missingKeys,
                                added = loadingKeys,
                                limit = MEDIA_MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } finally {
                mediaImageLoadingKeys -= loadingKeys
            }
        }
    }

    fun requestGifTargets(rawTargets: List<String>) {
        val normalizedTargets =
            rawTargets
                .map { raw -> normalizeGuideMediaSource(raw) }
                .filter { target -> target.isNotBlank() && isGifMediaSource(target) }
                .distinct()
        if (normalizedTargets.isEmpty()) return

        val currentState = mutableState.value
        val missingTargets =
            normalizedTargets.filter { target ->
                !currentState.resolvedGifTargets.containsKey(target) &&
                    !currentState.missingGifTargets.contains(target) &&
                    !mediaGifTargetLoadingKeys.contains(target)
            }
        if (missingTargets.isEmpty()) return

        mediaGifTargetLoadingKeys += missingTargets
        scope.launch {
            try {
                val result =
                    repository.resolveGifTargets(
                        context = appContext,
                        rawTargets = missingTargets,
                    )
                mutableState.update { state ->
                    state.copy(
                        resolvedGifTargets =
                            mergeLimitedMap(
                                current = state.resolvedGifTargets,
                                added = result.resolvedTargets,
                                limit = MEDIA_GIF_TARGET_STATE_LIMIT,
                            ),
                        missingGifTargets =
                            mergeLimitedSet(
                                current = state.missingGifTargets,
                                added = result.missingTargets,
                                limit = MEDIA_MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                mutableState.update { state ->
                    state.copy(
                        missingGifTargets =
                            mergeLimitedSet(
                                current = state.missingGifTargets,
                                added = missingTargets.toSet(),
                                limit = MEDIA_MISSING_STATE_LIMIT,
                            ),
                    )
                }
            } finally {
                mediaGifTargetLoadingKeys -= missingTargets
            }
        }
    }

    fun clearLoadingState() {
        mediaImageLoadingKeys = emptySet()
        mediaGifTargetLoadingKeys = emptySet()
    }
}

private fun <T> mergeLimitedMap(
    current: Map<String, T>,
    added: Map<String, T>,
    limit: Int,
): Map<String, T> {
    if (added.isEmpty()) return current
    val merged = LinkedHashMap<String, T>(current.size + added.size)
    current.forEach { (key, value) ->
        merged[key] = value
    }
    added.forEach { (key, value) ->
        merged.remove(key)
        merged[key] = value
    }
    trimFirstEntries(merged, limit)
    return merged.toMap()
}

private fun mergeLimitedSet(
    current: Set<String>,
    added: Set<String>,
    limit: Int,
): Set<String> {
    if (added.isEmpty()) return current
    val merged = LinkedHashSet<String>(current.size + added.size)
    merged.addAll(current)
    added.forEach { value ->
        merged.remove(value)
        merged.add(value)
    }
    while (merged.size > limit.coerceAtLeast(1)) {
        val first = merged.firstOrNull() ?: break
        merged.remove(first)
    }
    return merged.toSet()
}

private fun <T> trimFirstEntries(
    map: LinkedHashMap<String, T>,
    limit: Int,
) {
    val safeLimit = limit.coerceAtLeast(1)
    while (map.size > safeLimit) {
        val firstKey = map.entries.firstOrNull()?.key ?: break
        map.remove(firstKey)
    }
}
