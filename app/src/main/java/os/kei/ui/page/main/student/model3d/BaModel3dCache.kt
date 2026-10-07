package os.kei.ui.page.main.student.model3d

import android.content.Context
import androidx.annotation.WorkerThread
import okhttp3.Call
import okhttp3.Request
import os.kei.core.io.SharedHttpClient
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Immutable models are verified as Git blobs before the browser sees any bytes. */
internal class BaModel3dFileCache(private val directory: File, private val budget: Long = 256L * 1024 * 1024,
    baseClient: okhttp3.OkHttpClient = SharedHttpClient.base,
) {
    private val client = baseClient.newBuilder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    // Serialize commit/open/eviction so another request cannot prune a verified file before it opens.
    @WorkerThread @Synchronized fun open(asset: BaModel3dAsset, session: BaModel3dCacheSession): InputStream {
            directory.mkdirs()
            val file = File(directory, asset.gitBlob + ".glb")
            session.checkOpen()
            if (!valid(file, asset)) {
                val temp = File.createTempFile(asset.gitBlob, ".part", directory)
                try {
                    val call = client.newCall(Request.Builder().url(asset.url).build())
                    session.track(call)
                    try {
                        call.execute().use { response ->
                            require(response.code == 200) { "Model HTTP ${response.code}" }
                            response.body.byteStream().use { input -> temp.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var written = 0L
                                while (true) {
                                    session.checkOpen()
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    written += count
                                    require(written <= asset.bytes) { "Oversized model" }
                                    output.write(buffer, 0, count)
                                }
                            } }
                        }
                    } finally { session.untrack(call) }
                    session.checkOpen()
                    require(valid(temp, asset)) { "Model integrity mismatch" }
                    require(temp.renameTo(file)) { "Cannot commit model cache" }
                } finally { temp.delete() }
            }
            file.setLastModified(System.currentTimeMillis())
            // Open before pruning so the current asset survives an eviction pass on Unix.
            val stream = file.inputStream()
            prune(file)
            return session.track(stream)
    }

    private fun valid(file: File, asset: BaModel3dAsset): Boolean {
        if (!file.isFile || file.length() != asset.bytes) return false
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${asset.bytes}\u0000".toByteArray(Charsets.UTF_8))
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == asset.gitBlob
    }

    @Synchronized private fun prune(current: File) {
        val files = directory.listFiles()?.filter { it.extension == "glb" }.orEmpty().sortedBy(File::lastModified)
        var size = files.sumOf(File::length)
        for (file in files) {
            if (size <= budget) break
            if (file != current) { val bytes = file.length(); if (file.delete()) size -= bytes }
        }
    }
}

internal class BaModel3dCacheSession : AutoCloseable {
    private val closed = AtomicBoolean()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val streams = ConcurrentHashMap.newKeySet<InputStream>()
    fun checkOpen() { if (closed.get()) throw InterruptedIOException("Model viewer closed") }
    fun track(call: Call) { calls.add(call); if (closed.get()) call.cancel() }
    fun untrack(call: Call) { calls.remove(call) }
    fun track(input: InputStream): InputStream {
        val stream = object : java.io.FilterInputStream(input) {
            override fun close() { try { super.close() } finally { streams.remove(this) } }
        }
        streams.add(stream)
        if (closed.get()) { stream.close(); checkOpen() }
        return stream
    }
    override fun close() { closed.set(true); calls.forEach(Call::cancel); streams.forEach { runCatching { it.close() } } }
}

internal object BaModel3dCache {
    private var instance: BaModel3dFileCache? = null
    @Synchronized fun get(context: Context): BaModel3dFileCache = instance ?: BaModel3dFileCache(
        File(context.applicationContext.cacheDir, "ba_models_3d"),
    ).also { instance = it }
}
