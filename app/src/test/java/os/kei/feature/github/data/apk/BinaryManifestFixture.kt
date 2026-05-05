package os.kei.feature.github.data.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object BinaryManifestFixture {
    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val UTF8_FLAG = 0x00000100
    private const val TYPE_STRING = 0x03

    fun build(packageName: String): ByteArray {
        val stringPool = stringPool(listOf("manifest", "package", packageName))
        val startElement = manifestStartElement(packageNameIndex = 2)
        val totalSize = 8 + stringPool.size + startElement.size
        return ByteArrayOutputStream().apply {
            writeU16(RES_XML_TYPE)
            writeU16(8)
            writeI32(totalSize)
            write(stringPool)
            write(startElement)
        }.toByteArray()
    }

    private fun stringPool(strings: List<String>): ByteArray {
        val offsets = mutableListOf<Int>()
        val data = ByteArrayOutputStream()
        strings.forEach { value ->
            offsets += data.size()
            val bytes = value.toByteArray(Charsets.UTF_8)
            data.writeLength8(value.length)
            data.writeLength8(bytes.size)
            data.write(bytes)
            data.write(0)
        }
        val stringsStart = 28 + offsets.size * 4
        val totalSize = stringsStart + data.size()
        return ByteArrayOutputStream().apply {
            writeU16(RES_STRING_POOL_TYPE)
            writeU16(28)
            writeI32(totalSize)
            writeI32(strings.size)
            writeI32(0)
            writeI32(UTF8_FLAG)
            writeI32(stringsStart)
            writeI32(0)
            offsets.forEach { offset -> writeI32(offset) }
            write(data.toByteArray())
        }.toByteArray()
    }

    private fun manifestStartElement(packageNameIndex: Int): ByteArray {
        val chunkSize = 56
        return ByteArrayOutputStream().apply {
            writeU16(RES_XML_START_ELEMENT_TYPE)
            writeU16(16)
            writeI32(chunkSize)
            writeI32(1)
            writeI32(-1)
            writeI32(-1)
            writeI32(0)
            writeU16(20)
            writeU16(20)
            writeU16(1)
            writeU16(0)
            writeU16(0)
            writeU16(0)
            writeI32(-1)
            writeI32(1)
            writeI32(packageNameIndex)
            writeU16(8)
            write(0)
            write(TYPE_STRING)
            writeI32(packageNameIndex)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLength8(value: Int) {
        if (value > 0x7F) {
            write(((value shr 8) and 0x7F) or 0x80)
            write(value and 0xFF)
        } else {
            write(value)
        }
    }

    private fun ByteArrayOutputStream.writeU16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeI32(value: Int) {
        val bytes = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(value)
            .array()
        write(bytes)
    }
}
