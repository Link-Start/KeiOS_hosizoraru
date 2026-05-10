package os.kei.core.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.Closeable
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ApkInstallSessionParams(
    val packageName: String,
    val totalSizeBytes: Long,
    val replaceExisting: Boolean,
    val allowTestOnly: Boolean
)

data class PackageInstallCommitResult(
    val status: Int,
    val legacyStatus: Int,
    val message: String,
    val userAction: Intent?
) {
    val success: Boolean
        get() = status == PackageInstaller.STATUS_SUCCESS

    val pendingUserAction: Boolean
        get() = status == PackageInstaller.STATUS_PENDING_USER_ACTION && userAction != null
}

interface PackageInstallerSessionHandle : Closeable {
    suspend fun write(
        entry: ApkInstallEntry,
        onBytesWritten: suspend (Long) -> Unit = {}
    )

    suspend fun commit(): PackageInstallCommitResult
}

interface PackageInstallerSessionGateway {
    suspend fun createSession(params: ApkInstallSessionParams): Int
    suspend fun openSession(sessionId: Int): PackageInstallerSessionHandle
    suspend fun abandonSession(sessionId: Int)
}

class AndroidPackageInstallerSessionGateway(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PackageInstallerSessionGateway {
    override suspend fun createSession(params: ApkInstallSessionParams): Int =
        withContext(ioDispatcher) {
            val packageInstaller = resolveShizukuPackageInstaller()
            packageInstaller.createSession(params.toSessionParams())
        }

    override suspend fun openSession(sessionId: Int): PackageInstallerSessionHandle =
        withContext(ioDispatcher) {
            val session = resolveShizukuPackageInstaller()
                .openSession(sessionId)
                .wrapSessionBinder()
            AndroidPackageInstallerSessionHandle(
                context = context.applicationContext,
                session = session,
                ioDispatcher = ioDispatcher
            )
        }

    override suspend fun abandonSession(sessionId: Int) {
        withContext(ioDispatcher) {
            runCatching { resolveShizukuPackageInstaller().abandonSession(sessionId) }
        }
    }

    private fun ApkInstallSessionParams.toSessionParams(): PackageInstaller.SessionParams {
        val mode = PackageInstaller.SessionParams.MODE_FULL_INSTALL
        return PackageInstaller.SessionParams(mode).apply {
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (packageName.isNotBlank()) setAppPackageName(packageName)
            setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
            applyOptionalInstallFlags(this@toSessionParams)
        }
    }

    private fun PackageInstaller.SessionParams.applyOptionalInstallFlags(
        params: ApkInstallSessionParams
    ) {
        runCatching {
            val flagsField = PackageInstaller.SessionParams::class.java
                .getDeclaredField("installFlags")
                .apply { isAccessible = true }
            var flags = flagsField.getInt(this)
            if (params.replaceExisting) {
                flags = flags or hiddenPackageManagerFlag("INSTALL_REPLACE_EXISTING")
            }
            if (params.allowTestOnly) {
                flags = flags or hiddenPackageManagerFlag("INSTALL_ALLOW_TEST")
            }
            flagsField.setInt(this, flags)
        }
    }

    private fun hiddenPackageManagerFlag(name: String): Int {
        return runCatching {
            PackageManager::class.java.getDeclaredField(name).apply {
                isAccessible = true
            }.getInt(null)
        }.getOrDefault(0)
    }

    private fun resolveShizukuPackageInstaller(): PackageInstaller {
        val packageBinder = SystemServiceHelper.getSystemService("package")
            ?: error("package system service unavailable")
        val wrappedPackageBinder = ShizukuBinderWrapper(packageBinder)
        val packageManagerInterface = invokeStaticAsInterface(
            className = "android.content.pm.IPackageManager\$Stub",
            binder = wrappedPackageBinder
        )
        val packageInstallerInterface = packageManagerInterface.javaClass.methods
            .firstOrNull { method ->
                method.name == "getPackageInstaller" && method.parameterTypes.isEmpty()
            }
            ?.invoke(packageManagerInterface)
            ?: error("IPackageManager.getPackageInstaller unavailable")
        return newPackageInstaller(
            wrapBinderInterface(
                target = packageInstallerInterface,
                stubClassName = "android.content.pm.IPackageInstaller\$Stub",
                debugName = "IPackageInstaller"
            )
        )
    }

    private fun invokeStaticAsInterface(className: String, binder: IBinder): Any {
        val stubClass = Class.forName(className)
        val method = stubClass.getMethod("asInterface", IBinder::class.java)
        return method.invoke(null, binder) ?: error("$className.asInterface returned null")
    }

    private fun wrapBinderInterface(
        target: Any,
        stubClassName: String,
        debugName: String
    ): Any {
        val iInterface = target as? IInterface
            ?: error("$debugName is not an IInterface")
        val wrappedBinder = ShizukuBinderWrapper(iInterface.asBinder())
        return invokeStaticAsInterface(stubClassName, wrappedBinder)
    }

    private fun PackageInstaller.Session.wrapSessionBinder(): PackageInstaller.Session {
        val sessionField = javaClass.getDeclaredField("mSession").apply {
            isAccessible = true
        }
        val rawSession = sessionField.get(this) as? IInterface
            ?: error("IPackageInstallerSession unavailable")
        val wrappedSession = wrapBinderInterface(
            target = rawSession,
            stubClassName = "android.content.pm.IPackageInstallerSession\$Stub",
            debugName = "IPackageInstallerSession"
        )
        sessionField.set(this, wrappedSession)
        return this
    }

    private fun newPackageInstaller(iPackageInstaller: Any): PackageInstaller {
        val userId = Process.myUid() / USER_ID_DIVISOR
        val constructor = PackageInstaller::class.java.declaredConstructors
            .sortedByDescending { it.parameterTypes.size }
            .firstOrNull { ctor ->
                val types = ctor.parameterTypes
                types.size in 3..4 &&
                        types[0].name == "android.content.pm.IPackageInstaller" &&
                        types[1] == String::class.java &&
                        types.last() == Int::class.javaPrimitiveType
            }
            ?: error("PackageInstaller hidden constructor unavailable")
        constructor.isAccessible = true
        val args = if (constructor.parameterTypes.size == 4) {
            arrayOf(iPackageInstaller, context.packageName, null, userId)
        } else {
            arrayOf(iPackageInstaller, context.packageName, userId)
        }
        return constructor.newInstance(*args) as PackageInstaller
    }

    private companion object {
        const val USER_ID_DIVISOR = 100000
    }
}

private class AndroidPackageInstallerSessionHandle(
    private val context: Context,
    private val session: PackageInstaller.Session,
    private val ioDispatcher: CoroutineDispatcher
) : PackageInstallerSessionHandle {
    override suspend fun write(
        entry: ApkInstallEntry,
        onBytesWritten: suspend (Long) -> Unit
    ) {
        withContext(ioDispatcher) {
            entry.openInputStream().use { input ->
                session.openWrite(entry.name, 0, entry.sizeBytes).use { output ->
                    copyToSession(
                        input = input,
                        output = output,
                        sizeBytes = entry.sizeBytes,
                        onBytesWritten = onBytesWritten
                    )
                    session.fsync(output)
                }
            }
        }
    }

    override suspend fun commit(): PackageInstallCommitResult = withContext(ioDispatcher) {
        val requestId = UUID.randomUUID().toString()
        val result = ShizukuInstallCommitResultStore.register(requestId)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            Intent(context, ShizukuInstallResultReceiver::class.java).apply {
                action = ShizukuInstallResultReceiver.ACTION_INSTALL_COMMIT_RESULT
                putExtra(ShizukuInstallResultReceiver.EXTRA_REQUEST_ID, requestId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        try {
            session.commit(pendingIntent.intentSender)
            parseCommitResult(
                withTimeout(COMMIT_TIMEOUT_MS) { result.await() }
            )
        } catch (error: TimeoutCancellationException) {
            PackageInstallCommitResult(
                status = PackageInstaller.STATUS_FAILURE_TIMEOUT,
                legacyStatus = PackageInstaller.STATUS_FAILURE_TIMEOUT,
                message = error.message.orEmpty().ifBlank { "PackageInstaller commit timed out" },
                userAction = null
            )
        } finally {
            ShizukuInstallCommitResultStore.unregister(requestId)
        }
    }

    override fun close() {
        session.close()
    }

    private suspend fun copyToSession(
        input: InputStream,
        output: java.io.OutputStream,
        sizeBytes: Long,
        onBytesWritten: suspend (Long) -> Unit
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read.toLong()
            onBytesWritten(if (sizeBytes > 0L) written.coerceAtMost(sizeBytes) else written)
        }
        output.flush()
    }

    private fun parseCommitResult(intent: Intent): PackageInstallCommitResult {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, status)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        return PackageInstallCommitResult(
            status = status,
            legacyStatus = legacyStatus,
            message = message,
            userAction = intent.installUserActionIntent()
        )
    }

    private fun Intent.installUserActionIntent(): Intent? {
        return getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 16 * 1024
        const val COMMIT_TIMEOUT_MS = 10 * 60 * 1000L
        const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
    }
}

object ShizukuInstallCommitResultStore {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Intent>>()

    fun register(requestId: String): CompletableDeferred<Intent> {
        return CompletableDeferred<Intent>().also { pending[requestId] = it }
    }

    fun complete(requestId: String, intent: Intent) {
        pending[requestId]?.complete(intent)
    }

    fun unregister(requestId: String) {
        pending.remove(requestId)
    }
}
