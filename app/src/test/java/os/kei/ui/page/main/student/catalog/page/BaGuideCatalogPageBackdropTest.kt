package os.kei.ui.page.main.student.catalog.page

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = BaGuideCatalogPageBackdropTestApp::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class BaGuideCatalogPageBackdropTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sceneBackdropIsIndependentAndStableAcrossRecomposition() {
        lateinit var recompositionSignal: MutableIntState
        val observedSceneBackdrops = mutableListOf<Pair<Int, Backdrop>>()
        var pageChromeBackdrop: Backdrop? = null
        var bottomChromeBackdrop: Backdrop? = null

        composeRule.setContent {
            val signal = remember { mutableIntStateOf(0) }
            recompositionSignal = signal
            val currentPageChromeBackdrop = rememberLayerBackdrop()
            val currentBottomChromeBackdrop = rememberLayerBackdrop()
            val currentSceneBackdrop = rememberBaGuideCatalogSceneBackdrop()
            val stateRevision = signal.intValue

            SideEffect {
                pageChromeBackdrop = currentPageChromeBackdrop
                bottomChromeBackdrop = currentBottomChromeBackdrop
                observedSceneBackdrops += stateRevision to currentSceneBackdrop
            }
            Box(modifier = Modifier.size(1.dp))
        }

        composeRule.runOnIdle {
            val firstSceneBackdrop = observedSceneBackdrops.first().second
            assertNotNull(pageChromeBackdrop)
            assertNotNull(bottomChromeBackdrop)
            assertNotSame(pageChromeBackdrop, firstSceneBackdrop)
            assertNotSame(bottomChromeBackdrop, firstSceneBackdrop)
            recompositionSignal.intValue += 1
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, observedSceneBackdrops.last().first)
            observedSceneBackdrops.forEach { (_, backdrop) ->
                assertSame(observedSceneBackdrops.first().second, backdrop)
            }
        }
    }

}

class BaGuideCatalogPageBackdropTestApp : Application()
