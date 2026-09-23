package os.kei.ui.page.main.host.pager

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * What a tab selection promises its caller, whichever animation carries it: a jump of more than
 * one page is bracketed by the dim hooks, an adjacent one is not, and the pager ends exactly on the
 * target page rather than a fraction short of it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = PagerSwitchAnimationTestApp::class, sdk = [35])
class PagerSwitchAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aFarJumpDimsAroundTheMoveAndLandsOnTheTarget() {
        val (pagerState, scope) = setPager()
        val events = mutableListOf<String>()

        composeRule.runOnIdle {
            scope.launch {
                pagerState.animateTabSwitch(
                    fromIndex = 0,
                    targetIndex = 4,
                    onFarJumpBefore = { events += "dim at ${pagerState.currentPage}" },
                    onFarJumpAfter = { events += "restore at ${pagerState.landing()}" },
                )
                events += "done"
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf("dim at 0", "restore at 4+0.0", "done"), events)
        assertEquals(4, pagerState.settledPage)
    }

    @Test
    fun anAdjacentJumpMovesWithoutDimming() {
        val (pagerState, scope) = setPager()
        val events = mutableListOf<String>()

        composeRule.runOnIdle {
            scope.launch {
                pagerState.animateTabSwitch(
                    fromIndex = 0,
                    targetIndex = 1,
                    onFarJumpBefore = { events += "dim" },
                    onFarJumpAfter = { events += "restore" },
                )
                events += "done at ${pagerState.landing()}"
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf("done at 1+0.0"), events)
    }

    private fun setPager(): Pair<PagerState, CoroutineScope> {
        lateinit var pagerState: PagerState
        lateinit var scope: CoroutineScope
        composeRule.setContent {
            pagerState = rememberPagerState(pageCount = { 6 })
            scope = rememberCoroutineScope()
            HorizontalPager(state = pagerState, modifier = Modifier.size(width = 320.dp, height = 200.dp)) {
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        return composeRule.runOnIdle { pagerState to scope }
    }

    private fun PagerState.landing(): String = "$currentPage+$currentPageOffsetFraction"
}

class PagerSwitchAnimationTestApp : Application()
