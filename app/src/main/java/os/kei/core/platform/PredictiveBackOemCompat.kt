package os.kei.core.platform

import android.os.Build
import os.kei.core.system.findPropString
import java.util.Locale

object PredictiveBackOemCompat {
    enum class RomFamily {
        Aosp,
        HyperOs,
        Miui,
        Xiaomi,
        ColorOs,
        OriginOs,
        MagicOs,
        Emui,
        OneUi,
        Unknown
    }

    enum class RouteBackPipeline {
        NavigationEvent,
        CommitOnly
    }

    enum class LocalBackPipeline {
        ComposePredictive,
        CommitOnly
    }

    enum class ActivityBackPipeline {
        FrameworkFinish,
        CommitCallback
    }

    data class DeviceSignals(
        val brand: String,
        val manufacturer: String,
        val display: String,
        val model: String,
        val properties: Map<String, String> = emptyMap()
    )

    data class Policy(
        val frameworkAnimationsEnabled: Boolean,
        val popDirectionFollowsSwipeEdge: Boolean,
        val routeBackPipeline: RouteBackPipeline,
        val localBackPipeline: LocalBackPipeline,
        val activityBackPipeline: ActivityBackPipeline,
        val romFamily: RomFamily
    ) {
        val routePredictiveBackEnabled: Boolean
            get() = frameworkAnimationsEnabled && routeBackPipeline == RouteBackPipeline.NavigationEvent

        val localPredictiveBackEnabled: Boolean
            get() = frameworkAnimationsEnabled && localBackPipeline == LocalBackPipeline.ComposePredictive

        val activityFrameworkFinishEnabled: Boolean
            get() = frameworkAnimationsEnabled && activityBackPipeline == ActivityBackPipeline.FrameworkFinish
    }

    private val currentRomFamily: RomFamily by lazy {
        resolveRomFamily(currentDeviceSignals())
    }

    fun currentPolicy(
        transitionAnimationsEnabled: Boolean,
        predictiveBackAnimationsEnabled: Boolean
    ): Policy {
        return resolvePolicy(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
            romFamily = currentRomFamily
        )
    }

    internal fun resolvePolicy(
        transitionAnimationsEnabled: Boolean,
        predictiveBackAnimationsEnabled: Boolean,
        signals: DeviceSignals
    ): Policy {
        return resolvePolicy(
            transitionAnimationsEnabled = transitionAnimationsEnabled,
            predictiveBackAnimationsEnabled = predictiveBackAnimationsEnabled,
            romFamily = resolveRomFamily(signals)
        )
    }

    private fun resolvePolicy(
        transitionAnimationsEnabled: Boolean,
        predictiveBackAnimationsEnabled: Boolean,
        romFamily: RomFamily
    ): Policy {
        val frameworkAnimationsEnabled = transitionAnimationsEnabled && predictiveBackAnimationsEnabled
        val routeBackPipeline = if (frameworkAnimationsEnabled) {
            RouteBackPipeline.NavigationEvent
        } else {
            RouteBackPipeline.CommitOnly
        }
        val localBackPipeline =
            if (frameworkAnimationsEnabled && romFamily.usesComposePredictiveLocalBack) {
                LocalBackPipeline.ComposePredictive
            } else {
                LocalBackPipeline.CommitOnly
            }
        val activityBackPipeline = if (frameworkAnimationsEnabled) {
            ActivityBackPipeline.FrameworkFinish
        } else {
            ActivityBackPipeline.CommitCallback
        }
        return Policy(
            frameworkAnimationsEnabled = frameworkAnimationsEnabled,
            popDirectionFollowsSwipeEdge = frameworkAnimationsEnabled && romFamily.usesTwoEdgeBackAnimation,
            routeBackPipeline = routeBackPipeline,
            localBackPipeline = localBackPipeline,
            activityBackPipeline = activityBackPipeline,
            romFamily = romFamily
        )
    }

