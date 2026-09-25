package os.kei.ui.page.main.mcp

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import os.kei.ui.navigation.KeiosRoute
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * The MCP page's ViewModel, created the way the page creates it: inside a miuix-nav entry.
 *
 * Until Miuix 39c40f99 an entry's ViewModelStoreOwner carried no SavedState creation extras, so
 * `createSavedStateHandle()` threw there (upstream #407). The page worked around it with a factory
 * that handed the ViewModel a bare `SavedStateHandle()`. That handle was backed by nothing, so the
 * cards a reader had opened were closed again after process death.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
class McpPageViewModelNavEntryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun openedCardsSurviveProcessDeathInsideTheNavEntry() {
        // The entry's ViewModelStores live in the parent owner's store, so a plain save-and-restore
        // would hand back the same ViewModel. A fresh parent for the second composition is what
        // process death leaves behind: the saved state survives, the ViewModels do not.
        val parents = ArrayDeque(listOf(TestViewModelStoreOwner(), TestViewModelStoreOwner()))
        var viewModel: McpPageViewModel? = null
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            val parent = remember { parents.removeFirst() }
            CompositionLocalProvider(LocalViewModelStoreOwner provides parent) {
                val backStack = rememberNavBackStack<KeiosRoute>(KeiosRoute.Main)
                NavDisplay(backStack = backStack, onBack = {}) {
                    entry<KeiosRoute.Main> {
                        viewModel = viewModel { McpPageViewModel(createSavedStateHandle()) }
                    }
                }
            }
        }

        val beforeDeath =
            composeRule.runOnIdle {
                requireNotNull(viewModel).apply {
                    updateOnboardingExpanded(true)
                    updateLogsExpanded(true)
                }
            }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            val afterDeath = requireNotNull(viewModel)
            assertNotSame(beforeDeath, afterDeath, "The restore must build a new ViewModel")
            with(afterDeath.uiState.value) {
                assertTrue(onboardingExpanded)
                assertTrue(logsExpanded)
                assertFalse(controlExpanded, "Only the cards that were opened come back open")
            }
        }
    }
}

private class TestViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}
