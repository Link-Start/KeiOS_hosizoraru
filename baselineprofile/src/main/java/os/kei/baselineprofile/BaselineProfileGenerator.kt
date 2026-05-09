package os.kei.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

    private val targetAppId: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not passed as instrumentation runner arg")

    @Test
    fun startup() {
        rule.collect(
            packageName = targetAppId,
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
            packageName = targetAppId,
            includeInStartupProfile = false
        ) {
            pressHome()
            startActivityAndWait()
            waitForHome()

            repeat(2) {
                device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
            }
            device.waitForIdle()

            device.findObject(By.text("GitHub"))?.click()
            device.wait(Until.hasObject(By.text("GitHub")), 3_000)
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
