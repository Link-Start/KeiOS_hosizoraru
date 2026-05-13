package os.kei.feature.github.data.remote

import java.text.Normalizer
import java.util.Locale

internal object GitHubDirectApkIndexReleaseNotesParser {
    fun extractForVersion(
        raw: String,
        version: String
    ): String {
        val normalizedVersion = version.normalizedReleaseNotesKey()
        if (normalizedVersion.isBlank()) return ""
        val lines = raw.removePrefix("\uFEFF").lines()
        val buffer = mutableListOf<String>()
        var collecting = false
        lines.forEach { line ->
            val heading = headingRegex.matchEntire(line.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
            if (heading != null) {
                if (collecting) return buffer.joinToString("\n").trim()
                collecting = heading.matchesReleaseNotesVersion(normalizedVersion)
                return@forEach
            }
            if (collecting) {
                buffer += line.trimEnd()
            }
        }
        return buffer.joinToString("\n").trim()
    }

    private fun String.matchesReleaseNotesVersion(normalizedVersion: String): Boolean {
        return split('/', '、', ',', ';', '|')
            .map { part -> part.normalizedReleaseNotesKey() }
            .any { key ->
                key == normalizedVersion ||
                        key.contains(normalizedVersion) ||
                        normalizedVersion.contains(key)
            }
    }

    private fun String.normalizedReleaseNotesKey(): String {
        val ascii = Normalizer.normalize(this, Normalizer.Form.NFKC)
        return ascii
            .lowercase(Locale.ROOT)
            .replace(Regex("""\([^)]*\)"""), " ")
            .replace(Regex("""[^a-z0-9.]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private val headingRegex = Regex("""^#{1,6}\s+(.+)$""")
}
