package os.kei.mcp.notification

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import os.kei.core.log.AppLogger
import os.kei.core.prefs.UiPrefs
import os.kei.core.system.ShizukuApiUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

internal object McpXiaomiMagicDispatcher {
    private const val TAG = "McpXiaomiMagic"
    private const val XMSF_PACKAGE_NAME = "com.xiaomi.xmsf"

    private enum class CommandSet {
        PACKAGE_NETWORKING,
        UID_FIREWALL,
        NONE
    }

    private val shizukuApiUtils = ShizukuApiUtils()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val networkMutex = Mutex()
    private val notificationStates = ConcurrentHashMap<Int, NotificationPulseState>()

    @Volatile
    private var commandSet: CommandSet? = null

    @Volatile
    private var isXmsfNetworkBlocked = false

    @Volatile
    private var isUidFirewallChainEnabled = false

    fun canUseCommand(): Boolean {
        return shizukuApiUtils.canUseCommand()
    }

    fun notify(
        context: Context,
        notificationId: Int,
        notification: Notification
    ) {
        val appContext = context.applicationContext
        val notificationManager = NotificationManagerCompat.from(appContext)
        val pulseState = notificationStates.computeIfAbsent(notificationId) {
            NotificationPulseState()
        }
        synchronized(pulseState) {
            pulseState.latest = notification
        }
        val targetUid = resolveXmsfUid(appContext)
        AppLogger.i(TAG, "notify: targetUid=$targetUid notifId=$notificationId")
        if (!shouldExecute(targetUid)) {
            AppLogger.w(TAG, "skip Xiaomi magic: preconditions not satisfied")
            if (canUseCommand()) {
                restoreNetworkIfNeeded(appContext)
            }
            notificationManager.notify(notificationId, notification)
            return
        }
        val nonNullUid = targetUid ?: run {
            AppLogger.w(TAG, "skip Xiaomi magic: xmsf uid is null")
            notificationManager.notify(notificationId, notification)
            return
        }
        val shouldLaunchPulse = synchronized(pulseState) {
            if (pulseState.pulseActive) {
                false
            } else {
                pulseState.pulseActive = true
                true
            }
        }
        if (!shouldLaunchPulse) {
            AppLogger.i(
                TAG,
                "merge Xiaomi magic pulse into active snapshot: notifId=$notificationId"
            )
            return
        }
        val pulseGeneration = pulseState.generation.get()

        scope.launch {
            try {
                networkMutex.withLock {
                    if (pulseState.generation.get() != pulseGeneration) {
                        AppLogger.i(
                            TAG,
                            "skip cancelled Xiaomi magic pulse: notifId=$notificationId"
                        )
                        return@withLock
                    }
                    var notificationDispatched = false
                    var networkTouched = false
                    try {
                        healXmsfNetworkingLocked(nonNullUid)
                        AppLogger.i(TAG, "blocking xmsf network for uid=$nonNullUid")
                        blockXmsfNetworkingLocked(nonNullUid)
                        networkTouched = isXmsfNetworkBlocked || isUidFirewallChainEnabled
                        if (pulseState.generation.get() != pulseGeneration) {
                            AppLogger.i(
                                TAG,
                                "skip superseded Xiaomi magic pulse: notifId=$notificationId"
                            )
                            return@withLock
                        }
                        var latestNotification = synchronized(pulseState) {
                            pulseState.latest
                        } ?: notification
                        notificationManager.notify(notificationId, latestNotification)
                        notificationDispatched = true
                        delay(resolveBlockIntervalMs().milliseconds)
                        latestNotification = synchronized(pulseState) {
                            pulseState.latest
                        } ?: latestNotification
                        notificationManager.notify(notificationId, latestNotification)
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        AppLogger.e(TAG, "Xiaomi magic execution failed", throwable)
                        if (!notificationDispatched) {
                            runCatching {
                                val latestNotification = synchronized(pulseState) {
                                    pulseState.latest
                                } ?: notification
                                notificationManager.notify(notificationId, latestNotification)
                            }.onFailure {
                                AppLogger.e(TAG, "Fallback notification dispatch failed", it)
                            }
                        }
                    } finally {
                        if (networkTouched || isXmsfNetworkBlocked || isUidFirewallChainEnabled) {
                            AppLogger.i(TAG, "restoring xmsf network for uid=$nonNullUid")
                            runCatching {
                                restoreXmsfNetworkingLocked(nonNullUid)
                            }.onFailure {
                                AppLogger.e(TAG, "Xiaomi magic network restoration failed", it)
                            }
                            runCatching {
                                healXmsfNetworkingLocked(nonNullUid)
                            }.onFailure {
                                AppLogger.e(TAG, "Xiaomi magic network healing failed", it)
                            }
                        }
                    }
                }
            } finally {
                synchronized(pulseState) {
                    if (pulseState.generation.get() == pulseGeneration) {
                        pulseState.pulseActive = false
                    }
                }
            }
        }
    }

