package os.kei.feature.github.data.apk

import os.kei.feature.github.model.GitHubApkManifestInfo
import os.kei.feature.github.model.GitHubApkManifestMetadata
import os.kei.feature.github.model.GitHubApkManifestNode
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object AndroidBinaryXmlPackageNameParser {
    private const val CHUNK_XML = 0x0003
    private const val CHUNK_STRING_POOL = 0x0001
    private const val CHUNK_XML_START_ELEMENT = 0x0102
    private const val XML_NODE_HEADER_SIZE = 16
    private const val UTF8_FLAG = 0x00000100
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_HEX = 0x11
    private const val TYPE_INT_BOOLEAN = 0x12

    fun parsePackageName(manifestBytes: ByteArray): Result<String> = runCatching {
        parseManifestInfo(manifestBytes).getOrThrow().packageName.ifBlank {
            error("AndroidManifest.xml does not expose a package name")
        }
    }

    fun parseManifestInfo(manifestBytes: ByteArray): Result<GitHubApkManifestInfo> = runCatching {
        val buffer = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)
        val strings = readStringPool(buffer)
        var packageName = ""
        var appLabel = ""
        var versionName = ""
        var versionCode = ""
        var minSdk = ""
        var targetSdk = ""
        val permissions = linkedSetOf<String>()
        val features = linkedSetOf<String>()
        val metadata = mutableListOf<GitHubApkManifestMetadata>()
        val nodes = mutableListOf<GitHubApkManifestNode>()
        var offset = 0
        while (offset + 8 <= manifestBytes.size) {
            val chunkType = buffer.u16(offset)
            val chunkSize = buffer.i32(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > manifestBytes.size) break
            if (chunkType == CHUNK_XML_START_ELEMENT) {
                val element = readStartElement(
                    buffer = buffer,
                    chunkOffset = offset,
                    strings = strings
                )
                when (element.name) {
                    "manifest" -> {
                        packageName = element.attr("package")
                        versionName = element.attr("versionName")
                        versionCode = element.attr("versionCode")
                    }

                    "uses-sdk" -> {
                        minSdk = element.attr("minSdkVersion")
                        targetSdk = element.attr("targetSdkVersion")
                    }

                    "application" -> {
                        appLabel = element.attr("label").takeIf { label ->
                            label.isLiteralManifestLabel()
                        }.orEmpty()
                    }

                    "uses-permission" -> {
                        element.attr("name").takeIf { it.isNotBlank() }?.let(permissions::add)
                    }

                    "uses-feature" -> {
                        element.attr("name").takeIf { it.isNotBlank() }?.let(features::add)
                    }

                    "meta-data" -> {
                        val name = element.attr("name")
                        val value = element.attr("value")
                            .ifBlank { element.attr("resource") }
                        if (name.isNotBlank()) {
                            metadata += GitHubApkManifestMetadata(name = name, value = value)
                        }
                    }
                }
                nodes += element.toManifestNode()
            }
            offset += nextChunkStep(buffer, offset, chunkType, chunkSize)
        }
        GitHubApkManifestInfo(
            assetName = "",
            appLabel = appLabel,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            permissions = permissions.toList(),
            features = features.toList(),
            metadata = metadata,
            manifestNodes = nodes
        )
    }

    private fun readStringPool(buffer: ByteBuffer): List<String> {
        var offset = 0
        while (offset + 28 <= buffer.capacity()) {
            val chunkType = buffer.u16(offset)
            val chunkSize = buffer.i32(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > buffer.capacity()) break
            if (chunkType == CHUNK_STRING_POOL) {
                val stringCount = buffer.i32(offset + 8)
                val flags = buffer.i32(offset + 16)
                val stringsStart = buffer.i32(offset + 20)
                val utf8 = flags and UTF8_FLAG != 0
                return List(stringCount) { index ->
                    val stringOffset = buffer.i32(offset + 28 + index * 4)
                    val absolute = offset + stringsStart + stringOffset
                    if (utf8) {
                        buffer.readUtf8String(absolute)
                    } else {
                        buffer.readUtf16String(absolute)
                    }
                }
            }
            offset += nextChunkStep(buffer, offset, chunkType, chunkSize)
        }
        return emptyList()
    }

    private fun readStartElement(
        buffer: ByteBuffer,
        chunkOffset: Int,
        strings: List<String>
    ): BinaryXmlStartElement {
        val nameIndex = buffer.i32(chunkOffset + 20)
        val elementName = strings.getOrNull(nameIndex).orEmpty()
        val attributeStart = buffer.u16(chunkOffset + 24)
        val attributeSize = buffer.u16(chunkOffset + 26)
        val attributeCount = buffer.u16(chunkOffset + 28)
        val attributesOffset = chunkOffset + XML_NODE_HEADER_SIZE + attributeStart
        val attrs = mutableMapOf<String, String>()
        for (index in 0 until attributeCount) {
            val attributeOffset = attributesOffset + index * attributeSize
            if (attributeOffset + 20 > buffer.capacity()) break
            val attributeName = strings.getOrNull(buffer.i32(attributeOffset + 4)).orEmpty()
            val rawValueIndex = buffer.i32(attributeOffset + 8)
            val valueType = buffer.u8(attributeOffset + 15)
            val valueData = buffer.i32(attributeOffset + 16)
            val value = readAttributeValue(
                strings = strings,
                rawValueIndex = rawValueIndex,
                valueType = valueType,
                valueData = valueData
            )
            if (attributeName.isNotBlank() && value.isNotBlank()) {
                attrs[attributeName.removeAndroidNamespace()] = value
            }
        }
        return BinaryXmlStartElement(
            name = elementName,
            attributes = attrs
        )
    }

    private fun readAttributeValue(
        strings: List<String>,
        rawValueIndex: Int,
        valueType: Int,
        valueData: Int
    ): String {
        return when {
            rawValueIndex >= 0 -> strings.getOrNull(rawValueIndex).orEmpty()
            valueType == TYPE_STRING -> strings.getOrNull(valueData).orEmpty()
            valueType == TYPE_INT_BOOLEAN -> (valueData != 0).toString()
            valueType == TYPE_INT_DEC -> valueData.toString()
            valueType == TYPE_INT_HEX -> "0x${valueData.toUInt().toString(16)}"
            valueType != 0 -> valueData.toString()
            else -> ""
        }.trim()
    }

    private fun String.removeAndroidNamespace(): String {
        return substringAfter(':')
    }

    private fun String.isLiteralManifestLabel(): Boolean {
        val label = trim()
        return label.isNotBlank() &&
                !label.startsWith("@") &&
                !label.startsWith("?") &&
                label.toIntOrNull() == null
    }

    private fun nextChunkStep(
        buffer: ByteBuffer,
        offset: Int,
        chunkType: Int,
        chunkSize: Int
    ): Int {
        return if (chunkType == CHUNK_XML) {
            buffer.u16(offset + 2).coerceAtLeast(8)
        } else {
            chunkSize
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

    private data class StringLength(
        val value: Int,
        val nextOffset: Int
    )

    private data class BinaryXmlStartElement(
        val name: String,
        val attributes: Map<String, String>
    ) {
        fun attr(name: String): String = attributes[name].orEmpty()

        fun toManifestNode(): GitHubApkManifestNode {
            val display = attr("name")
                .ifBlank { attr("authorities") }
                .ifBlank { attr("scheme") }
                .ifBlank { attr("host") }
                .ifBlank { name }
            return GitHubApkManifestNode(
                tagName = name,
                displayName = display,
                attributes = attributes
            )
        }
    }
}
