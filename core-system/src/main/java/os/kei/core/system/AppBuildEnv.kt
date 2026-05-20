package os.kei.core.system

import android.content.Context
import java.io.File

object AppBuildEnv {
    @Volatile
    private var snapshot = Snapshot()

    val buildType: String
        get() = snapshot.buildType

    val isDebugBuild: Boolean
        get() = snapshot.isDebugBuild

    val flavorFolderName: String
        get() = if (isDebugBuild) "debug" else "release"

    val displayName: String
        get() = if (isDebugBuild) "Debug" else "Release"

    fun configure(
        buildType: String,
        isDebugBuild: Boolean,
        applicationId: String,
    ) {
        snapshot = Snapshot(
            buildType = buildType.trim().ifBlank {
                if (isDebugBuild) "debug" else "release"
            },
            isDebugBuild = isDebugBuild,
            applicationId = applicationId.trim().ifBlank { DEFAULT_APPLICATION_ID },
        )
    }

    fun uiDumpDirectory(context: Context): File {
        val externalFiles = context.getExternalFilesDir(null)
        return if (externalFiles != null) {
            File(externalFiles, "$flavorFolderName/ui")
        } else {
            File(context.filesDir, "ui_dump/$flavorFolderName")
        }
    }

    fun uiDumpShellDirectory(): String {
        return "${'$'}{EXTERNAL_STORAGE:-/storage/emulated/0}/Android/data/" +
            "${snapshot.applicationId}/files/$flavorFolderName/ui"
    }

    private data class Snapshot(
        val buildType: String = "release",
        val isDebugBuild: Boolean = false,
        val applicationId: String = DEFAULT_APPLICATION_ID,
    )

    private const val DEFAULT_APPLICATION_ID = "os.kei"
}