    fun update(
        context: Context,
        notificationId: Int,
        notification: Notification
    ) {
        val appContext = context.applicationContext
        val notificationManager = NotificationManagerCompat.from(appContext)
        val pulseState = notificationStates.computeIfAbsent(notificationId) {
            NotificationPulseState()
        }
        val pulseActive = synchronized(pulseState) {
            pulseState.latest = notification
            pulseState.pulseActive
        }
        if (pulseActive) {
            AppLogger.i(
                TAG,
                "merge Xiaomi magic update into active snapshot: notifId=$notificationId"
            )
            return
        }
        notificationManager.notify(notificationId, notification)
    }

    fun invalidateNotification(notificationId: Int) {
        notificationStates.remove(notificationId)?.let { pulseState ->
            synchronized(pulseState) {
                pulseState.latest = null
                pulseState.pulseActive = false
                pulseState.generation.incrementAndGet()
            }
        }
    }

    fun restoreNetworkIfNeeded(context: Context) {
        val xmsfUid = resolveXmsfUid(context)
        scope.launch {
            networkMutex.withLock {
                if (xmsfUid != null && canUseCommand()) {
                    healXmsfNetworkingLocked(xmsfUid)
                }
            }
        }
    }

    private fun resolveXmsfUid(context: Context): Int? {
        return runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageUid(XMSF_PACKAGE_NAME, 0)
        }.getOrNull()?.takeIf { it > 0 }
    }

    private fun shouldExecute(xmsfUid: Int?): Boolean {
        if (xmsfUid == null) {
            AppLogger.w(TAG, "shouldExecute=false: xmsf uid not found")
            return false
        }
        if (!canUseCommand()) {
            AppLogger.w(TAG, "shouldExecute=false: Shizuku command unavailable")
            return false
        }
        val idOutput = shizukuApiUtils.execCommand("id").orEmpty()
        val isShellOrRoot = idOutput.contains("uid=2000") || idOutput.contains("uid=0")
        if (!isShellOrRoot) {
            AppLogger.w(TAG, "shouldExecute=false: unsupported Shizuku identity '$idOutput'")
            return false
        }
        val mode = resolveCommandSet()
        val canUseMode = mode != CommandSet.NONE
        AppLogger.i(TAG, "Shizuku id='$idOutput', mode=$mode, allowMagic=$canUseMode")
        return canUseMode
    }

    private fun blockXmsfNetworkingLocked(uid: Int) {
        when (resolveCommandSet()) {
            CommandSet.PACKAGE_NETWORKING -> {
                val blocked =
                    execCommand("cmd connectivity set-package-networking-enabled false $XMSF_PACKAGE_NAME")
                isXmsfNetworkBlocked = blocked
                AppLogger.i(TAG, "blockXmsfNetworkingLocked(package): uid=$uid blocked=$blocked")
            }

            CommandSet.UID_FIREWALL -> {
                val chainEnabled = execCommand("cmd connectivity set-firewall-chain-enabled 9 true")
                val blocked = execCommand("cmd connectivity set-uid-firewall-rule 9 $uid 2")
                isXmsfNetworkBlocked = blocked
                isUidFirewallChainEnabled = chainEnabled
                AppLogger.i(
                    TAG,
                    "blockXmsfNetworkingLocked(uid): uid=$uid chainEnabled=$chainEnabled blocked=$blocked"
                )
            }

            CommandSet.NONE -> {
                isXmsfNetworkBlocked = false
                isUidFirewallChainEnabled = false
                AppLogger.w(
                    TAG,
                    "blockXmsfNetworkingLocked skipped: no supported connectivity command"
                )
            }
        }
    }

    private fun restoreXmsfNetworkingLocked(uid: Int) {
        val mode = resolveCommandSet()
        val restored = when (mode) {
            CommandSet.PACKAGE_NETWORKING -> {
                if (isXmsfNetworkBlocked) {
                    execCommand("cmd connectivity set-package-networking-enabled true $XMSF_PACKAGE_NAME")
                } else {
                    true
                }
            }

            CommandSet.UID_FIREWALL -> {
                if (isXmsfNetworkBlocked) {
                    execCommand("cmd connectivity set-uid-firewall-rule 9 $uid 0")
                } else {
                    true
                }
            }

            CommandSet.NONE -> false
        }
        AppLogger.i(TAG, "restoreXmsfNetworkingLocked: uid=$uid mode=$mode restored=$restored")
        isXmsfNetworkBlocked = false
        isUidFirewallChainEnabled = false
    }

    private fun healXmsfNetworkingLocked(uid: Int) {
        when (resolveCommandSet()) {
            CommandSet.PACKAGE_NETWORKING -> {
                val restored =
                    execCommand("cmd connectivity set-package-networking-enabled true $XMSF_PACKAGE_NAME")
                AppLogger.i(TAG, "healXmsfNetworkingLocked(package): uid=$uid restored=$restored")
            }

            CommandSet.UID_FIREWALL -> {
                val ruleRestored = execCommand("cmd connectivity set-uid-firewall-rule 9 $uid 0")
                AppLogger.i(
                    TAG,
                    "healXmsfNetworkingLocked(uid): uid=$uid ruleRestored=$ruleRestored"
                )
            }

            CommandSet.NONE -> {
                AppLogger.w(
                    TAG,
                    "healXmsfNetworkingLocked skipped: no supported connectivity command"
                )
            }
        }
        isXmsfNetworkBlocked = false
        isUidFirewallChainEnabled = false
    }

    private fun resolveCommandSet(): CommandSet {
        commandSet?.let { return it }
        val helpText = shizukuApiUtils.execCommand("cmd connectivity help")
        if (helpText.isNullOrBlank()) {
            AppLogger.w(TAG, "resolveCommandSet skipped: connectivity help unavailable")
            return CommandSet.NONE
        }
        val resolved = when {
            helpText.contains("set-package-networking-enabled") -> CommandSet.PACKAGE_NETWORKING
            helpText.contains("set-firewall-chain-enabled") &&
                    helpText.contains("set-uid-firewall-rule") -> CommandSet.UID_FIREWALL

            else -> CommandSet.NONE
        }
        commandSet = resolved
        AppLogger.i(TAG, "resolved Xiaomi magic command set: $resolved")
        return resolved
    }

    private fun execCommand(command: String): Boolean {
        val output =
            shizukuApiUtils.execCommand("($command) >/dev/null 2>&1 && echo __OK__ || echo __FAIL__")
                ?: return false
        val success = output.contains("__OK__")
        if (!success) {
            AppLogger.w(TAG, "magic command failed: $command; output=$output")
        }
        return success
    }

    private fun resolveBlockIntervalMs(): Long {
        return UiPrefs.getSuperIslandRestoreDelayMs().toLong()
    }

    private class NotificationPulseState {
        val generation = AtomicLong(0L)
        var latest: Notification? = null
        var pulseActive: Boolean = false
    }
}
