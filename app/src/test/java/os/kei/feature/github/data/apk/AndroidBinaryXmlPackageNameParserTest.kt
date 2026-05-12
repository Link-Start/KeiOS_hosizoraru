package os.kei.feature.github.data.apk

import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals

class AndroidBinaryXmlPackageNameParserTest {
    @Test
    fun `parser reads package name from binary Android manifest`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.debug")

        val packageName = AndroidBinaryXmlPackageNameParser.parsePackageName(manifest).getOrThrow()

        assertEquals("os.kei.debug", packageName)
    }

    @Test
    fun `parser reads manifest info from binary Android manifest`() {
        val manifest = BinaryManifestFixture.build(packageName = "os.kei.demo")

        val info = AndroidBinaryXmlPackageNameParser.parseManifestInfo(manifest).getOrThrow()

        assertEquals("os.kei.demo", info.packageName)
    }

    @Test
    fun `parser resolves application label from resources arsc string`() {
        val labelResourceId = 0x7F010000
        val manifest = BinaryManifestFixture.buildWithApplicationLabelResource(
            packageName = "os.kei.label",
            labelResourceId = labelResourceId
        )
        val resources = BinaryResourceTableFixture.stringResourceTable(
            resourceId = labelResourceId,
            key = "app_name",
            value = "InstallerX Revived"
        )

        val info = AndroidBinaryXmlPackageNameParser.parseManifestInfo(
            manifestBytes = manifest,
            resourceTableBytes = resources
        ).getOrThrow()

        assertEquals("os.kei.label", info.packageName)
        assertEquals("InstallerX Revived", info.appLabel)
    }
}

internal object BinaryResourceTableFixture {
    private const val RES_TABLE_TYPE = 0x0002
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_TABLE_PACKAGE_TYPE = 0x0200
    private const val RES_TABLE_TYPE_TYPE = 0x0201
    private const val UTF8_FLAG = 0x00000100
    private const val VALUE_TYPE_STRING = 0x03
    private const val CONFIG_SIZE = 64
    private const val PACKAGE_HEADER_SIZE = 288

    fun stringResourceTable(
        resourceId: Int,
        key: String,
        value: String
    ): ByteArray {
        val packageId = resourceId ushr 24 and 0xFF
        val typeId = resourceId ushr 16 and 0xFF
        val entryId = resourceId and 0xFFFF
        val globalStringPool = stringPool(listOf(value))
        val typeStringPool = stringPool(listOf("string"))
        val keyStringPool = stringPool(listOf(key))
        val typeChunk = typeChunk(
            typeId = typeId,
            entryId = entryId,
            valueStringIndex = 0
        )
        val packageChunk = packageChunk(
            packageId = packageId,
            typeStringPool = typeStringPool,
            keyStringPool = keyStringPool,
            typeChunk = typeChunk
        )
        val totalSize = 12 + globalStringPool.size + packageChunk.size
        return ByteArrayOutputStream().apply {
            writeU16(RES_TABLE_TYPE)
            writeU16(12)
            writeI32(totalSize)
            writeI32(1)
            write(globalStringPool)
            write(packageChunk)
        }.toByteArray()
    }

    private fun packageChunk(
        packageId: Int,
        typeStringPool: ByteArray,
        keyStringPool: ByteArray,
        typeChunk: ByteArray
    ): ByteArray {
        val keyStringOffset = PACKAGE_HEADER_SIZE + typeStringPool.size
        val totalSize =
            PACKAGE_HEADER_SIZE + typeStringPool.size + keyStringPool.size + typeChunk.size
        return ByteArrayOutputStream().apply {
            writeU16(RES_TABLE_PACKAGE_TYPE)
            writeU16(PACKAGE_HEADER_SIZE)
            writeI32(totalSize)
            writeI32(packageId)
            write(ByteArray(256))
            writeI32(PACKAGE_HEADER_SIZE)
            writeI32(0)
            writeI32(keyStringOffset)
            writeI32(0)
            writeI32(0)
            write(typeStringPool)
            write(keyStringPool)
            write(typeChunk)
        }.toByteArray()
    }

    private fun typeChunk(
        typeId: Int,
        entryId: Int,
        valueStringIndex: Int
    ): ByteArray {
        val entryCount = entryId + 1
        val headerSize = 20 + CONFIG_SIZE
        val entriesStart = headerSize + entryCount * 4
        val entry = ByteArrayOutputStream().apply {
            writeU16(8)
            writeU16(0)
            writeI32(0)
            writeU16(8)
            write(0)
            write(VALUE_TYPE_STRING)
            writeI32(valueStringIndex)
        }.toByteArray()
        val totalSize = entriesStart + entry.size
        return ByteArrayOutputStream().apply {
            writeU16(RES_TABLE_TYPE_TYPE)
            writeU16(headerSize)
            writeI32(totalSize)
            write(typeId)
            write(0)
            writeU16(0)
            writeI32(entryCount)
            writeI32(entriesStart)
            writeI32(CONFIG_SIZE)
            write(ByteArray(CONFIG_SIZE - 4))
            repeat(entryId) { writeI32(-1) }
            writeI32(0)
            write(entry)
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
