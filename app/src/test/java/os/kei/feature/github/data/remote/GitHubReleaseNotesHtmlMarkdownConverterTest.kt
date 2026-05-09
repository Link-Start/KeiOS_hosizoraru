package os.kei.feature.github.data.remote

import org.junit.Test
import kotlin.test.assertContains

class GitHubReleaseNotesHtmlMarkdownConverterTest {
    @Test
    fun `converter keeps github release note tables details links and media`() {
        val markdown = GitHubReleaseNotesHtmlMarkdownConverter.parse(
            """
                <div class="markdown-body">
                  <ul>
                    <li>新增 Linux 支持通过 <code>D-Bus</code> 防屏幕休眠</li>
                    <li><input type="checkbox" checked> Signed universal APK</li>
                  </ul>
                  <hr>
                  <p>Full Changelog:
                    <a href="/open-ani/animeko/compare/v5.4.3...v5.5.0-alpha02">
                      <tt>v5.4.3...v5.5.0-alpha02</tt>
                    </a>
                  </p>
                  <details>
                    <summary>Android 细分架构下载</summary>
                    <table>
                      <thead>
                        <tr><th>处理器架构</th><th>下载</th></tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td>universal</td>
                          <td><a href="https://d.myani.org/app.apk">主线</a> / <a href="/open-ani/animeko/releases/download/v5.5.0-alpha02/app.apk">GitHub</a></td>
                        </tr>
                      </tbody>
                    </table>
                    <p><img alt="QR code" data-canonical-src="https://example.com/qr.png"></p>
                  </details>
                </div></div>
            """.trimIndent()
        )

        assertContains(markdown, "- 新增 Linux 支持通过 `D-Bus` 防屏幕休眠")
        assertContains(markdown, "- [x] Signed universal APK")
        assertContains(markdown, "---")
        assertContains(
            markdown,
            "[v5.4.3...v5.5.0-alpha02](https://github.com/open-ani/animeko/compare/v5.4.3...v5.5.0-alpha02)"
        )
        assertContains(markdown, "### Android 细分架构下载")
        assertContains(markdown, "| 处理器架构 | 下载 |")
        assertContains(
            markdown,
            "[GitHub](https://github.com/open-ani/animeko/releases/download/v5.5.0-alpha02/app.apk)"
        )
        assertContains(markdown, "[QR code](https://example.com/qr.png)")
    }
}
