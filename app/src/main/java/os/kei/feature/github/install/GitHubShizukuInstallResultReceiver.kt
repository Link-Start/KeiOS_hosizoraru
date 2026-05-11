package os.kei.feature.github.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class GitHubShizukuInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        GitHubShizukuInstallCommitRegistry.complete(context, intent)
    }
}

internal data class GitHubShizukuInstallCommitResult(
    val requestId: String,
    val sessionId: Int,
    val statusCode: Int,
    val legacyStatus: Int,
    val message: String,
    val packageName: String
)

internal object GitHubShizukuInstallCommitRegistry {
    private const val ACTION_INSTALL_RESULT_SUFFIX =
        ".github.install.action.SHIZUKU_INSTALL_RESULT"
    private const val EXTRA_REQUEST_ID_SUFFIX = ".github.install.extra.REQUEST_ID"
    private const val EXTRA_SESSION_ID_SUFFIX = ".github.install.extra.SESSION_ID"
    private const val EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS"
    private const val INSTALL_FAILED_INTERNAL_ERROR = -110

    private val pendingResults =
        ConcurrentHashMap<String, CompletableDeferred<GitHubShizukuInstallCommitResult>>()

    fun register(requestId: String): CompletableDeferred<GitHubShizukuInstallCommitResult> {
        return CompletableDeferred<GitHubShizukuInstallCommitResult>().also { deferred ->
            pendingResults[requestId] = deferred
        }
    }

    fun unregister(requestId: String) {
        pendingResults.remove(requestId)
    }

    fun buildIntentSender(
        context: Context,
        requestId: String,
        sessionId: Int
    ): android.content.IntentSender {
        return buildPendingIntent(context, requestId, sessionId).intentSender
    }

    internal fun buildPendingIntent(
        context: Context,
        requestId: String,
        sessionId: Int
    ): PendingIntent {
        val packageName = context.packageName
        val intent = Intent(context, GitHubShizukuInstallResultReceiver::class.java).apply {
            action = installResultAction(packageName)
            setPackage(packageName)
            putExtra(requestIdExtra(packageName), requestId)
            putExtra(sessionIdExtra(packageName), sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun complete(context: Context, intent: Intent?): Boolean {
        val packageName = context.packageName
        val action = intent?.action ?: return false
        if (action != installResultAction(packageName)) {
            return false
        }
        val requestId = intent.getStringExtra(requestIdExtra(packageName)).orEmpty()
        if (requestId.isBlank()) return false
        val deferred = pendingResults.remove(requestId) ?: return false
        val result = GitHubShizukuInstallCommitResult(
            requestId = requestId,
            sessionId = intent.getIntExtra(sessionIdExtra(packageName), -1),
            statusCode = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            ),
            legacyStatus = intent.getIntExtra(EXTRA_LEGACY_STATUS, INSTALL_FAILED_INTERNAL_ERROR),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty(),
            packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME).orEmpty()
        )
        deferred.complete(result)
        return true
    }

    internal fun installResultAction(packageName: String): String {
        return packageName.trim().ifBlank { "os.kei" } + ACTION_INSTALL_RESULT_SUFFIX
    }

    private fun requestIdExtra(packageName: String): String {
        return packageName.trim().ifBlank { "os.kei" } + EXTRA_REQUEST_ID_SUFFIX
    }

    private fun sessionIdExtra(packageName: String): String {
        return packageName.trim().ifBlank { "os.kei" } + EXTRA_SESSION_ID_SUFFIX
    }
}
