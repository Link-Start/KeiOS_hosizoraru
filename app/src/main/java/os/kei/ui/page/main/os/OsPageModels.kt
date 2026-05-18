package os.kei.ui.page.main.os

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import os.kei.R
import os.kei.ui.page.main.os.shortcut.ShortcutIntentExtra
import os.kei.ui.page.main.os.shortcut.normalizeShortcutIntentExtras

@Immutable
internal data class InfoRow(
    val key: String,
    val value: String
)

internal enum class OsSectionCard(@param:StringRes val titleRes: Int) {
    TOP_INFO(R.string.os_section_top_info_title),
    SHELL_RUNNER(R.string.os_shell_card_title),
    GOOGLE_SYSTEM_SERVICE(R.string.os_section_google_system_service_title),
    SYSTEM(R.string.os_section_system_title),
    SECURE(R.string.os_section_secure_title),
    GLOBAL(R.string.os_section_global_title),
    ANDROID(R.string.os_section_android_title),
    JAVA(R.string.os_section_java_title),
    LINUX(R.string.os_section_linux_title)
}

internal fun OsSectionCard.title(context: Context): String = context.getString(titleRes)

@Composable
internal fun OsSectionCard.titleText(): String = stringResource(titleRes)

internal enum class SectionKind {
    SYSTEM,
    SECURE,
    GLOBAL,
    ANDROID,
    JAVA,
    LINUX
}

internal enum class SystemOverviewState {
    Idle,
    Cached,
    Refreshing,
    Completed,
    Failed
}

@Immutable
internal data class OsUiSnapshot(
    val topInfoExpanded: Boolean = false,
    val shellRunnerExpanded: Boolean = false,
    val googleSystemServiceExpanded: Boolean = false,
    val systemTableExpanded: Boolean = false,
    val secureTableExpanded: Boolean = false,
    val globalTableExpanded: Boolean = false,
    val androidPropsExpanded: Boolean = false,
    val javaPropsExpanded: Boolean = false,
    val linuxEnvExpanded: Boolean = false,
    val visibleCards: Set<OsSectionCard> = OsCardVisibilityStore.defaultVisibleCards()
)

@Immutable
internal data class OsGoogleSystemServiceConfig(
    val title: String = "Google system services",
    val subtitle: String = "Update Google system service app",
    val appName: String = "Google Play Store",
    val packageName: String = "com.android.vending",
    val className: String = "com.google.android.finsky.systemservicesactivity.SystemServicesActivity",
    val intentAction: String = "android.intent.action.VIEW",
    val intentCategory: String = "",
    val intentFlags: String = "FLAG_ACTIVITY_NEW_TASK",
    val intentUriData: String = "",
    val intentMimeType: String = "",
    val intentExtras: List<ShortcutIntentExtra> = emptyList()
) {
    fun normalized(defaults: OsGoogleSystemServiceConfig = OsGoogleSystemServiceConfig()): OsGoogleSystemServiceConfig {
        return copy(
            title = title.trim().ifBlank { defaults.title },
            subtitle = subtitle.trim(),
            appName = appName.trim().ifBlank { defaults.appName },
            packageName = packageName.trim().ifBlank { defaults.packageName },
            className = className.trim(),
            intentAction = intentAction.trim(),
            intentCategory = intentCategory.trim(),
            intentFlags = intentFlags.trim(),
            intentUriData = intentUriData.trim(),
            intentMimeType = intentMimeType.trim(),
            intentExtras = normalizeShortcutIntentExtras(intentExtras)
        )
    }
}

@Immutable
internal data class CachedSectionsSnapshot(
    val cached: CachedSections = CachedSections(),
    val hasPersistedCache: Boolean = false
)

@Immutable
internal data class SectionState(
    val rows: List<InfoRow> = emptyList(),
    val loading: Boolean = false,
    val loadedFresh: Boolean = false,
    val loadFailed: Boolean = false
)

@Immutable
internal data class CachedSections(
    val system: List<InfoRow> = emptyList(),
    val secure: List<InfoRow> = emptyList(),
    val global: List<InfoRow> = emptyList(),
    val android: List<InfoRow> = emptyList(),
    val java: List<InfoRow> = emptyList(),
    val linux: List<InfoRow> = emptyList()
)

internal object TopInfoKeys {
    val system = linkedSetOf(
        "locked_apps",
    )

    val secure = linkedSetOf(
        "FBO_STATE_OPEN",
        "fbo_status",
        "KEY_FBO_DATA",
        "aod_mode_user_set",
        "aod_show_style",
        "enabled_accessibility_services",
        "enabled_input_methods",
        "package_verifier_state",
        "autofill_service",
        "credential_service",
        "credential_service_primary"
    )

    val global = linkedSetOf(
        "adb_enabled",
        "adb_wifi_enabled",
        "device_name",
        "miui_current_version_branch",
        "miui_memory_size",
        "miui_ram_size",
        "miui_update_ready",
        "miui_version_name",
        "usb_mass_storage_enabled",
        "zram_enabled"
    )

