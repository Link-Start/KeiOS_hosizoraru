package os.kei.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import os.kei.core.log.AppLogger
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

object ShizukuConnectivityBridge {
    private const val TAG = "ShizukuConnectivityBridge"
    private const val OEM_DENY_CHAIN = 9
    private const val FIREWALL_RULE_DEFAULT = 0
    private const val FIREWALL_RULE_DENY = 2

    private val lock = Any()

    @Volatile
    private var cachedApi: ConnectivityApi? = null

    @Volatile
    private var hiddenApiAccessEnabled = false

    fun canUseUidFirewall(): Boolean {
        return resolveConnectivityApi() != null
    }

    fun setUidNetworkingEnabled(uid: Int, enabled: Boolean): Boolean {
        if (uid <= 0) return false
        val api = resolveConnectivityApi() ?: return false
        return runCatching {
            if (enabled) {
                api.setUidFirewallRule(FIREWALL_RULE_DEFAULT, uid)
            } else {
                api.setFirewallChainEnabled(true)
                api.setUidFirewallRule(FIREWALL_RULE_DENY, uid)
            }
            true
        }.onFailure { error ->
            cachedApi = null
            AppLogger.w(
                TAG,
                "setUidNetworkingEnabled failed: uid=$uid enabled=$enabled error=${error.javaClass.simpleName}",
                error
            )
        }.getOrDefault(false)
    }

    private fun resolveConnectivityApi(): ConnectivityApi? {
        cachedApi?.let { return it }
        if (!canUseShizukuBinder()) return null
        synchronized(lock) {
            cachedApi?.let { return it }
            return createConnectivityApi().also { cachedApi = it }
        }
    }

    private fun createConnectivityApi(): ConnectivityApi? {
        return runCatching {
            enableHiddenApiAccess()
            val originalBinder = SystemServiceHelper.getSystemService(Context.CONNECTIVITY_SERVICE)
                ?: return null
            val wrappedBinder = ShizukuBinderWrapper(originalBinder)
            val stubClass = Class.forName("android.net.IConnectivityManager\$Stub")
            val interfaceClass = Class.forName("android.net.IConnectivityManager")
            val service = stubClass
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrappedBinder)
                ?: return null
            ConnectivityApi(
                service = service,
                setFirewallChainEnabled = interfaceClass.getMethod(
                    "setFirewallChainEnabled",
                    Int::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!
                ),
                setUidFirewallRule = interfaceClass.getMethod(
                    "setUidFirewallRule",
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                )
            )
        }.onFailure { error ->
            AppLogger.w(
                TAG,
                "createConnectivityApi failed: ${error.javaClass.simpleName}",
                error
            )
        }.getOrNull()
    }

    private fun enableHiddenApiAccess() {
        if (hiddenApiAccessEnabled) return
        synchronized(lock) {
            if (hiddenApiAccessEnabled) return
            runCatching {
                LSPass.setHiddenApiExemptions("")
            }.onSuccess {
                hiddenApiAccessEnabled = true
            }.onFailure { error ->
                AppLogger.w(TAG, "enableHiddenApiAccess failed: ${error.javaClass.simpleName}", error)
            }
        }
    }

    private fun canUseShizukuBinder(): Boolean {
        return runCatching {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED &&
                when (Shizuku.getUid()) {
                    0, 2000 -> true
                    else -> false
                }
        }.getOrDefault(false)
    }

    private data class ConnectivityApi(
        val service: Any,
        val setFirewallChainEnabled: Method,
        val setUidFirewallRule: Method
    ) {
        fun setFirewallChainEnabled(enabled: Boolean) {
            setFirewallChainEnabled.invoke(service, OEM_DENY_CHAIN, enabled)
        }

        fun setUidFirewallRule(rule: Int, uid: Int) {
            setUidFirewallRule.invoke(service, OEM_DENY_CHAIN, uid, rule)
        }
    }
}
