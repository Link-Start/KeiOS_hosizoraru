package os.kei.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = true
        ) {
            pressHome()
            startActivityAndWait()
            waitForHome()
        }
    }

    @Test
    fun homeAndGitHubInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false
        ) {
            pressHome()
            startActivityAndWait()
            waitForHome()

            repeat(2) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            }
            device.waitForIdle()

            clickTestTag(MAIN_BOTTOM_TAB_GITHUB)
            waitForTestTag(GITHUB_PAGE_ROOT)

            clickTestTag(GITHUB_IMPORT_MENU_BUTTON)
            waitForTestTag(GITHUB_IMPORT_TRACKS)
            waitForTestTag(GITHUB_IMPORT_STARS)
            device.pressBack()
            device.waitForIdle()

            repeat(2) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            }
            device.waitForIdle()
        }
    }
}

private fun MacrobenchmarkScope.waitForHome() {
    device.wait(Until.hasObject(By.text("KeiOS")), 5_000)
    device.waitForIdle()
}

private const val MAIN_BOTTOM_TAB_GITHUB = "main_bottom_tab_github"
private const val GITHUB_PAGE_ROOT = "github_page_root"
private const val GITHUB_IMPORT_MENU_BUTTON = "github_import_menu_button"
private const val GITHUB_IMPORT_TRACKS = "github_import_tracks"
private const val GITHUB_IMPORT_STARS = "github_import_stars"

private fun targetAppId(): String {
    return InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: error("targetAppId not passed as instrumentation runner arg")
}

private fun MacrobenchmarkScope.testTagSelector(tag: String): BySelector {
    return By.res("${targetAppId()}:id/$tag")
}

private fun MacrobenchmarkScope.waitForTestTag(tag: String, timeoutMs: Long = 5_000) {
    check(device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs)) {
        "Timed out waiting for testTag=$tag in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickTestTag(tag: String, timeoutMs: Long = 5_000) {
    waitForTestTag(tag, timeoutMs)
    val node = device.findObject(testTagSelector(tag))
    checkNotNull(node) { "Missing testTag=$tag in ${targetAppId()}" }
    node.click()
    device.waitForIdle()
}