    val android = linkedSetOf(
        "device.identity",
        "device.model",
        "device.product",
        "device.board_hardware",
        "device.first_api_level",
        "build.release_summary",
        "build.fingerprint",
        "runtime.cpu_summary",
        "runtime.abi.primary",
        "runtime.abi.supported",
        "runtime.abi.bitness",
        "runtime.vm",
        "runtime.zygote.mode",
        "runtime.sdk_extensions",
        "memory.ram",
        "storage.data",
        "environment.locale_timezone",
        "environment.debug_state",
        "partition.update_model",
        "partition.slot",
        "partition.fingerprinted",
        "partition.dynamic",
        "treble.enabled",
        "gsi.vndk",
        "mainline.apex_updatable",
        "verified_boot.state",
        "security.dm_verity",
        "security.patch_levels",
        "webview.provider",
        "kernel.page_size",
        "kernel.version",
        "graphics.opengl.es",
        "graphics.opengl.es.decoded",
        "graphics.vulkan.version",
        "graphics.vulkan.level",
        "graphics.gpu.driver.package",
        "graphics.gpu.driver.version",
        "feature.vulkan.version",
        "feature.opengl.aep",
        "feature.usb_host",
        "feature.wifi_aware",
        "prop.ro.hardware.egl",
        "prop.ro.hardware.vulkan",
        "dalvik.vm.usejit",
        "debug.device.usb_state",
        "gsm.network.type",
        "gsm.sim.state",
        "gsm.version.baseband",
        "persist.sys.computility.cpulevel",
        "persist.sys.computility.gpulevel",
        "persist.sys.device_config_gki",
        "persist.sys.locale",
        "persist.sys.memory.totalsize",
        "persist.sys.memory.user_freesize",
        "persist.sys.miui_resolution",
        "persist.sys.updater.version",
        "persist.sys.usb.config",
        "pm.dexopt.ab-ota",
        "pm.dexopt.baseline",
        "pm.dexopt.bg-dexopt",
        "pm.dexopt.boot-after-mainline-update",
        "pm.dexopt.boot-after-ota",
        "pm.dexopt.boot-after-ota.concurrency",
        "pm.dexopt.cmdline",
        "pm.dexopt.first-boot",
        "pm.dexopt.first-use",
        "pm.dexopt.inactive",
        "pm.dexopt.install",
        "pm.dexopt.secondary",
        "ro.adb.secure",
        "ro.ai.os.version.code",
        "ro.ai.os.version.name",
        "ro.apex.updatable",
        "ro.board.api_frozen",
        "ro.board.api_level",
        "ro.board.first_api_level",
        "ro.boot.flash.locked",
        "ro.boot.hardware.sku",
        "ro.boot.vbmeta.device_state",
        "ro.boot.verifiedbootstate",
        "ro.boot.dynamic_partitions",
        "ro.boot.slot_suffix",
        "ro.build.ab_update",
        "ro.build.description",
        "ro.build.display.id",
        "ro.build.id",
        "ro.build.product",
        "ro.build.tags",
        "ro.build.type",
        "ro.build.version.all_codenames",
        "ro.build.version.codename",
        "ro.build.version.incremental",
        "ro.build.version.min_supported_target_sdk",
        "ro.build.version.preview_sdk",
        "ro.build.version.release",
        "ro.build.version.release_or_codename",
        "ro.build.version.sdk",
        "ro.build.version.security_patch",
        "ro.hardware.egl",
        "ro.hardware.vulkan",
        "ro.llndk.api_level",
        "ro.mediaserver.64b.enable",
        "ro.mi.os.soc.vendor",
        "ro.mi.os.version.code",
        "ro.mi.os.version.incremental",
        "ro.mi.os.version.name",
        "ro.mi.os.version.publish",
        "ro.mi.xms.version.incremental",
        "ro.millet.netlink",
        "ro.miui.build.region",
        "ro.miui.business.version",
        "ro.miui.has_gmscore",
        "ro.miui.mcc",
        "ro.miui.mnc",
        "ro.miui.product.home",
        "ro.miui.region",
        "ro.miui.support_miui_ime_bottom",
        "ro.miui.ui.font.mi_font_path",
        "ro.miui.ui.font.mi_fonts_customization_xml",
        "ro.miui.ui.version.code",
        "ro.miui.ui.version.name",
        "ro.odm.build.media_performance_class",
        "ro.opengles.version",
        "ro.product.bootimage.marketname",
        "ro.product.brand",
        "ro.product.build.fingerprint",
        "ro.product.build.id",
        "ro.product.build.version.incremental",
        "ro.product.mod_device",
        "ro.secureboot.devicelock",
        "ro.secureboot.lockstate",
        "ro.sf.lcd_density",
        "ro.system.build.fingerprint",
        "ro.system.build.id",
        "ro.system.build.version.incremental",
        "ro.treble.enabled",
        "ro.vendor.api_level",
        "ro.vendor.build.fingerprint",
        "ro.vendor.build.security_patch",
        "ro.vendor.display.dynamic_refresh_rate",
        "ro.vendor.mi_fake_32bit_support",
        "ro.vendor.mi_sf.new_dynamic_refresh_rate",
        "ro.vendor.mi_support_zygote32_lazyload",
        "ro.vndk.version",
        "rust.runtime_version",
        "sys.boot_completed",
        "sys.ota.type",
        "sys.usb.adb.disabled",
        "sys.usb.config",
        "sys.usb.configfs",
        "sys.usb.controller",
        "vendor.display.default_resolution",
        "vendor.display.lcd_density",
        "vendor.xiaomi.trustedvm.version"
    )

    val java = linkedSetOf(
        "android.icu.cldr.version",
        "android.icu.library.version",
        "android.icu.unicode.version",
        "android.openssl.version",
        "android.zlib.version",
        "file.encoding",
        "http.agent",
        "java.class.version",
        "java.runtime.version",
        "java.vm.version",
        "os.arch",
        "os.version",
        "user.language",
        "user.locale",
        "user.name",
        "user.region"
    )

    val linux = linkedSetOf(
        "uname-a",
        "getenforce",
        "proc.version",
        "toybox --version",
    )
}
