package os.kei.core.export

data class ExportJobResult(
    val attempted: Int,
    val saved: Int,
    val failed: Int,
    val fileName: String,
    val errorPreview: String = ""
) {
    val isSuccess: Boolean
        get() = attempted > 0 && saved > 0 && failed == 0

    companion object {
        fun success(
            fileName: String,
            attempted: Int = 1
        ): ExportJobResult {
            val normalizedAttempted = attempted.coerceAtLeast(1)
            return ExportJobResult(
                attempted = normalizedAttempted,
                saved = normalizedAttempted,
                failed = 0,
                fileName = fileName.trim()
            )
        }

        fun failure(
            fileName: String,
            error: Throwable?,
            attempted: Int = 1
        ): ExportJobResult {
            val normalizedAttempted = attempted.coerceAtLeast(1)
            return ExportJobResult(
                attempted = normalizedAttempted,
                saved = 0,
                failed = normalizedAttempted,
                fileName = fileName.trim(),
                errorPreview = error.toExportErrorPreview()
            )
        }
    }
}

private fun Throwable?.toExportErrorPreview(limit: Int = 160): String {
    if (this == null) return ""
    val compact = "${javaClass.simpleName}:${message.orEmpty()}"
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .trim()
    if (compact.length <= limit) return compact
    return compact.take(limit) + "..."
}
