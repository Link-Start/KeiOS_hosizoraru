package os.kei.feature.github.data.remote

internal object GitHubReleaseNotesHtmlMarkdownConverter {
    private const val MAX_RELEASE_NOTES_BODY_CHARS = 24_000

    fun parse(html: String): String {
        val markdownBlock = Regex(
            """<div[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>\s*</div>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(html)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .ifBlank {
                Regex(
                    """<div[^>]*class="[^"]*markdown-body[^"]*"[^>]*>(.*?)</div>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(html)
                    ?.groupValues
                    ?.getOrNull(1)
                    .orEmpty()
            }
        return markdownBlock.htmlReleaseNotesToMarkdown().take(MAX_RELEASE_NOTES_BODY_CHARS)
    }

    private fun String.htmlReleaseNotesToMarkdown(): String {
        return this
            .replace(
                Regex(
                    """<pre[^>]*>\s*<code[^>]*>(.*?)</code>\s*</pre>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val code = match.groupValues.getOrNull(1).orEmpty()
                    .stripHtml()
                    .decodeHtmlEntities()
                    .trimEnd()
                "\n```\n$code\n```\n"
            }
            .replace(
                Regex(
                    """<table[^>]*>(.*?)</table>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                "\n${match.groupValues.getOrNull(1).orEmpty().htmlTableToMarkdown()}\n"
            }
            .replace(
                Regex(
                    """<h([1-6])[^>]*>(.*?)</h\1>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val level = match.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 4) ?: 2
                val text = match.groupValues.getOrNull(2).orEmpty().htmlFragmentToMarkdown()
                "\n${"#".repeat(level)} $text\n\n"
            }
            .replace(
                Regex(
                    """<summary[^>]*>(.*?)</summary>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n### $text\n\n"
            }
            .replace(
                Regex(
                    """<blockquote[^>]*>(.*?)</blockquote>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n> $text\n"
            }
            .replace(
                Regex(
                    """<li[^>]*>(.*?)</li>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty()
                    .htmlTaskPrefixAndBody()
                    .let { (prefix, body) -> "$prefix${body.htmlFragmentToMarkdown()}" }
                "\n- $text\n"
            }
            .replace(
                Regex(
                    """<p[^>]*>(.*?)</p>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val text = match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()
                "\n$text\n\n"
            }
            .replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n---\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(
                Regex("""</(?:div|section|article|ul|ol|details)>""", RegexOption.IGNORE_CASE),
                "\n"
            )
            .replace(Regex("<[^>]+>"), " ")
            .decodeHtmlEntities()
            .normalizeReleaseNotesMarkdownLines()
    }

    private fun String.htmlTableToMarkdown(): String {
        val rowRegex = Regex(
            """<tr[^>]*>(.*?)</tr>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val cellRegex = Regex(
            """<t([hd])[^>]*>(.*?)</t\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val rows = rowRegex.findAll(this).mapNotNull { rowMatch ->
            val rowHtml = rowMatch.groupValues.getOrNull(1).orEmpty()
            val cells = cellRegex.findAll(rowHtml)
                .map { cellMatch ->
                    cellMatch.groupValues.getOrNull(2)
                        .orEmpty()
                        .htmlFragmentToMarkdown()
                        .asMarkdownTableCell()
                }
                .toList()
            cells.takeIf { it.isNotEmpty() }
                ?.let { rowHtml.contains("<th", ignoreCase = true) to it }
        }.toList()
        if (rows.isEmpty()) return ""
        return buildString {
            rows.forEachIndexed { index, row ->
                appendLine(
                    row.second.joinToString(
                        prefix = "| ",
                        separator = " | ",
                        postfix = " |"
                    )
                )
                if (index == 0 && row.first) {
                    appendLine(
                        row.second.joinToString(
                            prefix = "| ",
                            separator = " | ",
                            postfix = " |"
                        ) { "---" })
                }
            }
        }.trim()
    }

    private fun String.htmlFragmentToMarkdown(): String {
        return replace(
            Regex("""<img\b([^>]*)>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        ) { match ->
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val src = attrs.htmlAttribute("data-canonical-src")
                .ifBlank { attrs.htmlAttribute("src") }
                .normalizeGitHubUrl()
            val alt = attrs.htmlAttribute("alt").ifBlank { "image" }
            if (src.isBlank()) alt else "[$alt]($src)"
        }
            .replace(
                Regex(
                    """<a\b([^>]*)>(.*?)</a>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                val href = match.groupValues.getOrNull(1).orEmpty()
                    .htmlAttribute("href")
                    .normalizeGitHubUrl()
                val label = match.groupValues.getOrNull(2).orEmpty()
                    .htmlFragmentToMarkdown()
                    .trimMarkdownCodeFence()
                    .ifBlank { href }
                if (href.isBlank()) label else "[$label]($href)"
            }
            .replace(
                Regex(
                    """<(?:code|tt)[^>]*>(.*?)</(?:code|tt)>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                "`${
                    match.groupValues.getOrNull(1).orEmpty().stripHtml().decodeHtmlEntities().trim()
                }`"
            }
            .replace(
                Regex(
                    """<(?:strong|b)[^>]*>(.*?)</(?:strong|b)>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                "**${match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()}**"
            }
            .replace(
                Regex(
                    """<(?:em|i)[^>]*>(.*?)</(?:em|i)>""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                )
            ) { match ->
                "*${match.groupValues.getOrNull(1).orEmpty().htmlFragmentToMarkdown()}*"
            }
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .decodeHtmlEntities()
            .replace(Regex("""[ \t\x0B\f\r]+"""), " ")
            .lines()
            .joinToString(" ") { it.trim() }
            .trim()
    }

    private fun String.htmlTaskPrefixAndBody(): Pair<String, String> {
        val checkbox = Regex("""<input\b([^>]*)>""", RegexOption.IGNORE_CASE).find(this)
            ?: return "" to this
        val attrs = checkbox.groupValues.getOrNull(1).orEmpty()
        val checked = attrs.contains("checked", ignoreCase = true)
        val body = replaceRange(checkbox.range, "").trim()
        return if (checked) "[x] " to body else "[ ] " to body
    }

    private fun String.trimMarkdownCodeFence(): String {
        val trimmed = trim()
        return if (trimmed.length >= 2 && trimmed.first() == '`' && trimmed.last() == '`') {
            trimmed.substring(1, trimmed.lastIndex).trim()
        } else {
            trimmed
        }
    }

    private fun String.htmlAttribute(name: String): String {
        val doubleQuoted =
            Regex("""\b${Regex.escape(name)}\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
                .find(this)
                ?.groupValues
                ?.getOrNull(1)
        if (doubleQuoted != null) return doubleQuoted.decodeHtmlEntities().trim()
        return Regex("""\b${Regex.escape(name)}\s*=\s*'([^']*)'""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .decodeHtmlEntities()
            .trim()
    }

    private fun String.normalizeGitHubUrl(): String {
        val cleaned = decodeHtmlEntities().trim()
        return when {
            cleaned.startsWith("https://", ignoreCase = true) ||
                    cleaned.startsWith("http://", ignoreCase = true) -> cleaned

            cleaned.startsWith("/") -> "https://github.com$cleaned"
            else -> cleaned
        }
    }

    private fun String.asMarkdownTableCell(): String {
        return replace('\n', ' ')
            .replace("|", "\\|")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun String.normalizeReleaseNotesMarkdownLines(): String {
        val lines = lines().map { it.trim() }
        val nextNonBlankLines = MutableList(lines.size) { "" }
        var nextNonBlank = ""
        for (index in lines.indices.reversed()) {
            nextNonBlankLines[index] = nextNonBlank
            if (lines[index].isNotBlank()) {
                nextNonBlank = lines[index]
            }
        }
        val normalized = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val nextLine = nextNonBlankLines[index]
            when {
                line.isBlank() &&
                        normalized.lastOrNull()?.startsWith("- ") == true &&
                        nextLine.startsWith("- ") -> Unit

                line.isBlank() && normalized.lastOrNull().isNullOrBlank() -> Unit
                line.isBlank() -> normalized += ""
                else -> normalized += line
            }
        }
        return normalized
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
    }

    private fun String.stripHtml(): String {
        return replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
