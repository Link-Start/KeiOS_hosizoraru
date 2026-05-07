package os.kei.ui.page.main.os.shell.page

import os.kei.ui.page.main.os.shell.OsShellRunnerCopyMode
import os.kei.ui.page.main.os.shell.OsShellRunnerSettings
import os.kei.ui.page.main.os.shell.ShellOutputDisplayEntry

private val dangerousShellPatterns = listOf(
    Regex("""(^|\s)rm(\s+-[^\n]*)?\s+/(?!sdcard)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)pm\s+uninstall(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)settings\s+put\s+global(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)settings\s+delete\s+(system|secure|global)(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)setprop(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)reboot(\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(^|\s)am\s+force-stop(\s|$)""", RegexOption.IGNORE_CASE)
)

internal fun isPotentiallyDangerousShellCommand(command: String): Boolean {
    val normalized = command.trim()
    if (normalized.isBlank()) return false
    return dangerousShellPatterns.any { regex -> regex.containsMatchIn(normalized) }
}

internal fun resolveShellOutputCopyText(
    settings: OsShellRunnerSettings,
    latestOutputEntry: ShellOutputDisplayEntry?,
    latestRunResultOutput: String,
    outputText: String
): String {
    val preferred = when (settings.copyMode) {
        OsShellRunnerCopyMode.FullHistory -> outputText.trim()
        OsShellRunnerCopyMode.LatestResult -> latestOutputEntry?.result.orEmpty().trim()
    }
    return preferred.ifBlank {
        if (settings.copyMode == OsShellRunnerCopyMode.LatestResult) {
            latestRunResultOutput.trim().ifBlank { outputText.trim() }
        } else {
            outputText.trim()
        }
    }
}
