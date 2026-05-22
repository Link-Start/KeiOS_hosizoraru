package os.kei.ui.page.main.github.share

internal fun buildShareImportTargetDisplayName(
    appLabel: String = "",
    repo: String = "",
    assetName: String = "",
    packageName: String = "",
): String =
    appLabel
        .trim()
        .ifBlank { repo.trim() }
        .ifBlank { cleanShareImportAssetName(assetName) }
        .ifBlank { packageName.trim() }

internal fun cleanShareImportAssetName(assetName: String): String {
    val fileName =
        assetName
            .trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
    if (fileName.isBlank()) return ""
    return fileName
        .replace(apkExtensionRegex, "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(whitespaceRegex, " ")
        .trim()
}

private val apkExtensionRegex = Regex("""\.apk$""", RegexOption.IGNORE_CASE)
private val whitespaceRegex = Regex("""\s+""")
