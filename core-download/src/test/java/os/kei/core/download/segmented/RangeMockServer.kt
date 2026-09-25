package os.kei.core.download.segmented

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/** Serves [bytes] as a range-capable resource: `Range` gets a 206 slice, no `Range` the whole body. */
internal fun byteRangeDispatcher(bytes: ByteArray): Dispatcher =
    object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            rangeResponse(bytes, request.getHeader("Range"))
    }

internal fun rangeResponse(
    bytes: ByteArray,
    rangeHeader: String?,
): MockResponse {
    if (rangeHeader.isNullOrBlank()) {
        return MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Length", bytes.size)
            .setBody(Buffer().write(bytes))
    }
    val parts = rangeHeader.removePrefix("bytes=").split("-", limit = 2)
    return rangeResponse(bytes, start = parts[0].toInt(), endInclusive = parts[1].toInt())
}

internal fun rangeResponse(
    bytes: ByteArray,
    start: Int,
    endInclusive: Int,
): MockResponse {
    val safeEnd = endInclusive.coerceAtMost(bytes.lastIndex)
    return MockResponse()
        .setResponseCode(206)
        .addHeader("Content-Range", "bytes $start-$safeEnd/${bytes.size}")
        .addHeader("Content-Length", safeEnd - start + 1)
        .setBody(Buffer().write(bytes.copyOfRange(start, safeEnd + 1)))
}

internal fun MockWebServer.takeAllRequests(): List<RecordedRequest> =
    buildList {
        repeat(requestCount) {
            add(takeRequest())
        }
    }
