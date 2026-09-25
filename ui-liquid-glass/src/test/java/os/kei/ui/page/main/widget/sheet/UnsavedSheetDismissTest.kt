package os.kei.ui.page.main.widget.sheet

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import os.kei.ui.liquidglass.R
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class,
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
)
class UnsavedSheetDismissTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationPreservesMetadataAndDispatchesKeepEditingAction() {
        var keepEditingCount = 0
        var discardChangesCount = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.common_unsaved_changes_title)
        val summary = context.getString(R.string.common_unsaved_changes_summary)
        val keepEditing = context.getString(R.string.common_keep_editing)

        composeRule.setContent {
            UnsavedSheetDismissTestTheme {
                UnsavedSheetDismissConfirmDialog(
                    show = true,
                    onKeepEditing = { keepEditingCount++ },
                    onDiscardChanges = { discardChangesCount++ },
                )
            }
        }

        composeRule.onNodeWithText(title, useUnmergedTree = true).assertTextEquals(title)
        composeRule.onNodeWithText(summary, useUnmergedTree = true).assertTextEquals(summary)
        composeRule
            .onNode(hasText(keepEditing) and hasClickAction())
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, keepEditingCount)
            assertEquals(0, discardChangesCount)
        }
    }

    @Test
    fun confirmationDispatchesDiscardChangesAction() {
        var keepEditingCount = 0
        var discardChangesCount = 0
        val context = ApplicationProvider.getApplicationContext<Context>()
        val discardChanges = context.getString(R.string.common_discard_changes)

        composeRule.setContent {
            UnsavedSheetDismissTestTheme {
                UnsavedSheetDismissConfirmDialog(
                    show = true,
                    onKeepEditing = { keepEditingCount++ },
                    onDiscardChanges = { discardChangesCount++ },
                )
            }
        }

        composeRule
            .onNode(hasText(discardChanges) and hasClickAction())
            .assertHasClickAction()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(0, keepEditingCount)
            assertEquals(1, discardChangesCount)
        }
    }

    @Test
    fun handlerInterceptsUnsavedDismissAndCompletesEveryResolutionPath() {
        val hasUnsavedChanges = mutableStateOf(true)
        var dismissRequestCount = 0
        lateinit var handler: UnsavedSheetDismissHandler

        composeRule.setContent {
            handler =
                rememberUnsavedSheetDismissHandler(
                    hasUnsavedChanges = hasUnsavedChanges.value,
                    onDismissRequest = { dismissRequestCount++ },
                )
        }

        composeRule.runOnIdle {
            assertFalse(handler.allowDismiss)
            assertFalse(handler.showConfirmDialog)
            handler.requestDismiss()
        }
        composeRule.runOnIdle {
            assertTrue(handler.showConfirmDialog)
            assertEquals(0, dismissRequestCount)
            handler.keepEditing()
        }
        composeRule.runOnIdle {
            assertFalse(handler.showConfirmDialog)
            handler.requestDismiss()
        }
        composeRule.runOnIdle {
            assertTrue(handler.showConfirmDialog)
            handler.discardChanges()
        }
        composeRule.runOnIdle {
            assertFalse(handler.showConfirmDialog)
            assertEquals(1, dismissRequestCount)
            hasUnsavedChanges.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(handler.allowDismiss)
            assertFalse(handler.showConfirmDialog)
            handler.requestDismiss()
            assertEquals(2, dismissRequestCount)
        }
    }
}

class UnsavedSheetDismissPresentationContractTest {
    /**
     * Apple's sheet guidance names this exact case: *"If people have unsaved changes in the sheet when
     * they begin swiping to dismiss it, use an action sheet to let them confirm their action."*
     *
     * It used to be miuix's `WindowDialog` inside an `AppLiquidWindowBoundary`, which meant no glass at
     * all — the boundary blanks `LocalSceneBackdrop` and a blur inside a Dialog window draws nothing.
     */
    @Test
    fun confirmationIsAnActionSheetWithTheDiscardMarkedDestructive() {
        val source = unsavedSheetDismissSource(UNSAVED_SHEET_DISMISS_SOURCE)
        val function = source.substringAfter("fun UnsavedSheetDismissConfirmDialog(")

        val actionSheetIndex = function.indexOf("LiquidActionSheet(").markerFound()
        val discardIndex = function.indexOf("R.string.common_discard_changes", actionSheetIndex).markerFound()
        val keepEditingIndex = function.indexOf("R.string.common_keep_editing", discardIndex).markerFound()

        assertTrue(actionSheetIndex < discardIndex)
        assertTrue(
            discardIndex < keepEditingIndex,
            "Destructive first and Cancel last, so the enforced ordering has nothing to reorder",
        )
        assertTrue("role = LiquidActionRole.Destructive" in function)
        assertTrue("role = LiquidActionRole.Cancel" in function)
        assertTrue("onDismissRequest = onKeepEditing" in function)

        assertFalse(
            "AppLiquidWindowBoundary" in function,
            "A Dialog window would take the confirmation's glass away again",
        )
        assertFalse("WindowDialog(" in function)
    }
}

@Composable
private fun UnsavedSheetDismissTestTheme(content: @Composable () -> Unit) {
    MiuixTheme(controller = ThemeController(ColorSchemeMode.Light)) {
        content()
    }
}

private fun Int.markerFound(): Int {
    require(this >= 0) { "Expected source marker was not found" }
    return this
}

private fun unsavedSheetDismissSource(relativePath: String): String {
    val roots = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
    val source = roots.map { File(it, relativePath) }.firstOrNull(File::isFile)
    return requireNotNull(source) {
        "Unable to locate $relativePath from ${System.getProperty("user.dir")}"
    }.readText()
}

private const val UNSAVED_SHEET_DISMISS_SOURCE =
    "ui-liquid-glass/src/main/java/os/kei/ui/page/main/widget/sheet/UnsavedSheetDismiss.kt"