    internal fun resolveRomFamily(signals: DeviceSignals): RomFamily {
        val text = signals.normalizedText
        return when {
            signals.prop("ro.mi.os.version.name").startsWith("os") ||
                signals.prop("ro.mi.os.version.incremental").startsWith("os") -> RomFamily.HyperOs
            signals.prop("ro.miui.ui.version.name").isNotBlank() ||
                signals.prop("ro.miui.ui.version.code").isNotBlank() -> RomFamily.Miui
            text.hasAny("oppo", "oneplus", "realme", "coloros", "oplus") ||
                signals.hasAnyProp(
                    "ro.build.version.opporom",
                    "ro.build.version.oplusrom",
                    "ro.build.version.realmeui",
                    "ro.oplus.version"
                ) -> RomFamily.ColorOs
            text.hasAny("vivo", "iqoo", "originos", "funtouch") ||
                signals.hasAnyProp(
                    "ro.vivo.os.version",
                    "ro.vivo.rom.version",
                    "ro.build.version.bbk"
                ) -> RomFamily.OriginOs
            text.hasAny("honor", "magicos") ||
                signals.hasAnyProp("ro.build.version.magic") -> RomFamily.MagicOs
            text.hasAny("huawei", "emui", "harmonyos") ||
                signals.hasAnyProp("ro.build.version.emui") -> RomFamily.Emui
            text.hasAny("samsung", "oneui") ||
                signals.hasAnyProp(
                    "ro.build.version.oneui",
                    "ro.build.version.sep"
                ) -> RomFamily.OneUi
            text.hasAny("xiaomi", "redmi", "poco") -> RomFamily.Xiaomi
            text.hasAny("google", "pixel", "aosp") -> RomFamily.Aosp
            else -> RomFamily.Unknown
        }
    }

    private fun currentDeviceSignals(): DeviceSignals {
        return DeviceSignals(
            brand = Build.BRAND.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            display = Build.DISPLAY.orEmpty(),
            model = Build.MODEL.orEmpty(),
            properties = watchedPropertyKeys.associateWith { key -> findPropString(key) }
        )
    }

    private val RomFamily.usesTwoEdgeBackAnimation: Boolean
        get() = when (this) {
            RomFamily.HyperOs,
            RomFamily.Miui,
            RomFamily.Xiaomi,
            RomFamily.ColorOs,
            RomFamily.OriginOs,
            RomFamily.MagicOs,
            RomFamily.Emui,
            RomFamily.OneUi -> true
            RomFamily.Aosp,
            RomFamily.Unknown -> false
        }

    private val RomFamily.usesComposePredictiveLocalBack: Boolean
        get() = when (this) {
            RomFamily.Aosp,
            RomFamily.Unknown -> true

            RomFamily.HyperOs,
            RomFamily.Miui,
            RomFamily.Xiaomi,
            RomFamily.ColorOs,
            RomFamily.OriginOs,
            RomFamily.MagicOs,
            RomFamily.Emui,
            RomFamily.OneUi -> false
        }

    private val DeviceSignals.normalizedText: List<String>
        get() = listOf(brand, manufacturer, display, model) + properties.values

    private fun DeviceSignals.prop(key: String): String {
        return properties[key].orEmpty().lowercase(Locale.ROOT)
    }

    private fun DeviceSignals.hasAnyProp(vararg keys: String): Boolean {
        return keys.any { key -> properties[key].orEmpty().isNotBlank() }
    }

    private fun List<String>.hasAny(vararg needles: String): Boolean {
        val haystack = joinToString(separator = " ").lowercase(Locale.ROOT)
        return needles.any(haystack::contains)
    }

    private val watchedPropertyKeys = listOf(
        "ro.mi.os.version.name",
        "ro.mi.os.version.incremental",
        "ro.miui.ui.version.name",
        "ro.miui.ui.version.code",
        "ro.build.version.opporom",
        "ro.build.version.oplusrom",
        "ro.build.version.realmeui",
        "ro.oplus.version",
        "ro.vivo.os.version",
        "ro.vivo.rom.version",
        "ro.build.version.bbk",
        "ro.build.version.magic",
        "ro.build.version.emui",
        "ro.build.version.oneui",
        "ro.build.version.sep",
        "ro.rom.version",
        "ro.product.marketname",
        "persist.sys.device_name"
    )
}
