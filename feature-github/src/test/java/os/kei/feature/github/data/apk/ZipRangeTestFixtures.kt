package os.kei.feature.github.data.apk

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ZipRangeTestFixtures {
    fun zipWithManifest(manifestBytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zip.write(manifestBytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    fun zipWithStoredEntry(
        entryName: String,
        bytes: ByteArray
    ): ByteArray {
        return zipWithStoredEntries(listOf(entryName to bytes))
    }

    fun zipWithStoredEntries(entries: List<Pair<String, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (entryName, bytes) ->
                val crc = CRC32().apply { update(bytes) }.value
                val entry = ZipEntry(entryName).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    this.crc = crc
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun rangeDispatcher(bytes: ByteArray): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range").orEmpty()
                val responseBytes = when {
                    range.startsWith("bytes=-") -> {
                        val count = range.removePrefix("bytes=-").toInt()
                        bytes.copyOfRange((bytes.size - count).coerceAtLeast(0), bytes.size)
                    }

                    range.startsWith("bytes=") -> {
                        val parts = range.removePrefix("bytes=").split('-', limit = 2)
                        val start = parts[0].toInt()
                        val end = parts[1].toInt().coerceAtMost(bytes.lastIndex)
                        bytes.copyOfRange(start, end + 1)
                    }

                    else -> bytes
                }
                val start = when {
                    range.startsWith("bytes=-") -> bytes.size - responseBytes.size
                    range.startsWith("bytes=") -> {
                        range.removePrefix("bytes=").substringBefore('-').toInt()
                    }

                    else -> 0
                }
                val end = start + responseBytes.size - 1
                return MockResponse()
                    .setResponseCode(206)
                    .addHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setBody(Buffer().write(responseBytes))
            }
        }
    }
}
