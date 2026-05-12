package os.kei.core.log

enum class AppLogLevel(
    val storageId: String,
    private val threshold: Int
) {
    Off("off", Int.MAX_VALUE),
    Error("error", 4),
    Warning("warning", 3),
    Info("info", 2),
    Debug("debug", 1);

    fun allows(entryLevel: AppLogLevel): Boolean {
        return this != Off && entryLevel.threshold >= threshold
    }

    companion object {
        fun fromStorageId(
            raw: String?,
            fallback: AppLogLevel = Off
        ): AppLogLevel {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageId == normalized } ?: fallback
        }
    }
}
