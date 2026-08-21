package os.kei.ui.page.main.about.section

import org.junit.Test
import java.io.File
import kotlin.test.assertContains

class AboutReleaseCardContentTest {
    @Test
    fun releaseCardPublishesV1140InEverySupportedLocale() {
        aboutReleaseResourceFiles().forEach { resourceFile ->
            val resources = resourceFile.readText()

            assertContains(
                resources,
                """<string name="about_card_release_title">Release v1.14.0</string>""",
                message = "${resourceFile.path} must publish the v1.14.0 card title",
            )
            assertContains(
                resources,
                """<string name="about_release_value_version">v1.14.0 · Android 15+ · arm64-v8a · targetSdk 37</string>""",
                message = "${resourceFile.path} must publish the v1.14.0 version line",
            )
        }
    }

    @Test
    fun primaryReleaseCardCoversTheFinalUserFacingFeatureGroups() {
        val resources = aboutReleaseResourceFiles()
            .first { resourceFile -> resourceFile.path.endsWith("/values/strings_about.xml") }
            .readText()

        listOf(
            "制造室与提醒",
            "一键日常",
            "大屏与导航",
            "Liquid Glass 与背景",
            "性能、内存与兼容",
        ).forEach { expected ->
            assertContains(resources, expected)
        }
    }
}

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
