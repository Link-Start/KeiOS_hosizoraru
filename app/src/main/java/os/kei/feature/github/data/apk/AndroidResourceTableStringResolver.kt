package os.kei.feature.github.data.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

internal object AndroidResourceTableStringResolver {
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val UTF8_FLAG = 0x00000100
    private const val VALUE_TYPE_STRING = 0x03
    private const val ENTRY_FLAG_COMPLEX = 0x0001
    private const val PACKAGE_TYPE_STRING_OFFSET = 268
    private const val PACKAGE_KEY_STRING_OFFSET = 276

    fun resolveString(
        tableBytes: ByteArray,
        resourceId: Int,
        preferredLanguages: List<String> = preferredLanguages()
    ): String? {
        return runCatching {
            val buffer = ByteBuffer.wrap(tableBytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buffer.capacity() < 12 || buffer.u16(0) != RES_TABLE_TYPE) return@runCatching null
            val packageId = resourceId ushr 24 and 0xFF
            val typeId = resourceId ushr 16 and 0xFF
            val entryId = resourceId and 0xFFFF
            val globalStrings = readGlobalStringPool(buffer)
            if (globalStrings.isEmpty()) return@runCatching null
            val candidates = buildList {
                forEachChunk(
                    buffer,
                    buffer.u16(2).coerceAtLeast(12),
                    buffer.capacity()
                ) { offset, type, size ->
                    if (type == RES_TABLE_PACKAGE_TYPE) {
                        addAll(
                            readPackageStringCandidates(
                                buffer = buffer,
                                packageOffset = offset,
                                packageSize = size,
                                packageId = packageId,
                                typeId = typeId,
                                entryId = entryId,
                                globalStrings = globalStrings
                            )
                        )
                    }
                }
            }
            selectCandidate(candidates, preferredLanguages)
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun readGlobalStringPool(buffer: ByteBuffer): List<String> {
        forEachChunk(
            buffer,
            buffer.u16(2).coerceAtLeast(12),
            buffer.capacity()
        ) { offset, type, _ ->
            if (type == RES_STRING_POOL_TYPE) {
                return readStringPool(buffer, offset)
            }
        }
        return emptyList()
    }

    private fun readPackageStringCandidates(
        buffer: ByteBuffer,
        packageOffset: Int,
        packageSize: Int,
        packageId: Int,
        typeId: Int,
        entryId: Int,
        globalStrings: List<String>
    ): List<ResourceStringCandidate> {
        if (packageOffset + PACKAGE_KEY_STRING_OFFSET + 4 > buffer.capacity()) return emptyList()
        val currentPackageId = buffer.i32(packageOffset + 8) and 0xFF
        if (currentPackageId != packageId) return emptyList()
        val typeStringsOffset = buffer.i32(packageOffset + PACKAGE_TYPE_STRING_OFFSET)
        val typeStrings = if (typeStringsOffset > 0) {
            readStringPool(buffer, packageOffset + typeStringsOffset)
        } else {
            emptyList()
        }
        val typeName = typeStrings.getOrNull(typeId - 1).orEmpty()
        if (typeName.isNotBlank() && typeName != "string") return emptyList()

        val packageEnd = (packageOffset + packageSize).coerceAtMost(buffer.capacity())
        return buildList {
            forEachChunk(
                buffer,
                packageOffset + buffer.u16(packageOffset + 2),
                packageEnd
            ) { offset, type, _ ->
                if (type == RES_TABLE_TYPE_TYPE && buffer.u8(offset + 8) == typeId) {
                    readTypeStringCandidate(
                        buffer = buffer,
                        typeOffset = offset,
                        entryId = entryId,
                        globalStrings = globalStrings
                    )?.let(::add)
                }
            }
        }
    }

    private fun readTypeStringCandidate(
        buffer: ByteBuffer,
        typeOffset: Int,
        entryId: Int,
        globalStrings: List<String>
    ): ResourceStringCandidate? {
        if (typeOffset + 20 > buffer.capacity()) return null
        val headerSize = buffer.u16(typeOffset + 2)
        val entryCount = buffer.i32(typeOffset + 12)
        val entriesStart = buffer.i32(typeOffset + 16)
        if (entryId !in 0 until entryCount) return null
        val entryOffsetTable = typeOffset + headerSize
        val entryOffsetPosition = entryOffsetTable + entryId * 4
        if (entryOffsetPosition + 4 > buffer.capacity()) return null
        val entryOffset = buffer.i32(entryOffsetPosition)
        if (entryOffset < 0) return null
        val entryAbsolute = typeOffset + entriesStart + entryOffset
        if (entryAbsolute + 16 > buffer.capacity()) return null
        val entrySize = buffer.u16(entryAbsolute)
        val entryFlags = buffer.u16(entryAbsolute + 2)
        if (entryFlags and ENTRY_FLAG_COMPLEX != 0) return null
        val valueOffset = entryAbsolute + entrySize
        if (valueOffset + 8 > buffer.capacity()) return null
        val dataType = buffer.u8(valueOffset + 3)
        val data = buffer.i32(valueOffset + 4)
        if (dataType != VALUE_TYPE_STRING) return null
        val value = globalStrings.getOrNull(data)?.trim().orEmpty()
        if (value.isBlank()) return null
        return ResourceStringCandidate(
            value = value,
            language = readConfigLanguage(buffer, typeOffset + 20)
        )
    }

    private fun selectCandidate(
        candidates: List<ResourceStringCandidate>,
        preferredLanguages: List<String>
    ): String? {
        if (candidates.isEmpty()) return null
        val normalizedPreferences = preferredLanguages
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotBlank() }
            .distinct()
        normalizedPreferences.forEach { language ->
            candidates.firstOrNull { it.language == language }?.let { return it.value }
        }
        candidates.firstOrNull { it.language.isBlank() }?.let { return it.value }
        return candidates.firstOrNull()?.value
    }

    private fun preferredLanguages(): List<String> {
        val language = Locale.getDefault().language.orEmpty()
        return listOf(language, "", "en")
    }

    private fun readConfigLanguage(buffer: ByteBuffer, configOffset: Int): String {
        if (configOffset + 12 > buffer.capacity()) return ""
        val first = buffer.u8(configOffset + 8)
        val second = buffer.u8(configOffset + 9)
        if (first == 0 && second == 0) return ""
        val chars = charArrayOf(first.toChar(), second.toChar())
        return chars.concatToString()
            .takeIf { value -> value.all { it in 'a'..'z' || it in 'A'..'Z' } }
            ?.lowercase(Locale.ROOT)
            .orEmpty()
    }

    private inline fun forEachChunk(
        buffer: ByteBuffer,
        startOffset: Int,
        endOffset: Int,
        action: (offset: Int, type: Int, size: Int) -> Unit
    ) {
        var offset = startOffset
        val boundedEnd = endOffset.coerceAtMost(buffer.capacity())
        while (offset + 8 <= boundedEnd) {
            val type = buffer.u16(offset)
            val size = buffer.i32(offset + 4)
            if (size <= 0 || offset + size > boundedEnd) break
            action(offset, type, size)
            offset += size
        }
    }

    private fun readStringPool(buffer: ByteBuffer, offset: Int): List<String> {
        if (offset + 28 > buffer.capacity() || buffer.u16(offset) != RES_STRING_POOL_TYPE) {
            return emptyList()
        }
        val chunkSize = buffer.i32(offset + 4)
        if (chunkSize <= 0 || offset + chunkSize > buffer.capacity()) return emptyList()
        val stringCount = buffer.i32(offset + 8)
        val flags = buffer.i32(offset + 16)
        val stringsStart = buffer.i32(offset + 20)
        val utf8 = flags and UTF8_FLAG != 0
        return List(stringCount) { index ->
            val stringOffsetPosition = offset + 28 + index * 4
            if (stringOffsetPosition + 4 > buffer.capacity()) {
                ""
            } else {
                val stringOffset = buffer.i32(stringOffsetPosition)
                val absolute = offset + stringsStart + stringOffset
                if (utf8) {
                    buffer.readUtf8String(absolute)
                } else {
                    buffer.readUtf16String(absolute)
                }
            }
        }
    }

    private fun ByteBuffer.readUtf8String(offset: Int): String {
        val firstLength = readLength8(offset)
        val byteLength = readLength8(firstLength.nextOffset)
        val start = byteLength.nextOffset
        if (start + byteLength.value > capacity()) return ""
        return String(array(), arrayOffset() + start, byteLength.value, Charsets.UTF_8)
    }

    private fun ByteBuffer.readUtf16String(offset: Int): String {
        val charLength = readLength16(offset)
        val start = charLength.nextOffset
        val byteLength = charLength.value * 2
        if (start + byteLength > capacity()) return ""
        return Charsets.UTF_16LE.decode(
            duplicate()
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    position(start)
                    limit(start + byteLength)
                }
                .slice()
        ).toString()
    }

    private fun ByteBuffer.readLength8(offset: Int): StringLength {
        if (offset >= capacity()) return StringLength(0, offset)
        val first = u8(offset)
        return if (first and 0x80 != 0 && offset + 1 < capacity()) {
            StringLength(((first and 0x7F) shl 8) or u8(offset + 1), offset + 2)
        } else {
            StringLength(first, offset + 1)
        }
    }

    private fun ByteBuffer.readLength16(offset: Int): StringLength {
        if (offset + 1 >= capacity()) return StringLength(0, offset)
        val first = u16(offset)
        return if (first and 0x8000 != 0 && offset + 3 < capacity()) {
            StringLength(((first and 0x7FFF) shl 16) or u16(offset + 2), offset + 4)
        } else {
            StringLength(first, offset + 2)
        }
    }

    private fun ByteBuffer.u8(offset: Int): Int = get(offset).toInt() and 0xFF

    private fun ByteBuffer.u16(offset: Int): Int = getShort(offset).toInt() and 0xFFFF

    private fun ByteBuffer.i32(offset: Int): Int = getInt(offset)

    private data class ResourceStringCandidate(
        val value: String,
        val language: String
    )

    private data class StringLength(
        val value: Int,
        val nextOffset: Int
    )
}
