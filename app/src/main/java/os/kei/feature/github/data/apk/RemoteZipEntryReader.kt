package os.kei.feature.github.data.apk

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

internal class RemoteZipEntryReader(
    private val client: OkHttpClient = defaultClient
) {
    fun readEntry(
        url: String,
        entryName: String,
        apiToken: String = ""
    ): Result<ByteArray> = runCatching {
        val probe = fetchRangeProbe(url = url, apiToken = apiToken)
        val totalSize = probe.totalSize
        val rangeUrl = probe.resolvedUrl
        val tailStart = (totalSize - EOCD_SEARCH_WINDOW).coerceAtLeast(0L)
        val tail = fetchRange(
            url = rangeUrl,
            start = tailStart,
            endInclusive = totalSize - 1L,
            apiToken = apiToken
        )
        val eocdOffsetInTail = findLastSignature(tail.bytes, EOCD_SIGNATURE)
        check(eocdOffsetInTail >= 0) { "APK zip end record was not found" }
        val eocdAbsoluteOffset = tail.start + eocdOffsetInTail
        val centralDirectorySize = tail.bytes.i32(eocdOffsetInTail + 12).toLong()
        val centralDirectoryOffset = tail.bytes.i32(eocdOffsetInTail + 16).toLong()
        check(centralDirectorySize > 0L && centralDirectoryOffset >= 0L) {
            "APK central directory is invalid"
        }
        check(centralDirectorySize <= CENTRAL_DIRECTORY_SIZE_LIMIT) {
            "APK central directory is too large"
        }
        check(eocdAbsoluteOffset >= centralDirectoryOffset) {
            "APK central directory offset is invalid"
        }
        val centralDirectory = fetchRange(
            url = rangeUrl,
            start = centralDirectoryOffset,
            endInclusive = centralDirectoryOffset + centralDirectorySize - 1L,
            apiToken = apiToken
        ).bytes
        val entry = findCentralDirectoryEntry(centralDirectory, entryName)
            ?: error("$entryName was not found in APK")
        val compressedBytes = fetchEntryCompressedBytes(
            url = rangeUrl,
            entry = entry,
            centralDirectoryOffset = centralDirectoryOffset,
            apiToken = apiToken
        )
        when (entry.compressionMethod) {
            ZIP_METHOD_STORED -> compressedBytes
            ZIP_METHOD_DEFLATED -> inflateRaw(compressedBytes, entry.uncompressedSize)
            else -> error("APK manifest compression method is unsupported: ${entry.compressionMethod}")
        }
    }

    private fun fetchEntryCompressedBytes(
        url: String,
        entry: CentralDirectoryEntry,
        centralDirectoryOffset: Long,
        apiToken: String
    ): ByteArray {
        check(entry.compressedSize >= 0L) { "APK entry compressed size is invalid" }
        check(entry.compressedSize <= ENTRY_COMPRESSED_SIZE_LIMIT) {
            "APK entry compressed size is too large"
        }
        check(entry.uncompressedSize <= ENTRY_UNCOMPRESSED_SIZE_LIMIT) {
            "APK entry uncompressed size is too large"
        }
        if (entry.compressedSize <= INLINE_ENTRY_COMPRESSED_SIZE_LIMIT) {
            val prefetchEnd =
                (entry.localHeaderOffset + LOCAL_ENTRY_PREFETCH_PREFIX_LIMIT + entry.compressedSize - 1L)
                    .coerceAtMost(centralDirectoryOffset - 1L)
            val prefetchBytes = fetchRange(
                url = url,
                start = entry.localHeaderOffset,
                endInclusive = prefetchEnd,
                apiToken = apiToken
            ).bytes
            val localEntry = parseLocalEntry(prefetchBytes)
            val dataEnd = localEntry.dataOffset + entry.compressedSize
            if (dataEnd <= prefetchBytes.size.toLong()) {
                return prefetchBytes.copyOfRange(localEntry.dataOffset.toInt(), dataEnd.toInt())
            }
        }

        val localHeader = fetchRange(
            url = url,
            start = entry.localHeaderOffset,
            endInclusive = entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE - 1L,
            apiToken = apiToken
        ).bytes
        val localEntry = parseLocalEntry(localHeader)
        val dataStart = entry.localHeaderOffset + localEntry.dataOffset
        return fetchRange(
            url = url,
            start = dataStart,
            endInclusive = dataStart + entry.compressedSize - 1L,
            apiToken = apiToken
        ).bytes
    }

    private fun parseLocalEntry(localHeaderBytes: ByteArray): LocalEntry {
        check(localHeaderBytes.size >= LOCAL_FILE_HEADER_SIZE.toInt()) { "APK local file header is incomplete" }
        check(localHeaderBytes.i32(0) == LOCAL_FILE_HEADER_SIGNATURE) { "APK local file header is invalid" }
        val localNameLength = localHeaderBytes.u16(26)
        val localExtraLength = localHeaderBytes.u16(28)
        return LocalEntry(
            dataOffset = LOCAL_FILE_HEADER_SIZE + localNameLength + localExtraLength
        )
    }

    private fun fetchRangeProbe(
        url: String,
        apiToken: String
    ): RangeProbe {
        val request = requestBuilder(url, apiToken)
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(request).execute().use { response ->
            val contentRange = response.header("Content-Range").parseContentRange()
            check(response.code == 206 && contentRange != null) {
                "APK byte-range probe failed (HTTP ${response.code})"
            }
            check(contentRange.totalSize > 0L) { "APK size is invalid" }
            return RangeProbe(
                totalSize = contentRange.totalSize,
                resolvedUrl = response.request.url.toString()
            )
        }
    }

    private fun fetchRange(
        url: String,
        start: Long,
        endInclusive: Long,
        apiToken: String
    ): RangeBytes {
        check(start >= 0L && endInclusive >= start) { "Invalid APK byte range" }
        val request = requestBuilder(url, apiToken)
            .header("Range", "bytes=$start-$endInclusive")
            .build()
        client.newCall(request).execute().use { response ->
            val contentRange = response.header("Content-Range").parseContentRange()
            check(response.code == 206 && contentRange != null) {
                "APK byte-range request failed (HTTP ${response.code})"
            }
            check(contentRange.start == start && contentRange.endInclusive == endInclusive) {
                "APK byte-range response is invalid"
            }
            val body = response.body.bytes()
            val expectedSize = endInclusive - start + 1L
            check(body.size.toLong() == expectedSize) {
                "APK byte-range response size is invalid"
            }
            return RangeBytes(
                bytes = body,
                start = contentRange.start
            )
        }
    }

    private fun requestBuilder(
        url: String,
        apiToken: String
    ): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", GITHUB_USER_AGENT)
            .header("Cache-Control", "no-store")
            .header("Pragma", "no-cache")
            .header(
                "Accept",
                "application/vnd.android.package-archive,application/octet-stream,*/*"
            )
        if (apiToken.isNotBlank() && url.shouldAttachGitHubToken()) {
            builder.header("Authorization", "Bearer ${apiToken.trim()}")
        }
        return builder
    }

    private fun findCentralDirectoryEntry(
        centralDirectory: ByteArray,
        entryName: String
    ): CentralDirectoryEntry? {
        var offset = 0
        while (offset + CENTRAL_DIRECTORY_HEADER_SIZE <= centralDirectory.size) {
            if (centralDirectory.i32(offset) != CENTRAL_DIRECTORY_SIGNATURE) break
            val compressionMethod = centralDirectory.u16(offset + 10)
            val compressedSize = centralDirectory.i32(offset + 20).toLong()
            val uncompressedSize = centralDirectory.i32(offset + 24).toLong()
            val nameLength = centralDirectory.u16(offset + 28)
            val extraLength = centralDirectory.u16(offset + 30)
            val commentLength = centralDirectory.u16(offset + 32)
            val localHeaderOffset = centralDirectory.i32(offset + 42).toLong()
            val nameStart = offset + CENTRAL_DIRECTORY_HEADER_SIZE
            val nameEnd = nameStart + nameLength
            if (nameEnd > centralDirectory.size) return null
            val name = String(centralDirectory, nameStart, nameLength, Charsets.UTF_8)
            if (name == entryName) {
                return CentralDirectoryEntry(
                    compressionMethod = compressionMethod,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    localHeaderOffset = localHeaderOffset
                )
            }
            offset = nameEnd + extraLength + commentLength
        }
        return null
    }

    private fun inflateRaw(
        compressedBytes: ByteArray,
        expectedSize: Long
    ): ByteArray {
        val inflater = Inflater(true)
        val output =
            ByteArrayOutputStream(expectedSize.takeIf { it in 1..Int.MAX_VALUE }?.toInt() ?: 4096)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            inflater.setInput(compressedBytes)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (inflater.needsInput()) {
                    break
                } else {
                    error("APK manifest inflate stalled")
                }
            }
            return output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private fun String?.parseContentRange(): ContentRange? {
        val value = this?.trim().orEmpty()
        val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""").matchEntire(value) ?: return null
        val total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull() ?: return null
        return ContentRange(
            start = match.groupValues[1].toLong(),
            endInclusive = match.groupValues[2].toLong(),
            totalSize = total
        )
    }

    private fun String.shouldAttachGitHubToken(): Boolean {
        val host = runCatching { URI(this).host.orEmpty().lowercase() }.getOrDefault("")
        return host == "github.com" || host == "api.github.com" || host.endsWith(".github.com")
    }

    private fun findLastSignature(bytes: ByteArray, signature: Int): Int {
        var offset = bytes.size - 4
        while (offset >= 0) {
            if (bytes.i32(offset) == signature) return offset
            offset -= 1
        }
        return -1
    }

    private fun ByteArray.u16(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
                ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.i32(offset: Int): Int {
        return u16(offset) or (u16(offset + 2) shl 16)
    }

    private data class RangeBytes(
        val bytes: ByteArray,
        val start: Long
    )

    private data class RangeProbe(
        val totalSize: Long,
        val resolvedUrl: String
    )

    private data class LocalEntry(
        val dataOffset: Long
    )

    private data class ContentRange(
        val start: Long,
        val endInclusive: Long,
        val totalSize: Long
    )

    private data class CentralDirectoryEntry(
        val compressionMethod: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long
    )

    companion object {
        private const val GITHUB_USER_AGENT = "KeiOS-App/1.0 (Android)"
        private const val EOCD_SIGNATURE = 0x06054b50
        private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50
        private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50
        private const val EOCD_SEARCH_WINDOW = 65_557L
        private const val CENTRAL_DIRECTORY_HEADER_SIZE = 46
        private const val LOCAL_FILE_HEADER_SIZE = 30L
        private const val ZIP_U16_MAX = 65_535L
        private const val CENTRAL_DIRECTORY_SIZE_LIMIT = 4L * 1024L * 1024L
        private const val ENTRY_COMPRESSED_SIZE_LIMIT = 1L * 1024L * 1024L
        private const val ENTRY_UNCOMPRESSED_SIZE_LIMIT = 2L * 1024L * 1024L
        private const val LOCAL_ENTRY_PREFETCH_PREFIX_LIMIT =
            LOCAL_FILE_HEADER_SIZE + ZIP_U16_MAX + ZIP_U16_MAX
        private const val INLINE_ENTRY_COMPRESSED_SIZE_LIMIT = 512L * 1024L
        private const val ZIP_METHOD_STORED = 0
        private const val ZIP_METHOD_DEFLATED = 8
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .callTimeout(18, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(14, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .fastFallback(true)
                .build()
        }
    }
}
