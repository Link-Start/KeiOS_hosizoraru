package os.kei.core.prefs

enum class LauncherIconDesign(
    val storageId: String,
    val aliasClassName: String,
) {
    Apple("apple", "LauncherAppleDesigns"),
    Android("android", "LauncherAndroidDesigns"),
    ;

    companion object {
        fun fromStorageId(raw: String?): LauncherIconDesign {
            val normalized = raw.orEmpty().trim()
            return entries.firstOrNull { it.storageId == normalized } ?: Android
        }
    }
}
