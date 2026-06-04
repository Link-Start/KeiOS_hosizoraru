package os.kei.ui.page.main.widget.sheet

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = SheetContentColumnTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi"
)
class SheetContentColumnTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportsScrollableOverflowWhenContentExceedsAvailableHeight() {
        var overflows: Boolean? = null

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 120.dp)) {
                CompositionLocalProvider(
                    LocalLiquidSheetContentOverflowReporter provides { overflows = it }
                ) {
                    SheetContentColumn(verticalSpacing = 0.dp) {
                        repeat(8) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { overflows == true }
    }

    @Test
    fun reportsNoOverflowWhenScrollingIsDisabled() {
        var overflows = true

        composeRule.setContent {
            Box(modifier = Modifier.size(width = 320.dp, height = 120.dp)) {
                CompositionLocalProvider(
                    LocalLiquidSheetContentOverflowReporter provides { overflows = it }
                ) {
                    SheetContentColumn(
                        scrollable = false,
                        verticalSpacing = 0.dp
                    ) {
                        repeat(8) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .background(Color.Gray)
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        assertFalse(overflows)
    }

    @Test
    fun reportsManagedScrollableContentState() {
        var managedScrollable: Boolean? = null

        composeRule.setContent {
            CompositionLocalProvider(
                LocalLiquidSheetManagedScrollableContentReporter provides {
                    managedScrollable = it
                }
            ) {
                SheetContentColumn(verticalSpacing = 0.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Color.Gray)
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { managedScrollable == true }
    }
}

class SheetContentColumnTestApp : Application()
