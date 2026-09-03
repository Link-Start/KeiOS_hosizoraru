package os.kei.ui.page.main.github.fdroid

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The version history's own copy, held to the two rules the compiler cannot check.
 *
 * `R.string` resolves against the default locale alone, so a key added to `values/` and forgotten in
 * `values-en/` compiles, runs, and quietly shows Chinese to an English reader. And a positional
 * specifier that disagrees between locales — `%1$d` in one, `%1$s` in another — is not a fallback but a
 * crash inside `getString`, on that locale only, which is exactly the failure nobody tests on.
 */
class FdroidVersionStringsTest {
    @Test
    fun `every version-history string exists in every locale`() {
        val byLocale = LOCALES.associateWith { locale -> versionStrings(locale) }
        val reference = byLocale.getValue(DEFAULT_LOCALE).keys

        assertTrue(reference.isNotEmpty(), "no github_fdroid_version_* keys found at all")
        byLocale.forEach { (locale, strings) ->
            assertEquals(
                emptySet(),
                reference - strings.keys,
                "$locale is missing keys the default locale declares",
            )
            assertEquals(
                emptySet(),
                strings.keys - reference,
                "$locale declares keys the default locale does not",
            )
        }
    }

    @Test
    fun `a format string carries the same specifiers in every locale`() {
        val byLocale = LOCALES.associateWith { locale -> versionStrings(locale) }
        val reference = byLocale.getValue(DEFAULT_LOCALE)

        reference.forEach { (key, defaultText) ->
            val expected = specifiers(defaultText)
            byLocale.forEach { (locale, strings) ->
                assertEquals(
                    expected,
                    specifiers(strings.getValue(key)),
                    "$key: $locale would crash getString, or drop an argument",
                )
            }
        }
    }

    @Test
    fun `a key named as a format actually takes an argument, and one not named as a format takes none`() {
        // The naming is load-bearing rather than decorative: a `*_format` with no specifier means a call
        // site is passing an argument that goes nowhere, and a specifier on a key not named `*_format`
        // means a call site is about to hand `getString` no argument at all.
        versionStrings(DEFAULT_LOCALE).forEach { (key, text) ->
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
private val LOCALES = listOf("values", "values-en", "values-ja", "values-zh-rCN")
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
