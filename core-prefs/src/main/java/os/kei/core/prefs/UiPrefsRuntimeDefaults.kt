package os.kei.core.prefs

import os.kei.core.log.AppLogLevel

internal object UiPrefsRuntimeDefaults {
    @Volatile
    private var snapshot = Snapshot()

    val buildType: String
        get() = snapshot.buildType

    val defaultLogLevel: AppLogLevel
        get() = snapshot.defaultLogLevel

    fun configure(
        buildType: String,
        defaultLogLevel: AppLogLevel,
    ) {
        snapshot = Snapshot(
            buildType = buildType.trim().ifBlank { DEFAULT_BUILD_TYPE },
            defaultLogLevel = defaultLogLevel,
        )
    }

    private data class Snapshot(
        val buildType: String = DEFAULT_BUILD_TYPE,
        val defaultLogLevel: AppLogLevel = AppLogLevel.Off,
    )

    private const val DEFAULT_BUILD_TYPE = "release"
}
