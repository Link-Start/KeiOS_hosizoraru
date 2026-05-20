package os.kei.feature.github.data.apk

import okhttp3.OkHttpClient
import okhttp3.Request
import os.kei.core.io.SharedHttpClient
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.Inflater
import kotlin.time.Duration.Companion.seconds

private val contentRangeRegex = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")

data class RemoteZipSelectedEntries(
    val entryNames: List<String>,
    val entries: Map<String, ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RemoteZipSelectedEntries

        if (entryNames != other.entryNames) return false
        if (entries.keys != other.entries.keys) return false
        for ((name, bytes) in entries) {
            val otherBytes = other.entries[name] ?: return false
            if (!bytes.contentEquals(otherBytes)) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = entryNames.hashCode()
        val entriesHash = entries.entries.fold(0) { acc, (name, bytes) ->
            acc + (31 * name.hashCode() + bytes.contentHashCode())
        }
        result = 31 * result + entriesHash
        return result
    }
}

class RemoteZipEntryReader(
    private val client: OkHttpClient = defaultClient
) {
    fun listEntryNames(
        url: String,
        apiToken: String = ""
    ): Result<List<String>> = runCatching {
        val centralDirectory = fetchCentralDirectory(url = url, apiToken = apiToken).bytes
        parseCentralDirectoryEntries(centralDirectory).map { it.name }
    }

    fun readSelectedEntries(
        url: String,
        apiToken: String = "",
        selectEntryNames: (List<String>) -> List<String>
    ): Result<RemoteZipSelectedEntries> = runCatching {
        val directory = fetchCentralDirectory(url = url, apiToken = apiToken)
        val directoryEntries = parseCentralDirectoryEntries(directory.bytes)
        val entriesByName = directoryEntries.associateBy { it.name }
        val entryNames = directoryEntries.map { it.name }
        val selectedNames = selectEntryNames(entryNames).distinct()
        val selectedEntries = linkedMapOf<String, ByteArray>()
        selectedNames.forEach { entryName ->
            val entry = entriesByName[entryName] ?: error("$entryName was not found in APK")
            selectedEntries[entryName] = readEntryFromDirectory(
                directory = directory,
                entry = entry,
                apiToken = apiToken,
                baseOffset = 0L
            )
        }
        RemoteZipSelectedEntries(
            entryNames = entryNames,
            entries = selectedEntries
        )
    }

    fun readNestedStoredZipEntry(
        url: String,
        outerEntryName: String,
        innerEntryName: String,
        apiToken: String = ""
    ): Result<ByteArray> = runCatching {
        val outerDirectory = fetchCentralDirectory(url = url, apiToken = apiToken)
        val outerEntry = findCentralDirectoryEntry(outerDirectory.bytes, outerEntryName)
            ?: error("$outerEntryName was not found in ZIP")
        readNestedStoredZipEntryFromDirectory(
            outerDirectory = outerDirectory,
            outerEntry = outerEntry,
            innerEntryName = innerEntryName,
            apiToken = apiToken
        )
    }

    fun readSelectedNestedStoredZipEntry(
        url: String,
        innerEntryName: String,
        apiToken: String = "",
        selectOuterEntryNames: (List<String>) -> List<String>
    ): Result<ByteArray> = runCatching {
        val outerDirectory = fetchCentralDirectory(url = url, apiToken = apiToken)
        val outerEntries = parseCentralDirectoryEntries(outerDirectory.bytes)
        val entriesByName = outerEntries.associateBy { it.name }
        val selectedNames = selectOuterEntryNames(outerEntries.map { it.name }).distinct()
        check(selectedNames.isNotEmpty()) {
            "No nested ZIP entry was selected"
        }

        var lastFailure: Throwable? = null
        selectedNames.forEach { outerEntryName ->
            val outerEntry = entriesByName[outerEntryName]
            if (outerEntry == null) {
                lastFailure = IllegalStateException("$outerEntryName was not found in ZIP")
                return@forEach
            }
            runCatching {
                readNestedStoredZipEntryFromDirectory(
                    outerDirectory = outerDirectory,
                    outerEntry = outerEntry,
                    innerEntryName = innerEntryName,
                    apiToken = apiToken
                )
            }.fold(
                onSuccess = { return@runCatching it },
                onFailure = { error -> lastFailure = error }
            )
        }
        throw lastFailure ?: IllegalStateException("No readable nested ZIP entry was found")
    }

    fun readSelectedNestedStoredZipEntries(
        url: String,
        apiToken: String = "",
        selectOuterEntryNames: (List<String>) -> List<String>,
        selectInnerEntryNames: (List<String>) -> List<String>
    ): Result<RemoteZipSelectedEntries> = runCatching {
        val outerDirectory = fetchCentralDirectory(url = url, apiToken = apiToken)
        val outerEntries = parseCentralDirectoryEntries(outerDirectory.bytes)
        val entriesByName = outerEntries.associateBy { it.name }
        val selectedNames = selectOuterEntryNames(outerEntries.map { it.name }).distinct()
        check(selectedNames.isNotEmpty()) {
            "No nested ZIP entry was selected"
        }

        var lastFailure: Throwable? = null
        selectedNames.forEach { outerEntryName ->
            val outerEntry = entriesByName[outerEntryName]
            if (outerEntry == null) {
                lastFailure = IllegalStateException("$outerEntryName was not found in ZIP")
                return@forEach
            }
            runCatching {
                readSelectedNestedStoredZipEntriesFromDirectory(
                    outerDirectory = outerDirectory,
                    outerEntry = outerEntry,
                    apiToken = apiToken,
                    selectInnerEntryNames = selectInnerEntryNames
                )
            }.fold(
                onSuccess = { return@runCatching it },
                onFailure = { error -> lastFailure = error }
            )
        }
        throw lastFailure ?: IllegalStateException("No readable nested ZIP entry was found")
    }

    private fun readNestedStoredZipEntryFromDirectory(
        outerDirectory: CentralDirectoryBytes,
        outerEntry: CentralDirectoryEntry,
        innerEntryName: String,
        apiToken: String
    ): ByteArray {
        check(outerEntry.compressionMethod == ZIP_METHOD_STORED) {
            "Nested APK entry is compressed and cannot be scanned by range"
        }
        check(outerEntry.compressedSize == outerEntry.uncompressedSize) {
            "Nested APK stored entry size is inconsistent"
        }
        check(outerEntry.uncompressedSize > 0L) {
            "Nested APK entry is empty"
        }

        val outerEntryDataStart = fetchEntryDataStart(
            url = outerDirectory.resolvedUrl,
            entry = outerEntry,
            baseOffset = 0L,
            apiToken = apiToken
        )
        val nestedDirectory = fetchCentralDirectoryAtBase(
            url = outerDirectory.resolvedUrl,
            baseOffset = outerEntryDataStart,
            zipSize = outerEntry.uncompressedSize,
            apiToken = apiToken
        )
        val innerEntry = findCentralDirectoryEntry(nestedDirectory.bytes, innerEntryName)
            ?: error("$innerEntryName was not found in nested APK")
        val compressedBytes = fetchEntryCompressedBytes(
            url = nestedDirectory.resolvedUrl,
            entry = innerEntry,
            centralDirectoryOffset = nestedDirectory.offset,
            apiToken = apiToken,
            baseOffset = outerEntryDataStart
        )
        return when (innerEntry.compressionMethod) {
            ZIP_METHOD_STORED -> compressedBytes
            ZIP_METHOD_DEFLATED -> inflateRaw(compressedBytes, innerEntry.uncompressedSize)
            else -> error("Nested APK entry compression method is unsupported: ${innerEntry.compressionMethod}")
        }
    }

    private fun readSelectedNestedStoredZipEntriesFromDirectory(
        outerDirectory: CentralDirectoryBytes,
        outerEntry: CentralDirectoryEntry,
        apiToken: String,
        selectInnerEntryNames: (List<String>) -> List<String>
    ): RemoteZipSelectedEntries {
        check(outerEntry.compressionMethod == ZIP_METHOD_STORED) {
            "Nested APK entry is compressed and cannot be scanned by range"
        }
        check(outerEntry.compressedSize == outerEntry.uncompressedSize) {
            "Nested APK stored entry size is inconsistent"
        }
        check(outerEntry.uncompressedSize > 0L) {
            "Nested APK entry is empty"
        }

        val outerEntryDataStart = fetchEntryDataStart(
            url = outerDirectory.resolvedUrl,
            entry = outerEntry,
            baseOffset = 0L,
            apiToken = apiToken
        )
        val nestedDirectory = fetchCentralDirectoryAtBase(
            url = outerDirectory.resolvedUrl,
            baseOffset = outerEntryDataStart,
            zipSize = outerEntry.uncompressedSize,
            apiToken = apiToken
        )
        val innerEntries = parseCentralDirectoryEntries(nestedDirectory.bytes)
        val entriesByName = innerEntries.associateBy { it.name }
        val entryNames = innerEntries.map { it.name }
        val selectedNames = selectInnerEntryNames(entryNames).distinct()
        check(selectedNames.isNotEmpty()) {
            "No nested APK entry content was selected"
        }
        val selectedEntries = linkedMapOf<String, ByteArray>()
        selectedNames.forEach { entryName ->
            val entry = entriesByName[entryName] ?: error("$entryName was not found in nested APK")
            selectedEntries[entryName] = readEntryFromDirectory(
                directory = nestedDirectory,
                entry = entry,
                apiToken = apiToken,
                baseOffset = outerEntryDataStart
            )
        }
        return RemoteZipSelectedEntries(
            entryNames = entryNames,
            entries = selectedEntries
        )
    }

    fun readEntry(
        url: String,
        entryName: String,
        apiToken: String = ""
    ): Result<ByteArray> = runCatching {
        val directory = fetchCentralDirectory(url = url, apiToken = apiToken)
        val entry = findCentralDirectoryEntry(directory.bytes, entryName)
            ?: error("$entryName was not found in APK")
        readEntryFromDirectory(
            directory = directory,
            entry = entry,
            apiToken = apiToken,
            baseOffset = 0L
        )
    }

    private fun readEntryFromDirectory(
        directory: CentralDirectoryBytes,
        entry: CentralDirectoryEntry,
        apiToken: String,
        baseOffset: Long
    ): ByteArray {
        val compressedBytes = fetchEntryCompressedBytes(
            url = directory.resolvedUrl,
            entry = entry,
            centralDirectoryOffset = directory.offset,
            apiToken = apiToken,
            baseOffset = baseOffset
        )
        return when (entry.compressionMethod) {
            ZIP_METHOD_STORED -> compressedBytes
            ZIP_METHOD_DEFLATED -> inflateRaw(compressedBytes, entry.uncompressedSize)
            else -> error("APK manifest compression method is unsupported: ${entry.compressionMethod}")
        }
    }

    private fun fetchCentralDirectory(
        url: String,
        apiToken: String
    ): CentralDirectoryBytes {
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
        return CentralDirectoryBytes(
            bytes = fetchRange(
                url = rangeUrl,
                start = centralDirectoryOffset,
                endInclusive = centralDirectoryOffset + centralDirectorySize - 1L,
                apiToken = apiToken
            ).bytes,
            offset = centralDirectoryOffset,
            resolvedUrl = rangeUrl
        )
    }

    private fun fetchCentralDirectoryAtBase(
        url: String,
        baseOffset: Long,
        zipSize: Long,
        apiToken: String
    ): CentralDirectoryBytes {
        val tailStartRelative = (zipSize - EOCD_SEARCH_WINDOW).coerceAtLeast(0L)
        val tail = fetchRange(
            url = url,
            start = baseOffset + tailStartRelative,
            endInclusive = baseOffset + zipSize - 1L,
            apiToken = apiToken
        )
        val eocdOffsetInTail = findLastSignature(tail.bytes, EOCD_SIGNATURE)
        check(eocdOffsetInTail >= 0) { "Nested APK zip end record was not found" }
        val eocdRelativeOffset = tail.start - baseOffset + eocdOffsetInTail
        val centralDirectorySize = tail.bytes.i32(eocdOffsetInTail + 12).toLong()
        val centralDirectoryOffset = tail.bytes.i32(eocdOffsetInTail + 16).toLong()
        check(centralDirectorySize > 0L && centralDirectoryOffset >= 0L) {
            "Nested APK central directory is invalid"
        }
        check(centralDirectorySize <= CENTRAL_DIRECTORY_SIZE_LIMIT) {
            "Nested APK central directory is too large"
        }
        check(eocdRelativeOffset >= centralDirectoryOffset) {
            "Nested APK central directory offset is invalid"
        }
        val centralDirectoryAbsoluteOffset = baseOffset + centralDirectoryOffset
        return CentralDirectoryBytes(
            bytes = fetchRange(
                url = url,
                start = centralDirectoryAbsoluteOffset,
                endInclusive = centralDirectoryAbsoluteOffset + centralDirectorySize - 1L,
                apiToken = apiToken
            ).bytes,
            offset = centralDirectoryAbsoluteOffset,
            resolvedUrl = url
        )
    }

    private fun fetchEntryCompressedBytes(
        url: String,
        entry: CentralDirectoryEntry,
        centralDirectoryOffset: Long,
        apiToken: String,
        baseOffset: Long
    ): ByteArray {
        check(entry.compressedSize >= 0L) { "APK entry compressed size is invalid" }
        check(entry.compressedSize <= ENTRY_COMPRESSED_SIZE_LIMIT) {
            "APK entry compressed size is too large"
        }
        check(entry.uncompressedSize <= ENTRY_UNCOMPRESSED_SIZE_LIMIT) {
            "APK entry uncompressed size is too large"
        }
        if (entry.compressedSize <= INLINE_ENTRY_COMPRESSED_SIZE_LIMIT) {
            val localHeaderOffset = baseOffset + entry.localHeaderOffset
            val prefetchEnd =
                (localHeaderOffset + LOCAL_ENTRY_PREFETCH_PREFIX_LIMIT + entry.compressedSize - 1L)
                    .coerceAtMost(centralDirectoryOffset - 1L)
            val prefetchBytes = fetchRange(
                url = url,
                start = localHeaderOffset,
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
            start = baseOffset + entry.localHeaderOffset,
            endInclusive = baseOffset + entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE - 1L,
            apiToken = apiToken
        ).bytes
        val localEntry = parseLocalEntry(localHeader)
        val dataStart = baseOffset + entry.localHeaderOffset + localEntry.dataOffset
        return fetchRange(
            url = url,
            start = dataStart,
            endInclusive = dataStart + entry.compressedSize - 1L,
            apiToken = apiToken
        ).bytes
    }

    private fun fetchEntryDataStart(
        url: String,
        entry: CentralDirectoryEntry,
        baseOffset: Long,
        apiToken: String
    ): Long {
        val localHeader = fetchRange(
            url = url,
            start = baseOffset + entry.localHeaderOffset,
            endInclusive = baseOffset + entry.localHeaderOffset + LOCAL_FILE_HEADER_SIZE - 1L,
            apiToken = apiToken
        ).bytes
        val localEntry = parseLocalEntry(localHeader)
        return baseOffset + entry.localHeaderOffset + localEntry.dataOffset
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
        return parseCentralDirectoryEntries(centralDirectory).firstOrNull { it.name == entryName }
    }

    private fun parseCentralDirectoryEntries(
        centralDirectory: ByteArray
    ): List<CentralDirectoryEntry> {
        return buildList {
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
                if (nameEnd > centralDirectory.size) return@buildList
                val name = String(centralDirectory, nameStart, nameLength, Charsets.UTF_8)
                add(
                    CentralDirectoryEntry(
                        name = name,
                        compressionMethod = compressionMethod,
                        compressedSize = compressedSize,
                        uncompressedSize = uncompressedSize,
                        localHeaderOffset = localHeaderOffset
                    )
                )
                offset = nameEnd + extraLength + commentLength
            }
        }
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
        val match = contentRangeRegex.matchEntire(value) ?: return null
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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RangeBytes

            if (start != other.start) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = start.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    private data class RangeProbe(
        val totalSize: Long,
        val resolvedUrl: String
    )

    private data class CentralDirectoryBytes(
        val bytes: ByteArray,
        val offset: Long,
        val resolvedUrl: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CentralDirectoryBytes

            if (offset != other.offset) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (resolvedUrl != other.resolvedUrl) return false

            return true
        }

        override fun hashCode(): Int {
            var result = offset.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + resolvedUrl.hashCode()
            return result
        }
    }

    private data class LocalEntry(
        val dataOffset: Long
    )

    private data class ContentRange(
        val start: Long,
        val endInclusive: Long,
        val totalSize: Long
    )

    private data class CentralDirectoryEntry(
        val name: String,
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
            SharedHttpClient.base.newBuilder()
                .callTimeout(18.seconds)
                .readTimeout(14.seconds)
                .build()
        }
    }
}
