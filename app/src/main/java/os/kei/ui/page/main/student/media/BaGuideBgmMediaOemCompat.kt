package os.kei.ui.page.main.student

import android.os.Build
import androidx.annotation.DrawableRes
import os.kei.R
import os.kei.core.system.findPropString
import java.util.Locale

internal val BA_GUIDE_BGM_MEDIA_AOSP_SMALL_ICON_RES: Int = R.drawable.ic_launcher_monochrome
internal val BA_GUIDE_BGM_MEDIA_XIAOMI_SMALL_ICON_RES: Int = R.drawable.ic_kei_logo_notification

internal object BaGuideBgmMediaOemCompat {
    data class DeviceSignals(
        val brand: String,
        val manufacturer: String,
        val display: String,
        val model: String,
        val properties: Map<String, String> = emptyMap()
    )

    @DrawableRes
    fun mediaSmallIconRes(): Int {
        return mediaSmallIconRes(currentSignals())
    }

    @DrawableRes
    internal fun mediaSmallIconRes(signals: DeviceSignals): Int {
        return if (signals.isXiaomiMediaSurface) {
            BA_GUIDE_BGM_MEDIA_XIAOMI_SMALL_ICON_RES
        } else {
            BA_GUIDE_BGM_MEDIA_AOSP_SMALL_ICON_RES
        }
    }

    private fun currentSignals(): DeviceSignals {
        return DeviceSignals(
            brand = Build.BRAND.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            display = Build.DISPLAY.orEmpty(),
            model = Build.MODEL.orEmpty(),
            properties = watchedPropertyKeys.associateWith { key -> findPropString(key) }
        )
    }

    private val DeviceSignals.isXiaomiMediaSurface: Boolean
        get() {
            if (prop("ro.mi.os.version.name").isNotBlank()) return true
            if (prop("ro.mi.os.version.incremental").isNotBlank()) return true
            if (prop("ro.miui.ui.version.name").isNotBlank()) return true
            if (prop("ro.miui.ui.version.code").isNotBlank()) return true
            return normalizedText.hasAny("xiaomi", "redmi", "poco", "hyperos", "miui")
        }

    private val DeviceSignals.normalizedText: List<String>
        get() = listOf(brand, manufacturer, display, model) + properties.values

    private fun DeviceSignals.prop(key: String): String {
        return properties[key].orEmpty().lowercase(Locale.ROOT)
    }

    private fun List<String>.hasAny(vararg needles: String): Boolean {
        val haystack = joinToString(separator = " ").lowercase(Locale.ROOT)
        return needles.any(haystack::contains)
    }

    private val watchedPropertyKeys = listOf(
        "ro.mi.os.version.name",
        "ro.mi.os.version.incremental",
        "ro.miui.ui.version.name",
        "ro.miui.ui.version.code"
    )
}
