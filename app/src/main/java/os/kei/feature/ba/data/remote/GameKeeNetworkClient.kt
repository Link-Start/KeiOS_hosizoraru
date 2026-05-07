package os.kei.feature.ba.data.remote

import android.graphics.Bitmap
import java.io.File

internal data class GameKeeNetworkRequest(
    val pathOrUrl: String,
    val refererPath: String,
    val extraHeaders: Map<String, String> = emptyMap()
)

internal sealed interface GameKeeNetworkResult<out T> {
    val request: GameKeeNetworkRequest?
    val errorPreview: String

    data class Success<T>(
        val value: T,
        override val request: GameKeeNetworkRequest?,
        val bytes: Long = 0L
    ) : GameKeeNetworkResult<T> {
        override val errorPreview: String = ""
    }

    data class Failure(
        override val request: GameKeeNetworkRequest?,
        override val errorPreview: String,
        val throwable: Throwable? = null
    ) : GameKeeNetworkResult<Nothing>
}

internal fun <T> GameKeeNetworkResult<T>.getOrThrow(): T {
    return when (this) {
        is GameKeeNetworkResult.Success -> value
        is GameKeeNetworkResult.Failure -> throw throwable ?: IllegalStateException(errorPreview)
    }
}

internal object GameKeeNetworkClient {
    fun fetchJson(request: GameKeeNetworkRequest): GameKeeNetworkResult<String> {
        return capture(request) {
            GameKeeFetchHelper.fetchJson(
                pathOrUrl = request.pathOrUrl,
                refererPath = request.refererPath,
                extraHeaders = request.extraHeaders
            )
        }
    }

    fun fetchHtml(request: GameKeeNetworkRequest): GameKeeNetworkResult<String> {
        return capture(request) {
            GameKeeFetchHelper.fetchHtml(
                pathOrUrl = request.pathOrUrl,
                refererPath = request.refererPath,
                extraHeaders = request.extraHeaders
            )
        }
    }

    fun fetchImage(
        imageUrl: String,
        maxDecodeDimension: Int = 2560
    ): GameKeeNetworkResult<Bitmap?> {
        val request = GameKeeNetworkRequest(pathOrUrl = imageUrl, refererPath = "")
        return capture(request) {
            GameKeeFetchHelper.fetchImage(
                imageUrl = imageUrl,
                maxDecodeDimension = maxDecodeDimension
            )
        }
    }

    fun downloadToFile(
        mediaUrl: String,
        targetFile: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): GameKeeNetworkResult<File> {
        val request = GameKeeNetworkRequest(pathOrUrl = mediaUrl, refererPath = "")
        return capture(request) {
            val downloaded = GameKeeFetchHelper.downloadToFile(
                mediaUrl = mediaUrl,
                targetFile = targetFile,
                onProgress = onProgress
            )
            if (!downloaded) {
                error("download failed")
            }
            targetFile
        }.let { result ->
            when (result) {
                is GameKeeNetworkResult.Success -> result.copy(
                    bytes = targetFile.length().coerceAtLeast(0L)
                )

                is GameKeeNetworkResult.Failure -> result
            }
        }
    }

    private fun <T> capture(
        request: GameKeeNetworkRequest,
        block: () -> T
    ): GameKeeNetworkResult<T> {
        return runCatching { block() }
            .fold(
                onSuccess = { value ->
                    GameKeeNetworkResult.Success(
                        value = value,
                        request = request,
                        bytes = when (value) {
                            is String -> value.toByteArray(Charsets.UTF_8).size.toLong()
                            is File -> value.length().coerceAtLeast(0L)
                            else -> 0L
                        }
                    )
                },
                onFailure = { error ->
                    GameKeeNetworkResult.Failure(
                        request = request,
                        errorPreview = error.compactGameKeeError(),
                        throwable = error
                    )
                }
            )
    }
}

internal object GameKeeRepository {
    fun fetchBaContentDetailJson(
        contentId: Long,
        refererPath: String
    ): String {
        return GameKeeNetworkClient.fetchJson(
            GameKeeNetworkRequest(
                pathOrUrl = contentDetailApiPath(contentId),
                refererPath = refererPath,
                extraHeaders = baApiHeaders()
            )
        ).getOrThrow()
    }

    fun fetchBaCatalogTreeJson(
        pid: Int,
        refererPath: String
    ): String {
        return GameKeeNetworkClient.fetchJson(
            GameKeeNetworkRequest(
                pathOrUrl = "/v1/entry/treesByPid?pid=${pid.coerceAtLeast(0)}",
                refererPath = refererPath,
                extraHeaders = baApiHeaders()
            )
        ).getOrThrow()
    }

    fun fetchHtml(
        pathOrUrl: String,
        refererPath: String = "/ba/"
    ): String {
        return GameKeeNetworkClient.fetchHtml(
            GameKeeNetworkRequest(
                pathOrUrl = pathOrUrl,
                refererPath = refererPath
            )
        ).getOrThrow()
    }

    internal fun contentDetailApiPath(contentId: Long): String {
        return "/v1/content/detail/${contentId.coerceAtLeast(0L)}"
    }

    internal fun baApiHeaders(): Map<String, String> {
        return mapOf(
            "device-num" to "1",
            "game-alias" to "ba"
        )
    }
}

private fun Throwable.compactGameKeeError(limit: Int = 220): String {
    val root = generateSequence(this) { it.cause }.last()
    val head = "${javaClass.simpleName}:${message.orEmpty()}".compactGameKeeLog(limit / 2)
    val tail = if (root !== this) {
        " root=${root.javaClass.simpleName}:${root.message.orEmpty()}".compactGameKeeLog(limit / 2)
    } else {
        ""
    }
    return (head + tail).compactGameKeeLog(limit)
}

private fun String.compactGameKeeLog(limit: Int): String {
    val compact = replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .trim()
    if (compact.length <= limit) return compact
    return compact.take(limit) + "..."
}
