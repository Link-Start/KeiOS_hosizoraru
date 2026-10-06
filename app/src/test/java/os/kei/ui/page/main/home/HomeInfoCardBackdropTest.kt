package os.kei.ui.page.main.home

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.page.main.widget.glass.LocalLiquidParentBackdrop
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class HomeInfoCardBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun batchedCardRespondsToTouchInEmptyAreaBeyondItsPills() {
        var clicks = 0
        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                Box(modifier = Modifier.size(400.dp)) {
                    Box(Modifier.matchParentSize().background(Color.White).layerBackdrop(backdrop))
                    HomeOverviewGlassBatchHost(backdrop = backdrop, blurEnabled = true) {
                        HomeInfoCard(
                            backdrop = backdrop,
                            blurEnabled = true,
                            onClick = { clicks++ },
                            testTag = "overview_click_target",
                        ) {
                            HomeInfoPillCard(
                                pills = listOf(HomeCardPillItem(value = "WebDAV", color = Color.Blue)),
                                naText = "N/A",
                            )
                        }
                    }
                }
            }
        }

        // A real pointer tap on the card's blank right half must reach its clickable, even though
        // the decorative pill material has no outline at this position.
        composeRule.onNodeWithTag("overview_click_target").performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun batchesCardAndPillMaterialWithoutNestedBackdropExport() {
        var sceneBackdrop: Backdrop? = null
        var cardContentBackdrop: Backdrop? = null

        composeRule.setContent {
            MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
                val backdrop = rememberLayerBackdrop()
                sceneBackdrop = backdrop
                Box(modifier = Modifier.size(260.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(Color.White)
                                .layerBackdrop(backdrop),
                    )
                    HomeOverviewGlassBatchHost(
                        backdrop = backdrop,
                        blurEnabled = true,
                    ) {
                        HomeInfoCard(
                            backdrop = backdrop,
                            blurEnabled = true,
                        ) {
                            cardContentBackdrop = LocalLiquidParentBackdrop.current
                            HomeInfoPillCard(
                                pills =
                                    listOf(
                                        HomeCardPillItem(
                                            value = "运行中",
                                            color = Color(0xFF2563EB),
                                        ),
                                    ),
                                naText = "N/A",
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("运行中").assertExists()
        composeRule.runOnIdle {
            assertNotNull(sceneBackdrop)
            assertNull(cardContentBackdrop)
        }
    }
}
