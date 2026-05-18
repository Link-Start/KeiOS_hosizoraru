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
            includeInStartupProfile = true,
        ) {
            launchHomeFromColdStart()
        }
    }

    @Test
    fun homeAndGitHubInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            flingVisibleScrollable(times = 2)

            clickTestTag(MAIN_BOTTOM_TAB_GITHUB)
            waitForTestTag(GITHUB_PAGE_ROOT)

            clickTestTag(GITHUB_IMPORT_MENU_BUTTON)
            waitForTestTag(GITHUB_IMPORT_TRACKS)
            waitForTestTag(GITHUB_IMPORT_STARS)
            device.pressBack()
            device.waitForIdle()

            flingVisibleScrollable(times = 2)
        }
    }

    @Test
    fun osPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickTestTag(MAIN_BOTTOM_TAB_OS)
            waitForTestTag(OS_PAGE_ROOT)
            flingVisibleScrollable(times = 3)
        }
    }

    @Test
    fun mcpPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickTestTag(MAIN_BOTTOM_TAB_MCP)
            waitForTestTag(MCP_PAGE_ROOT)
            flingVisibleScrollable(times = 3)
        }
    }

    @Test
    fun baPageInteractions() {
        rule.collect(
            packageName = targetAppId(),
            includeInStartupProfile = false,
        ) {
            launchHomeFromColdStart()

            clickTestTag(MAIN_BOTTOM_TAB_BA)
            waitForTestTag(BA_PAGE_ROOT)
            flingVisibleScrollable(times = 3)
        }
    }
}

private fun MacrobenchmarkScope.waitForHome() {
    check(device.wait(Until.hasObject(By.pkg(targetAppId()).depth(0)), 10_000)) {
        "Timed out waiting for target package ${targetAppId()}"
    }
    waitForTestTag(HOME_PAGE_ROOT, timeoutMs = 10_000)
    device.waitForIdle()
}

private const val MAIN_BOTTOM_TAB_OS = "main_bottom_tab_os"
private const val MAIN_BOTTOM_TAB_MCP = "main_bottom_tab_mcp"
private const val MAIN_BOTTOM_TAB_GITHUB = "main_bottom_tab_github"
private const val MAIN_BOTTOM_TAB_BA = "main_bottom_tab_ba"
private const val HOME_PAGE_ROOT = "home_page_root"
private const val OS_PAGE_ROOT = "os_page_root"
private const val MCP_PAGE_ROOT = "mcp_page_root"
private const val GITHUB_PAGE_ROOT = "github_page_root"
private const val BA_PAGE_ROOT = "ba_page_root"
private const val GITHUB_IMPORT_MENU_BUTTON = "github_import_menu_button"
private const val GITHUB_IMPORT_TRACKS = "github_import_tracks"
private const val GITHUB_IMPORT_STARS = "github_import_stars"

private fun targetAppId(): String {
    val configuredAppId =
        InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")
    return if (configuredAppId == RELEASE_APP_ID) {
        BENCHMARK_APP_ID
    } else {
        configuredAppId
    }
}

private const val RELEASE_APP_ID = "os.kei"
private const val BENCHMARK_APP_ID = "os.kei.benchmark"
private const val LAUNCHER_ACTIVITY = "os.kei.LauncherAppleDesigns"

private fun MacrobenchmarkScope.launchHomeFromColdStart() {
    pressHome()
    grantRuntimePermissions()
    device.executeShellCommand("am force-stop ${targetAppId()}")
    device.executeShellCommand(
        "am start -W -a android.intent.action.MAIN " +
            "-c android.intent.category.LAUNCHER " +
            "-n ${targetAppId()}/$LAUNCHER_ACTIVITY",
    )
    waitForHome()
}

private fun MacrobenchmarkScope.grantRuntimePermissions() {
    listOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.POST_PROMOTED_NOTIFICATIONS",
        "android.permission.ACCESS_LOCAL_NETWORK",
        "android.permission.USE_LOOPBACK_INTERFACE",
    ).forEach { permission ->
        device.executeShellCommand("pm grant ${targetAppId()} $permission >/dev/null 2>&1 || true")
    }
}

private fun MacrobenchmarkScope.testTagSelector(tag: String): BySelector = By.res(tag)

private fun MacrobenchmarkScope.waitForTestTag(
    tag: String,
    timeoutMs: Long = 5_000,
) {
    check(device.wait(Until.hasObject(testTagSelector(tag)), timeoutMs)) {
        "Timed out waiting for testTag=$tag in ${targetAppId()}"
    }
    device.waitForIdle()
}

private fun MacrobenchmarkScope.clickTestTag(
    tag: String,
    timeoutMs: Long = 5_000,
) {
    waitForTestTag(tag, timeoutMs)
    val node = device.findObject(testTagSelector(tag))
    checkNotNull(node) { "Missing testTag=$tag in ${targetAppId()}" }
    node.click()
    device.waitForIdle()
}

private fun MacrobenchmarkScope.flingVisibleScrollable(times: Int) {
    val scrollable = device.findObject(By.scrollable(true)) ?: return
    scrollable.setGestureMargin(device.displayWidth / 5)
    repeat(times) {
        scrollable.fling(Direction.DOWN)
        device.waitForIdle()
    }
}
