package os.kei.ui.page.main.github.fdroid

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The version history's own copy, held to a naming rule the compiler cannot check. Key and
 * format-argument parity across locales is `LocalizedStringParityTest`'s job.
 */
class FdroidVersionStringsTest {
    @Test
    fun `a key named as a format actually takes an argument, and one not named as a format takes none`() {
        // The naming is load-bearing rather than decorative: a `*_format` with no specifier means a call
        // site is passing an argument that goes nowhere, and a specifier on a key not named `*_format`
        // means a call site is about to hand `getString` no argument at all.
        val strings = versionStrings(DEFAULT_LOCALE)
        assertTrue(strings.isNotEmpty(), "no github_fdroid_version_* keys found at all")
        strings.forEach { (key, text) ->
            val hasSpecifiers = specifiers(text).isNotEmpty()
            assertEquals(
                key.endsWith("_format"),
                hasSpecifiers,
                "$key: name and specifiers disagree -- \"$text\"",
            )
        }
    }
}

private const val DEFAULT_LOCALE = "values"
private const val KEY_PREFIX = "github_fdroid_version_"

/** `%1$s`, `%2$d` and friends, as a set so ordering in a translation is free to differ. */
private fun specifiers(text: String): Set<String> =
    Regex("""%(\d+\$[a-zA-Z])""").findAll(text).map { match -> match.groupValues[1] }.toSet()

private fun versionStrings(locale: String): Map<String, String> {
    val file = File(repositoryRoot(), "app/src/main/res/$locale/strings_github.xml")
    assertTrue(file.isFile, "missing string resources for $locale")
    // Read as text rather than parsed as XML: the assertions are about names and specifiers, and a
    // regex over the declarations keeps this test free of an XML parser in the unit-test classpath.
    return Regex("""<string name="($KEY_PREFIX[a-z0-9_]+)"\s*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        .findAll(file.readText())
        .associate { match -> match.groupValues[1] to match.groupValues[2] }
}

private fun repositoryRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "app/src/main/res/values/strings_github.xml").isFile },
    ) {
        "Unable to locate the repository root from $workingDirectory"
    }
}
