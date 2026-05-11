package os.kei.feature.github.install

import android.Manifest
import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.IBinder
import org.lsposed.hiddenapibypass.LSPass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

data class ShizukuPackageInstallerCapability(
    val available: Boolean,
    val failureReason: GitHubApkInstallFailureReason? = null,
    val message: String = ""
)

open class ShizukuPackageInstallerBridge {
    open fun checkCapability(): ShizukuPackageInstallerCapability {
        return runCatching {
            when {
                !Shizuku.pingBinder() -> ShizukuPackageInstallerCapability(
                    available = false,
                    failureReason = GitHubApkInstallFailureReason.ShizukuUnavailable,
                    message = "Shizuku service is not running"
                )

                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                    ShizukuPackageInstallerCapability(
                        available = false,
                        failureReason = GitHubApkInstallFailureReason.ShizukuPermissionMissing,
                        message = "KeiOS has no Shizuku permission"
                    )

                Shizuku.checkRemotePermission(Manifest.permission.INSTALL_PACKAGES) !=
                        PackageManager.PERMISSION_GRANTED ->
                    ShizukuPackageInstallerCapability(
                        available = false,
                        failureReason =
                            GitHubApkInstallFailureReason.RemoteInstallPermissionMissing,
                        message = "Shizuku remote process has no INSTALL_PACKAGES permission"
                    )

                else -> ShizukuPackageInstallerCapability(available = true)
            }
        }.getOrElse { error ->
            ShizukuPackageInstallerCapability(
                available = false,
                failureReason = GitHubApkInstallFailureReason.ShizukuUnavailable,
                message = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
            )
        }
    }

    open fun packageInstaller(context: Context): PackageInstaller {
        enableHiddenApiAccess()
        return context.packageManager.packageInstaller.also(::wrapPackageInstaller)
    }

    open fun wrapSession(session: PackageInstaller.Session): PackageInstaller.Session {
        enableHiddenApiAccess()
        wrapBinderBackedField(
            target = session,
            fieldName = "mSession",
            stubClassName = "android.content.pm.IPackageInstallerSession\$Stub"
        )
        return session
    }

    open fun applySessionParams(
        params: PackageInstaller.SessionParams,
        context: Context,
        appPackageName: String
    ) {
        params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        params.setOriginatingUid(context.applicationInfo.uid)
        params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
        if (appPackageName.isNotBlank()) {
            params.setAppPackageName(appPackageName)
        }
        invokeIfPresent(
            target = params,
            methodName = "setInstallerPackageName",
            parameterTypes = arrayOf(String::class.java),
            args = arrayOf(context.packageName)
        )
        applyInstallFlags(params)
    }

    private fun wrapPackageInstaller(installer: PackageInstaller) {
        wrapBinderBackedField(
            target = installer,
            fieldName = "mInstaller",
            stubClassName = "android.content.pm.IPackageInstaller\$Stub"
        )
    }

    private fun wrapBinderBackedField(
        target: Any,
        fieldName: String,
        stubClassName: String
    ) {
        val field = target.javaClass.findDeclaredField(fieldName)
        field.isAccessible = true
        val current = field.get(target) ?: return
        val binder = current.javaClass.methods
            .firstOrNull { method ->
                method.name == "asBinder" &&
                        method.parameterTypes.isEmpty() &&
                        IBinder::class.java.isAssignableFrom(method.returnType)
            }
            ?.invoke(current) as? IBinder ?: return
        val wrappedBinder = ShizukuBinderWrapper(binder)
        val wrappedInterface = Class.forName(stubClassName)
            .getDeclaredMethod("asInterface", IBinder::class.java)
            .invoke(null, wrappedBinder)
        field.set(target, wrappedInterface)
    }

    private fun applyInstallFlags(params: PackageInstaller.SessionParams) {
        val field = params.javaClass.findDeclaredField("installFlags")
        field.isAccessible = true
        var flags = field.getInt(params)
        listOf(
            "INSTALL_ALLOW_TEST",
            "INSTALL_REPLACE_EXISTING",
            "INSTALL_REQUEST_DOWNGRADE",
            "INSTALL_FULL_APP",
            "INSTALL_BYPASS_LOW_TARGET_SDK_BLOCK",
            "INSTALL_REQUEST_UPDATE_OWNERSHIP"
        ).forEach { fieldName ->
            flags = flags or packageManagerFlag(fieldName)
        }
        field.setInt(params, flags)
    }

    private fun packageManagerFlag(name: String): Int {
        return runCatching {
            PackageManager::class.java.findDeclaredField(name).let { field ->
                field.isAccessible = true
                field.getInt(null)
            }
        }.getOrDefault(0)
    }

    private fun invokeIfPresent(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        args: Array<Any>
    ) {
        runCatching {
            target.javaClass.getMethod(methodName, *parameterTypes).invoke(target, *args)
        }
    }

    private fun enableHiddenApiAccess() {
        runCatching {
            LSPass.setHiddenApiExemptions("")
        }
    }
}

private fun Class<*>.findDeclaredField(name: String): java.lang.reflect.Field {
    var current: Class<*>? = this
    while (current != null) {
        runCatching {
            return current.getDeclaredField(name)
        }
        current = current.superclass
    }
    throw NoSuchFieldException(name)
}
