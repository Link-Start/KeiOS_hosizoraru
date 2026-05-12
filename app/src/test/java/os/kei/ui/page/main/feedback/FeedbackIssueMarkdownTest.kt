package os.kei.ui.page.main.feedback

import org.junit.Test
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackIssueMarkdownTest {
    @Test
    fun redactsCommonSecretShapes() {
        val raw = """
            Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz1234567890
            mcp_token=secret-value
            apiToken: github_pat_11AAAAAA_secret
        """.trimIndent()

        val redacted = FeedbackIssueMarkdown.redactSensitiveText(raw)

        assertFalse(redacted.contains("ghp_abcdefghijklmnopqrstuvwxyz1234567890"))
        assertFalse(redacted.contains("secret-value"))
        assertFalse(redacted.contains("github_pat_11AAAAAA_secret"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun browserIssueUrlPrefillsIssueFormFields() {
        val deviceInfo = FeedbackDeviceInfo(
            appVersionName = "1.4.5",
            appVersionCode = 10405021,
            packageName = "os.kei.debug",
            buildType = "debug",
            androidRelease = "17",
            sdkInt = 37,
            manufacturer = "Google",
            model = "sdk_gphone16k_arm64",
            abis = "arm64-v8a",
            installSource = "Android Studio"
        )
        val body = FeedbackIssueMarkdown.buildBody(
            deviceInfo = deviceInfo,
            logPreview = "Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz1234567890",
            logPreviewTruncated = false
        )

        val params = queryParams(
            FeedbackIssueMarkdown.buildBrowserIssueUrl(
                title = "[Bug]: retry crash",
                body = body,
                deviceInfo = deviceInfo
            )
        )

        assertEquals("bug_report.yml", params["template"])
        assertEquals("[Bug]: retry crash", params["title"])
        assertEquals("Please describe the problem here.", params["description"])
        assertTrue(params.getValue("device-info").contains("Android 17 · API 37"))
        assertTrue(params.getValue("keios-info").contains("os.kei.debug"))
        assertFalse(params.getValue("logs").contains("ghp_abcdefghijklmnopqrstuvwxyz1234567890"))
    }

    private fun queryParams(url: String): Map<String, String> {
        return url.substringAfter('?')
            .split('&')
            .filter { it.contains('=') }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=')
                URLDecoder.decode(key, Charsets.UTF_8.name()) to
                        URLDecoder.decode(value, Charsets.UTF_8.name())
            }
    }
}
