package os.kei.ui.testing

import java.io.File

/** The checkout root: the nearest directory above the test's working directory with `settings.gradle.kts`. */
internal fun repoRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile },
    ) { "Unable to locate the repository root from $workingDirectory" }
}

/** [relativePath] under the checkout root, such as `app/src/main/AndroidManifest.xml`. */
internal fun repoFile(relativePath: String): File = File(repoRoot(), relativePath)

/** The text of the repo-relative file at [relativePath]; fails naming the path when it is missing. */
internal fun repoSource(relativePath: String): String {
    val file = repoFile(relativePath)
    require(file.isFile) { "Unable to locate $relativePath under ${repoRoot()}" }
    return file.readText()
}

/**
 * Kotlin source with block and line comments blanked, newlines kept so positions stay meaningful.
 *
 * A doc line naming an API is not a call to it, so every scan that bans or counts calls reads this.
 */
internal fun String.withoutComments(): String = COMMENT.replace(this) { match -> match.value.filter { it == '\n' } }

private val COMMENT = Regex("""/\*.*?\*/|//[^\n]*""", RegexOption.DOT_MATCHES_ALL)
