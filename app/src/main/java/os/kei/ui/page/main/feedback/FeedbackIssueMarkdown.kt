package os.kei.ui.page.main.feedback

import java.net.URLEncoder

private const val ISSUE_NEW_URL = "https://github.com/hosizoraru/KeiOS/issues/new"
private const val BUG_TEMPLATE = "bug_report.yml"

internal object FeedbackIssueMarkdown {
    fun defaultTitle(deviceInfo: FeedbackDeviceInfo): String {
        val version = deviceInfo.appVersionName.ifBlank { "unknown" }
        return "[Bug]: KeiOS $version issue"
    }

    fun buildBody(
        deviceInfo: FeedbackDeviceInfo,
        logPreview: String,
        logPreviewTruncated: Boolean
    ): String {
        val sanitizedLog =
            redactSensitiveText(logPreview).ifBlank { "No local AppLogger records yet." }
        return buildString {
            appendLine("## Problem description / 问题描述")
            appendLine()
            appendLine("Please describe the problem here.")
            appendLine()
            appendLine("## Steps to reproduce / 复现步骤")
            appendLine()
            appendLine("1. Open KeiOS")
            appendLine("2. ")
            appendLine("3. ")
            appendLine()
            appendLine("## Expected behavior / 期望行为")
            appendLine()
            appendLine("Please describe the expected behavior.")
            appendLine()
            appendLine("## Actual behavior / 实际行为")
            appendLine()
            appendLine("Please describe what happened.")
            appendLine()
            appendLine("## Device information / 设备信息")
            appendLine()
            appendLine("| Field | Value |")
            appendLine("| --- | --- |")
            appendLine("| KeiOS | ${deviceInfo.appVersionLine} |")
            appendLine("| Android | ${deviceInfo.androidLine} |")
            appendLine("| Device | ${deviceInfo.deviceLine} |")
            appendLine("| ABI | ${deviceInfo.abis.ifBlank { "Unknown" }} |")
            appendLine("| Install source | ${deviceInfo.installSource.ifBlank { "Unknown" }} |")
            appendLine()
            appendLine("## Exported log ZIP / 导出日志 ZIP")
            appendLine()
            appendLine("Please attach the exported ZIP from KeiOS when it helps reproduce the issue.")
            appendLine()
            appendLine("## Local log summary / 本机日志摘要")
            appendLine()
            appendLine("```text")
            appendLine(sanitizedLog.trimEnd())
            appendLine("```")
            if (logPreviewTruncated) {
                appendLine()
                appendLine("> Log summary is truncated to the latest local records.")
            }
            appendLine()
            appendLine("## Screenshots or recordings / 截图或录屏")
            appendLine()
            appendLine("Drag screenshots or recordings here if they help.")
            appendLine()
            appendLine("## Sensitive information check / 敏感信息检查")
            appendLine()
            appendLine("- [ ] I reviewed the issue body and attachments.")
            append("- [ ] I removed private tokens, account data, and unrelated personal information.")
        }
    }

    fun buildBrowserIssueUrl(
        title: String,
        body: String,
        deviceInfo: FeedbackDeviceInfo
    ): String {
        val sanitizedBody = redactSensitiveText(body)
        val description = extractSection(
            markdown = sanitizedBody,
            heading = "## Problem description / 问题描述"
        ).ifBlank { sanitizedBody }.limitUrlField(4_000)
        val steps = extractSection(
            markdown = sanitizedBody,
            heading = "## Steps to reproduce / 复现步骤"
        ).ifBlank { "1. Open KeiOS\n2. \n3. " }.limitUrlField(1_200)
        val expected = extractSection(
            markdown = sanitizedBody,
            heading = "## Expected behavior / 期望行为"
        ).limitUrlField(1_200)
        val actual = extractSection(
            markdown = sanitizedBody,
            heading = "## Actual behavior / 实际行为"
        ).limitUrlField(1_200)
        val exportedLogZip = extractSection(
            markdown = sanitizedBody,
            heading = "## Exported log ZIP / 导出日志 ZIP"
        ).ifBlank { "Attach the exported KeiOS log ZIP when it helps reproduce the issue." }
            .limitUrlField(1_000)
        val logSummary = extractSection(
            markdown = sanitizedBody,
            heading = "## Local log summary / 本机日志摘要"
        ).withoutTextFence().ifBlank { "No local AppLogger records included." }
            .limitUrlField(4_000)
        val media = extractSection(
            markdown = sanitizedBody,
            heading = "## Screenshots or recordings / 截图或录屏"
        ).limitUrlField(800)
        return buildUrl(
            "template" to BUG_TEMPLATE,
            "title" to title,
            "description" to description,
            "steps" to steps,
            "expected" to expected,
            "actual" to actual,
            "device-info" to "${deviceInfo.deviceLine}\n${deviceInfo.androidLine}\nABI: ${deviceInfo.abis.ifBlank { "Unknown" }}",
            "keios-info" to deviceInfo.appVersionLine,
            "install-source" to deviceInfo.installSource,
            "exported-log-zip" to exportedLogZip,
            "logs" to logSummary,
            "media" to media
        )
    }

    fun redactSensitiveText(raw: String): String {
        var redacted = raw
        val replacements = listOf(
            Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s|]+") to "$1[REDACTED]",
            Regex("(?i)(bearer\\s+)(github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9_]+|[A-Za-z0-9._\\-]{20,})") to "$1[REDACTED]",
            Regex("(?i)\\b(github_pat_[A-Za-z0-9_]+|gh[pousr]_[A-Za-z0-9_]+)\\b") to "[REDACTED_GITHUB_TOKEN]",
            Regex("(?i)\\b(access_token|api[_-]?token|github[_-]?token|mcp[_-]?token|token)\\s*[:=]\\s*[^\\s|,&]+") to "$1=[REDACTED]",
            Regex("(?i)(x-api-key\\s*[:=]\\s*)[^\\s|]+") to "$1[REDACTED]",
        )
        replacements.forEach { (regex, replacement) ->
            redacted = regex.replace(redacted, replacement)
        }
        return redacted
    }

    private fun extractSection(
        markdown: String,
        heading: String
    ): String {
        val start = markdown.indexOf(heading)
        if (start < 0) return ""
        val bodyStart = start + heading.length
        val nextHeading = markdown.indexOf("\n## ", startIndex = bodyStart)
        val raw = if (nextHeading >= 0) {
            markdown.substring(bodyStart, nextHeading)
        } else {
            markdown.substring(bodyStart)
        }
        return raw.trim()
    }

    private fun String.withoutTextFence(): String {
        return trim()
            .removePrefix("```text")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun String.limitUrlField(maxLength: Int): String {
        val normalized = trim()
        if (normalized.length <= maxLength) return normalized
        return normalized.take(maxLength).trimEnd() + "\n\n[truncated for URL prefill]"
    }

    private fun buildUrl(vararg params: Pair<String, String>): String {
        val query = params.joinToString(separator = "&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        return "$ISSUE_NEW_URL?$query"
    }

    private fun String.urlEncode(): String {
        return URLEncoder.encode(this, Charsets.UTF_8.name())
    }
}
