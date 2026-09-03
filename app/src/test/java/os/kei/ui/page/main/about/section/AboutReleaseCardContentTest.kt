package os.kei.ui.page.main.about.section

import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The release card documents a line, so it is pinned to one.
 *
 * Every other version on the About page is read from the build, and this card deliberately is not: its
 * body lists what a particular release brought, so a header that tracked the running build would print
 * one version above notes describing another. Pinning it here is what makes updating the card a
 * deliberate act at release time instead of something that silently falls behind — which it had, sitting
 * at 1.14.0 through 117 commits.
 */
class AboutReleaseCardContentTest {
    @Test
    fun releaseCardPublishesV1150InEverySupportedLocale() {
        aboutReleaseResourceFiles().forEach { resourceFile ->
            val resources = resourceFile.readText()

            assertContains(
                resources,
                """<string name="about_card_release_title">Release v1.15.0</string>""",
                message = "${resourceFile.path} must publish the v1.15.0 card title",
            )
            assertContains(
                resources,
                """<string name="about_release_value_version">v1.15.0 · Android 15+ · arm64-v8a · targetSdk 37</string>""",
                message = "${resourceFile.path} must publish the v1.15.0 version line",
            )
        }
    }

    @Test
    fun releaseCardSummarizesFinalUserFacingOutcomesInEveryLocale() {
        releaseLocaleExpectations().forEach { expectation ->
            val resourceFile = locateProjectRoot().resolve(expectation.path)
            expectation.headings.forEach { (resourceName, expectedValue) ->
                assertEquals(
                    expectedValue,
                    resourceFile.stringValue(resourceName),
                    "$resourceName must describe the same release area in ${expectation.path}",
                )
            }
            expectation.outcomeFragments.forEach { expected ->
                assertContains(
                    resourceFile.readText(),
                    expected,
                    message = "${expectation.path} must state the final user-facing outcome: $expected",
                )
            }

            val resources = resourceFile.readText()
            RETIRED_RELEASE_RESOURCE_NAMES.forEach { retiredName ->
                assertFalse(
                    "name=\"$retiredName\"" in resources,
                    "${expectation.path} still uses the retired semantic name $retiredName",
                )
            }
        }
    }
}

private data class ReleaseLocaleExpectation(
    val path: String,
    val headings: Map<String, String>,
    val outcomeFragments: List<String>,
)

private fun releaseLocaleExpectations(): List<ReleaseLocaleExpectation> = listOf(
    ReleaseLocaleExpectation(
        path = "app/src/main/res/values/strings_about.xml",
        headings = chineseReleaseHeadings,
        outcomeFragments = listOf("直接安装旧版本", "约 37 KB", "悬浮工具栏", "约 11 分钟"),
    ),
    ReleaseLocaleExpectation(
        path = "app/src/main/res/values-zh-rCN/strings_about.xml",
        headings = chineseReleaseHeadings,
        outcomeFragments = listOf("直接安装旧版本", "约 37 KB", "悬浮工具栏", "约 11 分钟"),
    ),
    ReleaseLocaleExpectation(
        path = "app/src/main/res/values-en/strings_about.xml",
        headings = mapOf(
            "about_release_row_releases" to "Releases and rollback",
            "about_release_row_fdroid" to "F-Droid version history",
            "about_release_row_large_screens" to "Two lanes on large screens",
            "about_release_row_catalog" to "Catalog and its rail",
            "about_release_row_mcp_security" to "MCP and security",
            "about_release_row_performance" to "Performance and startup",
        ),
        outcomeFragments = listOf(
            "direct installation of an older build",
            "about 37 KB",
            "floating toolbar",
            "about 11",
        ),
    ),
    ReleaseLocaleExpectation(
        path = "app/src/main/res/values-ja/strings_about.xml",
        headings = mapOf(
            "about_release_row_releases" to "リリースとロールバック",
            "about_release_row_fdroid" to "F-Droid バージョン履歴",
            "about_release_row_large_screens" to "大画面の2カラム",
            "about_release_row_catalog" to "図鑑とサイドバー",
            "about_release_row_mcp_security" to "MCP とセキュリティ",
            "about_release_row_performance" to "パフォーマンスと起動",
        ),
        outcomeFragments = listOf(
            "旧バージョンの再インストール",
            "約 37 KB",
            "フローティングツールバー",
            "約 11 分",
        ),
    ),
)

private val chineseReleaseHeadings = mapOf(
    "about_release_row_releases" to "发行版与回滚",
    "about_release_row_fdroid" to "F-Droid 版本历史",
    "about_release_row_large_screens" to "大屏双栏",
    "about_release_row_catalog" to "图鉴与侧栏",
    "about_release_row_mcp_security" to "MCP 与安全",
    "about_release_row_performance" to "性能与启动",
)

private val RETIRED_RELEASE_RESOURCE_NAMES = listOf(
    "about_release_row_github",
    "about_release_value_github",
    "about_release_row_ba_guide",
    "about_release_value_ba_guide",
    "about_release_row_navigation",
    "about_release_value_navigation",
    "about_release_row_icon",
    "about_release_value_icon",
    "about_release_row_release_gate",
    "about_release_value_release_gate",
)

private fun aboutReleaseResourceFiles(): List<File> {
    val projectRoot = locateProjectRoot()
    return listOf(
        "app/src/main/res/values/strings_about.xml",
        "app/src/main/res/values-zh-rCN/strings_about.xml",
        "app/src/main/res/values-en/strings_about.xml",
        "app/src/main/res/values-ja/strings_about.xml",
    ).map(projectRoot::resolve)
}

private fun locateProjectRoot(): File {
    val workingDirectory = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
    return requireNotNull(
        generateSequence(workingDirectory) { directory -> directory.parentFile }
            .firstOrNull { directory -> File(directory, "settings.gradle.kts").isFile },
    ) {
        "Unable to locate the KeiOS project root from $workingDirectory"
    }
}

private fun File.stringValue(name: String): String {
    val pattern = Regex("""<string name="$name">(.*?)</string>""")
    return requireNotNull(pattern.find(readText())?.groupValues?.get(1)) {
        "$path is missing $name"
    }
}
